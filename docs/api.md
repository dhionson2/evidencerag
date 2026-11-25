# API REST

Base local: `http://localhost:8080`. Swagger UI: `/swagger-ui.html`; documento OpenAPI: `/v3/api-docs`. Quando `APP_API_TOKEN` não estiver vazio, envie `X-Api-Key` em endpoints `/api/**`. Erros públicos não retornam stack traces ou mensagens do provider.

No perfil `demo`, rotas `/api/documents` (upload/listagem/exclusão), Swagger e OpenAPI são removidos do servidor. Apenas `POST /api/query` permanece; busca filtra `visibility=public-demo` e o modelo de embedding configurado. CORS não bloqueia clientes HTTP diretos; o controle é aplicado no backend.

## `POST /api/documents`

`multipart/form-data`, campo `file`; aceita extensão `.pdf`, `.md` ou `.markdown`, com MIME compatível. Limite padrão 10 MiB. Resposta `201`:

```json
{"id":"uuid","filename":"procedimento.md","mediaType":"text/markdown","sha256":"...","chunkCount":2,"createdAt":"2026-11-24T18:00:00Z"}
```

O mesmo conteúdo já indexado retorna o registro existente sem duplicar chunks.

## `GET /api/documents`

Retorna o catálogo sem o conteúdo integral.

## `DELETE /api/documents/{id}`

Remove todos os vetores e metadados associados; resposta `204`. Um ID ausente retorna `404`.

## `POST /api/query`

```json
{"question":"Qual é o prazo da análise padrão?"}
```

Resposta com evidências:

```json
{
  "runId":"uuid",
  "status":"GROUNDED",
  "answer":"A análise padrão deve ser concluída em até dez dias úteis.",
  "citations":[{"documentId":"uuid","documentName":"procedimento.md","chunkId":"uuid","pageNumber":1,"similarity":0.83,"excerpt":"A análise padrão deve ser concluída em até dez dias úteis."}]
}
```

Sem correspondência acima do threshold, ou se a geração retornar status inválido/IDs fora da busca, status `INSUFFICIENT_EVIDENCE`, lista vazia e mensagem explícita. O modelo responde em JSON estruturado (`answer`, `evidenceIds`, `status`); somente IDs recuperados são validados e as citações/excertos são montados pelo backend.

## Erros

JSON `{ "code": "INVALID_INPUT", "message": "...", "requestId": "..." }`; status comuns: `400`, `401`, `404`, `413`, `429`, `502`, `503`, `504`. `X-Request-Id` é devolvido na resposta. O ID pode ser fornecido pelo cliente se corresponder a `[A-Za-z0-9._-]{1,80}`.
