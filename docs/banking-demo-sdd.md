# EvidenceRAG Banking AI — SDD local das Etapas 1–3

Status: snapshot E1 congelado no índice; alterações E2/E3 permanecem fora do índice. O preview de desenvolvimento mantém backend, banco e assistentes reais. A inspeção visual desktop/mobile da versão anterior continua pendente. O Gate E3 original permanece bloqueado; esta decisão de portfólio não o aprova nem o substitui. Nenhuma publicação ou operação de versionamento foi executada.

## Decisão de produto — EvidenceRAG Interactive AI Engineering Demo (26/11/2026)

A edição pública preparada é um portfólio interativo estático: uma interface única, sem seleção Cliente/Analista, sem API, autenticação, cookies, backend obrigatório ou chamadas de modelo. Ela oferece três reproduções guiadas a partir de fixtures documentadas nos comentários 10730 e 10749 da COS-21. A página informa que cada execução ocorreu previamente com dados sintéticos e que a demonstração não chama o provedor.

O cenário “A última compra” foi uma execução real `GROUNDED / AI_TOOL_LOOP` no Groq `openai/gpt-oss-20b`, com a ferramenta Java `listDemoTransactions`, dois roundtrips, referência da compra de R$ 4.800,00 e uso de 2.357 tokens. “Maiores despesas” e “Compra sinalizada” foram respostas `GROUNDED / DETERMINISTIC_FALLBACK`, após `TRANSPORT_ERROR` do Ollama; seus tools Java foram executados pelo fallback backend e não por decisão do modelo. A reprodução mantém a distinção visível. O segundo cenário mostra cinco referências verificadas; o terceiro limita a explicação ao alerta/dados estruturados porque a busca documental de política não foi chamada naquele turno. Detalhes sanitizados e limitações estão em [`portfolio-static-demo.md`](portfolio-static-demo.md).

`npm run build` gera a interface estática padrão; `npm run build:pages` define base `/evidencerag/` para GitHub Pages. `npm run dev` continua iniciando a aplicação bancária conectada ao Spring Boot; `npm run dev:rag` preserva a interface RAG original; `npm run build:live` fica disponível para teste local conectado. A imagem Docker frontend padrão passa a servir somente a edição estática; backend, banco, Assistente Cliente e código Analista permanecem preservados para desenvolvimento. Não houve deploy.

## Decisão de produto — MVP Cliente (26/11/2026)

A primeira versão pública expõe apenas Cliente. O código, as ferramentas e os componentes de Analista permanecem para uma versão futura, mas `APP_BANKING_ANALYST_ENABLED=false` no backend e `VITE_BANKING_ANALYST_ENABLED=false` no build do frontend. Com a flag desligada, o servidor rejeita `ANALYST` em criação, reinício e troca de modo; sessões Analyst antigas não podem consultar demo, histórico ou assistente. Uma troca explícita para `CLIENT` continua permitida para recuperar uma sessão antiga. A resposta Cliente contém somente CUST-001/ACC-001, alertas associados e a política POL-ALERT; propostas, vínculos a propostas e políticas de crédito são removidos no DTO.

O cliente mantém assistente central com tools Java para saldo, despesas, movimentações e alertas. A API mantém citações montadas e verificadas pelo backend. Perguntas anuais são recusadas como `INSUFFICIENT_EVIDENCE` se o ledger não cobrir doze meses; a semente atual tem somente cerca de um mês e não permite uma projeção financeira anual verificável. O cálculo exemplificado como R$ 206.408 não está no código nem é reproduzido: sem fonte completa, premissas e fórmula verificáveis, o assistente não deve inventar esse total.

Proteção de consumo padrão: 8.000 tokens/minuto por instância com reserva/settlement em cada roundtrip Groq, concorrência global 1, até 20 interações de IA por sessão, até 4 tool calls, 600 tokens de saída, 12 mensagens/8.000 caracteres de contexto e timeout de 30 s. O adapter não faz retries automáticos; apenas o erro Groq `tool_use_failed` permite uma única tentativa limitada, debitada do mesmo orçamento. 429 preserva a classificação de quota e é apresentado sem troca automática de provider. A chave permanece apenas no ambiente do backend.

