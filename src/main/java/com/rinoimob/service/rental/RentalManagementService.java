package com.rinoimob.service.rental;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.rental.RentalDtos.*;
import com.rinoimob.domain.entity.*;
import com.rinoimob.domain.enums.PropertyStatus;
import com.rinoimob.domain.enums.RentalEnums.*;
import com.rinoimob.domain.repository.*;
import com.rinoimob.service.storage.FileStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class RentalManagementService {
    private final RentalPartyRepository parties;
    private final RentalContractRepository contracts;
    private final RentalContractPartyRepository contractParties;
    private final RentalDocumentRepository documents;
    private final PropertyRepository properties;
    private final LeadRepository leads;
    private final FileStorageService storage;

    public RentalManagementService(RentalPartyRepository parties, RentalContractRepository contracts,
                                   RentalContractPartyRepository contractParties, RentalDocumentRepository documents,
                                   PropertyRepository properties, LeadRepository leads, FileStorageService storage) {
        this.parties = parties; this.contracts = contracts; this.contractParties = contractParties;
        this.documents = documents; this.properties = properties; this.leads = leads; this.storage = storage;
    }

    public List<PartyResponse> listParties() {
        return parties.findByTenantIdAndDeletedAtIsNullOrderByNameAsc(tenant()).stream().map(this::partyResponse).toList();
    }

    @Transactional
    public PartyResponse createParty(PartyRequest request) {
        RentalParty party = new RentalParty();
        applyParty(party, request); party.setTenantId(tenant());
        return partyResponse(parties.save(party));
    }

    @Transactional
    public PartyResponse updateParty(UUID id, PartyRequest request) {
        RentalParty party = party(id); applyParty(party, request);
        return partyResponse(parties.save(party));
    }

    public List<ContractResponse> listContracts() {
        return contracts.findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(tenant()).stream().map(this::contractResponse).toList();
    }

    public ContractResponse getContract(UUID id) { return contractResponse(contract(id)); }

    @Transactional
    public ContractResponse createContract(ContractRequest request) {
        UUID tenantId = tenant();
        if (request.endDate().isBefore(request.startDate())) throw bad("Contract end date must not precede start date");
        properties.findByIdAndTenantIdAndDeletedAtIsNull(request.propertyId(), tenantId)
                .orElseThrow(() -> notFound("Property not found"));
        if (request.sourceLeadId() != null) leads.findByIdAndTenantIdAndDeletedAtIsNull(request.sourceLeadId(), tenantId)
                .orElseThrow(() -> notFound("Lead not found"));
        validateContractParties(request.parties());
        RentalContract contract = new RentalContract();
        contract.setTenantId(tenantId); applyContract(contract, request);
        contract = contracts.save(contract);
        saveContractParties(contract.getId(), request.parties());
        return contractResponse(contract);
    }

    @Transactional
    public ContractResponse updateContract(UUID id, ContractRequest request) {
        RentalContract contract = contract(id);
        if (contract.getStatus() != ContractStatus.DRAFT) throw bad("Only draft contracts can be edited");
        validateContractParties(request.parties()); applyContract(contract, request);
        contractParties.deleteByTenantIdAndContractId(tenant(), id);
        saveContractParties(id, request.parties());
        return contractResponse(contracts.save(contract));
    }

    @Transactional
    public ContractResponse activate(UUID id) {
        RentalContract contract = contract(id); UUID tenantId = tenant();
        if (contract.getStatus() != ContractStatus.DRAFT) throw bad("Only draft contracts can be activated");
        if (contracts.existsByTenantIdAndPropertyIdAndStatusAndDeletedAtIsNull(tenantId, contract.getPropertyId(), ContractStatus.ACTIVE))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Property already has an active rental contract");
        List<RentalContractParty> links = contractParties.findByTenantIdAndContractId(tenantId, id);
        if (links.stream().noneMatch(link -> link.getRole() == PartyRole.OWNER)
                || links.stream().noneMatch(link -> link.getRole() == PartyRole.TENANT && link.isBillingResponsible()))
            throw bad("Contract requires an owner and one billing-responsible tenant");
        Property property = properties.findByIdAndTenantIdAndDeletedAtIsNull(contract.getPropertyId(), tenantId)
                .orElseThrow(() -> notFound("Property not found"));
        contract.setStatus(ContractStatus.ACTIVE); contract.setActivatedAt(LocalDateTime.now());
        property.setStatus(PropertyStatus.RENTED); properties.save(property);
        return contractResponse(contracts.save(contract));
    }

    @Transactional
    public ContractResponse terminate(UUID id) {
        RentalContract contract = contract(id);
        if (contract.getStatus() != ContractStatus.ACTIVE) throw bad("Only active contracts can be terminated");
        contract.setStatus(ContractStatus.TERMINATED); contract.setEndedAt(LocalDateTime.now());
        properties.findByIdAndTenantIdAndDeletedAtIsNull(contract.getPropertyId(), tenant()).ifPresent(property -> {
            property.setStatus(PropertyStatus.DRAFT); properties.save(property);
        });
        return contractResponse(contracts.save(contract));
    }

    @Transactional
    public ContractResponse applyAdjustment(UUID id, AdjustmentRequest request) {
        RentalContract contract = contract(id);
        if (contract.getStatus() != ContractStatus.ACTIVE) throw bad("Only active contracts can be adjusted");
        if (!request.nextAdjustmentDate().isAfter(LocalDateTime.now().toLocalDate())) throw bad("Next adjustment date must be in the future");
        contract.setBaseRent(request.newBaseRent()); contract.setNextAdjustmentDate(request.nextAdjustmentDate());
        return contractResponse(contracts.save(contract));
    }

    @Transactional
    public DocumentResponse uploadDocument(UUID contractId, String name, MultipartFile file) {
        contract(contractId);
        if (file == null || file.isEmpty()) throw bad("Document file is required");
        if (!"application/pdf".equalsIgnoreCase(file.getContentType())) throw bad("Only PDF documents are accepted");
        FileStorageService.UploadResult uploaded = storage.upload(file);
        RentalDocument document = new RentalDocument(); document.setTenantId(tenant()); document.setContractId(contractId);
        document.setName(name == null || name.isBlank() ? file.getOriginalFilename() : name);
        document.setFileFid(uploaded.fid()); document.setFileUrl(uploaded.url());
        return documentResponse(documents.save(document));
    }

    @Transactional
    public void deleteDocument(UUID contractId, UUID documentId) {
        contract(contractId);
        RentalDocument document = documents.findByIdAndTenantIdAndContractId(documentId, tenant(), contractId)
                .orElseThrow(() -> notFound("Document not found"));
        storage.delete(document.getFileFid(), document.getFileUrl()); documents.delete(document);
    }

    public RentalContract contractEntity(UUID id) { return contract(id); }
    public RentalParty partyEntity(UUID id) { return party(id); }

    private void applyParty(RentalParty party, PartyRequest request) {
        party.setType(request.type()); party.setName(request.name().trim());
        party.setCpfCnpj(digits(request.cpfCnpj())); party.setEmail(blank(request.email())); party.setPhone(blank(request.phone()));
        party.setAddress(blank(request.address())); party.setPixKey(blank(request.pixKey())); party.setPixKeyType(blank(request.pixKeyType()));
    }
    private void applyContract(RentalContract c, ContractRequest r) {
        c.setPropertyId(r.propertyId()); c.setSourceLeadId(r.sourceLeadId()); c.setStartDate(r.startDate()); c.setEndDate(r.endDate());
        c.setDueDay(r.dueDay()); c.setChargeLeadDays(r.chargeLeadDays()); c.setPayoutDay(r.payoutDay());
        c.setBillingType(r.billingType() == null ? "BOLETO" : r.billingType()); c.setBaseRent(r.baseRent());
        c.setCondoAmount(zero(r.condoAmount())); c.setIptuAmount(zero(r.iptuAmount())); c.setInsuranceAmount(zero(r.insuranceAmount()));
        c.setDiscountAmount(zero(r.discountAmount())); c.setManagementFeeType(r.managementFeeType()); c.setManagementFeeValue(r.managementFeeValue());
        c.setLateFeePercent(r.lateFeePercent() == null ? BigDecimal.valueOf(2) : r.lateFeePercent());
        c.setMonthlyInterestPercent(r.monthlyInterestPercent() == null ? BigDecimal.ONE : r.monthlyInterestPercent());
        c.setGuaranteeType(r.guaranteeType()); c.setGuaranteeDetails(r.guaranteeDetails());
        c.setAdjustmentIndex(r.adjustmentIndex()); c.setNextAdjustmentDate(r.nextAdjustmentDate());
    }
    private void validateContractParties(List<ContractPartyRequest> values) {
        UUID tenantId = tenant(); long billing = values.stream().filter(v -> v.role() == PartyRole.TENANT && v.billingResponsible()).count();
        if (billing != 1) throw bad("Exactly one tenant must be billing responsible");
        if (values.stream().noneMatch(v -> v.role() == PartyRole.OWNER)) throw bad("At least one owner is required");
        for (ContractPartyRequest value : values) parties.findByIdAndTenantIdAndDeletedAtIsNull(value.partyId(), tenantId)
                .orElseThrow(() -> notFound("Rental party not found"));
        BigDecimal shares = values.stream().filter(v -> v.role() == PartyRole.OWNER)
                .map(v -> v.ownershipPercent() == null ? BigDecimal.ZERO : v.ownershipPercent()).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (shares.compareTo(BigDecimal.valueOf(100)) != 0) throw bad("Owner participation must total 100%");
    }
    private void saveContractParties(UUID contractId, List<ContractPartyRequest> values) {
        UUID tenantId = tenant();
        contractParties.saveAll(values.stream().map(value -> {
            RentalContractParty link = new RentalContractParty(); link.setTenantId(tenantId); link.setContractId(contractId);
            link.setPartyId(value.partyId()); link.setRole(value.role()); link.setOwnershipPercent(value.ownershipPercent());
            link.setBillingResponsible(value.billingResponsible()); return link;
        }).toList());
    }
    private ContractResponse contractResponse(RentalContract c) {
        UUID tenantId = tenant();
        List<ContractPartyResponse> links = contractParties.findByTenantIdAndContractId(tenantId, c.getId()).stream().map(link -> {
            RentalParty party = parties.findByIdAndTenantIdAndDeletedAtIsNull(link.getPartyId(), tenantId).orElse(null);
            return new ContractPartyResponse(link.getId(), link.getPartyId(), party == null ? "Removido" : party.getName(), link.getRole(), link.getOwnershipPercent(), link.isBillingResponsible());
        }).toList();
        List<DocumentResponse> docs = documents.findByTenantIdAndContractIdOrderByCreatedAtDesc(tenantId, c.getId()).stream().map(this::documentResponse).toList();
        return new ContractResponse(c.getId(), c.getPropertyId(), c.getSourceLeadId(), c.getStatus(), c.getStartDate(), c.getEndDate(), c.getDueDay(),
                c.getChargeLeadDays(), c.getPayoutDay(), c.getBillingType(), c.getBaseRent(), c.getCondoAmount(), c.getIptuAmount(), c.getInsuranceAmount(),
                c.getDiscountAmount(), c.getManagementFeeType(), c.getManagementFeeValue(), c.getLateFeePercent(), c.getMonthlyInterestPercent(),
                c.getGuaranteeType(), c.getGuaranteeDetails(), c.getAdjustmentIndex(), c.getNextAdjustmentDate(), links, docs, c.getActivatedAt(), c.getEndedAt());
    }
    private PartyResponse partyResponse(RentalParty p) { return new PartyResponse(p.getId(), p.getType(), p.getName(), mask(p.getCpfCnpj()), p.getEmail(), p.getPhone(), p.getAddress(), mask(p.getPixKey()), p.getPixKeyType()); }
    private DocumentResponse documentResponse(RentalDocument d) { return new DocumentResponse(d.getId(), d.getName(), d.getFileUrl(), d.getCreatedAt()); }
    private RentalContract contract(UUID id) { return contracts.findByIdAndTenantIdAndDeletedAtIsNull(id, tenant()).orElseThrow(() -> notFound("Rental contract not found")); }
    private RentalParty party(UUID id) { return parties.findByIdAndTenantIdAndDeletedAtIsNull(id, tenant()).orElseThrow(() -> notFound("Rental party not found")); }
    private UUID tenant() { String value = TenantContext.getTenantId(); if (value == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED); return UUID.fromString(value); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private String digits(String value) { return value == null || value.isBlank() ? null : value.replaceAll("\\D", ""); }
    private String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String mask(String value) { if (value == null || value.length() < 5) return value; return "***" + value.substring(value.length() - 4); }
}
