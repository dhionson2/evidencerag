import { useEffect, useMemo, useState } from 'react';
import { ArrowLeftRight, Banknote, Clock3, RefreshCw, ShieldAlert, ShieldCheck, Sparkles, Send, ChevronDown, Landmark, MessagesSquare } from 'lucide-react';
import {
  askBankingAssistant, BankingDemoError, changeBankingDemoMode, getBankingAssistantHistory,
  getBankingDemo, restartBankingDemo, startBankingDemo,
  type AssistantEvidence, type BankingDemo as DemoData,
  type ConversationMessage, type DemoMode,
} from './lib/bankingDemoApi';

const money = (amount: number) => new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(amount);
const date = (value: string) => new Date(value).toLocaleDateString('pt-BR');
const suggestions: Record<DemoMode, string[]> = {
  CLIENT: ['Quanto tenho na conta?', 'Quais foram minhas maiores despesas?', 'Quanto gastei em alimentação?', 'Por que a compra de R$ 4.800 foi sinalizada?'],
  ANALYST: ['A proposta CR-102 atende à política?', 'Quais documentos estão faltando?', 'Que regra se aplica à compra sinalizada?', 'Analise a proposta CR-102 e seus alertas relacionados.'],
};

export function BankingDemo() {
  const analystEnabled = import.meta.env.VITE_BANKING_ANALYST_ENABLED === 'true';
  const [demo, setDemo] = useState<DemoData | null>(null);
  const [screen, setScreen] = useState<'loading' | 'entry' | 'active' | 'expired' | 'error'>('loading');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [question, setQuestion] = useState('');
  const [assistantBusy, setAssistantBusy] = useState(false);
  const [assistantError, setAssistantError] = useState('');
  const [conversations, setConversations] = useState<Record<DemoMode, ConversationMessage[]>>({ CLIENT: [], ANALYST: [] });
  const [now, setNow] = useState(Date.now());

  const loadHistory = async (mode: DemoMode) => {
    try {
      const history = await getBankingAssistantHistory();
      setConversations(previous => ({ ...previous, [mode]: history.experience === mode ? history.messages : [] }));
    } catch {
      setConversations(previous => ({ ...previous, [mode]: [] }));
    }
  };

  useEffect(() => {
    let mounted = true;
    getBankingDemo().then(async data => {
      if (!mounted) return;
      setDemo(data);
      setScreen('active');
      await loadHistory(data.session.selectedMode);
    }).catch((reason: unknown) => {
      if (!mounted) return;
      if (reason instanceof BankingDemoError && reason.status === 404) setScreen('entry');
      else if (reason instanceof BankingDemoError && reason.status === 410) setScreen('expired');
      else { setError((reason as Error).message); setScreen('error'); }
    });
    return () => { mounted = false; };
  }, []);

  useEffect(() => {
    if (screen !== 'active') return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [screen]);

  const mode = analystEnabled ? demo?.session.selectedMode ?? 'CLIENT' : 'CLIENT';
  const remaining = demo ? Math.max(0, Math.floor((Date.parse(demo.session.expiresAt) - now) / 1000)) : 0;
  const expired = screen === 'expired' || (screen === 'active' && remaining <= 0);
  const selectedAccount = useMemo(() => demo?.accounts.find(account => account.customerId === 'CUST-001'), [demo]);
  const recentTransactions = useMemo(() => demo?.transactions.filter(tx => tx.accountId === selectedAccount?.id).slice(0, 6) ?? [], [demo, selectedAccount]);
  const clockLabel = `${String(Math.floor(remaining / 3600)).padStart(2, '0')}:${String(Math.floor((remaining % 3600) / 60)).padStart(2, '0')}:${String(remaining % 60).padStart(2, '0')}`;

  const launch = async (newMode: DemoMode, restart = false) => {
    const requestedMode: DemoMode = analystEnabled ? newMode : 'CLIENT';
    setBusy(true); setError(''); setAssistantError('');
    try {
      const data = restart ? await restartBankingDemo(requestedMode) : await startBankingDemo(requestedMode);
      setDemo(data); setNow(Date.now()); setScreen('active'); setQuestion('');
      setConversations({ CLIENT: [], ANALYST: [] });
      await loadHistory(requestedMode);
    } catch (reason) {
      setError(userError(reason)); setScreen(reason instanceof BankingDemoError && reason.status === 410 ? 'expired' : 'error');
    } finally { setBusy(false); }
  };

  const switchMode = async (newMode: DemoMode) => {
    if (!analystEnabled && newMode === 'ANALYST') return;
    if (newMode === mode || busy || assistantBusy) return;
    setBusy(true); setError(''); setAssistantError(''); setQuestion('');
    try {
      const data = await changeBankingDemoMode(newMode);
      setDemo(data);
      await loadHistory(newMode);
    } catch (reason) {
      setAssistantError(userError(reason));
      if (reason instanceof BankingDemoError && reason.status === 410) setScreen('expired');
    } finally { setBusy(false); }
  };

  const submitQuestion = async () => {
    const prompt = question.trim();
    if (!prompt || assistantBusy) return;
    setAssistantBusy(true); setAssistantError('');
    try {
      await askBankingAssistant(prompt);
      setQuestion('');
      await loadHistory(mode);
    } catch (reason) {
      setAssistantError(userError(reason));
      await loadHistory(mode);
    } finally { setAssistantBusy(false); }
  };

  return <div className="bank-demo-shell">
    <header className="bank-demo-topbar">
      <a className="brand" href="#demo"><span className="brand-mark"><Banknote size={19}/></span><span>Evidence<span>RAG</span> <small>Banking AI</small></span></a>
      <span className="bank-demo-badge"><span/> AMBIENTE 100% FICTÍCIO</span>
    </header>
    <main id="demo" className="bank-demo-main">
      <div className="bank-demo-alert"><ShieldCheck size={17}/><span><strong>Ambiente fictício — nenhuma operação financeira real.</strong> Não informe dados pessoais ou credenciais.</span></div>
      {screen === 'loading' && <section className="bank-demo-card bank-demo-state">Carregando a demonstração…</section>}
      {screen === 'entry' && <section className="bank-demo-entry bank-demo-card">
        <div className="bank-demo-kicker"><Sparkles size={15}/> {analystEnabled ? 'UMA DEMONSTRAÇÃO, DOIS PONTOS DE VISTA' : 'ASSISTENTE FINANCEIRO · CLIENTE'}</div>
        <h1>Converse com seu<br/><em>assistente bancário.</em></h1>
        <p>Faça perguntas naturais sobre saldo, despesas, compras, movimentações e alertas fictícios. O assistente consulta dados do sandbox e mostra as fontes.</p>
        <div className={`bank-demo-choice-grid ${analystEnabled ? 'has-analyst' : 'client-only'}`}>
          <button disabled={busy} onClick={() => void launch('CLIENT')}><Banknote/><strong>Explorar como Cliente</strong><span>Saldo, movimentações e compra sinalizada</span></button>
          {analystEnabled && <button disabled={busy} onClick={() => void launch('ANALYST')}><ShieldAlert/><strong>Explorar como Analista</strong><span>Propostas, alertas e políticas versionadas</span></button>}
        </div>
        {error && <p className="bank-demo-error" role="alert">{error}</p>}
      </section>}
      {expired && <section className="bank-demo-card bank-demo-state"><Clock3 size={28}/><h1>Esta demonstração expirou</h1><p>A sessão tem validade fixa de quatro horas. O histórico anterior não pode mais ser acessado.</p><button className="bank-demo-primary" disabled={busy} onClick={() => void launch(mode, true)}><RefreshCw size={16}/> Iniciar nova demonstração</button></section>}
      {screen === 'error' && !expired && <section className="bank-demo-card bank-demo-state"><p className="bank-demo-error" role="alert">{error}</p><button className="bank-demo-primary" onClick={() => { setScreen('entry'); setError(''); }}>Voltar ao início</button></section>}
      {screen === 'active' && demo && !expired && <>
        <div className="bank-demo-heading"><div><span className="bank-demo-kicker">BANCO DEMO · DADOS SINTÉTICOS</span><h1>{mode === 'CLIENT' ? 'Olá, Cliente Exemplo A' : 'Painel do Analista Demo'}</h1><p>{mode === 'CLIENT' ? 'Converse sobre seu cenário fictício.' : 'Investigue propostas, alertas e políticas no mesmo assistente.'}</p></div><div className="bank-demo-session"><Clock3 size={15}/><span>Sessão restante <strong>{clockLabel}</strong></span></div></div>
        <nav className="bank-demo-switch" aria-label={analystEnabled ? 'Trocar experiência' : 'Experiência Cliente'}>
          {analystEnabled
            ? <><button className={mode === 'CLIENT' ? 'selected' : ''} disabled={busy || assistantBusy} onClick={() => void switchMode('CLIENT')}>Cliente</button><button className={mode === 'ANALYST' ? 'selected' : ''} disabled={busy || assistantBusy} onClick={() => void switchMode('ANALYST')}>Funcionário / Analista</button></>
            : <span className="bank-demo-mode-label" aria-current="page">Cliente</span>}
          <button className="bank-demo-restart" disabled={busy || assistantBusy} onClick={() => void launch(mode, true)}><RefreshCw size={14}/> Reiniciar demo</button>
        </nav>
        <div className="bank-demo-workspace">
          <AssistantPanel mode={mode} messages={conversations[mode]} question={question} setQuestion={setQuestion} busy={assistantBusy} error={assistantError} onAsk={() => void submitQuestion()} onSuggest={setQuestion}/>
          <aside className="bank-demo-context" aria-label={mode === 'CLIENT' ? 'Dados da conta fictícia' : 'Casos e políticas fictícios'}>
            {mode === 'CLIENT' && demo && <ClientContext demo={demo} account={selectedAccount} transactions={recentTransactions}/>}
            {mode === 'ANALYST' && demo && <AnalystContext demo={demo}/>}
          </aside>
        </div>
      </>}
      <footer className="bank-demo-footer"><span>EvidenceRAG Banking AI · sessão anônima isolada</span><span>Dados e operações exclusivamente sintéticos</span></footer>
    </main>
  </div>;
}

function AssistantPanel({ mode, messages, question, setQuestion, busy, error, onAsk, onSuggest }: {
  mode: DemoMode; messages: ConversationMessage[]; question: string; setQuestion: (value: string) => void;
  busy: boolean; error: string; onAsk: () => void; onSuggest: (value: string) => void;
}) {
  const prompts = suggestions[mode];
  return <section className="bank-demo-assistant" aria-label="Assistente bancário">
    <header className="bank-demo-assistant-head"><div><span className="bank-demo-kicker"><Sparkles size={15}/> ASSISTENTE BANCÁRIO</span><h2>Como posso ajudar?</h2><p>Faça uma pergunta. Eu consulto os dados e mostro de onde veio a resposta.</p></div><span className="assistant-context-pill">{mode === 'CLIENT' ? 'Experiência Cliente' : 'Experiência Analista'}</span></header>
    {messages.length === 0 ? <section className="bank-demo-welcome"><MessagesSquare size={21}/><div><strong>Uma conversa para investigar seus dados</strong><p>Escolha uma sugestão ou escreva com suas palavras. As fontes são validadas pelo backend.</p></div></section> : <div className="bank-demo-conversation" role="log" aria-label="Histórico da conversa" aria-live="polite">
      {messages.map(message => message.role === 'USER'
        ? <div className="chat-message user-message" key={message.id}><span>Você</span><p>{message.body}</p></div>
        : <AssistantMessageView message={message} key={message.id}/>)}
    </div>}
    {busy && <div className="bank-demo-thinking" role="status"><span className="thinking-dots"/>Consultando ferramentas e preparando uma resposta com fontes…</div>}
    {error && <p className="bank-demo-request-error" role="alert">{error}</p>}
    <div className="bank-demo-suggestions"><span className="suggestion-label">{mode === 'CLIENT' ? 'Experimente perguntar' : 'Perguntas para começar'}</span><div>{prompts.map(text => <button key={text} type="button" disabled={busy} onClick={() => onSuggest(text)}>{text}</button>)}</div></div>
    <form className="bank-demo-chat-form" onSubmit={event => { event.preventDefault(); onAsk(); }}>
      <label className="sr-only" htmlFor="bank-demo-question">Pergunte ao assistente</label>
      <textarea id="bank-demo-question" value={question} maxLength={1500} onChange={event => setQuestion(event.target.value)} onKeyDown={event => { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); onAsk(); } }} placeholder="Pergunte sobre saldo, gastos, alertas, propostas ou políticas…" />
      <div className="chat-form-footer"><span>Somente dados sintéticos · Enter envia · Shift+Enter nova linha</span><button className="bank-demo-primary" disabled={busy || !question.trim()} aria-label="Enviar pergunta">{busy ? 'Consultando…' : <><span>Enviar</span><Send size={15}/></>}</button></div>
    </form>
  </section>;
}

