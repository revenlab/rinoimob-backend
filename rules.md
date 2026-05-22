# rinoimob-backend — rule.md

Arquivo de histórico de mudanças e crumbs para reduzir tokens em contextos futuros.

---

## Última migration: V22

```
V21__support_permissions.sql — tabela support_user_permissions com UNIQUE(user_id, permission)
V22__tenant_website_config.sql — tabela tenant_website_config (tenant_id como PK, 1:1 com Tenant)
```

---

## Módulo de Suporte (Support Admin)

### Endpoints — `SupportAdminController` (`/api/v1/support/**`)

Todos exigem staff interno (TENANT_ADMIN / SUPPORT_MANAGER / SUPPORT_AGENT).
Permissões granulares carregadas do DB pelo `SupportPermissionFilter`.

| Método | Path | Permissão |
|--------|------|-----------|
| GET | `/dashboard` | `support:tenants:read` |
| GET | `/tenants` | `support:tenants:read` |
| PATCH | `/tenants/{id}/status` | `support:tenants:write` |
| PATCH | `/tenants/{id}` | `support:tenants:write` |
| GET | `/tenants/{id}/users` | `support:tenant_users:read` |
| PATCH | `/tenants/{id}/users/{uid}/status` | `support:tenant_users:write` |
| PATCH | `/tenants/{id}/users/{uid}` | `support:tenant_users:write` |
| POST | `/tenants/{id}/users/{uid}/resend-invitation` | `support:tenant_users:write` |
| POST | `/tenants/{id}/users/{uid}/reset-access` | `support:tenant_users:write` |
| GET | `/operators` | `support:operators:read` |
| PATCH | `/operators/{uid}/role` | `support:operators:write` |
| GET | `/operators/{uid}/permissions` | `support:operators:read` |
| PUT | `/operators/{uid}/permissions` | `support:operators:write` |
| GET | `/audit` | `support:audit:read` |
| GET | `/tenants/{id}/health` | `support:health:read` |

### Permissões disponíveis (enum `SupportPermission`)

```
support:tenants:read / write
support:tenant_users:read / write
support:operators:read / write
support:audit:read
support:health:read
```

### Arquitetura de permissões

- `SupportPermissionFilter` (roda após `JwtAuthenticationFilter`) detecta staff interno,
  carrega permissões da tabela `support_user_permissions`, substitui as do JWT.
- `@PreAuthorize` usa `hasAuthority('PERMISSION_support:X:Y')`.
- Permissões dos usuários tenant continuam no JWT normalmente.

### Seeders

- `DevDataSeeder` — semeia 3 contas de suporte com permissões específicas por role.
- `ProdDataSeeder` (`@Profile("prod")`) — lê `SUPPORT_ADMIN_EMAIL` / `SUPPORT_ADMIN_PASSWORD` do env, idempotente.

### Bug importante já corrigido

`SupportUserPermissionRepository.deleteByUserId` usa `@Query` JPQL para bulk delete.
**Não usar** derived delete sem `@Query` — causa duplicate key na troca de permissões.

### DTOs de suporte

| DTO | Uso |
|-----|-----|
| `SupportTenantSummaryResponse` | lista/detalhe de tenants |
| `SupportUserSummaryResponse` | lista/detalhe de usuários |
| `SupportDashboardResponse` | stats do dashboard |
| `SupportTenantHealthResponse` | saúde de tenant |
| `SupportAuditLogResponse` | logs de auditoria |
| `UpdateSupportTenantRequest` | editar nome/subdomínio (PATCH /tenants/{id}) |
| `UpdateSupportUserRequest` | editar firstName/lastName/email/phone (PATCH /users/{id}) |
| `SetOperatorPermissionsRequest` | set completo de permissões de operador |

---

## Segurança

- `UserController.GET /users` tem `@PreAuthorize("hasAuthority('PERMISSION_users:read')")`
  (adicionado — antes estava sem proteção).
- Padrão geral: `hasAuthority('PERMISSION_{permission_name}')` — nunca `hasRole()`.

---

## Módulo Website Config (`/api/v1/website-config`)

### Entidade: `TenantWebsiteConfig`

- PK = `tenant_id` (UUID, 1:1 com `Tenant`)
- Campos: `companyName`, `logo`, `favicon`, `primaryColor`, `secondaryColor`, `description`, `heroTitle`, `heroSubtitle`, `phone`, `email`, `address`, `facebookUrl`, `instagramUrl`, `whatsappNumber`

### Endpoints

| Método | Path | Auth |
|--------|------|------|
| GET | `/api/v1/website-config` | tenant auth |
| PUT | `/api/v1/website-config` | tenant auth |
| POST | `/api/v1/website-config/logo` | tenant auth (multipart) |
| POST | `/api/v1/website-config/favicon` | tenant auth (multipart) |
| DELETE | `/api/v1/website-config/logo` | tenant auth |
| DELETE | `/api/v1/website-config/favicon` | tenant auth |
| GET | `/api/v1/public/config` | sem auth (header `X-Tenant-Slug`) |
| GET | `/api/v1/support/tenants/{id}/website-config` | `support:tenants:read` |
| PUT | `/api/v1/support/tenants/{id}/website-config` | `support:tenants:write` |

### Serviço: `TenantWebsiteConfigService`

- Upload de logo/favicon via SeaweedFS
- `getOrCreateByTenantId()` — nunca retorna nulo, cria registro vazio se necessário
- DTOs: `TenantWebsiteConfigResponse`, `UpdateTenantWebsiteConfigRequest`

---

## Convenções deste projeto

- K&R, 4 espaços, sem tabs.
- Repos SEMPRE escopados por `tenantId` — nunca `findById()` solo em entidade de tenant.
- Auditoria em toda ação de suporte via `AuditLogRepository`.
- Flyway para migrations — próxima será `V23__...`.