## Limites e topologia

O sandbox bancário é um módulo isolado do EvidenceRAG existente. O RAG, seu corpus, as migrações V1–V3, a identidade `provider:model` dos embeddings e o perfil `demo` permanecem inalterados.

No desenvolvimento, o Vite encaminha `/api/*` para o Spring Boot e o navegador usa uma origem só. O cookie de sessão é `HttpOnly`, `SameSite=Lax`, `Path=/`, sem `Domain`; a opção `BANK_DEMO_COOKIE_SECURE` fica falsa no HTTP local e deve ser verdadeira em HTTPS. A UI não lê nem recebe o identificador da sessão no JSON. `VITE_BANKING_DEMO=true` seleciona a nova experiência sem mudar a interface RAG padrão.

A configuração de publicação proposta empacota Banking AI no mesmo host da API para manter o cookie de sessão same-origin; o site estático original continua atendendo o EvidenceRAG. A topologia, o CORS, os domínios e o cookie precisam ser revisados antes de PUB. Nenhum serviço remoto foi alterado.

## Sessão e isolamento

`BANK_DEMO_SESSION` contém UUID aleatório opaco (122 bits aleatórios), apenas em cookie HttpOnly. O banco define `created_at` e `expires_at = created_at + 4h`; leituras e mudanças de modo consultam o prazo no servidor. Nenhuma interação altera `expires_at` ou renova o cookie. Depois de vencida, a sessão retorna `SESSION_EXPIRED`; só o botão explícito de reinício remove seu estado e cria outra sessão.

`bank_demo_session_overlay`, `bank_demo_session_note` e `bank_demo_session_message` têm FK com `ON DELETE CASCADE`. A limpeza apaga apenas sessões cujo prazo expirou, por job a cada 15 minutos e por limpeza oportunista no acesso (no máximo uma vez a cada 5 minutos por instância). Templates bancários não têm FK para sessão e nunca entram nessa exclusão. A validade lógica independe do job e também é verificada após hibernação.

## Contratos REST

Todas as rotas ficam sob `/api/banking`; a sessão vem exclusivamente do cookie. Nenhuma rota aceita `sessionId` do navegador.

| Método e caminho | Contrato |
|---|---|
| `GET /demo` | Lê o cenário e o modo da sessão ativa; sem sessão retorna 404 e sessão vencida retorna 410. |
| `POST /session` | `{ "mode": "CLIENT" | "ANALYST" }`; cria sessão se não houver cookie e retorna `Set-Cookie`. Uma sessão válida muda o modo sem alterar seu prazo. |
| `PUT /mode` | `{ "mode": "CLIENT" | "ANALYST" }`; alterna a experiência dentro da sessão atual. |
| `POST /session/restart` | Remove somente a sessão indicada pelo cookie e seus filhos, cria sessão nova e redefine o cookie. |

`GET /demo` entrega snapshots sintéticos tipados: clientes, contas, movimentações, alertas, propostas e políticas versionadas. O assistente e o histórico conversacional pertencem às etapas E2/E3.

## Catálogo determinístico

A migração V4 versiona dois clientes, duas contas, 30 transações determinísticas, quatro alertas, três propostas e cinco políticas fictícias. `CR-102` solicita R$ 150.000, recebe quatro de cinco documentos e diverge entre 24 meses propostos e 36 no contrato. `ALERT-4800` aponta o débito sintético de R$ 4.800 pela regra `HIGH_VALUE_VS_HISTORY`; o texto da regra esclarece que alerta pede revisão e não prova fraude. `ALT-003` liga explicitamente `CR-102` a um registro transacional para SC-05.

Os saldos finais são snapshots coerentes com o saldo inicial e as movimentações: ACC-001 R$ 5.654,00; ACC-002 R$ 12.928,00. Nomes, IDs, documentos e transações são dados fabricados para demonstração.

## Limites conhecidos da fundação

