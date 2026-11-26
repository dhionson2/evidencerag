import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { BankingDemo } from './BankingDemo';
import {
  askBankingAssistant, BankingDemoError, changeBankingDemoMode, getBankingAssistantHistory,
  getBankingDemo, startBankingDemo, type ConversationHistory,
} from './lib/bankingDemoApi';

vi.mock('./lib/bankingDemoApi', () => ({
  BankingDemoError: class extends Error { constructor(readonly status: number, message: string) { super(message); } },
  getBankingDemo: vi.fn(), startBankingDemo: vi.fn(), restartBankingDemo: vi.fn(), changeBankingDemoMode: vi.fn(),
  askBankingAssistant: vi.fn(), getBankingAssistantHistory: vi.fn(),
}));

const scenario = {
  session: { createdAt: new Date().toISOString(), expiresAt: new Date(Date.now() + 4 * 60 * 60 * 1000).toISOString(), selectedMode: 'CLIENT' as const },
  customers: [], accounts: [{ id: 'ACC-001', customerId: 'CUST-001', customerName: 'Cliente Exemplo A', accountType: 'Conta corrente', currency: 'BRL', openingBalance: 5600, currentBalance: 5654 }],
  transactions: [], alerts: [], proposals: [], policies: [],
};
const assistantHistory = (experience: 'CLIENT' | 'ANALYST', question: string, body: string, answerMode: 'AI_TOOL_LOOP' | 'DETERMINISTIC_FALLBACK' = 'AI_TOOL_LOOP', providerIssue: string | null = null, evidence: ConversationHistory['messages'][number]['evidence'] = []): ConversationHistory => ({
  experience,
  messages: [
    { id: 'q1', role: 'USER', body: question, outcome: 'USER', answerMode: null, providerIssue: null, evidence: [], createdAt: new Date().toISOString() },
    { id: 'a1', role: 'ASSISTANT', body, outcome: 'GROUNDED', answerMode, providerIssue, evidence, createdAt: new Date().toISOString() },
  ],
});

beforeEach(() => {
  vi.unstubAllEnvs();
  vi.stubEnv('VITE_BANKING_ANALYST_ENABLED', 'false');
  vi.mocked(getBankingDemo).mockRejectedValue(new BankingDemoError(404, 'Demo não iniciada.'));
  vi.mocked(startBankingDemo).mockResolvedValue(scenario);
  vi.mocked(getBankingAssistantHistory).mockResolvedValue({ experience: 'CLIENT', messages: [] });
});
afterEach(() => { cleanup(); vi.clearAllMocks(); vi.unstubAllEnvs(); });

