# EvidenceRAG — Interactive AI Engineering Demo

## Propósito e modo público

A edição de portfólio demonstra engenharia de IA com um frontend estático React/TypeScript. A pessoa visitante escolhe um de três cenários registrados e avança manualmente por pergunta, execução, dados, resposta e evidências. A reprodução não envia requisições, não usa sessão/cookie e não chama Groq, Gemini, Ollama ou outro modelo.

Aviso visível na interface: **“Execução previamente registrada de um sistema real com dados sintéticos. Nenhuma chamada ao modelo é realizada nesta demonstração.”** O avanço é controlado pelo usuário; não há animação de digitação nem alegação de processamento em tempo real.

## Proveniência dos cenários

| Cenário | Execução comprovada | Ferramentas Java | Limites conhecidos |
|---|---|---|---|
| Qual foi minha última compra? | `GROUNDED / AI_TOOL_LOOP`, GroqCloud `openai/gpt-oss-20b`, 2 roundtrips, 2.357 tokens, 3.323 ms ponta a ponta | O modelo selecionou `listDemoTransactions`; o backend consultou PostgreSQL e validou a referência da compra de R$ 4.800,00 em 01/11/2026 | Resumo dos campos registrados no comentário 10749; não é transcrição literal do texto gerado |
| Quais foram minhas maiores despesas? | `GROUNDED / DETERMINISTIC_FALLBACK`, `TRANSPORT_ERROR` do Ollama, aproximadamente 2,0–2,6 s; tokens indisponíveis | `summarizeDemoSpending` executada pelo fallback Java; nenhuma ferramenta escolhida pelo modelo | Seis movimentações recentes do painel; só débitos; crédito do salário excluído; resposta e cinco referências preservadas no comentário 10730 |
| Por que a compra de R$ 4.800 foi sinalizada? | `GROUNDED / DETERMINISTIC_FALLBACK`, `TRANSPORT_ERROR` do Ollama, aproximadamente 2,0–2,6 s; tokens indisponíveis | `getFraudAlertContext` executada pelo fallback Java; nenhuma ferramenta escolhida pelo modelo | A execução registrada cita sete evidências. Não chamou `searchPolicyEvidence`; a interface explicita que não houve citação documental de política e não conclui fraude |

Fonte: COS-21, comentários 10730 e 10749. O UUID da compra foi confirmado no PostgreSQL sintético. Os demais IDs do cenário 3 foram reconciliados com o seed local e o código da ferramenta; a issue não preserva o payload bruto desse turno, limitação declarada na interface. Não há chaves, cookies, IDs de sessão, prompts completos ou logs brutos nas fixtures.

## Arquitetura real e separação de retrieval

O assistente local percorre React → Spring Boot → Spring AI → tool calling Java → PostgreSQL e valida as referências no backend antes de responder. O LLM não executa SQL. As respostas financeiras estruturadas e os cálculos determinísticos são computados no servidor; uma indisponibilidade do provedor não é evidência insuficiente e não deve ser apresentada como resposta gerada pelo modelo.

O EvidenceRAG original mantém recuperação documental vetorial com embeddings e pgvector. No catálogo bancário, a busca de políticas utiliza PostgreSQL full-text; **não existe índice vetorial dedicado às políticas bancárias**. A interface estática só reproduz os registros e não executa nenhum dos caminhos de retrieval.

Tecnologias presentes no repositório: Java 21, Spring Boot, Spring AI, React, TypeScript, Vite, PostgreSQL/pgvector e Flyway. As validações automatizadas disponíveis incluem JUnit, Testcontainers, Vitest, ESLint, typecheck e builds; consulte o relatório desta tarefa para saber quais foram executadas e quais ficaram pendentes.

## Modos e execução local

- `npm run build`: build estático padrão em `frontend/dist`, base `/`.
- `npm run build:pages`: build estático para a rota `/evidencerag/` de GitHub Pages.
- `npm run dev`: Banking AI conectado ao backend local, com proxy `/api`.
- `npm run build:live`: build Banking AI local conectado.
- `npm run dev:rag`: interface RAG original.

O `Dockerfile.frontend` usa a build estática padrão. O sandbox local pode servir essa build na URL já configurada, sem que ela precise do backend. A API e o PostgreSQL podem continuar ativos para desenvolvimento e testes independentes.

## Critérios de aceite desta edição

