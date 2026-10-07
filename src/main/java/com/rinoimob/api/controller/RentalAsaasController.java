package com.rinoimob.api.controller;
import com.rinoimob.domain.dto.rental.RentalDtos.*;
import com.rinoimob.service.rental.RentalAsaasService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/rentals/asaas-account") @Tag(name="Rental Asaas account")
@PreAuthorize("hasAuthority('PERMISSION_rentals:integration_manage')")
public class RentalAsaasController {
    private final RentalAsaasService service; public RentalAsaasController(RentalAsaasService service){this.service=service;}
    @GetMapping @Operation(summary="Get rental Asaas account status") public AccountResponse status(){return service.status();}
    @PostMapping("/provision") @Operation(summary="Provision tenant Asaas subaccount") public AccountResponse provision(@Valid @RequestBody ProvisionAccountRequest request){return service.provision(request);}
}
