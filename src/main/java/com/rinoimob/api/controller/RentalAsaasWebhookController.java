package com.rinoimob.api.controller;
import com.fasterxml.jackson.databind.*;
import com.rinoimob.service.billing.AsaasWebhookInboxService;
import com.rinoimob.service.rental.RentalAsaasService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/webhooks/asaas/rentals")
public class RentalAsaasWebhookController {
    private final RentalAsaasService accounts; private final AsaasWebhookInboxService inbox; private final ObjectMapper mapper;
    public RentalAsaasWebhookController(RentalAsaasService accounts,AsaasWebhookInboxService inbox,ObjectMapper mapper){this.accounts=accounts;this.inbox=inbox;this.mapper=mapper;}
    @PostMapping("/{tenantId}") @Operation(summary="Receive tenant rental Asaas events") public ResponseEntity<Void> receive(@PathVariable UUID tenantId,@RequestBody byte[] body,HttpServletRequest request) throws Exception { if(!accounts.validateWebhook(tenantId,request.getHeader("asaas-access-token")))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid rental webhook token"); JsonNode payload=mapper.readTree(body); if(payload==null||!payload.isObject())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid Asaas payload"); inbox.receive(payload); return ResponseEntity.accepted().build(); }
}
