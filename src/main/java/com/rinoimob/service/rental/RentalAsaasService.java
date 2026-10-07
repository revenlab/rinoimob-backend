package com.rinoimob.service.rental;

import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.rental.RentalDtos.*;
import com.rinoimob.domain.entity.RentalAsaasAccount;
import com.rinoimob.domain.enums.RentalEnums.AccountStatus;
import com.rinoimob.domain.repository.RentalAsaasAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class RentalAsaasService {
    private final RentalAsaasAccountRepository accounts; private final RentalSecretService secrets; private final RestTemplate http;
    private final String baseUrl; private final String rootApiKey; private final String publicApiUrl; private final String rinoWalletId;
    public RentalAsaasService(RentalAsaasAccountRepository accounts, RentalSecretService secrets, RestTemplate http,
            @Value("${rental.asaas.base-url:https://api.asaas.com}") String baseUrl,
            @Value("${rental.asaas.root-api-key:}") String rootApiKey,
            @Value("${app.public-api-url:http://localhost:39000}") String publicApiUrl,
            @Value("${rental.asaas.rino-wallet-id:}") String rinoWalletId) {
        this.accounts=accounts; this.secrets=secrets; this.http=http; this.baseUrl=baseUrl; this.rootApiKey=rootApiKey; this.publicApiUrl=publicApiUrl; this.rinoWalletId=rinoWalletId;
    }
    public AccountResponse status() { return accounts.findById(tenant()).map(this::response).orElse(new AccountResponse(AccountStatus.NOT_CONFIGURED, null, null, BigDecimal.ONE, false)); }
    @Transactional
    public AccountResponse provision(ProvisionAccountRequest request) {
        secrets.ensureConfigured(); UUID tenantId = tenant(); if (accounts.findById(tenantId).filter(a -> a.getStatus() != AccountStatus.REJECTED).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Rental Asaas account already exists");
        String webhookToken = token();
        Map<String,Object> webhook = new LinkedHashMap<>(); webhook.put("name", "Rinoimob Alugueis"); webhook.put("url", publicApiUrl + "/api/v1/webhooks/asaas/rentals/" + tenantId); webhook.put("authToken", webhookToken); webhook.put("enabled", true); webhook.put("interrupted", false);
        webhook.put("events", List.of("PAYMENT_CREATED","PAYMENT_UPDATED","PAYMENT_CONFIRMED","PAYMENT_RECEIVED","PAYMENT_OVERDUE","PAYMENT_REFUNDED","PAYMENT_DELETED","TRANSFER_PENDING","TRANSFER_DONE","TRANSFER_FAILED","TRANSFER_CANCELLED"));
        Map<String,Object> body = new LinkedHashMap<>(); body.put("name", request.name()); body.put("email", request.email()); body.put("cpfCnpj", digits(request.cpfCnpj())); body.put("mobilePhone", digits(request.mobilePhone())); body.put("address", request.address()); body.put("addressNumber", request.addressNumber()); body.put("province", request.province()); body.put("postalCode", digits(request.postalCode())); body.put("webhooks", List.of(webhook));
        Map<?,?> result = exchange(rootApiKey, HttpMethod.POST, "/v3/accounts", body);
        String apiKey = string(result.get("apiKey")); if (apiKey == null) throw new IllegalStateException("Asaas subaccount response did not include apiKey");
        RentalAsaasAccount account = new RentalAsaasAccount(); account.setTenantId(tenantId); account.setProviderAccountId(string(result.get("id"))); account.setWalletId(string(result.get("walletId"))); account.setEncryptedApiKey(secrets.encrypt(apiKey)); account.setWebhookTokenHash(secrets.sha256(webhookToken)); account.setStatus(AccountStatus.PENDING); account.setRinoFeePercent(request.rinoFeePercent() == null ? BigDecimal.ONE : request.rinoFeePercent());
        return response(accounts.save(account));
    }
    public Map<?,?> createCustomer(RentalAsaasAccount account, Map<String,Object> body) { return exchange(apiKey(account), HttpMethod.POST, "/v3/customers", body); }
    public Map<?,?> createPayment(RentalAsaasAccount account, Map<String,Object> body) { return exchange(apiKey(account), HttpMethod.POST, "/v3/payments", body); }
    public Map<?,?> findPayment(RentalAsaasAccount account, String externalReference) { Map<?,?> page=exchange(apiKey(account),HttpMethod.GET,UriComponentsBuilder.fromPath("/v3/payments").queryParam("externalReference",externalReference).queryParam("limit",1).toUriString(),null); Object data=page.get("data"); if(data instanceof List<?> list&&!list.isEmpty()&&list.get(0) instanceof Map<?,?> payment)return payment; return null; }
    public Map<?,?> createTransfer(RentalAsaasAccount account, Map<String,Object> body) { return exchange(apiKey(account), HttpMethod.POST, "/v3/transfers", body); }
    public RentalAsaasAccount requiredAccount(UUID tenantId) { RentalAsaasAccount a=accounts.findById(tenantId).orElseThrow(() -> new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,"Rental Asaas account is not configured")); if (a.getStatus()==AccountStatus.REJECTED || a.getStatus()==AccountStatus.SUSPENDED) throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,"Rental Asaas account is not operational"); return a; }
    public boolean validateWebhook(UUID tenantId, String tokenValue) { return tokenValue != null && accounts.findById(tenantId).map(a -> secrets.matchesHash(tokenValue, a.getWebhookTokenHash())).orElse(false); }
    public String rinoWalletId() { if (rinoWalletId == null || rinoWalletId.isBlank()) throw new IllegalStateException("RENTAL_ASAAS_RINO_WALLET_ID is required"); return rinoWalletId; }
    private Map<?,?> exchange(String key, HttpMethod method, String path, Object body) { if (key == null || key.isBlank()) throw new IllegalStateException("Asaas API key is required"); HttpHeaders h=new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); h.set("access_token",key); ResponseEntity<Map> response=http.exchange(baseUrl+path,method,new HttpEntity<>(body,h),Map.class); if(response.getBody()==null) throw new IllegalStateException("Empty Asaas response"); return response.getBody(); }
    private String apiKey(RentalAsaasAccount a) { return secrets.decrypt(a.getEncryptedApiKey()); }
    private AccountResponse response(RentalAsaasAccount a) { return new AccountResponse(a.getStatus(),a.getProviderAccountId(),a.getWalletId(),a.getRinoFeePercent(),a.getEncryptedApiKey()!=null); }
    private UUID tenant() { String v=TenantContext.getTenantId(); if(v==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED); return UUID.fromString(v); }
    private String token() { byte[] b=new byte[32]; new SecureRandom().nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    private String string(Object v) { return v==null?null:String.valueOf(v); }
    private String digits(String v) { return v==null?null:v.replaceAll("\\D",""); }
}
