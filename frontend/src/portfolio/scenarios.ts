export type EvidenceReference = {
  label: string;
  detail: string;
  id?: string;
};

export type ScenarioStep = {
  title: string;
  detail: string;
  kind: 'input' | 'model' | 'tool' | 'database' | 'result';
};

export type PortfolioScenario = {
  id: string;
  eyebrow: string;
  title: string;
  question: string;
  execution: 'AI_TOOL_LOOP' | 'DETERMINISTIC_FALLBACK';
  status: 'GROUNDED';
  provider: string;
  model: string | null;
  providerOutcome: string;
  observedAt: string;
  latency: string;
  tokens: string;
  roundtrips: string;
  modelTools: string[];
  backendTools: string[];
  answer: string;
  answerNote: string;
  steps: ScenarioStep[];
  references: EvidenceReference[];
  provenance: string;
};

export const portfolioScenarios: PortfolioScenario[] = [
  {
    id: 'last-purchase',
    eyebrow: '01 · consulta financeira',
    title: 'A última compra',
    question: 'Qual foi minha última compra?',
    execution: 'AI_TOOL_LOOP',
    status: 'GROUNDED',
    provider: 'GroqCloud',
    model: 'openai/gpt-oss-20b',
    providerOutcome: 'HTTP 200 · resposta final do modelo',
    observedAt: '26/11/2026 · COS-21 comentário 10749',
    latency: '3.323 ms ponta a ponta (3.018 ms na telemetria)',
    tokens: '2.357 (2.192 prompt + 165 conclusão)',
    roundtrips: '2 roundtrips',
    modelTools: ['listDemoTransactions'],
    backendTools: ['listDemoTransactions'],
    answer: 'Compra identificada: R$ 4.800,00 em 01/11/2026.',
    answerNote: 'Síntese dos campos e da resposta registrados no resultado real; não é uma transcrição literal do texto do modelo.',
    steps: [
      { title: 'Pergunta recebida', detail: '“Qual foi minha última compra?” foi enviada na sessão fictícia Cliente.', kind: 'input' },
      { title: 'Decisão do modelo', detail: 'O GPT-OSS 20B selecionou a ferramenta tipada listDemoTransactions. A telemetria registrou dois roundtrips ao provedor.', kind: 'model' },
      { title: 'Execução Java', detail: 'Spring AI despachou listDemoTransactions no backend, usando o escopo da sessão. Não há chamada feita por esta página estática.', kind: 'tool' },
      { title: 'Consulta PostgreSQL', detail: 'O registro ACC-001 mais recente correspondente a compra é um débito de R$ 4.800,00 em 01/11/2026.', kind: 'database' },
      { title: 'Resposta e evidência', detail: 'O backend respondeu GROUNDED / AI_TOOL_LOOP e validou a referência da transação antes de devolver o resultado.', kind: 'result' },
    ],
    references: [
      { label: 'Compra eletrônica de demonstração', detail: 'ACC-001 · débito de R$ 4.800,00 · 01/11/2026', id: '47d88dae-0de0-faca-d415-7508c544c5df' },
    ],
    provenance: 'COS-21, comentário 10749; a transação foi confirmada por consulta somente leitura ao PostgreSQL sintético do sandbox.',
  },
  {
    id: 'largest-expenses',
    eyebrow: '02 · agregação determinística',
    title: 'Maiores despesas',
    question: 'Quais foram minhas maiores despesas?',
    execution: 'DETERMINISTIC_FALLBACK',
    status: 'GROUNDED',
    provider: 'Ollama (configurado na execução registrada)',
    model: null,
    providerOutcome: 'TRANSPORT_ERROR · nenhuma resposta do modelo',
    observedAt: '25/11/2026 · COS-21 comentário 10730',
    latency: 'aprox. 2,0–2,6 s',
    tokens: 'indisponíveis',
    roundtrips: '1 tentativa da aplicação · 0 tool calls escolhidas pelo modelo',
    modelTools: [],
    backendTools: ['summarizeDemoSpending'],
    answer: 'No período de 2026-10-22 a 2026-11-01, considerando as seis movimentações mais recentes do painel, as maiores despesas (débitos) foram: R$ 4.800,00 (2026-11-01), R$ 480,00 (2026-10-28), R$ 170,00 (2026-10-22), R$ 120,00 (2026-10-24) e R$ 85,00 (2026-10-26). O crédito de salário foi excluído do ranking.',
    answerNote: 'Resposta determinística observada e preservada no comentário 10730. A indisponibilidade do provedor não foi tratada como falta de evidência nem como resposta de IA.',
    steps: [
      { title: 'Pergunta recebida', detail: 'A consulta pede um ranking das despesas do período disponível no painel.', kind: 'input' },
      { title: 'Provedor indisponível', detail: 'A execução registrou TRANSPORT_ERROR no Ollama e zero chamadas de ferramenta selecionadas pelo modelo.', kind: 'model' },
      { title: 'Fallback Java explícito', detail: 'O backend executou summarizeDemoSpending. Esse cálculo pertence ao código Java e não é apresentado como decisão do modelo.', kind: 'tool' },
      { title: 'Filtro e ordenação', detail: 'O backend considerou as seis movimentações recentes do painel, manteve apenas DEBIT, somou/ordenou os valores e excluiu o crédito de salário.', kind: 'database' },
      { title: 'Resposta e evidências', detail: 'O resultado ficou GROUNDED / DETERMINISTIC_FALLBACK com cinco referências de transações verificadas.', kind: 'result' },
    ],
    references: [
      { label: 'Compra eletrônica de demonstração', detail: 'Débito de R$ 4.800,00 · 01/11/2026', id: '47d88dae-0de0-faca-d415-7508c544c5df' },
      { label: 'Mercado Modelo', detail: 'Débito de R$ 480,00 · 28/10/2026', id: '56d13de7-5718-8897-d121-67f16c9f5779' },
      { label: 'Restaurante Central', detail: 'Débito de R$ 170,00 · 22/10/2026', id: '2f469b16-1902-838e-6c9d-fff92fc95380' },
      { label: 'Farmácia Exemplo', detail: 'Débito de R$ 120,00 · 24/10/2026', id: '9be041c7-a8cd-c1b7-2be6-2ddaeffb596c' },
      { label: 'Transporte Demo', detail: 'Débito de R$ 85,00 · 26/10/2026', id: 'e5785dfc-d2e5-9ede-a91d-1bbef144a30e' },
    ],
    provenance: 'COS-21, comentário 10730; nomes, datas, valores e UUIDs foram conferidos contra as referências e o seed sintético do sandbox.',
  },
  {
    id: 'flagged-purchase',
    eyebrow: '03 · explicação de alerta',
    title: 'Compra sinalizada',
    question: 'Por que a compra de R$ 4.800 foi sinalizada?',
    execution: 'DETERMINISTIC_FALLBACK',
    status: 'GROUNDED',
    provider: 'Ollama (configurado na execução registrada)',
    model: null,
    providerOutcome: 'TRANSPORT_ERROR · nenhuma resposta do modelo',
    observedAt: '25/11/2026 · COS-21 comentário 10730',
    latency: 'aprox. 2,0–2,6 s',
    tokens: 'indisponíveis',
    roundtrips: '1 tentativa da aplicação · 0 tool calls escolhidas pelo modelo',
    modelTools: [],
    backendTools: ['getFraudAlertContext'],
    answer: 'O registro ALERT-4800 foi disparado pela regra HIGH_VALUE_VS_HISTORY: a compra de R$ 4.800,00 superou R$ 3.500,00 e foi descrita como mais de três vezes o maior débito individual observado nos 90 dias anteriores. O alerta pede revisão; não comprova fraude.',
    answerNote: 'Síntese dos fatos estruturados recuperados pelo fallback e do alerta sintético. A busca documental por política não foi executada nesta interação; portanto, este cenário não afirma ter citado uma política documental.',
    steps: [
      { title: 'Pergunta recebida', detail: 'A consulta pergunta pela razão registrada para sinalizar a compra fictícia de R$ 4.800,00.', kind: 'input' },
      { title: 'Provedor indisponível', detail: 'A execução registrada terminou com TRANSPORT_ERROR; o modelo não produziu decisão nem selecionou ferramentas.', kind: 'model' },
      { title: 'Fallback Java explícito', detail: 'O backend executou getFraudAlertContext para ALERT-4800 e reuniu o alerta, a transação relacionada e o histórico da conta.', kind: 'tool' },
      { title: 'Consulta dos dados', detail: 'O contexto retornou a regra HIGH_VALUE_VS_HISTORY, a compra e até cinco débitos anteriores dentro da janela de 90 dias.', kind: 'database' },
      { title: 'Resposta e limite', detail: 'O resultado ficou GROUNDED / DETERMINISTIC_FALLBACK. O alerta solicita revisão e não demonstra fraude. A ferramenta searchPolicyEvidence não foi chamada nessa execução.', kind: 'result' },
    ],
    references: [
      { label: 'Alerta ALT-001 · ALERT-4800', detail: 'Regra HIGH_VALUE_VS_HISTORY · registro sintético de alerta' },
      { label: 'Compra eletrônica de demonstração', detail: 'ACC-001 · débito de R$ 4.800,00 · 01/11/2026', id: '47d88dae-0de0-faca-d415-7508c544c5df' },
      { label: 'Débito histórico · Mercado Modelo', detail: 'R$ 480,00 · 28/10/2026', id: '56d13de7-5718-8897-d121-67f16c9f5779' },
      { label: 'Débito histórico · Transporte Demo', detail: 'R$ 85,00 · 26/10/2026', id: 'e5785dfc-d2e5-9ede-a91d-1bbef144a30e' },
      { label: 'Débito histórico · Farmácia Exemplo', detail: 'R$ 120,00 · 24/10/2026', id: '9be041c7-a8cd-c1b7-2be6-2ddaeffb596c' },
      { label: 'Débito histórico · Restaurante Central', detail: 'R$ 170,00 · 22/10/2026', id: '2f469b16-1902-838e-6c9d-fff92fc95380' },
      { label: 'Débito histórico · Conta de energia', detail: 'R$ 210,00 · 20/10/2026', id: '78b1a402-272b-f4c5-4de0-77d443c8b257' },
    ],
    provenance: 'COS-21, comentário 10730, que registra resposta determinística grounded, sete referências e a regra observada. Os IDs do seed foram conciliados em consulta somente leitura; o Jira não preserva o payload bruto da interação.',
  },
];
