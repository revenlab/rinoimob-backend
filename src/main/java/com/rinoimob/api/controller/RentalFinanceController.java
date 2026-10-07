package com.rinoimob.api.controller;
import com.rinoimob.domain.dto.rental.RentalDtos.*;
import com.rinoimob.service.rental.RentalFinanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v1/rentals") @Tag(name="Rental finance")
public class RentalFinanceController {
    private final RentalFinanceService service; public RentalFinanceController(RentalFinanceService service){this.service=service;}
    @GetMapping("/dashboard") @PreAuthorize("hasAuthority('PERMISSION_rentals:read')") @Operation(summary="Rental dashboard") public DashboardResponse dashboard(){return service.dashboard();}
    @GetMapping("/charges") @PreAuthorize("hasAuthority('PERMISSION_rentals:finance')") @Operation(summary="List rental charges") public List<ChargeResponse> charges(){return service.listCharges();}
    @PostMapping("/contracts/{contractId}/charges") @PreAuthorize("hasAuthority('PERMISSION_rentals:finance')") @Operation(summary="Generate itemized rental charge") public ChargeResponse generate(@PathVariable UUID contractId,@Valid @RequestBody GenerateChargeRequest request){return service.generate(contractId,request);}
    @PostMapping("/charges/{id}/issue") @PreAuthorize("hasAuthority('PERMISSION_rentals:finance')") @Operation(summary="Issue charge in Asaas") public ChargeResponse issue(@PathVariable UUID id){return service.issue(id);}
    @GetMapping("/payouts") @PreAuthorize("hasAuthority('PERMISSION_rentals:finance')") @Operation(summary="List owner payouts") public List<PayoutResponse> payouts(){return service.listPayouts();}
    @PostMapping("/payouts/{id}/approve") @PreAuthorize("hasAuthority('PERMISSION_rentals:payout_approve')") @Operation(summary="Approve and send owner payout") public PayoutResponse approve(@PathVariable UUID id){return service.approvePayout(id);}
}
