package com.rinoimob.domain.dto.tenant;

/**
 * DTO para atualizar configuração de domínio customizado.
 */
public record UpdateTenantDomainRequest(
        String customDomain
) {
}
