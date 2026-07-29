# rinoimob-backend — rule.md

Arquivo de histórico de mudanças e crumbs para reduzir tokens em contextos futuros.

---

## Última migration: V40

```
V21__support_permissions.sql — tabela support_user_permissions com UNIQUE(user_id, permission)
V22__tenant_website_config.sql — tabela tenant_website_config (tenant_id como PK, 1:1 com Tenant)
V23__add_hero_image_to_website_config.sql — adiciona hero_image_fid e hero_image_url em tenant_website_config
V24__expand_tenant_website_config_cms.sql — adiciona campos CMS da home (destaques, lançamentos, categorias, serviços, stats, blog, CTA)
V25__create_tenant_blog_posts.sql — cria CMS real de blog por tenant (draft/publicado, slug único, conteúdo HTML)
V38__property_videos.sql — cria vídeos de imóveis com origem UPLOAD/YOUTUBE
V39__granular_broker_permissions.sql — migra Corretor padrão para permissões próprias em leads/tarefas
V40__tenant_property_types.sql — cria tipos de imóveis configuráveis por tenant sobre os códigos fixos do enum
```

---

## Módulo de Suporte (Support Admin)

### Endpoints — `SupportAdminController` (`/api/v1/support/**`)

Todos exigem staff interno (SUPPORT_MANAGER / SUPPORT_AGENT).
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
- `GET /api/v1/public/properties` aceita `categorySlug` além de `operation`/`propertyType`; o join em `categories` usa `distinct(true)` para evitar duplicidade em listagens filtradas por categoria.

---

## Convenções deste projeto

- K&R, 4 espaços, sem tabs.
- Repos SEMPRE escopados por `tenantId` — nunca `findById()` solo em entidade de tenant.
- Auditoria em toda ação de suporte via `AuditLogRepository`.
- Flyway para migrations — próxima será `V41__...`.

## Structural Note

