# EvidenceRAG — memória arquitetural

## Estado
Projeto novo, sem dependência de repositório externo. Documento inicial criado em 2026-11-24.

## Decisões
- Java 21, Spring Boot 4.2.0 e Spring AI 2.1.0 GA. A documentação oficial confirma que Spring AI 2.1.x requer Spring Boot 4.2.x e Java 17+; o ambiente de trabalho tem Java 21.
- Repositório não inclui Maven Wrapper. Na validação de 2026-11-24, Maven 3.9.11 foi extraído temporariamente fora do projeto; Java 21 disponível.
- API Spring MVC com camadas enxutas: `api` (HTTP/DTO), `application` (casos de uso), `domain` (tipos e regras) e `infrastructure` (Spring AI, PostgreSQL, arquivos e provedores).
- Spring AI `VectorStore` com `PgVectorStore` para embeddings, busca e metadados; Flyway prepara a extensão e tabela document-store antes da aplicação.
- PostgreSQL guarda também catálogo e relacionamento de IDs de vetores para exclusão/reindexação idempotente.
- Conteúdo PDF extraído por página com PDFBox; Markdown decodificado como UTF-8. Chunks persistem página, offset local da página, documento, nome e índice. Heading Markdown acompanha os parágrafos seguintes.
- Providers independentes: `CHAT_PROVIDER` e `EMBEDDING_PROVIDER` selecionam Ollama, OpenAI ou Google GenAI; `APP_EMBEDDING_MODEL` e `APP_EMBEDDING_DIMENSIONS` identificam o espaço vetorial. Google GenAI recebe timeout explícito de 45 s e retry desativado no client.
- Resposta usa JSON estruturado (`answer`, `evidenceIds`, `status`); IDs são resolvidos apenas entre os chunks recuperados. O backend monta nomes, scores e excertos.
- Documentos são entradas não confiáveis: o prompt proíbe seguir instruções embutidas, não há ferramentas de execução e textos integrais não vão para logs.
- A proteção local opcional usa `APP_API_TOKEN`; vazio permite desenvolvimento local sem autenticação. CORS fica limitado a `APP_CORS_ORIGIN`. Não confiar em `X-Forwarded-For`; limite em memória usa `remoteAddr`.
- Perfil `demo` remove `/api/documents`, fecha OpenAPI/Swagger, filtra `visibility=public-demo` e provider de embedding. Rate limit padrão demo: 8/minuto e uma geração concorrente; os contadores reiniciam com o processo.
- V1 deve permanecer imutável após aplicação. V2 fixa `vector(768)` e HNSW; V3 adiciona offsets. Validado num PostgreSQL 17.12 descartável, nunca no Neon remoto.
- O corpus público e avaliação de 25 cenários são fictícios em `docs/demo-corpus`, `eval` e `scripts`. Baseline lexical não é score de RAG real.
- Imagem/backend compilados, testes HTTP e smoke local com Ollama/pgvector foram executados em 2026-11-24; Testcontainers não descobre o Docker Desktop deste host, mas teste externo JDBC validou as migrações.

## Convenções
- API REST prefixada por `/api` e OpenAPI em `/swagger-ui.html`.
- Limite de upload padrão: 10 MiB; limite de extração: 2 Mi caracteres por documento.
- IDs UUID; SHA-256 do conteúdo controla reindexação idempotente.
- Toda evidência emitida carrega ID de documento, ID de chunk, página opcional, score opcional e excerto literal.
- Sem fallback de conhecimento geral em insuficiência de evidência.
