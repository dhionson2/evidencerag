# Correção da integração Gemini

## Diagnóstico

Com `CHAT_PROVIDER=google-genai` e `EMBEDDING_PROVIDER=google-genai`, a configuração disponibilizava uma instância genérica Google GenAI SDK como bean Spring `Client` e a injetava nos detalhes de conexão de embeddings. Como a autoconfiguração do chat é condicional à ausência desse bean, esse client compartilhado interferia na criação dos clients independentes. A chave testada diretamente funcionava; o erro não era chave inválida.

Spring AI 2.1.0 configurava dimensões e `taskType` em opções, mas seu `GoogleGenAiTextEmbeddingModel` não encaminhava o task type à requisição SDK. O SDK suporta o campo e a integração agora o envia explicitamente.

## Mudanças

- Separação de client de embedding (gerenciado pelo holder) e client Chat oficial autoconfigurado.
- Adaptador compatível com o `EmbeddingModel` Spring AI, aplicando `RETRIEVAL_DOCUMENT` para documentos e `RETRIEVAL_QUERY` para consultas; mantém dimensão configurada e fecha recursos no shutdown.
- Metadado de embedding namespaced por provider/modelo na escrita e filtro de busca em todos os modos; fallback para metadados legados do mesmo modelo ativo preserva o corpus existente compatível.
- Diagnóstico de falha Gemini com campos limitados e redigidos; sem conteúdo de prompt/documento ou chave.
- Testes para payload/task type, seleção/configuração do modelo e filtro de isolamento; atualização dos documentos de arquitetura e validação.

## Validação

Ver [`docs/validation-report.md`](../../docs/validation-report.md): Maven verify (23 testes, 3 pulados); teste Gemini Spring AI com dado fictício; imagem Docker reconstruída; ingestão, dimensão 768, busca/citação e resposta sem evidência em PostgreSQL descartável; modo `demo`; checks Vitest, typecheck, lint e build.

## Impacto em dados existentes

Não há exclusão ou migração destrutiva. Metadados antigos sem namespace do modelo ativo continuam pesquisáveis. Vetores de outro provider/modelo ficam preservados e fora do filtro atual. Bancos/volumes existentes não foram tocados durante a validação.