function AssistantMessageView({ message }: { message: ConversationMessage }) {
  const insufficient = message.outcome === 'INSUFFICIENT_EVIDENCE';
  const failed = message.outcome === 'PROVIDER_ERROR';
  return <article className={`chat-message assistant-message ${insufficient ? 'insufficient' : ''} ${failed ? 'failed' : ''}`}>
    <div className="assistant-message-meta"><span>Assistente</span><span className={`answer-origin ${message.answerMode === 'DETERMINISTIC_FALLBACK' ? 'data-origin' : ''}`}>
      {failed ? 'Serviço indisponível' : insufficient ? 'Dados insuficientes' : message.answerMode === 'DETERMINISTIC_FALLBACK' ? 'Consulta aos dados' : 'IA · fontes verificadas'}
    </span></div>
    {message.providerIssue && message.answerMode === 'DETERMINISTIC_FALLBACK' && <p className="provider-note">{fallbackProviderNote(message.providerIssue)}</p>}
    {message.providerIssue && failed && <p className="provider-note">{providerFailureNote(message.providerIssue)}</p>}
    {message.outcome === 'GROUNDED' && message.answerMode === 'AI_TOOL_LOOP' && <p className="answer-trust-note">Resposta elaborada pelo assistente com dados atuais e fontes validadas.</p>}
    <p className="assistant-answer-text">{message.body}</p>
    {message.evidence.length > 0 && <div className="answer-evidence"><strong>Fontes desta resposta ({message.evidence.length})</strong>{message.evidence.map(item => <EvidenceCard evidence={item} key={item.id}/>)}</div>}
  </article>;
}