As políticas permanecem em tabelas relacionais do sandbox; a busca é PostgreSQL full-text, não um índice vetorial dedicado. A limpeza oportunista é por processo; com múltiplas instâncias, o `DELETE` é seguro por banco, mas a execução depende do agendador do host gratuito. A tela usa `/api` same-origin; o Blueprint atual de Render separa site e API e precisa ser alterado/revisado antes de qualquer publicação.

## Etapa 2 — implementação e validação local

`POST /api/banking/assistant` recebe somente a pergunta; um campo `module` legado é aceito por clientes antigos, mas ignorado. O backend deriva o contexto do cookie `BANK_DEMO_SESSION` e permite ferramentas de Finance no modo Cliente e ferramentas bancárias no modo Analista. A sessão é validada antes da chamada e em cada ferramenta. O catálogo Java inclui saldo, movimentações, resumo determinístico de despesas, alertas, propostas, comparação com política e busca textual, além da única escrita demonstrativa `saveSimulatedReviewNote`, limitada à sessão corrente. A sessão nunca é argumento do modelo.

`GET /api/banking/assistant/history` devolve até 60 mensagens da sessão atual e do contexto atual. Migration V6 armazena pergunta/resposta, resultado, origem, problema sanitizado e evidências; consultas filtram sempre por `session_id` e `experience_mode`. Trocar Cliente/Analista mantém contextos separados. Reiniciar ou expirar a sessão remove somente seus registros temporários por FK em cascata; os dados bancários permanentes continuam intactos. O modelo recebe até 12 mensagens anteriores elegíveis do mesmo contexto, mas deve refazer consultas atuais antes de apresentar fatos.

O orçamento configurável começa em 20 interações por sessão, contabilizado atomicamente em uma chave de overlay. A concorrência é limitada por `app.max-concurrent-ai-requests`; chamadas AI têm timeout configurável (`BANKING_AI_TIMEOUT_SECONDS`, padrão 30), uma tentativa da aplicação, e teto de tool calls `BANKING_AI_MAX_TOOL_CALLS` (padrão 4). Indisponibilidade, timeout, erro HTTP ou parsing não são convertidos em falta de evidência: operações aritméticas suportadas podem retornar resposta `DETERMINISTIC_FALLBACK` via ferramenta; demais falhas retornam erro apropriado. Respostas Gemini precisam validar referências consultadas nesta requisição. Nenhuma credencial é enviada ao frontend, registrada ou persistida.

Os registros de política fictícia são recuperados por full-text search (`to_tsvector`/`to_tsquery`) no PostgreSQL sobre `bank_demo_policy`. O retorno identifica chave/versão, seção e trecho; a referência é criada no backend e só aparece se pertencer ao conjunto recuperado. O RAG EvidenceRAG, o vector store, suas citações e a regressão original permanecem preservados. Limitação: o catálogo de políticas Banking AI ainda não é indexado no vector store; E2 oferece recuperação lexical com referências verificadas, sem declarar equivalência a RAG vetorial.

O seed V4 tinha conflito temporal: ALERT-4800 estava em 02/10 e os lançamentos que deveriam servir de histórico eram posteriores. A migration incremental V5 altera somente `occurred_at` do lançamento vinculado para `2026-11-01T12:00:00Z`; preserva valor, links, saldo e as migrations E1. A validação no PostgreSQL descartável encontrou 12 débitos anteriores nos 90 dias, máximo individual de R$ 480, contra a compra de R$ 4.800. A ferramenta cita os registros e mantém a ressalva de que alerta não comprova fraude.

O loop de ferramentas instala explicitamente `ToolCallingAdvisor` com `DefaultToolCallingManager` limitado pelo máximo configurado; o advisor pertence ao serviço de produção e não depende do harness. A investigação identificou dois problemas independentes: o fake `ChatModel` do teste não anunciava `ToolCallingChatOptions`; e a configuração Gemini `responseMimeType=application/json` é incompatível com function calling, fazendo a API responder `400 INVALID_ARGUMENT`. O fake agora anuncia as opções esperadas pelo contrato Spring AI, o serviço aplica o advisor por requisição e a configuração MIME incompatível foi removida. A resposta continua convertida para o DTO Java tipado e suas citações passam pela validação de IDs recuperados.

