package com.rinoimob.service;

import com.rinoimob.api.client.EvolutionApiClient;
import com.rinoimob.context.TenantContext;
import com.rinoimob.domain.dto.CreateWhatsappInstanceRequest;
import com.rinoimob.domain.dto.WhatsappInstanceResponse;
import com.rinoimob.domain.dto.WhatsappQrCodeResponse;
import com.rinoimob.domain.entity.Tenant;
import com.rinoimob.domain.entity.WhatsappInstance;
import com.rinoimob.domain.repository.TenantRepository;
import com.rinoimob.domain.repository.WhatsappInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsappInstanceService {

    private final WhatsappInstanceRepository instanceRepo;
    private final TenantRepository tenantRepo;
    private final EvolutionApiClient evolutionClient;

    public List<WhatsappInstanceResponse> listForTenant() {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        List<WhatsappInstance> instances = instanceRepo.findByTenantIdOrderByCreatedAtAsc(tenantId);

        // Sync connection status from Evolution API on every listing
        instances.forEach(instance -> {
            try {
                String state = evolutionClient.getConnectionState(instance.getInstanceName());
                String newStatus = switch (state) {
                    case "open" -> "CONNECTED";
                    case "connecting" -> "CONNECTING";
                    default -> "DISCONNECTED";
                };
                if (!newStatus.equals(instance.getStatus())) {
                    instance.setStatus(newStatus);
                    instanceRepo.save(instance);
                }
            } catch (Exception e) {
                log.warn("Could not sync status for instance '{}': {}", instance.getInstanceName(), e.getMessage());
            }
        });

        return instances.stream().map(this::toResponse).toList();
    }

    public WhatsappInstanceResponse create(CreateWhatsappInstanceRequest req) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        Tenant tenant = tenantRepo.findById(tenantId)
            .orElseThrow(() -> new RuntimeException("Tenant not found"));

        // instanceName: subdomain + short random suffix to ensure uniqueness
        String instanceName = tenant.getSubdomain() + "-" + UUID.randomUUID().toString().substring(0, 8);

        WhatsappInstance instance = new WhatsappInstance();
        instance.setTenantId(tenantId);
        instance.setInstanceName(instanceName);
        instance.setDisplayName(req.getDisplayName());
        instance.setPhoneNumber(req.getPhoneNumber());
        instance.setStatus("DISCONNECTED");
        instance = instanceRepo.save(instance);

        // Register in Evolution API (fire-and-forget on failure)
        try {
            evolutionClient.createInstance(instanceName);
            instance.setStatus("CONNECTING");
            instance = instanceRepo.save(instance);
        } catch (Exception e) {
            log.error("Failed to create Evolution API instance '{}': {}", instanceName, e.getMessage(), e);
            // Keep as DISCONNECTED — user can delete and recreate
        }

        return toResponse(instance);
    }

    public WhatsappQrCodeResponse getQrCode(UUID instanceId) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        WhatsappInstance instance = instanceRepo.findByIdAndTenantId(instanceId, tenantId)
            .orElseThrow(() -> new RuntimeException("Instance not found"));

        WhatsappQrCodeResponse resp = new WhatsappQrCodeResponse();
        resp.setStatus(instance.getStatus());
        try {
            Map<String, Object> qr = evolutionClient.getQrCode(instance.getInstanceName());
            resp.setPairingCode((String) qr.get("pairingCode"));
            resp.setCode((String) qr.get("code")); // base64 QR or URL
        } catch (Exception e) {
            // QR not available yet
        }
        return resp;
    }

    public WhatsappInstanceResponse getStatus(UUID instanceId) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        WhatsappInstance instance = instanceRepo.findByIdAndTenantId(instanceId, tenantId)
            .orElseThrow(() -> new RuntimeException("Instance not found"));

        // Sync status from Evolution API
        String state = evolutionClient.getConnectionState(instance.getInstanceName());
        String newStatus = switch (state) {
            case "open" -> "CONNECTED";
            case "connecting" -> "CONNECTING";
            default -> "DISCONNECTED";
        };
        if (!newStatus.equals(instance.getStatus())) {
            instance.setStatus(newStatus);
            instanceRepo.save(instance);
        }
        return toResponse(instance);
    }

    public void delete(UUID instanceId) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        WhatsappInstance instance = instanceRepo.findByIdAndTenantId(instanceId, tenantId)
            .orElseThrow(() -> new RuntimeException("Instance not found"));
        evolutionClient.deleteInstance(instance.getInstanceName());
        instanceRepo.delete(instance);
    }

    public WhatsappInstanceResponse toResponse(WhatsappInstance i) {
        return new WhatsappInstanceResponse(i.getId(), i.getInstanceName(),
            i.getDisplayName(), i.getPhoneNumber(), i.getStatus(), i.getCreatedAt());
    }
}
