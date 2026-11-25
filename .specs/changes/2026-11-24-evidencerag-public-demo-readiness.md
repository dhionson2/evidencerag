# Mudança: EvidenceRAG demo pública

## Escopo

Preparar localmente a demo read-only de portfólio: corpus fictício pré-indexável, consultas públicas limitadas, administração desativada no perfil `demo`, providers de chat/embeddings independentes, configuração incremental de PostgreSQL/Neon e Render revisável. Nenhum deploy ou recurso remoto foi criado.

## Fora do escopo

- Commit, push, merge, rebase, reset, limpeza destrutiva, serviço remoto ou deploy.
- Alteração de qualquer recurso Neon/Render externo.
- Dados reais, autenticação multiusuário, OCR obrigatório, microserviços, pagamento ou serviços adicionais.

## Estado e preservação

- Workspace: `D:\dev\EvidenceRAG-0.1.0`.
- A pasta não tem `.git`; `git status`, `git ls-files` e diff real não podem ser avaliados. Não foi inicializado repositório. Segredos já rastreados não puderam ser verificados sem metadados Git.
- `.env` local existe; valores não foram lidos, impressos nem copiados. `.gitignore` agora ignora `.env*`, exceto `.env.example`.
- V1 foi preservada; V2/V3 são incrementais. O banco/volume original do projeto não foi conectado nem alterado. Todas as migrações e o smoke foram executados em PostgreSQL 17.12 descartável com projeto/porta próprios.
- Nenhum Neon, Render, GitHub, Gemini real ou serviço remoto foi acessado/configurado. O CLI Render local não tem workspace selecionado; validação foi feita contra o JSON Schema oficial, sem sincronização.

## Arquitetura implementada

Browser estático → API Spring Boot (`demo`) → filtros de visibilidade/modelo → PgVector/Neon; Gemini Chat e Embedding separados por `CHAT_PROVIDER`/`EMBEDDING_PROVIDER`. Resposta estruturada (`answer`, `evidenceIds`, `status`) e IDs reconciliados contra a recuperação. Ingestão extrai/chunka/embeda antes da transação; catálogo, offsets e upsert dos vetores pré-computados são persistidos juntos.

## Checklist por fase

### Fase 1 — Segurança e privacidade: concluída localmente

- [x] `.gitignore` protege configuração/secrets/dumps locais; `.env` não foi examinado e rastreamento não é verificável sem `.git`.
- [x] `DocumentController` não existe no perfil `demo`; GET/POST/DELETE `/api/documents` confirmados com HTTP 404.
- [x] CORS exige origem única em `demo`; filtro não confia em `X-Forwarded-For`; rate limit 8/min e concorrência de geração 1 no perfil público.
- [x] Filtro de busca exige `visibility=public-demo` e modelo de embedding; consulta vazia responde sem LLM.
- [x] Swagger/OpenAPI fechados no perfil demo; provider errors sanitizados; SDK Gemini configurado para timeout 45 s e sem retry interno.
- [x] UI read-only, cold-start/429/timeout amigáveis e build sem padrões/nomes de secrets.
- [x] Testes de status 404, erros Gemini 429/timeout sanitizados, citações e filtros.
- [x] Documentos Gemini Free serão somente fictícios; política informa potencial uso de conteúdo para aprimorar produtos.

### Fase 2 — Gemini: implementação concluída, smoke real pendente por chave

- [x] Spring AI 2.1.0 estável; starters oficiais Google GenAI de chat/embeddings separados; Ollama/OpenAI mantidos.
- [x] Modelos Gemini configuráveis, dimensão 768 e metadados modelo/provider para isolamento.
- [x] Timeout configurado em 45 s no SDK Google; tratamento sanitizado para 429, falhas provider e timeout; testes unitários com erros sintéticos.
- [ ] Não há chave disponível de forma segura para smoke Gemini; disponibilidade de Free/cota deve ser confirmada no AI Studio antes de qualquer chamada. Nenhuma chamada real foi feita.

### Fase 3 — Neon/pgvector/Flyway: SQL validado localmente, remoto pendente

- [x] V1 preservada; V2 fixa `vector(768)` e cria HNSW; V3 adiciona offsets por página.
- [x] Flyway validou e aplicou V1–V3 em PostgreSQL 17.12 descartável; coluna `vector(768)`, índice HNSW e offsets foram verificados.
- [x] Pool Hikari 3/0, timeout 10 s; runtime pode usar Neon pooler/TLS e Flyway URL direta separada.
- [ ] Nenhuma migração foi executada no Neon. Antes de migração, revisar dimensão de dados existentes/backups e ativar manualmente.

### Fase 4 — Render/frontend: configuração pronta para revisão

- [x] `PORT`, `0.0.0.0`, Docker multi-stage e usuário não-root.
- [x] `render.yaml` declara API Free/Oregon + static site; `autoDeployTrigger: 'off'`; sem banco Render; secrets `sync:false`.
- [x] JSON Schema oficial do Render aceitou `render.yaml`; CLI local exigiu workspace e não foi autenticado.
- [x] URL Vite configurável, sem segredo no bundle, UI tolera cold start e status 429/timeouts.
- [x] Build Docker concluído; container demo respondeu health 200 sob limite de 512 MiB e medição pontual observou ~292 MiB.

### Fase 5 — RAG e avaliações: concluída com limites registrados

- [x] Saída estruturada e IDs reconciliados; modelo não fornece excerto/metadado ao cliente.
- [x] Chunking mantém headings, páginas e offsets; PDF escaneado dá instrução clara sem OCR.
- [x] Transação SQL removida de parsing e chamadas externas de embedding.
- [x] Avaliação lexical executou 25 casos: recall do trecho esperado @5 16/21 (76,2%), MRR@5 0,567. Não é avaliação semântica nem LLM.
- [x] Smoke Ollama local, usando apenas manual fictício e DB descartável: ingestão HTTP 201 (27 chunks), resposta `GROUNDED` com citação verdadeira; consulta sem evidência e consulta com prompt injection devolveram `INSUFFICIENT_EVIDENCE`.
- [ ] Avaliação real de Gemini e julgamento semântico humano continuam pendentes.

### Fase 6 — Validação final: em andamento

- [x] Maven: `test` passou; detalhe final no relatório. Testcontainers padrão pula por falha de descoberta Docker Desktop; caminho externo JDBC foi executado com sucesso no DB descartável.
- [x] Frontend: Vitest, typecheck, ESLint, build e `npm audit` sem vulnerabilidades.
- [x] `docker compose config --quiet` e validação JSON Schema Render passaram.
- [x] Imagem Docker backend compilada e iniciada sob limite 512 MiB; health 200 e rota administrativa 404.
- [x] Sem commit, push, deploy, migração remota ou modificação dos projetos externos.

## Evidências

- Teste de migração executado contra `localhost:55432/mission` num container efêmero `evidencerag-mission-validation-db`; não reutilizou o Compose/volume existente.
- Smoke HTTP foi executado em `localhost:18080` com Ollama e somente `docs/demo-corpus/manual-horizonte-azul.md`; nunca abriu o currículo do usuário.
- Avaliação lexical: [`scripts/evaluate_corpus.py`](../../scripts/evaluate_corpus.py) + [`eval/public-demo-cases.json`](../../eval/public-demo-cases.json).
- Relatório final consolidado em [`docs/validation-report.md`](../../docs/validation-report.md).
