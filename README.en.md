# EvidenceRAG

A document analysis copilot that answers from retrieved passages and returns source citations that can be checked. It is a full-stack demonstration of PDF/Markdown ingestion, chunking, embeddings, pgvector retrieval, evidence-conditioned generation, and citation validation.

## Verified status

- Frontend typecheck, three Vitest tests, and production build passed in this workspace on 2026-11-24.
- The offline evaluation has 25 synthetic cases and is a lexical baseline only; it does not invoke embeddings, vector search, or an LLM.
- Backend Maven tests and Docker/Testcontainers could not be run in the initial environment because Maven and Docker were unavailable. A real model end-to-end run also requires PostgreSQL/pgvector and a configured provider.

## Run locally

Copy `.env.example` to `.env`, configure OpenAI or Ollama, then run:

```bash
docker compose --env-file .env -f compose.yml up --build
```

Open `http://localhost:5173`; API docs are at `http://localhost:8080/swagger-ui.html`. See [README.md](README.md) for the Windows PowerShell guide, provider setup, API examples, security notes, and known limitations.

Spring AI 2.1.0 GA is paired with Spring Boot 4.2.x. See the [Spring AI 2.1 release announcement](https://spring.io/blog/2026/11/20/spring-ai-2-1-0-GA-available-now/) and official [system requirements](https://docs.spring.io/spring-boot/system-requirements.html).

The application uses synthetic documents only. It does not claim to eliminate hallucinations. Code and fictional demo data are MIT licensed; direct dependency license families and remaining verification are described in the Portuguese README.
