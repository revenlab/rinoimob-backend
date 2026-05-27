# Integração Google Gemini AI - Documentação

## Visão Geral

Este projeto implementa integração com **Google Gemini** seguindo princípios **SOLID**, permitindo fácil troca de provedores de IA (OpenAI, Claude, etc.) sem modificar a lógica de negócio.

## Arquitetura

### Camadas

```
┌─ Interface (DIP - Dependency Inversion Principle)
│  └─ AiLanguageModelService
│
├─ Implementações
│  ├─ GeminiAiService (REST-based, produção)
│  └─ MockAiService (fallback, desenvolvimento)
│
├─ DTOs & Exceptions
│  ├─ AiGenerationConfig (configurações de geração)
│  ├─ AiServiceException (exceções customizadas)
│  └─ AiPromptRequest/Response
│
└─ Controller
   └─ AiController (REST API)
```

## Configuração

### 1. Obter API Key do Gemini

1. Acesse [Google AI Studio](https://ai.google.dev/)
2. Clique em "Get API Key"
3. Crie uma chave API no Google Cloud Project
4. Copie a chave

### 2. Configurar Variáveis de Ambiente

#### `.env` (Desenvolvimento)

```bash
# AI Configuration
AI_PROVIDER=gemini
AI_GEMINI_API_KEY=sua-api-key-aqui
AI_GEMINI_MODEL=gemini-1.5-flash  # ou gemini-1.5-pro
```

#### `application.yml` (Já configurado)

```yaml
ai:
  provider: ${AI_PROVIDER:gemini}
  gemini:
    api-key: ${AI_GEMINI_API_KEY:}
    model: ${AI_GEMINI_MODEL:gemini-1.5-flash}
```

### 3. Modelos Disponíveis

| Modelo | Descrição | Recomendado para |
|--------|-----------|------------------|
| `gemini-1.5-pro` | Mais poderoso, melhor qualidade | Análises complexas, processamento pesado |
| `gemini-1.5-flash` | Mais rápido, mais barato | Geração rápida, prompts simples |

## Uso

### Via REST API

#### Gerar resposta simples

```bash
POST /api/ai/generate
Content-Type: application/json

{
  "prompt": "Gere um título interessante para um apartamento de 3 quartos em São Paulo"
}
```

**Response:**

```json
{
  "prompt": "Gere um título interessante para um apartamento de 3 quartos em São Paulo",
  "response": "Apartamento Moderno com 3 Quartos em Zona Premium de São Paulo",
  "generatedAt": "2024-05-27T12:30:45"
}
```

#### Verificar status

```bash
GET /api/ai/status
```

### Via Injeção de Dependência (Em Serviços)

```java
@Service
@RequiredArgsConstructor
public class PropertyService {
    
    private final AiLanguageModelService aiService;
    
    public void generatePropertyTitle(Property property) throws AiServiceException {
        String prompt = String.format(
            "Gere um título atrativo para um %s com %d quartos em %s",
            property.getPropertyType().getLabel(),
            property.getBedrooms(),
            property.getAddressCity()
        );
        
        String title = aiService.generateResponse(prompt);
        property.setTitle(title);
    }
    
    public void generateWithCustomConfig(String prompt) throws AiServiceException {
        AiGenerationConfig config = new AiGenerationConfig()
            .setTemperature(0.7)
            .setMaxTokens(2048);
        
        String response = aiService.generateResponse(prompt, config);
    }
}
```

## Tratamento de Erros

### Tipos de Erro (AiErrorType)

| Tipo | HTTP | Significado |
|------|------|------------|
| `CONFIGURATION_ERROR` | 503 | API Key não configurada |
| `UNAUTHORIZED` | 401 | API Key inválida |
| `RATE_LIMIT_ERROR` | 429 | Limite de requisições atingido |
| `INVALID_REQUEST` | 400 | Prompt ou configuração inválida |
| `TIMEOUT_ERROR` | 504 | Timeout na requisição |
| `API_ERROR` | 500+ | Erro no servidor Gemini |

### Tratamento

```java
try {
    String response = aiService.generateResponse(prompt);
} catch (AiServiceException e) {
    switch(e.getErrorType()) {
        case RATE_LIMIT_ERROR:
            // Implementar retry com backoff
            Thread.sleep(1000);
            break;
        case UNAUTHORIZED:
            // Verificar API Key
            logger.error("API Key inválida");
            break;
        default:
            logger.error("Erro de IA: {}", e.getMessage());
    }
}
```

## Substituir Provider

Para trocar de Gemini para outro provedor (ex: OpenAI):

### 1. Criar Nova Implementação

```java
@Service
@ConditionalOnProperty(name = "ai.provider", havingValue = "openai")
public class OpenAiService implements AiLanguageModelService {
    // Implementar interface
}
```

### 2. Atualizar Configuração

```yaml
ai:
  provider: openai  # Trocar para openai
  openai:
    api-key: ${OPENAI_API_KEY:}
    model: gpt-4
```

### 3. Nenhuma mudança no código de negócio!

A injeção automática usará `OpenAiService` sem modificação em nenhum serviço.

## Modelos de Prompt Úteis

### Geração de Título para Imóvel

```
Gere um título curto e atraente (máx 60 caracteres) para um 
{bedrooms}q apartamento em {city} ({state}).
Tipo: {propertyType}, Operação: {operation}
```

### Geração de Descrição

```
Escreva uma descrição concisa (máx 300 palavras) para um imóvel com:
- {bedrooms} quartos, {bathrooms} banheiros
- Área: {area}m²
- Localização: {address}
Seja profissional mas atraente para potenciais compradores.
```

### Melhorias para Listagem

```
Analise este anúncio e sugira 3 melhorias:
"{currentDescription}"
Retorne como lista numerada.
```

## Limites e Preços

### Gemini 1.5 Flash (Recomendado)

- **Limite grátis**: 15 requisições/minuto
- **Preço**: $0.075/milhão input tokens, $0.30/milhão output tokens
- **Melhor para**: Geração rápida, prototipagem

### Gemini 1.5 Pro

- **Preço**: $1.50/milhão input tokens, $6.00/milhão output tokens
- **Melhor para**: Análises complexas

## Monitoramento

### Logs

```
DEBUG: Enviando prompt para Gemini: 150 caracteres
DEBUG: Configuração: AiGenerationConfig{temperature=0.7, maxTokens=1024, topP=null, topK=null, useCache=false}
DEBUG: Resposta recebida: 200 caracteres
```

### Métricas

- Tempo médio de resposta: ~1-2 segundos
- Taxa de erro típica: < 0.1% (com retry)

## Troubleshooting

### "Serviço Gemini não está disponível"

**Verificar:**
- API Key configurada em `.env`?
- `AI_PROVIDER=gemini` está setado?
- Conexão de internet ativa?

```bash
# Testar
curl -X GET http://localhost:39000/api/ai/status
```

### "Unauthorized" (HTTP 401)

**Verificar:**
- API Key está correta?
- Token expirou?
- Aceitar Terms of Service em ai.google.dev?

### "Rate limit reached" (HTTP 429)

**Solução:**
- Implementar backoff exponencial
- Usar modelo flash em vez de pro
- Solicitar aumento de limite ao Google

### Respostas em branco

**Verificar:**
- Prompt está sendo enviado?
- Formato da resposta JSON está correto?
- Ativar logs DEBUG em `application.yml`:

```yaml
logging:
  level:
    com.rinoimob.service.ai: DEBUG
```

## Testes

```java
@SpringBootTest
class GeminiAiServiceTest {
    
    @Autowired
    private AiLanguageModelService aiService;
    
    @Test
    void testGenerateResponse() throws AiServiceException {
        String prompt = "Traduza 'Hello' para português";
        String response = aiService.generateResponse(prompt);
        
        assertThat(response).contains("Olá");
    }
    
    @Test
    void testWithCustomConfig() throws AiServiceException {
        AiGenerationConfig config = new AiGenerationConfig(0.3, 256);
        String response = aiService.generateResponse("Escreva um haiku", config);
        
        assertThat(response).isNotBlank();
    }
}
```

## Performance & Otimizações

### Cache de Respostas

Para prompts repetitivos, implementar cache:

```java
@Cacheable(value = "aiResponses", key = "#prompt")
public String generateResponse(String prompt) throws AiServiceException {
    return aiService.generateResponse(prompt);
}
```

### Timeout

```yaml
spring:
  webflux:
    client:
      timeout: 30000  # 30 segundos
```

## Links Úteis

- [Google AI Studio](https://ai.google.dev/)
- [Gemini API Docs](https://ai.google.dev/tutorials/rest_quickstart)
- [Pricing Calculator](https://ai.google.dev/pricing)
- [Model Cards](https://ai.google.dev/models)

## Suporte

Para issues:
1. Verificar logs com DEBUG ativado
2. Testar com `curl` ou Postman
3. Consultar documentação Gemini
4. Verificar status em https://status.cloud.google.com/

---

**Versão**: 1.0  
**Atualizado**: Maio 2024  
**Arquiteto**: Rinoimob AI Integration Team