function EvidenceCard({ evidence }: { evidence: AssistantEvidence }) {
  const type = ({ transaction: 'Transação', account: 'Conta', alert: 'Alerta', proposal: 'Proposta', policy: 'Política', 'simulated-review-note': 'Nota simulada' } as Record<string, string>)[evidence.kind] ?? 'Fonte';
  return <details className="bank-demo-evidence-card"><summary><span className="evidence-kind">{type}</span><strong>{evidence.label}</strong><ChevronDown size={15}/></summary><p>{evidence.excerpt}</p><small>Identificador da fonte <code>{evidence.recordId}</code></small></details>;
}

function ClientContext({ demo, account, transactions }: { demo: DemoData; account: DemoData['accounts'][number] | undefined; transactions: DemoData['transactions'] }) {
  const alert = demo.alerts.find(item => item.code === 'ALERT-4800');
  const policy = demo.policies.find(item => item.id === 'POL-ALERT');
  return <>
    <section className="context-card balance-context"><span><Landmark size={15}/> Saldo da conta</span><strong>{money(account?.currentBalance ?? 0)}</strong><small>Conta corrente · {account?.id ?? '—'}</small></section>
    <section className="context-card"><div className="context-heading"><span className="bank-demo-kicker">MOVIMENTAÇÕES</span><strong>{transactions.length} recentes</strong></div><div className="context-transaction-list">{transactions.map(tx => <div className="context-transaction" key={tx.id}><span className="context-transaction-icon"><ArrowLeftRight size={14}/></span><span><strong>{tx.description}</strong><small>{tx.category} · {date(tx.occurredAt)}</small></span><b className={tx.direction === 'CREDIT' ? 'credit' : ''}>{tx.direction === 'CREDIT' ? '+' : '−'}{money(tx.amount)}</b></div>)}</div></section>
    <section className="context-card context-alert"><span className="bank-demo-kicker"><ShieldAlert size={13}/> ALERTA PARA REVISÃO</span><h3>Compra de R$ 4.800 sinalizada</h3><p>{alert?.summary ?? 'Regra fictícia de revisão.'}</p><small>Política {policy?.version ?? '—'} · o alerta não comprova fraude</small></section>
  </>;
}

