# Mudança: EvidenceRAG

## Escopo
Aplicação full stack de demonstração para ingestão de PDF/Markdown, busca vetorial e respostas RAG com referências rastreáveis.

## Critérios de aceite
- [x] Upload valida extensão, MIME, tamanho, conteúdo e trata PDF por página.
- [x] Ingestão faz chunking com overlap configurável, metadados e reindexação idempotente.
- [x] API lista e remove documentos e vetores associados.
- [x] Pergunta usa embeddings, busca pgvector, limiar de evidência, geração condicionada e validação das referências.
- [x] Resposta sem evidência é explícita e não consulta o modelo.
- [x] UI permite upload, lista, pergunta, resposta e leitura de citações.
- [x] OpenAPI, Actuator/Micrometer, CORS e token local opcional estão configurados.
- [x] Suíte determinística tem 25 cenários; avaliação de modelo fica separada.
- [x] Docker, CI, documentos fictícios, licença, guias e roteiro LinkedIn estão presentes.
- [x] Builds/testes têm status baseado somente na execução observada.

## Estratégia de testes
- Unitários: chunking, extração/validação, política de evidência e checagem de citações com doubles.
- API: MockMvc com serviços mockados.
- Integração: Testcontainers PostgreSQL/pgvector isolado por classe, se Docker disponível.
- Frontend: Vitest/Testing Library para renderização e helpers; typecheck/build separados.
- Avaliação determinística: corpus sintético, sem LLM, scoring de recuperação e citações.
- E2E RAG real: manual/opcional, exige Docker, modelo e credencial local.

## Evidências de ambiente inicial (2026-11-24; antes da preparação da missão pública)
- `java -version`: OpenJDK 21.0.13.
- `node --version`: v24.22.3; npm 11.19.1.
- `mvn -version`: Maven ausente.
- `docker --version` / `docker compose version`: Docker ausente.
- Pesquisa em documentação oficial: Spring AI 2.1.0 GA em Maven Central (2026-11-20); Spring Boot 4.2.0 GA (2026-11-19); Spring AI 2.1.x suporta Spring Boot 4.2.x e Java 17+.

## Checklist de implementação
- [x] Compatibilidade inicial e ambiente registrados.
- [x] Memória arquitetural criada.
- [x] Backend, UI e infraestrutura implementados.
- [x] Validações executadas e resultados anexados; veja `docs/validation-report.md`.
- [x] Pacote ZIP criado e validado com `unzip -t`.


## Validação final (2026-11-24)
- Backend `mvn verify`: 18 testes descobertos, 16 passaram, 0 falharam e 2 integrações foram puladas; integração de migração foi executada separadamente por JDBC no PostgreSQL 17.12 temporário.
- Frontend: lint, typecheck, 3 testes, build demo e `npm audit` sem vulnerabilidades.
- Docker backend construído e iniciado sob limite 512 MiB; health 200, rota administrativa 404, cerca de 292 MiB observados durante a amostra.
- Ollama RAG local validado com corpus fictício: ingestão e resposta grounded com citação; casos sem evidência/injeção recusados.
- Avaliação lexical de 25 casos: detalhes e limites em `docs/validation-report.md`.
- Compose config e Render Blueprint schema validados sem iniciar a stack nem acessar recursos remotos.
- Gemini real, Neon e deploy Render não executados. A pasta não possui `.git`, portanto status e diff de Git não são disponíveis.
