# Relatório de validação — integração Gemini — 2026-11-24

## Causa confirmada e correções

- O Spring AI efetivo é 2.1.0. Os seletores configurados estavam corretos: `spring.ai.model.chat` para Chat e `spring.ai.model.embedding.text` para embeddings. As dimensões 768 eram encaminhadas pelas opções do modelo.
- A causa da falha no uso simultâneo de Gemini Chat e Embedding era o client genérico do SDK registrado como bean compartilhado. Isso fazia a autoconfiguração oficial do Chat recuar e reutilizava a mesma instância para os dois fluxos. A configuração agora mantém clients independentes e deixa o Chat autoconfigurado pelo Spring AI.
- A versão 2.1.0 do modelo de embedding Spring AI lia `taskType`, mas não o aplicava à chamada SDK. O adaptador agora envia `RETRIEVAL_DOCUMENT` nos embeddings dos documentos e `RETRIEVAL_QUERY` nas consultas. O holder fecha seu client no encerramento da aplicação.
- O aviso de configuração antecipada desapareceu após a remoção do bean compartilhado e o ajuste para `@Configuration(proxyBeanMethods=false)`. A inicialização Docker validada não registrou esse aviso nem exceções de configuração.
- A ingestão usa identidade namespaced `provedor:modelo`, como `google-genai:gemini-embedding-001`. A busca também aceita metadado legado sem namespace do modelo ativo, preservando documentos compatíveis já indexados; vetores identificados como outro modelo continuam armazenados e ficam fora da busca.
- O tratamento de falhas acrescenta diagnóstico sanitizado no servidor (request ID, status/categoria Google, tipo de exceção e detalhe limitado/redigido) sem retornar prompts, conteúdo ou credenciais ao cliente.

## Evidências executadas

| Verificação | Resultado | Evidência / limite |
|---|---|---|
| Backend Maven | Passou | `mvn -f backend/pom.xml verify`: 24 testes, 0 falhas, 0 erros, 3 pulados por condições de ambiente/integração. Inclui compilação e empacotamento. |
| Configuração de embedding Gemini | Passou | `GeminiEmbeddingConfigurationTest`: payload HTTP mock confirma `RETRIEVAL_DOCUMENT`, `RETRIEVAL_QUERY` e dimensão 768. O teste de API externa ficou pulado nesta execução da suíte. |
| Teste Gemini real do Spring AI | Passou | Teste separado com Chat Gemini e embedding em lote, incluindo caminho query; entrada fictícia; resposta e vetores de 768 dimensões. A chave foi usada em memória e não registrada. |
| Docker backend | Passou | Imagem `evidencerag-mission:gemini-final` reconstruída. Container de validação no perfil `demo` iniciou saudável; logs sem aviso de configuração ou exceção de inicialização. |
| Stack local existente | Passou | Backend `evidencerag-demo` reconstruído a partir do projeto, reiniciado em `localhost:18080` e health `UP`. Foi necessário passar `API_HOST_PORT=18080` porque `8080` já está ocupado pela outra stack local. Nenhuma consulta ou upload foi feito no banco original. |
| Ingestão Gemini + PostgreSQL | Passou | Banco descartável isolado, upload do manual fictício HTTP 201, 27 chunks; consulta SQL confirmou 27 vetores, todos dimensão 768, com identidade `google-genai:gemini-embedding-001`. |
| RAG Gemini | Passou | Pergunta sobre prazo de atendimento respondeu `GROUNDED` com citação/excerto correspondente. Pergunta sobre CPF/salário ausente respondeu `INSUFFICIENT_EVIDENCE`, sem citações. |
| Perfil `demo` | Passou | Após preparar no banco de teste o corpus fictício como `public-demo`, a pergunta apoiada retornou `GROUNDED` com citação; a pergunta sem evidência retornou `INSUFFICIENT_EVIDENCE`; `/api/documents` retornou 404. |
| Frontend | Passou | 3 testes Vitest; `npm run typecheck`, `npm run lint` e `npm run build`. A consulta bem-sucedida via API remove a condição que acionava a mensagem genérica; a validação foi feita no contrato/backend, não por automação visual da interface. |
| Compose | Passou | `docker compose -p evidencerag-demo -f compose.yml config --quiet`. |

## Segurança e limites

- O RAG foi exercitado em containers e banco descartáveis separados; esses containers temporários foram removidos ao final. A API local `evidencerag-demo` foi reconstruída e reiniciada, mas seu banco/volume e o banco/volume da stack `evidencerag-010` não foram alterados. Nenhuma consulta/upload atingiu o currículo local. Nenhuma migração remota, deploy ou operação de publicação foi feita.
- Somente o arquivo sintético [`manual-horizonte-azul.md`](demo-corpus/manual-horizonte-azul.md) foi enviado ao provedor durante os testes; nenhum currículo ou documento pessoal foi transmitido.
- A suite completa pulou 3 testes dependentes de ambiente. O teste Testcontainers não pôde se conectar ao daemon Docker neste processo; o Docker Compose e os containers manuais funcionaram, e o fluxo real foi validado manualmente no container isolado.
- Consultas com Ollama/OpenAI usam a mesma identidade namespaced e fallback compatível com metadados legados do modelo ativo. Ao mudar provider/modelo, reenvie/reindexe os documentos que deseja consultar naquele espaço; a proteção não apaga automaticamente dados antigos.
- Limites de cota, latência e disponibilidade do Gemini seguem dependentes da conta e do modelo. O teste externo comprova apenas o funcionamento observado com o texto fictício e no momento da execução.
- Na execução original desta validação, a pasta ainda não tinha metadados `.git`; o repositório foi inicializado localmente para a publicação inicial em 2026-11-24.

## Reproduzir localmente

Rode `mvn -f backend/pom.xml verify` na raiz e, em `frontend`, `npm test`, `npm run typecheck`, `npm run lint` e `npm run build`. Para validar apenas o Compose sem iniciar serviços: `docker compose config --quiet`.

Para atualizar somente o backend já executado localmente, depois de revisar `.env`, use `docker compose -p evidencerag-demo -f compose.yml --env-file .env up -d --build --no-deps backend`. Configure `CHAT_PROVIDER=google-genai`, `EMBEDDING_PROVIDER=google-genai`, `GEMINI_API_KEY`, `GEMINI_CHAT_MODEL`, `GEMINI_EMBEDDING_MODEL` e `APP_EMBEDDING_DIMENSIONS=768`. Não é necessário ajustar `APP_EMBEDDING_MODEL`: a identidade agora é derivada do provider/modelo ativo.
