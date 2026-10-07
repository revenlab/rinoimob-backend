package com.rinoimob.service.rental;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.rental.RentalDtos.GenerateChargeRequest;
import com.rinoimob.domain.entity.*;
import com.rinoimob.domain.enums.RentalEnums.*;
import com.rinoimob.domain.repository.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RentalFinanceServiceTest {
    private final UUID tenantId=UUID.randomUUID(),contractId=UUID.randomUUID();
    private RentalContractRepository contracts; private RentalChargeRepository charges; private RentalChargeItemRepository items;
    private RentalFinanceService service;
    @BeforeEach void setup(){contracts=mock(RentalContractRepository.class); RentalContractPartyRepository links=mock(RentalContractPartyRepository.class); RentalPartyRepository parties=mock(RentalPartyRepository.class); charges=mock(RentalChargeRepository.class); items=mock(RentalChargeItemRepository.class); RentalPayoutRepository payouts=mock(RentalPayoutRepository.class); RentalAsaasAccountRepository accounts=mock(RentalAsaasAccountRepository.class); RentalAsaasService asaas=mock(RentalAsaasService.class); service=new RentalFinanceService(contracts,links,parties,charges,items,payouts,accounts,asaas); TenantContext.setTenantId(tenantId.toString()); RentalAsaasAccount account=new RentalAsaasAccount();account.setTenantId(tenantId);account.setRinoFeePercent(new BigDecimal("1.50"));when(asaas.requiredAccount(tenantId)).thenReturn(account); when(charges.save(any())).thenAnswer(invocation->invocation.getArgument(0)); when(items.findByTenantIdAndChargeIdOrderByCreatedAtAsc(any(),any())).thenReturn(List.of()); }
    @AfterEach void clear(){TenantContext.clear();}
    @Test void shouldCalculateRinoFeeOnlyFromBaseRent(){RentalContract contract=new RentalContract();contract.setId(contractId);contract.setTenantId(tenantId);contract.setStatus(ContractStatus.ACTIVE);contract.setBaseRent(new BigDecimal("2000.00"));contract.setCondoAmount(new BigDecimal("500.00"));contract.setIptuAmount(new BigDecimal("100.00"));contract.setInsuranceAmount(BigDecimal.ZERO);contract.setDiscountAmount(BigDecimal.ZERO);contract.setBillingType("BOLETO");when(contracts.findByIdAndTenantIdAndDeletedAtIsNull(contractId,tenantId)).thenReturn(Optional.of(contract));when(charges.findByTenantIdAndContractIdAndCompetence(any(),any(),any())).thenReturn(Optional.empty());service.generate(contractId,new GenerateChargeRequest(LocalDate.of(2026,10,1),LocalDate.of(2026,10,10),List.of()));ArgumentCaptor<RentalCharge> captor=ArgumentCaptor.forClass(RentalCharge.class);verify(charges).save(captor.capture());assertThat(captor.getValue().getTotalAmount()).isEqualByComparingTo("2600.00");assertThat(captor.getValue().getRinoFeeAmount()).isEqualByComparingTo("30.00");}
}