describe('BankingDemo', () => {
  it('shows only the Cliente experience when the public MVP flag is disabled', async () => {
    render(<BankingDemo />);
    expect(await screen.findByRole('button', { name: /Explorar como Cliente/ })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Explorar como Analista/ })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Explorar como Cliente/ }));
    expect(await screen.findByRole('heading', { name: /Olá, Cliente Exemplo A/ })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Funcionário / Analista' })).not.toBeInTheDocument();
    expect(screen.getByLabelText('Experiência Cliente')).toBeInTheDocument();
  });

  it('keeps analyst UI code available behind the explicit feature flag', async () => {
    vi.stubEnv('VITE_BANKING_ANALYST_ENABLED', 'true');
    render(<BankingDemo />);
    expect(await screen.findByRole('button', { name: /Explorar como Analista/ })).toBeInTheDocument();
  });

  it('starts anonymously and shows one assistant without a manual module selector', async () => {
    render(<BankingDemo />);
    fireEvent.click(await screen.findByRole('button', { name: /Explorar como Cliente/ }));
    expect(await screen.findByText(/Olá, Cliente Exemplo A/)).toBeInTheDocument();
    expect(screen.getByText(/5\.654,00/)).toBeInTheDocument();
    expect(screen.getByText(/Ambiente fictício — nenhuma operação financeira real/)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Como posso ajudar?' })).toBeInTheDocument();
    expect(screen.queryByRole('combobox', { name: /Módulo do assistente/i })).not.toBeInTheDocument();
  });

  it('submits natural language without a module and renders grounded, human-readable sources', async () => {
    vi.mocked(askBankingAssistant).mockResolvedValue({ status: 'GROUNDED', answer: 'O saldo fictício é R$ 5.654,00.', message: null,
      evidence: [{ id: 'ref-1', kind: 'account', recordId: 'ACC-001', label: 'Conta corrente', excerpt: 'Saldo atual 5654 BRL' }] });
    vi.mocked(getBankingAssistantHistory).mockResolvedValue(assistantHistory('CLIENT', 'Quanto tenho na conta?', 'O saldo fictício é R$ 5.654,00.', 'AI_TOOL_LOOP', null,
      [{ id: 'ref-1', kind: 'account', recordId: 'ACC-001', label: 'Conta corrente', excerpt: 'Saldo atual 5654 BRL' }]));
    const { container } = render(<BankingDemo />);
    fireEvent.click(await screen.findByRole('button', { name: /Explorar como Cliente/ }));
    fireEvent.change(await screen.findByLabelText(/Pergunte ao assistente/i), { target: { value: 'Quanto tenho na conta?' } });
    fireEvent.click(screen.getByRole('button', { name: 'Enviar pergunta' }));
    expect(await screen.findByText('Resposta elaborada pelo assistente com dados atuais e fontes validadas.')).toBeInTheDocument();
    expect(screen.getByText('Conta corrente')).toBeInTheDocument();
    fireEvent.click(container.querySelector('.bank-demo-evidence-card summary')!);
    expect(screen.getByText('ACC-001')).toBeInTheDocument();
    expect(askBankingAssistant).toHaveBeenCalledWith('Quanto tenho na conta?');
  });

  it('labels deterministic sandbox consultation without exposing provider exception codes', async () => {
    vi.mocked(askBankingAssistant).mockResolvedValue({ status: 'GROUNDED', answer: 'As maiores despesas foram R$ 4.800,00 e R$ 480,00.',
      answerMode: 'DETERMINISTIC_FALLBACK', providerIssue: 'TRANSPORT_ERROR', message: null,
      evidence: [{ id: 'ref-top-1', kind: 'transaction', recordId: 'tx-top-1', label: 'Compra de demonstração', excerpt: 'Débito de R$ 4.800,00' }] });
    vi.mocked(getBankingAssistantHistory).mockResolvedValue(assistantHistory('CLIENT', 'Quais foram minhas maiores despesas?', 'As maiores despesas foram R$ 4.800,00 e R$ 480,00.', 'DETERMINISTIC_FALLBACK', 'TRANSPORT_ERROR',
      [{ id: 'ref-top-1', kind: 'transaction', recordId: 'tx-top-1', label: 'Compra de demonstração', excerpt: 'Débito de R$ 4.800,00' }]));
    render(<BankingDemo />);
    fireEvent.click(await screen.findByRole('button', { name: /Explorar como Cliente/ }));
    fireEvent.change(await screen.findByLabelText(/Pergunte ao assistente/i), { target: { value: 'Quais foram minhas maiores despesas?' } });
    fireEvent.click(screen.getByRole('button', { name: 'Enviar pergunta' }));
    expect(await screen.findByText('Consulta aos dados')).toBeInTheDocument();
    expect(screen.getByText(/A conexão com o serviço de IA falhou/)).toBeInTheDocument();
    expect(screen.queryByText(/TRANSPORT_ERROR/)).not.toBeInTheDocument();
    expect(screen.getByText(/R\$ 4\.800,00 e R\$ 480,00/)).toBeInTheDocument();
  });

  it('names a provider quota limit when deterministic data remains available', async () => {
    vi.mocked(askBankingAssistant).mockResolvedValue({ status: 'GROUNDED', answer: 'O saldo fictício é R$ 5.654,00.',
      answerMode: 'DETERMINISTIC_FALLBACK', providerIssue: 'HTTP_429', message: null,
      evidence: [{ id: 'ref-account', kind: 'account', recordId: 'ACC-001', label: 'Conta corrente', excerpt: 'Saldo atual 5654 BRL' }] });
    vi.mocked(getBankingAssistantHistory).mockResolvedValue(assistantHistory('CLIENT', 'Quanto tenho na conta?', 'O saldo fictício é R$ 5.654,00.', 'DETERMINISTIC_FALLBACK', 'HTTP_429',
      [{ id: 'ref-account', kind: 'account', recordId: 'ACC-001', label: 'Conta corrente', excerpt: 'Saldo atual 5654 BRL' }]));
    render(<BankingDemo />);
    fireEvent.click(await screen.findByRole('button', { name: /Explorar como Cliente/ }));
    fireEvent.change(await screen.findByLabelText(/Pergunte ao assistente/i), { target: { value: 'Quanto tenho na conta?' } });
    fireEvent.click(screen.getByRole('button', { name: 'Enviar pergunta' }));
    expect(await screen.findByText('Consulta aos dados')).toBeInTheDocument();
    expect(screen.getByText(/A cota do serviço de IA foi atingida/)).toBeInTheDocument();
    expect(screen.getByText(/O saldo fictício é R\$ 5\.654,00/)).toBeInTheDocument();
    expect(screen.queryByText(/HTTP_429/)).not.toBeInTheDocument();
  });

  it('labels missing annual coverage as insufficient data rather than a precise estimate', async () => {
    const answer = 'Não há evidência suficiente para calcular ou projetar um gasto anual: o histórico fictício disponível cobre menos de doze meses.';
    vi.mocked(askBankingAssistant).mockResolvedValue({ status: 'INSUFFICIENT_EVIDENCE', answer,
      answerMode: 'DETERMINISTIC_FALLBACK', providerIssue: null, message: null, evidence: [] });
    vi.mocked(getBankingAssistantHistory).mockResolvedValue({ experience: 'CLIENT', messages: [{
      id: 'annual-insufficient', role: 'ASSISTANT', body: answer, outcome: 'INSUFFICIENT_EVIDENCE',
      answerMode: 'DETERMINISTIC_FALLBACK', providerIssue: null, evidence: [], createdAt: new Date().toISOString(),
    }] });
    render(<BankingDemo />);
    fireEvent.click(await screen.findByRole('button', { name: /Explorar como Cliente/ }));
    fireEvent.change(await screen.findByLabelText(/Pergunte ao assistente/i), { target: { value: 'Qual meu gasto anual estimado?' } });
    fireEvent.click(screen.getByRole('button', { name: 'Enviar pergunta' }));
    expect(await screen.findByText('Dados insuficientes')).toBeInTheDocument();
    expect(screen.getByText(/menos de doze meses/)).toBeInTheDocument();
    expect(screen.queryByText(/R\$ 206\.408/)).not.toBeInTheDocument();
  });

  it('explains when a local token admission limit prevented the provider call', async () => {
    vi.mocked(askBankingAssistant).mockResolvedValue({ status: 'GROUNDED', answer: 'CR-102 tem 4/5 documentos. ' +
      'A comparação não aprova nem rejeita crédito e requer revisão humana.', answerMode: 'DETERMINISTIC_FALLBACK',
      providerIssue: 'LOCAL_TOKEN_BUDGET', message: null,
      evidence: [{ id: 'ref-proposal', kind: 'proposal', recordId: 'CR-102', label: 'Proposta CR-102', excerpt: '4/5 documentos' }] });
    vi.mocked(getBankingAssistantHistory).mockResolvedValue(assistantHistory('CLIENT', 'A proposta CR-102 atende à política?',
      'CR-102 tem 4/5 documentos. A comparação não aprova nem rejeita crédito e requer revisão humana.',
      'DETERMINISTIC_FALLBACK', 'LOCAL_TOKEN_BUDGET',
      [{ id: 'ref-proposal', kind: 'proposal', recordId: 'CR-102', label: 'Proposta CR-102', excerpt: '4/5 documentos' }]));
    render(<BankingDemo />);
    fireEvent.click(await screen.findByRole('button', { name: /Explorar como Cliente/ }));
    expect(await screen.findByText(/O limite local de tokens do modelo foi atingido na janela de 60 segundos/)).toBeInTheDocument();
    expect(screen.queryByText(/A geração de texto por IA não respondeu/)).not.toBeInTheDocument();
  });

  it('explains a Groq tool-call generation rejection separately from transport failures', async () => {
    vi.stubEnv('VITE_BANKING_ANALYST_ENABLED', 'true');
    vi.mocked(changeBankingDemoMode).mockResolvedValue({ ...scenario,
      session: { ...scenario.session, selectedMode: 'ANALYST' } });
    vi.mocked(askBankingAssistant).mockResolvedValue({ status: 'GROUNDED', answer: 'CR-102 requer revisão humana.',
      answerMode: 'DETERMINISTIC_FALLBACK', providerIssue: 'TOOL_CALL_GENERATION_FAILED', message: null,
      evidence: [{ id: 'ref-policy', kind: 'policy', recordId: 'POL-CRED@2.1#4.2', label: 'Política de crédito', excerpt: 'Documentos e prazo' }] });
    vi.mocked(getBankingAssistantHistory).mockResolvedValue(assistantHistory('ANALYST', 'A proposta CR-102 atende à política?',
      'CR-102 requer revisão humana.', 'DETERMINISTIC_FALLBACK', 'TOOL_CALL_GENERATION_FAILED',
      [{ id: 'ref-policy', kind: 'policy', recordId: 'POL-CRED@2.1#4.2', label: 'Política de crédito', excerpt: 'Documentos e prazo' }]));
    render(<BankingDemo />);
    fireEvent.click(await screen.findByRole('button', { name: /Explorar como Cliente/ }));
    fireEvent.click(await screen.findByRole('button', { name: 'Funcionário / Analista' }));
    fireEvent.change(await screen.findByLabelText(/Pergunte ao assistente/i), { target: { value: 'A proposta CR-102 atende à política?' } });
    fireEvent.click(screen.getByRole('button', { name: 'Enviar pergunta' }));
    expect(await screen.findByText(/O provedor não conseguiu gerar uma chamada de ferramenta válida/)).toBeInTheDocument();
    expect(screen.queryByText(/A geração de texto por IA não respondeu/)).not.toBeInTheDocument();
  });

  it('keeps analyst history scoped to the mode and submits an integrated case without selecting an agent', async () => {
    vi.stubEnv('VITE_BANKING_ANALYST_ENABLED', 'true');
    vi.mocked(changeBankingDemoMode).mockResolvedValue({ ...scenario, session: { ...scenario.session, selectedMode: 'ANALYST' } });
    vi.mocked(askBankingAssistant).mockResolvedValue({ status: 'GROUNDED', answer: 'Parecer preliminar com fontes sintéticas.', message: null,
      evidence: [{ id: 'policy-1', kind: 'policy', recordId: 'POL-CRED@2.1#4.2', label: 'Política fictícia de crédito v2.1', excerpt: 'Documentação e prazo.' }] });
    vi.mocked(getBankingAssistantHistory).mockResolvedValue(assistantHistory('ANALYST', 'Analise a proposta CR-102 e seus alertas relacionados.', 'Parecer preliminar com fontes sintéticas.', 'AI_TOOL_LOOP', null,
      [{ id: 'policy-1', kind: 'policy', recordId: 'POL-CRED@2.1#4.2', label: 'Política fictícia de crédito v2.1', excerpt: 'Documentação e prazo.' }]));
    render(<BankingDemo />);
    fireEvent.click(await screen.findByRole('button', { name: /Explorar como Cliente/ }));
    fireEvent.click(await screen.findByRole('button', { name: 'Funcionário / Analista' }));
    fireEvent.change(await screen.findByLabelText(/Pergunte ao assistente/i), { target: { value: 'Analise a proposta CR-102 e seus alertas relacionados.' } });
    fireEvent.click(screen.getByRole('button', { name: 'Enviar pergunta' }));
    expect(await screen.findByText('Política fictícia de crédito v2.1')).toBeInTheDocument();
    expect(screen.queryByRole('combobox', { name: /Módulo do assistente/i })).not.toBeInTheDocument();
    expect(askBankingAssistant).toHaveBeenCalledWith('Analise a proposta CR-102 e seus alertas relacionados.');
  });
});
