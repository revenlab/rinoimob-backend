package com.rinoimob.api.controller;
import com.rinoimob.domain.dto.rental.RentalDtos.*;
import com.rinoimob.service.rental.RentalManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v1/rentals/parties") @Tag(name="Rental parties")
public class RentalPartyController {
    private final RentalManagementService service; public RentalPartyController(RentalManagementService service){this.service=service;}
    @GetMapping @PreAuthorize("hasAuthority('PERMISSION_rentals:read')") @Operation(summary="List rental parties") public List<PartyResponse> list(){return service.listParties();}
    @PostMapping @PreAuthorize("hasAuthority('PERMISSION_rentals:write')") @Operation(summary="Create a rental party") public PartyResponse create(@Valid @RequestBody PartyRequest request){return service.createParty(request);}
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('PERMISSION_rentals:write')") @Operation(summary="Update a rental party") public PartyResponse update(@PathVariable UUID id,@Valid @RequestBody PartyRequest request){return service.updateParty(id,request);}
}
