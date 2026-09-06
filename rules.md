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

- **Preços do catálogo de billing**: `V60__set_billing_plan_prices.sql` atualiza os planos globais para Free R$ 0,00, Starter R$ 99,90, Prime R$ 399,90 e Ultimate R$ 799,90 sem alterar migrations já aplicadas.

- **Testes de IA alinhados ao contrato atual**: `AiIntegrationTest` usa `/api/v1/ai/status` e `/api/v1/ai/generate`, correspondendo ao `@RequestMapping("/api/v1/ai")` de `AiController`.

- **Troca de e-mail com confirmação (#13)**: `POST /api/v1/users/request-email-change` exige a senha atual e envia token de uso único ao novo endereço; `POST /api/v1/auth/confirm-email-change` só então substitui a credencial global e todos os vínculos de usuário com o mesmo e-mail, revogando as sessões existentes. `verification_tokens.pending_email` guarda o endereço pendente até a confirmação.

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
  - `BillingGatewayPort` + `AsaasBillingGateway` (REST via `RestTemplate`, configurável por `billing.asaas.*`).
- Fluxo Asaas:
  - o backend cria/reutiliza o cliente Asaas e cria um Checkout hospedado recorrente mensal em `POST /v3/checkouts`.
  - o checkout aceita Pix e cartão, tem `externalReference` único (`tenantId-planCode-UUID`) e retorna a URL do Asaas ao app.
  - o plano só é sincronizado por webhook. `PAYMENT_RECEIVED`/`PAYMENT_CONFIRMED` ativa, `PAYMENT_OVERDUE` marca atraso e cancelamento/expiração/reembolso encerra a assinatura.
  - webhook público em `POST /api/v1/webhooks/asaas` valida o header `asaas-access-token` contra `ASAAS_WEBHOOK_TOKEN`.
  - `PAYMENT_OVERDUE` registra a data de vencimento em `tenant_subscriptions.past_due_at`; o app mostra um aviso fixo com CTA de regularização enquanto o status for `PAST_DUE`.
  - `PastDueSubscriptionScheduler` executa a cada hora (configurável por `ASAAS_PAST_DUE_SCAN_INTERVAL_MS`), cancela a recorrência no Asaas e rebaixa para Free quando o atraso ultrapassa sete dias. Se o cancelamento remoto falhar, mantém a assinatura em atraso para nova tentativa.
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
  - Ao iniciar checkout, assinatura registra `provider=ASAAS`, `status=PENDING` e `provider_checkout_id`.
- Ajustes de integração Asaas:
  - autenticação da API usa o header `access_token` com `ASAAS_API_KEY`.
  - cancelamento usa `DELETE /v3/subscriptions/{subscriptionId}`.
  - `TenantBillingPortalService.startCheckout()` agora retorna `503` quando billing não estiver configurado e `502` quando a API do provedor falhar.
  - `AsaasWebhookService` resolve tenant/plano pelo `externalReference` do checkout ou da cobrança e persiste IDs de checkout, cliente e assinatura retornados pelo Asaas.
 - Anti-fraude na troca de plano (upgrade/downgrade):
   - Novo campo `tenant_subscriptions.last_plan_change_at` (migration `V36__add_last_plan_change_to_tenant_subscriptions.sql`) para controlar cooldown de downgrade.
   - `TenantBillingPortalService.startCheckout()` agora bloqueia downgrade antes de 31 dias da última troca (`409 CONFLICT`).
   - Upgrade continua permitido a qualquer momento.
   - Em troca permitida, o backend cancela a assinatura anterior no Asaas (`cancelSubscription`) antes de deixar a nova assinatura em `PENDING`.
   - `AsaasWebhookService` atualiza `lastPlanChangeAt` quando detectar mudança real de plano.
 - Checkout único e reconciliação no Asaas:
   - `TenantBillingPortalService` agora gera `externalId` único por tentativa (`tenantId-planCode-UUID`) ao iniciar checkout.
   - `AsaasBillingGateway` envia esse identificador em `externalReference` para que eventos de checkout/cobrança possam ser reconciliados com segurança.
   - Migration `V50__replace_abacatepay_with_asaas.sql` limpa os identificadores do provedor legado e marca essas assinaturas como manuais; elas não são canceladas na nova API por engano.
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
- Asaas Checkout recorrente usa exclusivamente `CREDIT_CARD`: o Asaas rejeita PIX com `chargeTypes=RECURRENT`; PIX futuro deverá usar um fluxo `DETACHED` separado.
- O callback do Checkout Asaas é montado exclusivamente a partir de `ASAAS_CHECKOUT_CALLBACK_BASE_URL` HTTPS; o app envia somente o plano e não pode escolher URLs de redirecionamento.
- `V56__add_billing_customer_details.sql` guarda CPF/CNPJ, telefone e endereço no perfil de billing do tenant. O checkout atualiza ou cria o cliente Asaas antes da assinatura e bloqueia com `409` quando o cadastro estiver incompleto.
- O ambiente compartilhado já possui `V52` a `V55`; o branch de billing mantém cópias idênticas dessas migrations para Flyway validar a sequência antes de aplicar `V56`. Nunca usar `flyway repair` para ignorar migrations aplicadas.
- Webhooks Asaas reconciliam primeiro por `externalReference`; quando o evento de pagamento trouxer apenas `payment.checkoutSession`, usam `tenant_subscriptions.provider_checkout_id`. O checkout persiste o plano-alvo antes do redirecionamento para manter essa reconciliação segura.
- Checkout Asaas: falhas após criação/atualização do cliente preservam o `provider_customer_id` para evitar duplicação na nova tentativa; respostas de atualização sem `id` reutilizam o ID conhecido. Um checkout `PENDING` bloqueia nova tentativa, e assinatura `CANCELED` não aplica cooldown de downgrade.
- Identidade de cliente Asaas: antes de atualizar um ID persistido, o gateway o consulta; se estiver removido, restaura-o e atualiza seus dados. Se o ID não existir, consulta por `externalReference + cpfCnpj` antes de criar, para impedir duplicação entre tentativas ou ambientes.
- Faturas vencidas do Asaas: o webhook persiste `payment.invoiceUrl` e `payment.dueDate` na assinatura tenant-scoped; o portal só expõe o link enquanto o status for `PAST_DUE`, permitindo a regularização da cobrança existente sem abrir outra assinatura.
- Correlação de cobrança sem referência: quando o Asaas não enviar `externalReference`, `checkoutSession` ou `subscription`, o webhook pode reconciliar pelo `payment.customer` apenas se houver exatamente uma assinatura local com aquele `provider_customer_id`; ambiguidade é registrada e ignorada para evitar atualizar tenant incorreto.
- **Billing Asaas robusto (V58; substitui as regras antigas de troca imediata)**:
  - `tenant_subscription_changes` mantém upgrade/downgrade separado da assinatura vigente; apenas `PAYMENT_CONFIRMED`/`PAYMENT_RECEIVED` aplica upgrade e cancela o contrato anterior por outbox.
  - `tenant_billing_payments` é o ledger tenant-scoped; `asaas_webhook_events` é a inbox idempotente e `billing_provider_operations` é a outbox com retentativa.
  - O controller do webhook apenas autentica/persiste e responde `202`; processors assíncronos usam locks pessimistas e reconciliação periódica consulta o Asaas.
  - Downgrade/cancelamento ocorre no fim do ciclo. Após sete dias vencida, `SUSPENDED` aplica limites Free sem apagar plano/cliente/assinatura; pagamento tardio restaura o contrato.
  - Gestão financeira (`/billing/me`, faturas e mutações) é exclusiva de owner/admin; `/billing/status` é a visão leve para qualquer usuário autenticado do tenant.
  - Troca de cartão nunca recebe PAN/CVV: aceita apenas token do Asaas e fica protegida por `ASAAS_CARD_TOKEN_UPDATE_ENABLED`.
- **Entitlements e gatilhos de upgrade (V59)**:
  - `TenantPlanAccessService` resolve as flags efetivas do perfil tenant-scoped e retorna `402 Payment Required` para Blog, domínio customizado e Automações CRM fora do plano.
  - O Blog público fica vazio e o domínio customizado deixa de resolver após downgrade; subdomínio e catálogo público continuam disponíveis.
  - Eventos, agendamentos e retomadas de automação respeitam `automationCrmEnabled`; retomadas pendentes são encerradas sem executar novas ações quando o recurso deixa de estar habilitado.
  - `/billing/status` expõe as flags efetivas para gatilhos leves no app. `V59__align_billing_plan_entitlements.sql` reserva Suporte VIP ao Ultimate e corrige perfis Prime existentes.

- **Portfólio público de corretores (#63)**: `V61` adiciona slug/bio público por tenant, `V62` controla a oferta por todos ou por lista explícita de usuários, e `V63` preserva no lead o corretor de origem (`referred_by_user_id`) sem substituir a distribuição automática. Os endpoints públicos ficam em `/api/v1/public/brokers/{slug}` e `/properties`; o perfil próprio é atualizado em `PUT /api/v1/users/profile/public`.

- **Perfil comercial do corretor (#63)**: `V64` inclui foto pública no storage, URL do Instagram e CRECI. O upload autenticado aceita imagens de até 5 MB e remove o arquivo anterior; a resposta pública expõe apenas URL, Instagram e CRECI.

- **Pipeline obrigatório na criação de leads**: `LeadService` resolve o pipeline padrão do tenant e sua primeira etapa aberta antes de persistir leads públicos ou leads criados automaticamente pelo WhatsApp, satisfazendo as colunas obrigatórias introduzidas por `V52`.
