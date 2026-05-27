# Próximos Passos - Integração Gemini AI

## ✅ Implementação Base Completa

Toda a infraestrutura de integração com Gemini foi criada com arquitetura SOLID:

- ✅ Interface desacoplada: `AiLanguageModelService`
- ✅ Implementação Gemini via REST: `GeminiAiService`
- ✅ Fallback para desenvolvimento: `MockAiService`
- ✅ Endpoints REST: `/api/ai/generate`, `/api/ai/status`
- ✅ Tratamento robusto de erros: `AiServiceException` com 6 tipos diferentes
- ✅ Configuração customizável: `AiGenerationConfig`
- ✅ Suporte a múltiplos modelos: `gemini-1.5-flash`, `gemini-1.5-pro`
- ✅ Documentação completa: `AI_GEMINI_INTEGRATION.md`
- ✅ Exemplo prático: `PropertyAiEnhancementService_EXAMPLE.java`

---

## 📋 Ações Necessárias ANTES de Usar em Produção

### 1. Obter API Key do Gemini (5 min)

```bash
# Ir em: https://ai.google.dev/
# 1. Clique em "Get API Key"
# 2. Clique em "Create API Key in new Google Cloud project"
# 3. Copie a chave gerada
```

### 2. Configurar Variáveis de Ambiente

```bash
# Em .env (ou em variáveis do sistema):
AI_PROVIDER=gemini                          # ou "mock" para testes
AI_GEMINI_API_KEY=sua_chave_aqui           # Cole a chave do passo 1
AI_GEMINI_MODEL=gemini-1.5-flash           # ou gemini-1.5-pro para mais qualidade
```

### 3. Testar Conexão com Gemini

```bash
# Após iniciar a aplicação:
curl -X GET http://localhost:39000/api/ai/status
# Resposta esperada: {"available":true,"message":"AI service is available"}
```

### 4. Fazer Primeiro Teste de Geração

```bash
curl -X POST http://localhost:39000/api/ai/generate \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <seu-jwt-token>" \
  -d '{
    "prompt": "Gere um título curto para um apartamento com 3 quartos em São Paulo",
    "config": {
      "temperature": 0.3,
      "maxTokens": 64
    }
  }'
```

---

## 🎯 Integrações Recomendadas (Por Prioridade)

### Priority 1: PropertyService - Dynamic Content Generation

**Arquivo:** `src/main/java/com/rinoimob/service/PropertyService.java`

```java
@RequiredArgsConstructor
public class PropertyService {
    private final AiLanguageModelService aiService;  // Injetar

    @Transactional
    public PropertyResponse createProperty(CreatePropertyRequest req) {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        Property property = new Property();
        property.setTenantId(tenantId);
        applyRequest(property, req);
        
        // ✨ NOVO: Gerar título se vazio
        if ((property.getTitle() == null || property.getTitle().isBlank()) 
            && aiService.isAvailable()) {
            String title = aiService.generateResponse(
                buildTitlePrompt(property),
                new AiGenerationConfig().setTemperature(0.3).setMaxTokens(64)
            );
            property.setTitle(title);
        }
        
        // ✨ NOVO: Gerar descrição se vazia
        if ((property.getDescription() == null || property.getDescription().isBlank())
            && aiService.isAvailable()) {
            String desc = aiService.generateResponse(
                buildDescriptionPrompt(property),
                new AiGenerationConfig().setTemperature(0.7).setMaxTokens(512)
            );
            property.setDescription(desc);
        }
        
        property = propertyRepository.save(property);
        return toResponse(property);
    }

    private String buildTitlePrompt(Property p) {
        return String.format(
            "Gere um título conciso (máx 60 caracteres): %dq %s em %s",
            p.getBedrooms() != null ? p.getBedrooms() : 0,
            p.getPropertyType() != null ? p.getPropertyType().name() : "",
            p.getAddressCity() != null ? p.getAddressCity() : ""
        );
    }

    private String buildDescriptionPrompt(Property p) {
        return String.format(
            "Descreva este imóvel (200-300 palavras): %s com %d quartos, " +
            "%d banheiros, %s m² em %s. Condição: %s",
            p.getPropertyType() != null ? p.getPropertyType().name() : "Imóvel",
            p.getBedrooms() != null ? p.getBedrooms() : 0,
            p.getBathrooms() != null ? p.getBathrooms() : 0,
            p.getAreaTotal() != null ? p.getAreaTotal() : "N/A",
            p.getAddressCity() != null ? p.getAddressCity() : "",
            p.getCondition() != null ? p.getCondition().name() : "N/A"
        );
    }
}
```

**Tempo estimado:** 30-45 min  
**Benefício:** Usuários não precisam digitar título/descrição manualmente

---

### Priority 2: BlogPostService - Content Enhancement

**Arquivo:** `src/main/java/com/rinoimob/service/BlogPostService.java`

Adicionar método para gerar sugestões de melhoria no conteúdo do blog:

```java
public String generateBlogSuggestions(BlogPost post) throws AiServiceException {
    String prompt = String.format(
        "Analise este artigo de blog e sugira 3 melhorias SEO:\n\n" +
        "Título: %s\n" +
        "Conteúdo: %s\n\n" +
        "Retorne como lista numerada.",
        post.getTitle(),
        post.getContent()
    );
    
    return aiService.generateResponse(prompt, 
        new AiGenerationConfig().setTemperature(0.6).setMaxTokens(300));
}
```

**Tempo estimado:** 20 min  
**Benefício:** Melhorar qualidade de conteúdo com sugestões automáticas

---

### Priority 3: Frontend - AI Suggestions UI

**Arquivo:** `rinoimob-app/src/components/PropertyForm.vue` (ou novo componente)

Adicionar componente que mostra sugestões de IA durante o preenchimento:

```vue
<template>
  <div v-if="aiSuggestions" class="ai-suggestions">
    <div class="suggestion-badge">
      <span class="ai-icon">✨</span>
      <p>{{ aiSuggestions }}</p>
      <button @click="applySuggestion">Aplicar</button>
      <button @click="dismissSuggestion">Descartar</button>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { usePropertyStore } from '@/stores/propertyStore'

const propertyStore = usePropertyStore()
const aiSuggestions = ref(null)

// Disparar sugestão quando o usuário termina de preencher características
watch(() => propertyStore.form.bedrooms, async (newVal) => {
  if (newVal && !propertyStore.form.title) {
    const response = await fetch('/api/ai/generate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        prompt: `Sugira um título curto: ${newVal} quartos, ${propertyStore.form.city}`
      })
    })
    aiSuggestions.value = await response.json()
  }
})
</script>
```

**Tempo estimado:** 1-2 horas  
**Benefício:** UX mais intuitiva com sugestões em tempo real

---

### Priority 4: Adicionar Mais Providers (Extensibilidade)

Exemplo com OpenAI (seguindo o mesmo padrão):

```java
// 1. Criar OpenAiService implementando AiLanguageModelService
@Service
@ConditionalOnProperty(name = "ai.provider", havingValue = "openai")
public class OpenAiService implements AiLanguageModelService {
    // ... implementação similar a GeminiAiService ...
}

// 2. Adicionar configuração em application.yml
ai:
  provider: openai
  openai:
    api-key: ${AI_OPENAI_API_KEY}
    model: gpt-4-turbo

// 3. Nenhuma mudança necessária em PropertyService ou Controllers!
// A injeção de dependência automaticamente usa OpenAiService
```

**Tempo estimado:** 1-2 horas por provider  
**Benefício:** Flexibilidade de escolher melhor provider conforme necessidade

---

### Priority 5: Implementar Caching de Respostas

```java
@Service
public class AiCacheService {
    private final AiLanguageModelService aiService;
    private final CacheManager cacheManager;
    
    @Cacheable(value = "aiResponses", key = "#prompt")
    public String generateCached(String prompt) {
        return aiService.generateResponse(prompt);
    }
}
```

**Tempo estimado:** 30-45 min  
**Benefício:** Reduzir custos de API e melhorar performance

---

### Priority 6: Auditar e Logar Chamadas de IA

```java
@Aspect
@Component
public class AiAuditAspect {
    private final AuditService auditService;
    
    @AfterReturning(pointcut = "@annotation(AiAudited)", returning = "result")
    public void logAiCall(JoinPoint jp, String result) {
        AiAudit audit = AiAudit.builder()
            .userId(TenantContext.getUserId())
            .tenantId(TenantContext.getTenantId())
            .prompt(jp.getArgs()[0].toString())
            .responseLength(result.length())
            .timestamp(LocalDateTime.now())
            .build();
        auditService.save(audit);
    }
}
```

**Tempo estimado:** 1-1.5 horas  
**Benefício:** Rastreabilidade e conformidade regulatória

---

## ⚠️ Considerações Importantes

### Custos
- **Gemini 1.5 Flash:** $0.075/1M input tokens, $0.30/1M output tokens (mais barato, rápido)
- **Gemini 1.5 Pro:** $3.50/1M input tokens, $10.50/1M output tokens (mais potente)
- Implemente caching e rate limiting para controlar gastos

### Rate Limiting
```java
@Service
public class AiRateLimitService {
    private final RateLimiter limiter = RateLimiter.create(10.0); // 10 req/sec
    
    public void checkRateLimit() throws AiServiceException {
        if (!limiter.tryAcquire()) {
            throw new AiServiceException("Rate limit exceeded", 
                AiErrorType.RATE_LIMIT_ERROR);
        }
    }
}
```

### Segurança
- ✅ API Key em variáveis de ambiente (nunca em código)
- ✅ Validar entrada de prompts (evitar injection)
- ✅ Logar todas as chamadas para auditoria
- ✅ Implementar timeout nas requisições (evitar hang)

### Latência
- ✅ Considerar async/await em frontend (`async generateTitle()`)
- ✅ Usar `CompletableFuture` no backend para operações em paralelo
- ✅ Exemplo: gerar título E descrição simultaneamente

---

## 📚 Referências Úteis

- **Documentação Oficial:** https://ai.google.dev/docs
- **Modelos Disponíveis:** https://ai.google.dev/models
- **Pricing:** https://ai.google.dev/pricing
- **Rate Limits:** https://ai.google.dev/docs/limits
- **Best Practices:** https://ai.google.dev/docs/best-practices

---

## ✅ Checklist de Produção

- [ ] API Key configurada e testada
- [ ] MockAiService funcionando para dev/test
- [ ] GeminiAiService testado com Gemini real
- [ ] Rate limiting implementado
- [ ] Caching de respostas comum
- [ ] Logs e auditoria funcionando
- [ ] Tratamento de erros completo (timeout, rate limit, etc)
- [ ] Testes unitários para AI services
- [ ] Documentação atualizada com exemplos
- [ ] Monitoramento de custos de API
- [ ] Fallback gracioso se IA indisponível

---

## 🎉 Resumo

A arquitetura foi preparada para ser:

- **Extensível:** Adicione OpenAI, Claude, local models sem tocar no código existente
- **Testável:** MockAiService para testes sem custos de API
- **Confiável:** Tratamento robusto de erros e fallbacks
- **Performático:** Caching, async, rate limiting
- **Seguro:** API Key em env, validação de entrada, logs
- **Manutenível:** SOLID principles, código limpo, bem documentado

**Próximo passo:** Configure a API Key e teste o endpoint `/api/ai/status` 🚀
