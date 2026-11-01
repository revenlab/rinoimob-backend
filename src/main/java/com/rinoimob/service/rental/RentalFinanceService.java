package com.rinoimob.service.rental;

import com.fasterxml.jackson.databind.JsonNode;
import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.rental.RentalDtos.*;
import com.rinoimob.domain.entity.*;
import com.rinoimob.domain.enums.RentalEnums.*;
import com.rinoimob.domain.repository.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.*;
import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class RentalFinanceService {
    private static final Logger log=LoggerFactory.getLogger(RentalFinanceService.class);
    private final RentalContractRepository contracts; private final RentalContractPartyRepository contractParties;
    private final RentalPartyRepository parties; private final RentalChargeRepository charges; private final RentalChargeItemRepository items;
    private final RentalPayoutRepository payouts; private final RentalAsaasAccountRepository accounts; private final RentalAsaasService asaas;
    public RentalFinanceService(RentalContractRepository contracts, RentalContractPartyRepository contractParties,
            RentalPartyRepository parties, RentalChargeRepository charges, RentalChargeItemRepository items,
            RentalPayoutRepository payouts, RentalAsaasAccountRepository accounts, RentalAsaasService asaas) {
        this.contracts=contracts; this.contractParties=contractParties; this.parties=parties; this.charges=charges; this.items=items; this.payouts=payouts; this.accounts=accounts; this.asaas=asaas;
    }
    public List<ChargeResponse> listCharges() { return charges.findByTenantIdOrderByDueDateDesc(tenant()).stream().map(this::chargeResponse).toList(); }
    public List<PayoutResponse> listPayouts() { return payouts.findByTenantIdOrderByCreatedAtDesc(tenant()).stream().map(this::payoutResponse).toList(); }
    public DashboardResponse dashboard() { UUID t=tenant(); return new DashboardResponse(contracts.countByTenantIdAndStatusAndDeletedAtIsNull(t,ContractStatus.ACTIVE), charges.countByTenantIdAndStatus(t,ChargeStatus.OVERDUE), payouts.countByTenantIdAndStatusIn(t,List.of(PayoutStatus.DRAFT,PayoutStatus.READY,PayoutStatus.FAILED)), charges.sumReceivedSince(t,ChargeStatus.RECEIVED,LocalDate.now().withDayOfMonth(1).atStartOfDay())); }

    @Transactional
    public ChargeResponse generate(UUID contractId, GenerateChargeRequest request) {
        UUID t=tenant(); RentalContract c=contracts.findByIdAndTenantIdAndDeletedAtIsNull(contractId,t).orElseThrow(()->notFound("Contract not found"));
        if(c.getStatus()!=ContractStatus.ACTIVE) throw bad("Only active contracts can generate charges");
        return charges.findByTenantIdAndContractIdAndCompetence(t,contractId,request.competence().withDayOfMonth(1)).map(this::chargeResponse).orElseGet(()->createCharge(c,request));
    }
    private ChargeResponse createCharge(RentalContract c, GenerateChargeRequest request) {
        UUID t=tenant(); RentalAsaasAccount account=asaas.requiredAccount(t); LocalDate competence=request.competence().withDayOfMonth(1);
        List<ChargeItemRequest> values=new ArrayList<>(); values.add(new ChargeItemRequest("RENT","Aluguel",c.getBaseRent(),PayoutTreatment.OWNER));
        add(values,"CONDO","Condomínio",c.getCondoAmount(),PayoutTreatment.OWNER); add(values,"IPTU","IPTU",c.getIptuAmount(),PayoutTreatment.OWNER); add(values,"INSURANCE","Seguro",c.getInsuranceAmount(),PayoutTreatment.OWNER);
        if(c.getDiscountAmount().signum()>0) values.add(new ChargeItemRequest("DISCOUNT","Desconto",c.getDiscountAmount().negate(),PayoutTreatment.OWNER));
        if(request.additionalItems()!=null) values.addAll(request.additionalItems());
        BigDecimal total=values.stream().map(ChargeItemRequest::amount).reduce(BigDecimal.ZERO,BigDecimal::add).setScale(2,RoundingMode.HALF_UP);
        if(total.signum()<=0) throw bad("Charge total must be positive");
        BigDecimal fee=c.getBaseRent().multiply(account.getRinoFeePercent()).divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP);
        RentalCharge charge=new RentalCharge(); charge.setTenantId(t); charge.setContractId(c.getId()); charge.setCompetence(competence); charge.setDueDate(request.dueDate()); charge.setTotalAmount(total); charge.setBaseRentAmount(c.getBaseRent()); charge.setRinoFeePercent(account.getRinoFeePercent()); charge.setRinoFeeAmount(fee); charge.setBillingType(c.getBillingType()); charge=charges.save(charge);
        UUID chargeId=charge.getId(); for(ChargeItemRequest value:values){ RentalChargeItem item=new RentalChargeItem(); item.setTenantId(t); item.setChargeId(chargeId); item.setType(value.type()); item.setDescription(value.description()); item.setAmount(value.amount()); item.setPayoutTreatment(value.payoutTreatment()); items.save(item); }
        return chargeResponse(charge);
    }
    @Transactional
    public ChargeResponse issue(UUID id) {
        UUID t=tenant(); RentalCharge charge=charges.findByIdAndTenantId(id,t).orElseThrow(()->notFound("Charge not found")); if(charge.getStatus()!=ChargeStatus.DRAFT&&charge.getStatus()!=ChargeStatus.ERROR) throw bad("Only draft or failed charges can be issued");
        RentalContract contract=contracts.findByIdAndTenantIdAndDeletedAtIsNull(charge.getContractId(),t).orElseThrow(()->notFound("Contract not found")); RentalAsaasAccount account=asaas.requiredAccount(t);
        RentalContractParty debtorLink=contractParties.findByTenantIdAndContractIdAndRole(t,contract.getId(),PartyRole.TENANT).stream().filter(RentalContractParty::isBillingResponsible).findFirst().orElseThrow(()->bad("Billing-responsible tenant not found"));
        RentalParty debtor=parties.findByIdAndTenantIdAndDeletedAtIsNull(debtorLink.getPartyId(),t).orElseThrow(()->notFound("Tenant party not found"));
        try { charge.setStatus(ChargeStatus.ISSUING); charges.save(charge); String customerId=debtor.getProviderCustomerId(); if(customerId==null){ Map<String,Object> customer=new LinkedHashMap<>(); customer.put("name",debtor.getName()); customer.put("cpfCnpj",debtor.getCpfCnpj()); if(debtor.getEmail()!=null) customer.put("email",debtor.getEmail()); if(debtor.getPhone()!=null) customer.put("mobilePhone",debtor.getPhone()); customer.put("externalReference","rental-party:"+debtor.getId()); Map<?,?> created=asaas.createCustomer(account,customer); customerId=text(created,"id"); debtor.setProviderCustomerId(customerId); parties.save(debtor); }
            String externalReference="rental-charge:"+charge.getId(); Map<String,Object> body=new LinkedHashMap<>(); body.put("customer",customerId); body.put("billingType",charge.getBillingType()); body.put("value",charge.getTotalAmount()); body.put("dueDate",charge.getDueDate().toString()); body.put("description","Aluguel "+charge.getCompetence()); body.put("externalReference",externalReference); body.put("fine",Map.of("value",contract.getLateFeePercent())); body.put("interest",Map.of("value",contract.getMonthlyInterestPercent())); if(charge.getRinoFeeAmount().signum()>0) body.put("split",List.of(Map.of("walletId",asaas.rinoWalletId(),"fixedValue",charge.getRinoFeeAmount())));
            Map<?,?> result=asaas.findPayment(account,externalReference); if(result==null)result=asaas.createPayment(account,body); charge.setProviderPaymentId(text(result,"id")); charge.setProviderCustomerId(customerId); charge.setInvoiceUrl(text(result,"invoiceUrl")); charge.setStatus(mapPaymentStatus(text(result,"status"))); charge.setErrorMessage(null);
        } catch(Exception e){ charge.setStatus(ChargeStatus.ERROR); charge.setErrorMessage(limit(e.getMessage())); }
        return chargeResponse(charges.save(charge));
    }
    @Transactional
    public PayoutResponse approvePayout(UUID id) {
        UUID t=tenant(); RentalPayout payout=payouts.findByIdAndTenantId(id,t).orElseThrow(()->notFound("Payout not found")); if(!List.of(PayoutStatus.DRAFT,PayoutStatus.READY,PayoutStatus.FAILED).contains(payout.getStatus())) throw bad("Payout cannot be approved in its current status");
        RentalParty owner=parties.findByIdAndTenantIdAndDeletedAtIsNull(payout.getOwnerPartyId(),t).orElseThrow(()->notFound("Owner not found")); if(owner.getPixKey()==null||owner.getPixKeyType()==null) throw bad("Owner Pix key is required");
        try { Map<String,Object> body=new LinkedHashMap<>(); body.put("value",payout.getNetAmount()); body.put("pixAddressKey",owner.getPixKey()); body.put("pixAddressKeyType",owner.getPixKeyType()); body.put("description","Repasse de aluguel"); body.put("externalReference","rental-payout:"+payout.getId()); if(payout.getScheduledDate()!=null&&!payout.getScheduledDate().isBefore(LocalDate.now())) body.put("scheduleDate",payout.getScheduledDate().toString()); Map<?,?> result=asaas.createTransfer(asaas.requiredAccount(t),body); payout.setProviderTransferId(text(result,"id")); payout.setStatus("DONE".equals(text(result,"status"))?PayoutStatus.PAID:PayoutStatus.PROCESSING); payout.setApprovedByUserId(TenantContext.getUserId()); payout.setApprovedAt(LocalDateTime.now()); if(payout.getStatus()==PayoutStatus.PAID)payout.setCompletedAt(LocalDateTime.now()); payout.setErrorMessage(null); } catch(Exception e){ payout.setStatus(PayoutStatus.FAILED); payout.setErrorMessage(limit(e.getMessage())); }
        return payoutResponse(payouts.save(payout));
    }
    @Transactional
    public UUID handleWebhook(JsonNode payload) {
        JsonNode payment=payload.path("payment"); JsonNode transfer=payload.path("transfer"); String external=first(text(payment,"externalReference"),text(transfer,"externalReference")); String event=text(payload,"event");
        if(external==null) return null;
        if(external.startsWith("rental-charge:")){ UUID id=UUID.fromString(external.substring(14)); RentalCharge c=charges.findById(id).orElseThrow(()->notFound("Rental charge not found for webhook")); applyPaymentEvent(c,event,payment); charges.save(c); if(c.getStatus()==ChargeStatus.RECEIVED) createPayouts(c); return c.getTenantId(); }
        if(external.startsWith("rental-payout:")){ UUID id=UUID.fromString(external.substring(14)); RentalPayout p=payouts.findById(id).orElseThrow(()->notFound("Rental payout not found for webhook")); if(p.getStatus()==PayoutStatus.PAID&&!"TRANSFER_DONE".equals(event))return p.getTenantId(); if("TRANSFER_DONE".equals(event)){p.setStatus(PayoutStatus.PAID);p.setCompletedAt(LocalDateTime.now());} else if("TRANSFER_FAILED".equals(event)||"TRANSFER_CANCELLED".equals(event)){p.setStatus(PayoutStatus.FAILED);p.setErrorMessage(first(text(transfer,"failReason"),event));} else p.setStatus(PayoutStatus.PROCESSING); payouts.save(p); return p.getTenantId(); }
        return null;
    }
    public boolean supports(JsonNode payload){ String e=first(text(payload.path("payment"),"externalReference"),text(payload.path("transfer"),"externalReference")); return e!=null&&(e.startsWith("rental-charge:")||e.startsWith("rental-payout:")); }
    @Scheduled(cron="${rental.charge-generation-cron:0 15 2 * * *}")
    @Transactional
    public void generateScheduledCharges(){ LocalDate today=LocalDate.now(); for(RentalContract contract:contracts.findByStatusAndDeletedAtIsNull(ContractStatus.ACTIVE)){ try{ LocalDate due=today.withDayOfMonth(Math.min(contract.getDueDay(),today.lengthOfMonth())); if(today.isAfter(due)) due=due.plusMonths(1).withDayOfMonth(Math.min(contract.getDueDay(),due.plusMonths(1).lengthOfMonth())); LocalDate competence=due.withDayOfMonth(1); if(due.minusDays(contract.getChargeLeadDays()).isAfter(today)||due.isAfter(contract.getEndDate())||due.isBefore(contract.getStartDate()))continue; if(charges.findByTenantIdAndContractIdAndCompetence(contract.getTenantId(),contract.getId(),competence).isEmpty())createCharge(contract,new GenerateChargeRequest(competence,due,List.of())); }catch(Exception exception){log.error("Could not generate rental charge for contract {}",contract.getId(),exception);} } }
    private void applyPaymentEvent(RentalCharge c,String event,JsonNode payment){ boolean refund="PAYMENT_REFUNDED".equals(event)||"PAYMENT_PARTIALLY_REFUNDED".equals(event); if((c.getStatus()==ChargeStatus.REFUNDED&&!refund)||(c.getStatus()==ChargeStatus.RECEIVED&&!refund&&!("PAYMENT_RECEIVED".equals(event)||"PAYMENT_CONFIRMED".equals(event))))return; switch(event){case "PAYMENT_RECEIVED","PAYMENT_CONFIRMED"->{c.setStatus(ChargeStatus.RECEIVED);c.setReceivedAt(LocalDateTime.now());c.setReceiptUrl(text(payment,"transactionReceiptUrl"));} case "PAYMENT_OVERDUE"->c.setStatus(ChargeStatus.OVERDUE); case "PAYMENT_REFUNDED","PAYMENT_PARTIALLY_REFUNDED"->{c.setStatus(ChargeStatus.REFUNDED);for(RentalPayout p:payouts.findByTenantIdOrderByCreatedAtDesc(c.getTenantId()).stream().filter(p->p.getChargeId().equals(c.getId())).toList()){p.setErrorMessage("Cobrança estornada após geração do repasse; conciliação manual obrigatória");if(p.getStatus()!=PayoutStatus.PAID)p.setStatus(PayoutStatus.FAILED);payouts.save(p);}} case "PAYMENT_DELETED"->c.setStatus(ChargeStatus.CANCELLED); default->c.setStatus(mapPaymentStatus(text(payment,"status")));} c.setInvoiceUrl(first(text(payment,"invoiceUrl"),c.getInvoiceUrl())); c.setLastProviderEventAt(LocalDateTime.now()); }
    private void createPayouts(RentalCharge charge){ UUID t=charge.getTenantId(); RentalContract contract=contracts.findByIdAndTenantIdAndDeletedAtIsNull(charge.getContractId(),t).orElseThrow(); BigDecimal ownerGross=items.findByTenantIdAndChargeIdOrderByCreatedAtAsc(t,charge.getId()).stream().filter(i->i.getPayoutTreatment()==PayoutTreatment.OWNER).map(RentalChargeItem::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add); BigDecimal management=contract.getManagementFeeType()==ManagementFeeType.PERCENTAGE?contract.getBaseRent().multiply(contract.getManagementFeeValue()).divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP):contract.getManagementFeeValue(); for(RentalContractParty link:contractParties.findByTenantIdAndContractIdAndRole(t,contract.getId(),PartyRole.OWNER)){ BigDecimal share=link.getOwnershipPercent().divide(BigDecimal.valueOf(100),8,RoundingMode.HALF_UP); if(payouts.findByTenantIdAndChargeIdAndOwnerPartyId(t,charge.getId(),link.getPartyId()).isPresent())continue; RentalPayout p=new RentalPayout();p.setTenantId(t);p.setContractId(contract.getId());p.setChargeId(charge.getId());p.setOwnerPartyId(link.getPartyId());p.setGrossOwnerAmount(ownerGross.multiply(share).setScale(2,RoundingMode.HALF_UP));p.setManagementFeeAmount(management.multiply(share).setScale(2,RoundingMode.HALF_UP));p.setNetAmount(p.getGrossOwnerAmount().subtract(p.getManagementFeeAmount()));LocalDate scheduled=charge.getDueDate().withDayOfMonth(Math.min(contract.getPayoutDay(),charge.getDueDate().lengthOfMonth()));if(scheduled.isBefore(charge.getDueDate()))scheduled=scheduled.plusMonths(1);p.setScheduledDate(scheduled);p.setStatus(PayoutStatus.READY);payouts.save(p); } }
    private ChargeResponse chargeResponse(RentalCharge c){ List<ChargeItemResponse> lineItems=items.findByTenantIdAndChargeIdOrderByCreatedAtAsc(c.getTenantId(),c.getId()).stream().map(i->new ChargeItemResponse(i.getId(),i.getType(),i.getDescription(),i.getAmount(),i.getPayoutTreatment())).toList(); return new ChargeResponse(c.getId(),c.getContractId(),c.getCompetence(),c.getDueDate(),c.getStatus(),c.getTotalAmount(),c.getBaseRentAmount(),c.getRinoFeePercent(),c.getRinoFeeAmount(),c.getInvoiceUrl(),c.getReceiptUrl(),c.getBillingType(),lineItems,c.getReceivedAt(),c.getErrorMessage()); }
    private PayoutResponse payoutResponse(RentalPayout p){ String owner=parties.findByIdAndTenantIdAndDeletedAtIsNull(p.getOwnerPartyId(),p.getTenantId()).map(RentalParty::getName).orElse("Removido");return new PayoutResponse(p.getId(),p.getContractId(),p.getChargeId(),p.getOwnerPartyId(),owner,p.getStatus(),p.getGrossOwnerAmount(),p.getManagementFeeAmount(),p.getNetAmount(),p.getScheduledDate(),p.getApprovedAt(),p.getCompletedAt(),p.getErrorMessage());}
    private ChargeStatus mapPaymentStatus(String s){ if(s==null)return ChargeStatus.PENDING; return switch(s){case "RECEIVED","CONFIRMED","RECEIVED_IN_CASH"->ChargeStatus.RECEIVED;case "OVERDUE"->ChargeStatus.OVERDUE;case "REFUNDED","PARTIALLY_REFUNDED"->ChargeStatus.REFUNDED;case "DELETED"->ChargeStatus.CANCELLED;default->ChargeStatus.PENDING;}; }
    private void add(List<ChargeItemRequest> v,String t,String d,BigDecimal a,PayoutTreatment p){if(a!=null&&a.signum()!=0)v.add(new ChargeItemRequest(t,d,a,p));}
    private UUID tenant(){String v=TenantContext.getTenantId();if(v==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);return UUID.fromString(v);} private ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);} private ResponseStatusException notFound(String m){return new ResponseStatusException(HttpStatus.NOT_FOUND,m);} private String text(Map<?,?>m,String k){Object v=m.get(k);return v==null?null:String.valueOf(v);} private String text(JsonNode n,String k){JsonNode v=n.get(k);return v==null||v.isNull()?null:v.asText();} private String first(String...v){for(String s:v)if(s!=null&&!s.isBlank())return s;return null;} private String limit(String s){return s==null?null:s.substring(0,Math.min(2000,s.length()));}
}
