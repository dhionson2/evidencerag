# Demonstração e conteúdo técnico

## Roteiro curto para vídeo (90–120 s)

1. Mostrar a tela de início e a nota de uso exclusivo de documentos fictícios.
2. Carregar `procedimento-administrativo-ficticio.md` e exibir sua contagem de fragmentos.
3. Perguntar “Qual é o prazo da análise padrão?”; destacar resposta fundamentada, nome do documento, página/trecho e UUID da evidência.
4. Perguntar “Qual é a taxa de juros do contrato?”; mostrar insuficiência e ausência de citações.
5. Mostrar o trecho fictício de prompt injection no arquivo: explicar que é dado documental, sem ferramenta de execução conectada.
6. Finalizar com Swagger, arquitetura, e o relatório do baseline lexical, esclarecendo que avaliação real de modelo exige configuração do banco/provedor.

Não mostre chaves ou documentos reais na captura. A primeira inicialização com Docker, download do modelo Ollama ou chamada OpenAI pode acrescentar tempo/custo e precisa ser preparada antes da gravação.

## Sugestão de publicação técnica

> Estou construindo o EvidenceRAG, uma demonstração open source de análise documental com Spring AI, PostgreSQL/pgvector e React. A ideia é tornar cada resposta inspecionável: a API devolve apenas referências ligadas a fragmentos recuperados e sinaliza quando não há evidência suficiente. Também separei uma suíte sintética offline para medir um baseline lexical e deixei claro o que ela não mede. O próximo passo é validar o pipeline com serviços reais e calibrar recuperação, threshold e comportamento de abstinência.
>
> O projeto usa apenas dados fictícios. As medições publicadas devem corresponder às execuções efetivamente feitas.
