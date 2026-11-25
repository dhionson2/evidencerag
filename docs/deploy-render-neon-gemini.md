# Preparação para Render + Neon + Gemini

Este guia prepara revisão futura; nenhum serviço ou migração remota foi criado/executado nesta missão.

## Antes de publicar

1. Publique o código no GitHub apenas depois da revisão humana e confirme que o repositório não contém `.env`, documentos reais, chaves ou artefatos locais. Este workspace não possui metadados `.git`, então a revisão de arquivos rastreados precisa acontecer no checkout Git de publicação.
2. Use somente o projeto Neon `evidencerag` já reservado para esta aplicação. Não conecte a aplicações externas. Confirme backups e verifique que a base contém somente corpus fictício aprovado.
3. Aplique Flyway V2 manualmente em janela revisada, com credenciais diretas de migração e conexão TLS. V2 converte todos os vetores para a dimensão escolhida (padrão 768) e cria HNSW; se a coluna já contiver vetores com outra dimensão, a conversão falhará. Não troque dimensão/modelo sem esvaziar/reindexar o corpus de forma planejada.
4. Crie um corpus fictício vazio e indexe `docs/demo-corpus/manual-horizonte-azul.md` usando o perfil local/admin, `APP_DOCUMENT_VISIBILITY=public-demo`, provider/modelo de embeddings iguais aos do backend público e dimensão idêntica. Nunca suba currículo ou documento real. O perfil público não possui upload/listagem/exclusão.

## Configuração Render Blueprint

`render.yaml` declara a API Docker `evidencerag-api` no plano Free/Oregon e o frontend estático `evidencerag-demo`, ambos com deploy automático desligado. A sincronização ou criação do Blueprint não foi realizada.

No painel da API configure os valores privados para os nomes:

- `GEMINI_API_KEY`
- `DATABASE_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD` para o endpoint JDBC **pooler** do Neon, com `sslmode=require` e o banco correto.
- `FLYWAY_URL`, `FLYWAY_USER`, `FLYWAY_PASSWORD` para endpoint JDBC **direto**, igualmente com TLS. Depois da migração inicial e revisão, avalie desligar `spring.flyway.enabled` para as próximas inicializações se não houver mudança de schema.
- `APP_CORS_ORIGIN` com a origem HTTPS exata gerada para o static site.

Os nomes dos modelos/provedores e parâmetros não secretos já estão no Blueprint. Confirme o acesso efetivo no AI Studio: preço Free publicado não garante quota/modelo disponível na conta. A tabela oficial indica que Gemini Embedding Free pode usar dados para aprimorar produtos; portanto, envie apenas documentos e perguntas fictícios.

## Operação e limitações

- Static build injeta `VITE_API_BASE_URL` e `VITE_DEMO_MODE`; se o nome/subdomínio do serviço mudar, ajuste a URL e faça rebuild do frontend. Nunca crie variável `VITE_*` para chave/token.
- Verifique a saúde da API em `/actuator/health`. Render Free pode suspender a API após 15 minutos sem tráfego; a primeira requisição seguinte pode demorar. A UI informa inicialização e usa limite de 90 segundos, sem retry automático.
- Há uma instância, oito chamadas API por minuto por endereço observado e uma geração de IA concorrente em memória. Reinícios zeram os contadores; se o proxy não propagar IP de forma confiável, visitantes podem compartilhar o mesmo bucket. Estes limites não previnem abuso distribuído nem substituem quotas do Gemini.
- Conteúdo RAG e perguntas enviados ao Gemini devem continuar fictícios. Não habilite dados pessoais. PDFs escaneados não têm OCR; envie PDF com texto selecionável ou Markdown.
- Revise métricas, logs sanitizados, cota Gemini e memória em produção antes de abrir a demo. Nunca cole valores secretos em issue, chat ou logs.
