package com.rinoimob.api.controller;
import com.rinoimob.domain.dto.rental.RentalDtos.*;
import com.rinoimob.service.rental.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
@RestController @RequestMapping("/api/v1/rentals/contracts") @Tag(name="Rental contracts")
public class RentalContractController {
    private final RentalManagementService service; public RentalContractController(RentalManagementService service){this.service=service;}
    @GetMapping @PreAuthorize("hasAuthority('PERMISSION_rentals:read')") @Operation(summary="List rental contracts") public List<ContractResponse> list(){return service.listContracts();}
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('PERMISSION_rentals:read')") @Operation(summary="Get rental contract") public ContractResponse get(@PathVariable UUID id){return service.getContract(id);}
    @PostMapping @PreAuthorize("hasAuthority('PERMISSION_rentals:write')") @Operation(summary="Create rental contract") public ContractResponse create(@Valid @RequestBody ContractRequest request){return service.createContract(request);}
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('PERMISSION_rentals:write')") @Operation(summary="Update draft rental contract") public ContractResponse update(@PathVariable UUID id,@Valid @RequestBody ContractRequest request){return service.updateContract(id,request);}
    @PostMapping("/{id}/activate") @PreAuthorize("hasAuthority('PERMISSION_rentals:write')") @Operation(summary="Activate rental contract") public ContractResponse activate(@PathVariable UUID id){return service.activate(id);}
    @PostMapping("/{id}/terminate") @PreAuthorize("hasAuthority('PERMISSION_rentals:write')") @Operation(summary="Terminate rental contract") public ContractResponse terminate(@PathVariable UUID id){return service.terminate(id);}
    @PostMapping("/{id}/adjustment") @PreAuthorize("hasAuthority('PERMISSION_rentals:finance')") @Operation(summary="Confirm a manual rent adjustment") public ContractResponse adjustment(@PathVariable UUID id,@Valid @RequestBody AdjustmentRequest request){return service.applyAdjustment(id,request);}
    @PostMapping(value="/{id}/documents",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasAuthority('PERMISSION_rentals:write')") @Operation(summary="Upload signed contract PDF") public DocumentResponse document(@PathVariable UUID id,@RequestPart("file") MultipartFile file,@RequestPart(value="name",required=false) String name){return service.uploadDocument(id,name,file);}
    @DeleteMapping("/{id}/documents/{documentId}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('PERMISSION_rentals:write')") @Operation(summary="Delete rental contract document") public void deleteDocument(@PathVariable UUID id,@PathVariable UUID documentId){service.deleteDocument(id,documentId);}
}