function AnalystContext({ demo }: { demo: DemoData }) {
  return <>
    <div className="analyst-context-stats"><section><span>Alertas</span><strong>{demo.alerts.length}</strong></section><section><span>Propostas</span><strong>{demo.proposals.length}</strong></section><section><span>Políticas</span><strong>{demo.policies.length}</strong></section></div>
    <section className="context-card"><div className="context-heading"><span className="bank-demo-kicker">CASOS DE CRÉDITO</span><strong>Revisão humana</strong></div>{demo.proposals.map(proposal => <article className="context-case" key={proposal.id}><div><strong>{proposal.id} · {proposal.companyName}</strong><small>{money(proposal.requestedAmount)} · {proposal.receivedDocumentCount}/{proposal.requiredDocumentCount} documentos</small><p>{proposal.missingDocument ?? 'Documentação completa'}{proposal.proposedTermMonths !== proposal.contractTermMonths ? ` · ${proposal.proposedTermMonths} meses solicitados / ${proposal.contractTermMonths} no contrato` : ''}</p></div><span className="bank-demo-review">{proposal.missingDocument || proposal.proposedTermMonths !== proposal.contractTermMonths ? 'PENDENTE' : 'COMPLETA'}</span></article>)}</section>
    <section className="context-card"><div className="context-heading"><span className="bank-demo-kicker">ALERTAS</span><strong>Dados para análise</strong></div>{demo.alerts.map(alert => <article className="context-alert-row" key={alert.id}><strong>{alert.code}{alert.relatedProposalId ? ` · ${alert.relatedProposalId}` : ''}</strong><p>{alert.summary}</p></article>)}</section>
    <section className="context-card"><div className="context-heading"><span className="bank-demo-kicker">POLÍTICAS</span><strong>Corpus fictício versionado</strong></div>{demo.policies.map(policy => <article className="context-policy" key={policy.id}><strong>{policy.title}</strong><small>v{policy.version} · §{policy.section} · vigente desde {date(`${policy.effectiveOn}T12:00:00`)}</small></article>)}</section>
  </>;
}

