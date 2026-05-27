# rinoimob-backend — rule.md

Arquivo de histórico de mudanças e crumbs para reduzir tokens em contextos futuros.

---

## Última migration: V25

```
V21__support_permissions.sql — tabela support_user_permissions com UNIQUE(user_id, permission)
V22__tenant_website_config.sql — tabela tenant_website_config (tenant_id como PK, 1:1 com Tenant)
V23__add_hero_image_to_website_config.sql — adiciona hero_image_fid e hero_image_url em tenant_website_config
V24__expand_tenant_website_config_cms.sql — adiciona campos CMS da home (destaques, lançamentos, categorias, serviços, stats, blog, CTA)
V25__create_tenant_blog_posts.sql — cria CMS real de blog por tenant (draft/publicado, slug único, conteúdo HTML)
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
- CORS usa `allowedOriginPatterns` em `SecurityConfig` para suportar subdomínios (ex.: `http://*.localhost:3000`) mantendo `allowCredentials=true`.

---

## Módulo Website Config (`/api/v1/website-config`)

### Entidade: `TenantWebsiteConfig`

- PK = `tenant_id` (UUID, 1:1 com `Tenant`)
- Campos: `companyName`, `logo`, `favicon`, `primaryColor`, `secondaryColor`, `description`, `heroTitle`, `heroSubtitle`, `phone`, `email`, `address`, `facebookUrl`, `instagramUrl`, `whatsappNumber`, `heroImageUrl`, `featuredSectionTitle/SubTitle`, `launchesSectionTitle/SubTitle`, `categoriesSectionTitle/SubTitle`, `servicesSectionTitle/SubTitle`, `servicesFormTitle/SubTitle`, `statsSectionTitle/SubTitle`, `blogSectionTitle/SubTitle`, `ctaSectionTitle/SubTitle`

### Endpoints

| Método | Path | Auth |
|--------|------|------|
| GET | `/api/v1/website-config` | tenant auth |
| PUT | `/api/v1/website-config` | tenant auth |
| POST | `/api/v1/website-config/logo` | tenant auth (multipart) |
| POST | `/api/v1/website-config/favicon` | tenant auth (multipart) |
| POST | `/api/v1/website-config/hero-image` | tenant auth (multipart) |
| DELETE | `/api/v1/website-config/logo` | tenant auth |
| DELETE | `/api/v1/website-config/favicon` | tenant auth |
| DELETE | `/api/v1/website-config/hero-image` | tenant auth |
| GET | `/api/v1/public/config` | sem auth (header `X-Tenant-Slug`) |
| POST | `/api/v1/public/leads` | sem auth (header `X-Tenant-Slug`) |
| GET | `/api/v1/public/blog-posts` | sem auth (header `X-Tenant-Slug`) |
| GET | `/api/v1/public/blog-posts/{slug}` | sem auth (header `X-Tenant-Slug`) |
| GET | `/api/v1/support/tenants/{id}/website-config` | `support:tenants:read` |
| PUT | `/api/v1/support/tenants/{id}/website-config` | `support:tenants:write` |

### Serviço: `TenantWebsiteConfigService`

- Upload de logo/favicon via SeaweedFS
- Upload de hero-image via SeaweedFS
- `getOrCreateByTenantId()` — nunca retorna nulo, cria registro vazio se necessário
- DTOs: `TenantWebsiteConfigResponse`, `UpdateTenantWebsiteConfigRequest`
- `PublicController.createLead()` normaliza `source` público para rastreamento de conversão (mantendo padrão `PORTAL*`)
- CORS hardening para website multi-tenant local: `SecurityConfig` sempre inclui padrões `*.localhost` (3000/5173/5174, http/https) além dos valores de `CORS_ALLOWED_ORIGINS`, evitando `Invalid CORS request` quando origin é `tenant.localhost`.
- Leads em tempo real: `LeadRealtimeService` publica eventos WebSocket no tópico `/topic/{tenantId}.leads` com tipos `LEAD_CREATED`, `LEAD_UPDATED` e `LEAD_DELETED`; `LeadService` dispara esses eventos em create/update/delete.

---

## Convenções deste projeto

- K&R, 4 espaços, sem tabs.
- Repos SEMPRE escopados por `tenantId` — nunca `findById()` solo em entidade de tenant.
- Auditoria em toda ação de suporte via `AuditLogRepository`.
- Flyway para migrations — próxima será `V26__...`.

---

## Blog CMS (`/api/v1/blog-posts`)

- Gestão autenticada por tenant owner/admin com status `DRAFT` e `PUBLISHED`.
- Conteúdo HTML sanitizado no backend com Jsoup.
- Slug único por tenant (com geração automática e sufixo incremental).
- Endpoints de suporte para gestão cruzada:
  - `GET/POST /api/v1/support/tenants/{tenantId}/blog-posts`
  - `PUT/PATCH(status)/DELETE /api/v1/support/tenants/{tenantId}/blog-posts/{postId}`