- [x] Interface única com três cenários e sem seleção Cliente/Analista.
- [x] Fixtures baseadas em execuções documentadas, com origem, tipo de execução e ferramentas verdadeiramente observadas.
- [x] Cenários 2 e 3 identificam explicitamente fallback determinístico e erro de transporte; cenário 3 declara que a busca documental de política não ocorreu.
- [x] Ferramentas, respostas, dados sintéticos e referências são legíveis; IDs técnicos ficam em detalhes e os registros disponíveis foram conciliados com o seed.
- [x] Controles nativos por botão, regiões semânticas, foco visível, atalho para pular conteúdo, respeito a `prefers-reduced-motion` e breakpoints mobile implementados.
- [x] Bundle público compilado sem rotas `/api/`, marcadores de credenciais ou padrões de chaves; os testes Vitest também confirmam zero chamadas a `fetch` ao navegar os cenários.
- [x] Vitest 16/16, ESLint, typecheck e builds estáticos `portfolio` e `pages` passaram. `mvn verify`: 80 testes, zero falhas/erros, 14 skips separados abaixo.
- [x] Preview local atualizado e respondendo em `http://localhost:15175/`; frontend, backend e PostgreSQL healthy. O backend e o container/volume PostgreSQL permaneceram inalterados.
- [ ] Inspeção visual desktop/mobile: pendente de revisão manual; a automação visual não foi usada porque não confirmou que a URL era permitida.
- [x] Nenhuma publicação foi feita. O gate E3 original continua bloqueado e separado.

## Validação local desta preparação (26/11/2026)

- Frontend: Vitest 16 testes passaram; ESLint e `tsc -b --pretty` passaram. `npm run build` e `npm run build:pages` passaram com 1.585 módulos transformados; bundle JavaScript 245.370 bytes e CSS 49.734 bytes. Na variante Pages, `index.html` usa `/evidencerag/assets/`.
- O bundle do portfólio foi escaneado sem imprimir conteúdo: nenhum caminho `/api/`, marcador `GROQ_API_KEY` ou padrão de chave detectado. O teste de componente seleciona e percorre os cenários sem chamar `fetch`.
- `mvn -o -B -ntp verify` executado em container Maven 3.9.9/Java 21: `BUILD SUCCESS`, exit code 0, 80 testes, 0 falhas, 0 erros, 14 skips. Causas: 12 testes Testcontainers sem socket Docker (`/var/run/docker.sock`) montado no container Maven; um teste requer `TEST_DATABASE_URL`; um smoke combinado requer `GEMINI_API_KEY`. Os skips não foram considerados aprovados.
- O build de imagem Docker do frontend não pôde consultar Docker Hub porque o Docker Desktop falhou na resolução DNS de `registry-1.docker.io`. Para manter o preview local atualizado sem tocar em backend/banco, o container frontend existente usa a build `frontend/dist` em bind mount somente leitura sobre a imagem Nginx local já disponível. A URL e os assets retornaram HTTP 200; os três containers ficaram healthy. O container backend e o ID/mount `evidencerag-groq-test_evidencerag-data` permaneceram os mesmos.
- Não houve smoke novo do provedor: os três cenários usam somente as execuções previamente documentadas; abrir e avançar a demo não gera chamadas ao modelo.
- Nenhum deploy, commit, push, merge ou PR foi realizado; Neon, Render e volumes legados não foram alterados.

## Configuração de publicação no Render (não executada)

Criar um recurso **Static Site**, não Web Service nem Docker, com:

| Campo | Valor |
|---|---|
| Repositório | `dhionson2/evidencerag` |
| Branch | `evo/portfolio-static-demo` |
| Root Directory | `frontend` |
| Build Command | `npm run build` |
| Publish Directory | `dist` |
| Environment Variables | Nenhuma |

O build padrão `portfolio` define `base: '/'`, adequado ao domínio raiz do Static Site. **Não use `npm run build:pages` no Render**; esse script usa a base `/evidencerag/` exclusiva para GitHub Pages. O site não requer `GROQ_API_KEY`, `GEMINI_API_KEY`, PostgreSQL, backend, cookies ou serviços pagos.

Antes de publicar, revisar o bundle final e o repositório para dados sensíveis. O deploy continua pendente de autorização humana explícita; esta preparação não cria nem altera qualquer serviço Render.
