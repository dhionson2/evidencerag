export type DemoMode = 'CLIENT' | 'ANALYST';
/** Retained only for legacy payload decoding; the UI no longer selects an assistant module. */
export type BankingModule = 'FINANCE' | 'FRAUD' | 'CREDIT' | 'COMPLIANCE' | 'CASE_REVIEW';
export type AssistantEvidence = { id: string; kind: string; recordId: string; label: string; excerpt: string };
export type AssistantReview = { structuredFacts: string[]; documentaryEvidence: AssistantEvidence[]; aiInterpretation: string; missingInformation: string[]; recommendations: string[] };
export type AssistantAnswer = { status: 'GROUNDED' | 'INSUFFICIENT_EVIDENCE'; answer: string; evidence: AssistantEvidence[]; message: string | null; review?: AssistantReview | null; answerMode?: 'AI_TOOL_LOOP' | 'DETERMINISTIC_FALLBACK'; providerIssue?: string | null };
export type ConversationMessage = { id: string; role: 'USER' | 'ASSISTANT'; body: string; outcome: string | null; answerMode: 'AI_TOOL_LOOP' | 'DETERMINISTIC_FALLBACK' | null; providerIssue: string | null; evidence: AssistantEvidence[]; createdAt: string };
export type ConversationHistory = { experience: DemoMode; messages: ConversationMessage[] };
export type BankingDemo = {
  session: { createdAt: string; expiresAt: string; selectedMode: DemoMode };
  customers: { id: string; displayName: string; scenarioCode: string }[];
  accounts: { id: string; customerId: string; customerName: string; accountType: string; currency: string; openingBalance: number; currentBalance: number }[];
  transactions: { id: string; accountId: string; occurredAt: string; description: string; category: string; amount: number; direction: 'CREDIT' | 'DEBIT'; alertId: string | null }[];
  alerts: { id: string; code: string; transactionId: string; relatedProposalId: string | null; ruleCode: string; summary: string }[];
  proposals: { id: string; companyName: string; requestedAmount: number; proposedTermMonths: number; contractTermMonths: number; requiredDocumentCount: number; receivedDocumentCount: number; missingDocument: string | null; policyId: string }[];
  policies: { id: string; version: string; title: string; section: string; effectiveOn: string; ruleText: string }[];
};

export class BankingDemoError extends Error {
  constructor(readonly status: number, message: string, readonly code?: string) { super(message); }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, { ...init, credentials: 'same-origin', headers: { 'Content-Type': 'application/json', ...init?.headers } });
  if (!response.ok) {
    const body = await response.json().catch(() => null) as { code?: string; message?: string } | null;
    throw new BankingDemoError(response.status, body?.message ?? `Falha na demonstração (${response.status}).`, body?.code);
  }
  return response.json() as Promise<T>;
}

export const getBankingDemo = () => request<BankingDemo>('/api/banking/demo');
export const startBankingDemo = (mode: DemoMode) => request<BankingDemo>('/api/banking/session', { method: 'POST', body: JSON.stringify({ mode }) });
export const restartBankingDemo = (mode: DemoMode) => request<BankingDemo>('/api/banking/session/restart', { method: 'POST', body: JSON.stringify({ mode }) });
export const changeBankingDemoMode = (mode: DemoMode) => request<BankingDemo>('/api/banking/mode', { method: 'PUT', body: JSON.stringify({ mode }) });
export const askBankingAssistant = (question: string) => request<AssistantAnswer>('/api/banking/assistant', {
  method: 'POST', body: JSON.stringify({ question }),
});
export const getBankingAssistantHistory = () => request<ConversationHistory>('/api/banking/assistant/history');