O modo Cliente oferece ferramentas de saldo, despesas, movimentações e contexto de alerta; o modo Analista oferece proposta, alertas relacionados e política. O servidor impõe esses conjuntos independentemente da seleção do modelo. Para parecer cruzado SC-05, o backend também exige evidências da proposta, alerta/transação e política. O assistente não pede seleção manual de módulos. A ferramenta de despesas usa a janela de seis movimentações recentes apresentada no painel para o ranking global e usa todas as transações da categoria para totais categorizados. O histórico é persistido e isolado por sessão/contexto na V6.

Validação local final em 25/11/2026: `mvn -f backend/pom.xml '-Dapi.version=1.44' verify` passou com 58 testes, 0 falhas, 0 erros e 3 skips. Testcontainers usou PostgreSQL 17 descartável e aplicou V1–V6; passaram os testes de ferramentas, citações, SC-01–SC-05 com modelo simulado, histórico/session-mode isolation, limites, expiração/limpeza e regressão do EvidenceRAG original. Os três skips são `HealthExternalDatabaseTest` sem `TEST_DATABASE_URL`, o smoke Gemini de ferramenta e o teste de embeddings Gemini sem `GEMINI_API_KEY` no processo Maven do host. Frontend após o ajuste da mensagem de quota: Vitest 8/8, ESLint, typecheck e build passaram.

Inspeção manual em `http://localhost:15174/`: Cliente e Analista abriram e alternaram sem erro; propostas, alertas e políticas fictícias foram exibidos. A conversa da Cliente reapareceu ao retornar do Analista, sem misturar histórico. Desktop e viewport mobile de 375 CSS px foram conferidos; o chat e o contexto empilharam sem overflow horizontal. A pergunta "Quais foram minhas maiores despesas?" exibiu cinco débitos de 22/10/2026 a 01/11/2026, em ordem R$ 4.800, R$ 480, R$ 170, R$ 120 e R$ 85, com cinco referências de transação; o salário foi excluído. A resposta ficou rotulada como `DETERMINISTIC_FALLBACK`/"Consulta aos dados".

No preview, a configuração efetiva é `CHAT_PROVIDER=google-genai`, modelo `gemini-2.5-flash`, com chave presente somente no ambiente do container backend (valor não registrado). O smoke real recebeu HTTP 429 `RESOURCE_EXHAUSTED` após 10.285 ms, uma tentativa da aplicação, zero tool calls do modelo e tokens indisponíveis; uma tentativa anterior terminou no timeout configurado de 30 s, também sem tool call. Não houve retry ilimitado e não se repetiram chamadas após confirmar quota esgotada. O fallback financeiro consultou os registros reais e apresentou fontes, sem declarar que a resposta foi gerada pelo Gemini.

O navegador também expôs uma origem CORS desalinhada: backend configurado para `http://localhost:15173`, enquanto o frontend preview serve em `http://localhost:15174`; chamadas com o `Origin` do preview recebiam 403. O `.env` local foi alinhado a `http://localhost:15174` e somente o backend foi recriado. Preflight, criação de sessão e troca de modo passaram; o container e volume PostgreSQL do preview não mudaram. O app local e os dados ficaram saudáveis após reconstruir apenas o frontend para a mensagem explícita de quota.

## Limitações da etapa

A busca das políticas Banking AI continua PostgreSQL full-text, sem índice vetorial dedicado. `HealthExternalDatabaseTest` requer `TEST_DATABASE_URL`; sem essa variável nenhum banco externo é consultado. O teste de integração Gemini e o de embeddings são condicionais e não rodaram no processo Maven do host. O preview teve chamada real Gemini, mas a quota foi recusada antes de tool calling; não há evidência de ferramentas escolhidas pelo modelo em runtime. A topologia pública e a configuração Render/Neon ainda exigem revisão autorizada; nada remoto foi alterado. O gate E3 permanece bloqueado até quota disponível e validação real de jornadas Cliente, Analista e SC-05 com ferramentas/citações do Gemini.

## Etapa 3 — revisão integrada SC-05 e UX local