- **Capas de blog por upload (#57)**: `tenant_blog_posts.cover_image_fid` mantém a referência no storage; upload, troca, remoção e exclusão do post limpam o arquivo associado e preservam URL externa como alternativa.
- **Comercialização por planta**: `floor_plans` passou a guardar faixa de preço e características próprias; o CRUD autenticado permite atualização da planta sem alterar o imóvel principal, preservando `properties.price` como fallback.

- **Referência automática (#61)**: na criação, imóveis sem `referenceCode` recebem `IMV-XXXXXXXX` único por tenant; códigos manuais são normalizados em maiúsculas, validados antes de persistir e protegidos por índice único parcial. A migration normaliza referências legadas vazias e desambigua duplicadas antes de criar o índice.

- **Destaques por imóvel (#50)**: `properties.is_featured` identifica imóveis escolhidos pelo tenant; o contrato autenticado permite marcar/desmarcar no cadastro e a listagem pública aceita `featured=true`, usada exclusivamente pela seção de destaques da home.

- **CORS público local**: a política de `/api/v1/public/**` também aceita `http(s)://*.localhost` nas portas de desenvolvimento, mantendo `allowCredentials=false`; assim o proxy Nuxt pode encaminhar leads de subdomínios locais sem afrouxar as rotas autenticadas.
- **Busca pública sem acentuação (#53)**: `PropertySpecification` normaliza o termo de consulta e usa `translate(lower(...))` nos campos públicos pesquisáveis (título, descrição, referência, bairro e cidade), mantendo a equivalência entre grafias com e sem acento no PostgreSQL e H2.
- `V41__tenant_custom_domain_cloudflare.sql` passou a guardar `custom_domain_status`, `custom_domain_provider_id` e `custom_domain_target`; o fluxo de domínio agora provisiona custom hostname via Cloudflare quando as credenciais estão configuradas, e `PublicController` resolve tenants por subdomínio ou domínio customizado.
- `GET /api/v1/website-config/domain` agora exige `TENANT_ADMIN` ou `TENANT_OWNER`, alinhando leitura e escrita do fluxo de domínio customizado.
- `V42__tenant_property_type_website_metadata.sql` adiciona `card_color`, `cover_image_fid` e `cover_image_url` em `tenant_property_types`; `PropertyTypeService` agora provisiona defaults no signup, permite editar cor do card e upload/remoção de capa por tipo via `/api/v1/property-types/{code}/cover-image`.
- Hardening pre-prod: logout revoga pelo `userId`, `GET /api/v1/users` exige `PERMISSION_users:read`, `TENANT_ADMIN` deixou de ser staff interno, vínculos de leads/tarefas validam tenant, auth sensível usa rate limit por IP e `ProdConfigValidator` falha startup `prod` com defaults inseguros.
- Docker prod: `Dockerfile` multi-stage compila com Maven/JDK 17 e roda o jar em `eclipse-temurin:17-jre-alpine` na porta interna `39000`; deploy esperado via `rinoimob-infrastructure/docker-compose.prod.yml` com Postgres/Redis/RabbitMQ/SeaweedFS/Evolution em rede privada atrás de Nginx como origin Cloudflare.
- Healthcheck prod: `/actuator/health` fica sem autenticação para o `docker-compose.prod.yml` conseguir marcar o backend como healthy; demais endpoints Actuator continuam protegidos pela regra autenticada geral.
- CORS prod: `/api/v1/public/**` usa `PUBLIC_CORS_ALLOWED_ORIGINS` para suportar websites em domínios customizados; rotas autenticadas continuam restritas por `CORS_ALLOWED_ORIGINS`.
- Subdomínios prod: `TENANT_BASE_DOMAIN` permite que `PublicController` normalize `cliente.rinoimob.com.br` para o subdomain `cliente`; se a env faltar, `PublicController` ainda tenta resolver o primeiro label do hostname após checar `custom_domain`.
- `V44__create_audit_logs_table.sql` cria a tabela `audit_logs` esperada pela entidade `AuditLog` e migra dados best-effort da tabela legada `audit_log` quando existir.
- Cloudflare custom hostname é criado sem `custom_metadata`, evitando falha 403/código 1413 em contas sem custom metadata provisionado.

## Tipos de imóveis por tenant (#40)

- `tenant_property_types` guarda rótulo, ordem e ativo por tenant para os códigos fixos de `PropertyType`.
- `GET /api/v1/property-types` lista todos os tipos do tenant; `activeOnly=true` retorna apenas ativos.
- `PUT /api/v1/property-types/{code}` atualiza `label`, `position` e `active`.
- `GET /api/v1/public/property-types` expõe apenas os ativos para o site público.
- `PropertyService` bloqueia criação/alteração para tipo inativo, sem migrar `properties.property_type` para FK nesta fase.

## Vídeos em imóveis (#47)

- Migration `V38__property_videos.sql` cria `property_videos` com `tenant_id`, `property_id`, `source` (`UPLOAD`/`YOUTUBE`), `seaweed_fid`, `url`, `youtube_video_id`, `title` e `position`.
- `Property` agora possui coleção `videos`; `PropertyResponse` expõe `List<PropertyVideoResponse> videos` também para o contrato público.
- Endpoints autenticados:
  - `POST /api/v1/properties/{id}/videos/upload` (`multipart/form-data`, `file`, `title?`) envia vídeo para SeaweedFS e limita arquivo a 25MB.
  - `POST /api/v1/properties/{id}/videos/youtube` cadastra URL do YouTube e normaliza para `https://www.youtube.com/embed/{id}`.
  - `DELETE /api/v1/properties/{id}/videos/{videoId}` remove o vídeo; se for upload, apaga também do storage.
- Mutação de vídeos usa `@CacheEvict` para `publicPropertyListings` e `publicPropertyDetails`.

---

## Blog CMS (`/api/v1/blog-posts`)

- Gestão autenticada por tenant owner/admin com status `DRAFT` e `PUBLISHED`.
- Conteúdo HTML sanitizado no backend com Jsoup.
- Slug único por tenant (com geração automática e sufixo incremental).
- Endpoints de suporte para gestão cruzada:
  - `GET/POST /api/v1/support/tenants/{tenantId}/blog-posts`
  - `PUT/PATCH(status)/DELETE /api/v1/support/tenants/{tenantId}/blog-posts/{postId}`

---

## Billing de Tenants (Planos/Assinaturas)

- Migration `V33__billing_and_subscriptions.sql` adiciona:
  - catálogo global de planos (`billing_plans` com `code` e flags de recursos),
  - `tenant_subscriptions` (status/provedor/período),
  - `tenant_billing_profiles` (limites e permissões efetivos por tenant).
- Planos seeded: `FREE`, `STARTER`, `PRIME`, `ULTIMATE`.
- Serviços de domínio criados:
  - `BillingPlanResolverService` (resolve plano efetivo por tenant),
  - `TenantSubscriptionService` (garante assinatura default Free),
  - `TenantBillingProfileService` (snapshot de limites/permissões efetivos),
  - `TenantBillingOnboardingService` (provisionamento no signup).
- Fluxo de cadastro atualizado:
  - `AuthService.signup()` agora chama `tenantBillingOnboardingService.provisionDefaultFreePlan(...)` após seed de roles.
- Adapter de cobrança desacoplado:
  - `BillingGatewayPort` + `AbacatePayBillingGateway` (REST via `RestTemplate`, configurável por `billing.abacatepay.*`).
- Fluxo AbacatePay v2 ajustado:
  - antes do checkout, o backend cria/reativa `customers/create` e reutiliza `customerId` no checkout.
  - o checkout de assinatura usa `POST /v2/subscriptions/create` com 1 item e `externalId` igual ao `tenantId`.
  - o plano só é marcado como ativo por webhook `subscription.completed` / `subscription.renewed`; `subscription.cancelled` encerra a assinatura.
  - `providerSubscriptionId` vem do webhook (`subs_*`), não da resposta do checkout (`bill_*`).
  - webhook público em `POST /api/v1/webhooks/abacatepay?webhookSecret=...` valida `X-Webhook-Signature` com HMAC-SHA256.
    - a assinatura usa bytes crus do body e aceita Base64 ou hex; o segredo de URL pode ser separado do segredo de assinatura via `ABACATEPAY_WEBHOOK_SECRET` e `ABACATEPAY_WEBHOOK_SIGNING_SECRET`.
- Convenção de ilimitado no snapshot de limites:
  - valor `-1` (`TenantBillingLimitsSnapshot.UNLIMITED`) quando o plano global tiver limite `NULL`.
- Suporte (Support Admin) agora possui endpoints de billing:
  - `GET /api/v1/support/tenants/{tenantId}/billing`
  - `PUT /api/v1/support/tenants/{tenantId}/billing`
  - retorno inclui assinatura atual, limites efetivos e catálogo de planos (`availablePlans`).
- Quota enforcement centralizado em `TenantQuotaEnforcementService`:
  - `assertCanCreateUser()` aplicado em `AuthService.register()` e `UserManagementService.inviteUser()`.
  - `assertCanCreateProperty()` aplicado em `PropertyService.createProperty()`.
  - `assertCanCreateLead()` aplicado em `LeadService.create()` e `findOrCreateLeadFromWhatsAppMessage()`.
- Portal de billing do tenant (novo):
  - `GET /api/v1/billing/me` retorna plano atual, limites efetivos e catálogo de planos.
  - `POST /api/v1/billing/checkout` inicia checkout de upgrade para plano pago.
  - `TenantBillingPortalService` orquestra visão do tenant + início de checkout via `BillingGatewayPort`.
  - Ao iniciar checkout, assinatura registra `provider=ABACATEPAY`, `status=PENDING` e `provider_checkout_id`.
- Ajustes de integração AbacatePay v2:
  - `AbacatePayBillingGateway` agora usa autenticação `Authorization: Bearer <API_KEY>` (em vez de `X-Api-Key`).
  - Endpoint de checkout alinhado para assinatura em `POST /v2/subscriptions/create`.
  - Checkout usa `items` com `productId` por plano (`ABACATEPAY_PRODUCT_STARTER_ID`, `...PRIME...`, `...ULTIMATE...`).
  - Cancelamento alinhado para `POST /v2/subscriptions/cancel` com body `{ "id": "<subscriptionId>" }`.
  - `TenantBillingPortalService.startCheckout()` agora retorna `503` quando billing não estiver configurado e `502` quando a API do provedor falhar.
  - `AbacatePayWebhookService` resolve `tenantId`/`planCode` com fallback para `data.checkout.externalId` e `data.checkout.metadata.*` (payload v2 de `subscription.completed`), além de mapear `providerCheckoutId` por `data.checkout.id`.
 - Anti-fraude na troca de plano (upgrade/downgrade):
   - Novo campo `tenant_subscriptions.last_plan_change_at` (migration `V36__add_last_plan_change_to_tenant_subscriptions.sql`) para controlar cooldown de downgrade.
   - `TenantBillingPortalService.startCheckout()` agora bloqueia downgrade antes de 31 dias da última troca (`409 CONFLICT`).
   - Upgrade continua permitido a qualquer momento.
   - Em troca permitida, o backend cancela a assinatura anterior no AbacatePay (`cancelSubscription`) antes de deixar a nova assinatura em `PENDING`.
   - `AbacatePayWebhookService` atualiza `lastPlanChangeAt` quando detectar mudança real de plano.
 - Fix checkout reutilizado/inválido no AbacatePay:
   - `TenantBillingPortalService` agora gera `externalId` único por tentativa (`tenantId-planCode-UUID`) ao iniciar checkout.
   - `AbacatePayBillingGateway` envia esse `externalId` único para `POST /v2/subscriptions/create` (com fallback para `tenantId` se vier vazio).
   - Mantemos `metadata.tenantId` para correlação de webhook e resolução do tenant, sem depender de `externalId` fixo.
   - `AbacatePayWebhookService` agora aceita `externalId` composto e prioriza `metadata.tenantId` para evitar `UUID string too large` no retorno do webhook.
   - `subscription.cancelled` agora valida `subscription.id` do webhook contra `tenant_subscriptions.provider_subscription_id`; cancelamentos com id divergente (assinatura antiga) são ignorados para não rebaixar plano ativo no Rino indevidamente.
 - `PublicController.GET /api/v1/public/properties` agora aceita os filtros opcionais `minPrice`, `maxPrice`, `bedrooms` e `city` (além de `operation`, `propertyType`, `categorySlug`) e repassa todos para `PropertyService.listProperties(...)`.
 - Busca textual no catálogo de imóveis:
   - `GET /api/v1/public/properties` agora também aceita `q`.
   - `PropertySpecification.withFilters(...)` passou a aplicar `LIKE` case-insensitive em `title`, `description`, `referenceCode`, `addressNeighborhood` e `addressCity` quando `q` for informado.
   - `PropertyController` e `PropertyService.listProperties(...)` foram ajustados para suportar o novo parâmetro `q` sem quebrar o fluxo interno.
 - Geospatial básico e cache do catálogo público:
   - `GET /api/v1/public/properties` também aceita `latitude`, `longitude` e `radiusKm`.
   - `PropertySpecification.withFilters(...)` aplica um bounding box simples em `lat/lng` quando o raio é informado.
   - `PropertyService` agora usa `@Cacheable` para listagem/detalhe públicos e `@CacheEvict(allEntries = true)` em mutações de imóvel, foto e planta para invalidar o catálogo público.
   - Correção de floor plans: `PropertyService.addFloorPlan()` também invalida `publicPropertyListings` e `publicPropertyDetails`; antes a criação da planta podia deixar app interno e website recebendo detalhe antigo sem `floorPlans`.
 - Bolsão de Leads hardening (#49):
 - `LeadPoolController` recebeu documentação Swagger e validação também no `PUT`.
 - `LeadPoolService` valida nome, JSON de critérios, tipos de critérios, faixa de preço, prioridade, inatividade, enums de roteamento/seleção e corretores obrigatórios em `SPECIFIC_BROKERS`.
 - `LeadPoolRuleEvaluator` passou a buscar imóvel por `propertyId + tenantId + deletedAt null`, mantendo avaliação tenant-scoped.
 - `LeadPoolInactivityScheduler` usa `LeadPoolRepository.findInactivityPools()` para buscar apenas pools com gatilho ativo, ordenados por tenant/prioridade.
- Comercialização por planta: `floor_plans` armazena preço inicial/final e cômodos próprios; `PropertyService` mantém tudo tenant-scoped, valida faixas e usa o menor `price_from` no resumo público como fallback ao preço do imóvel.
 - Tutorial guiado do app:
   - Migration `V43__user_onboarding_progress.sql` cria `user_onboarding_progress` com UNIQUE `(tenant_id, user_id, tutorial_key)` e timestamps de início/dismiss/conclusão.
   - `UserOnboardingService` faz upsert idempotente do progresso por usuário autenticado; `AuthService.getMe()` agora expõe `onboarding` para usuários CRM e omite para staff interno.
   - `PUT /api/v1/users/onboarding/{tutorialKey}` usa apenas `TenantContext + userId` autenticado, sem permitir escopo cruzado.
