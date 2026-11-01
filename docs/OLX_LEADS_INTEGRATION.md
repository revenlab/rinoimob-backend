# Recebimento de leads OLX e Canal Pro

Existem dois contratos de integração distintos. A API de leads em `developers.olx.com.br` exige OAuth para cadastrar a URL de recebimento; atualmente a documentação lista categorias de veículos. Para imóveis, usar a integração homologada do Canal Pro descrita em `developers.grupozap.com`. OAuth não habilita categorias que o fornecedor não oferece.

## Configuração OAuth OLX

No ambiente do backend, configurar:

```dotenv
GRUPO_OLX_CLIENT_ID=<client_id fornecido pela OLX>
GRUPO_OLX_CLIENT_SECRET=<client_secret fornecido pela OLX>
GRUPO_OLX_REDIRECT_URI=https://<api-publica>/api/v1/integrations/grupo-olx/oauth/callback
PUBLIC_API_URL=https://<api-publica>
INTEGRATIONS_ENCRYPTION_KEY=<chave estável com pelo menos 32 caracteres>
```

Cadastrar a URI de callback exatamente como acima com a OLX. Em desenvolvimento, usar a origem HTTPS pública do túnel para o callback e `PUBLIC_API_URL`. A chave de criptografia deve permanecer estável entre deploys. As variáveis são lidas no startup e precisam ser aplicadas no ambiente de execução do backend.

No app, abrir Integrações → Grupo OLX → Conectar conta OLX. O backend solicita `autoservice basic_user_info` e gera um estado aleatório válido por dez minutos. O callback valida e consome esse estado sob lock, identifica a conta autorizada, troca o código e registra o endpoint de leads com Bearer OAuth. Tokens não são enviados ao navegador. O cadastro inicial usa POST; a reconexão usa PUT com o ID da configuração existente e preserva a credencial de entrega. A reconexão exige a mesma conta; para outra conta, criar outra conexão. Uma configuração removida externamente exige nova conexão/homologação.

Após o cadastro, configurar o funil e a distribuição em Roteamento de leads. Eventos recebidos são persistidos criptografados antes da resposta 202 e processados pela inbox existente, com deduplicação e limites do plano. Quando `externalId` não vier, uma identidade derivada de origem, anúncio, data, nome, contato e mensagem evita duplicar reentregas; dois contatos com todos esses campos idênticos serão tratados como o mesmo evento.

Cada conexão usa sua própria URL com `inboundKey`. O campo `token` cadastrado na API contém uma credencial aleatória codificada em Base64; a entrega deve enviá-la em `Authorization` (também é aceita com o prefixo Basic). O backend compara o hash em tempo constante e não aceita a chave global do Canal Pro para conexões OAuth já cadastradas. O formato efetivo de entrega e a categoria contratada devem ser validados na homologação com a OLX.

Se a autorização ou o cadastro falhar, o app mostra erro seguro e permite reconexão. A API documentada não fornece fluxo de refresh token: renovar autorização pelo app quando necessário. Uma conexão que já funciona permanece ativa durante a reconexão; conexões pausadas permanecem pausadas. Pausar controla o processamento local e não exclui a configuração externa.

## Canal Pro / imóveis

Criar a conexão Canal Pro no app, copiar a URL de entrada e cadastrá-la no Canal Pro após homologar o CRM. `GRUPO_OLX_SECRET_KEY` é a chave global por CRM **fornecida pelo Grupo ZAP**. As entregas usam Basic Authentication, com essa chave na senha. Esse fluxo não depende das credenciais OAuth acima.

## Referências oficiais

- https://developers.olx.com.br/anuncio/api/oauth.html
- https://developers.olx.com.br/lead/how_to.html
- https://developers.olx.com.br/lead/leads.html
- https://developers.grupozap.com/webhooks/security.html
- https://developers.grupozap.com/webhooks/integration_leads.html

## Validação local

Os testes com cliente HTTP simulado cobrem os contratos OAuth e de cadastro. Os testes de serviço cobrem isolamento por tenant, estado inválido/expirado/reutilizado, recusa, falha de cadastro, reconexão e autenticação de entrega. A homologação real exige as credenciais da OLX, uma conta elegível e endpoints públicos HTTPS.