function userError(reason: unknown) {
  if (!(reason instanceof BankingDemoError)) return 'Não foi possível concluir a solicitação. Tente novamente.';
  if (reason.status === 429 && reason.code === 'AI_PROVIDER_QUOTA_EXCEEDED') return 'A cota do serviço de IA foi atingida. Tente novamente após a renovação da cota.';
  if (reason.status === 429 && reason.code === 'REQUEST_LIMIT_EXCEEDED') return 'O limite de consultas desta sessão foi atingido. Aguarde antes de tentar novamente.';
  if (reason.status === 429) return 'O limite de consultas foi atingido. Aguarde antes de tentar novamente.';
  if ([502, 503, 504].includes(reason.status)) return 'O assistente está indisponível no momento. Nenhuma resposta de IA foi apresentada como fato.';
  if (reason.status === 410) return 'Esta sessão expirou. Inicie uma nova demonstração.';
  return reason.message;
}

function fallbackProviderNote(issue: string) {
  const problem = issue === 'HTTP_429' ? 'A cota do serviço de IA foi atingida.'
    : issue === 'TIMEOUT' ? 'O serviço de IA excedeu o tempo limite configurado.'
      : issue === 'TRANSPORT_ERROR' ? 'A conexão com o serviço de IA falhou.'
        : issue === 'LOCAL_TOKEN_BUDGET' ? 'O limite local de tokens do modelo foi atingido na janela de 60 segundos. Aguarde a renovação antes de tentar novamente.'
          : issue === 'TOOL_CALL_GENERATION_FAILED' ? 'O provedor não conseguiu gerar uma chamada de ferramenta válida.'
        : 'A geração de texto por IA não respondeu.';
  return `${problem} Esta informação foi consultada e calculada no sandbox.`;
}

function providerFailureNote(issue: string) {
  if (issue === 'HTTP_429') return 'A cota do serviço de IA foi atingida. Nenhuma resposta factual foi apresentada; tente novamente após a renovação da cota.';
  if (issue === 'TIMEOUT') return 'O serviço de IA excedeu o tempo limite. Nenhuma resposta factual foi apresentada.';
  if (issue === 'TRANSPORT_ERROR') return 'A conexão com o serviço de IA falhou. Nenhuma resposta factual foi apresentada.';
  return 'A consulta não foi apresentada como resposta factual. Tente novamente quando o serviço estiver disponível.';
}