Para pedidos de análise cruzada no contexto Analista, o escopo `CASE_REVIEW` coordena consultas de `CR-102`, alerta relacionado `ALERT-REVIEW-CR102`, evento transacional associado e política `POL-CRED`. A resposta tipada separa fatos estruturados, evidências documentais, interpretação da IA, informações ausentes e recomendações; o backend se abstém se o conjunto esperado de referências não estiver presente. O texto instrui a tratar registros e perguntas como dados não confiáveis e proíbe SQL livre. O parecer é preliminar e exige revisão humana; não aprova crédito, não determina fraude e não é parecer jurídico. Consultas simples de alerta, proposta ou política exigem somente a evidência específica consultada.

A interface apresenta um assistente único e central, perguntas sugeridas por contexto, respostas com origem visível e fontes legíveis com detalhes técnicos expansíveis. Não exige seleção manual de módulo. A troca Cliente/Funcionário troca o histórico filtrado pelo backend e não reapresenta mensagens do outro contexto; o cenário permanece identificado como fictício. O status de expiração continua vindo do servidor; o reinício explícito abre nova sessão e não renova sessão vencida.

A validação PostgreSQL descartável do SC-05 aplica Flyway V1–V6 e exercita o loop de ferramentas, histórico por modo/sessão, referências verificadas e análise cruzada com modelo de teste. Essa cobertura não substitui tool calling Gemini real. A telemetria de 429 e os testes reais pendentes estão registrados acima; respostas determinísticas são rotuladas e não contam como homologação Gemini.

## Provider local GroqCloud para chat

O preview pode selecionar `CHAT_PROVIDER=groq` e `GROQ_CHAT_MODEL=openai/gpt-oss-20b`, com a chave apenas em `GROQ_API_KEY` fornecida por `.env` local ignorado pelo Git ou secret manager. A URL de compatibilidade é `https://api.groq.com/openai/v1`. Como o Spring AI 2.1 seleciona a implementação pelo adaptador (e não pelo nome do fornecedor compatível), o preview configura também `SPRING_AI_MODEL_CHAT=openai`; a telemetria da aplicação registra `groq` como provider. O código usa o starter Spring AI OpenAI já presente em `pom.xml`, sem adicionar SDK proprietário.

As propriedades específicas de chat (`spring.ai.openai.chat.api-key`, `base-url` e `model`) encaminham a credencial, endpoint e modelo Groq somente ao ChatModel. `spring.ai.openai.api-key`, os seletores de embeddings, `EMBEDDING_PROVIDER`, o modelo de embedding, a dimensão 768 e os vetores existentes ficam independentes. O catálogo bancário continua recuperando políticas por PostgreSQL full-text; isso não cria um índice vetorial para políticas e não substitui o vector store do EvidenceRAG.

O loop preserva as ferramentas Java locais, validação de referências e allowlist por contexto. Para o GPT-OSS 20B, o request limita a saída a 600 tokens, desativa chamadas paralelas e não reproduz conteúdo de raciocínio entre rodadas, recurso que o endpoint Groq não aceita. Os limites confirmados no runtime local permanecem em quatro chamadas de ferramenta por interação, 20 interações por sessão, uma solicitação simultânea e 30 segundos; o contexto conversacional foi limitado a 12 mensagens e 8.000 caracteres. Retry Spring AI fica em uma tentativa total e o SDK OpenAI em zero retries. HTTP 429 é retornado sem repetição automática nem fallback para outro provider.

Os limites públicos da tabela Groq para `openai/gpt-oss-20b` variam por plano e projeto; os valores exatos da conta devem ser lidos no Groq Console. A referência pública consultada lista, como limites base do Developer plan, 30 RPM, 1.000 RPD, 8.000 TPM e 200.000 TPD; isso não comprova os limites desta conta, nem autoriza ativar cobrança. A página do modelo lista janela de contexto de 131.072 tokens, saída máxima de 65.536 e suporte a tool use; esta aplicação impõe limite de saída menor. Links: [Groq limits](https://console.groq.com/docs/rate-limits), [GPT-OSS 20B](https://console.groq.com/docs/model/openai/gpt-oss-20b), [OpenAI compatibility](https://console.groq.com/docs/openai).
