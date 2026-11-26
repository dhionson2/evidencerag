import { useState } from 'react';
import { ArrowDown, ArrowLeft, ArrowRight, Check, ChevronDown, Database, Github, Layers3, ShieldCheck, Sparkles, Workflow } from 'lucide-react';
import { portfolioScenarios, type PortfolioScenario } from './scenarios';

function ScenarioCard({ scenario, selected, onSelect }: { scenario: PortfolioScenario; selected: boolean; onSelect: () => void }) {
  return (
    <button className={`portfolio-scenario${selected ? ' is-selected' : ''}`} type="button" aria-pressed={selected} onClick={onSelect}>
      <span className="portfolio-scenario-top"><span>{scenario.eyebrow}</span><span className={`portfolio-mode-badge ${scenario.execution === 'AI_TOOL_LOOP' ? 'mode-ai' : 'mode-deterministic'}`}>{scenario.execution === 'AI_TOOL_LOOP' ? 'IA real registrada' : 'Determinístico'}</span></span>
      <strong>{scenario.title}</strong>
      <span className="portfolio-scenario-question">{scenario.question}</span>
      <span className="portfolio-scenario-link">Explorar cenário <ArrowRight size={15} aria-hidden="true"/></span>
    </button>
  );
}

export function EvidenceRagPortfolio() {
  const [scenarioId, setScenarioId] = useState(portfolioScenarios[0].id);
  const [stepIndex, setStepIndex] = useState(0);
  const scenario = portfolioScenarios.find((item) => item.id === scenarioId) ?? portfolioScenarios[0];
  const step = scenario.steps[stepIndex];
  const chooseScenario = (id: string) => { setScenarioId(id); setStepIndex(0); };
  const advance = (direction: -1 | 1) => setStepIndex((current) => Math.max(0, Math.min(scenario.steps.length - 1, current + direction)));

  return (
    <div className="portfolio-shell">
      <a className="portfolio-skip-link" href="#portfolio-main">Pular para o conteúdo</a>
      <header className="portfolio-header">
        <a className="portfolio-wordmark" href="#top" aria-label="EvidenceRAG — início"><span className="portfolio-mark"><Layers3 size={19}/></span><span>Evidence<span>RAG</span></span></a>
        <nav className="portfolio-nav" aria-label="Navegação principal"><a href="#scenarios">Demonstrações</a><a href="#architecture">Arquitetura</a><a href="#engineering">Engenharia</a></nav>
        <a className="portfolio-github" href="https://github.com/dhionson2/evidencerag" target="_blank" rel="noreferrer"><Github size={17}/> Código no GitHub <span aria-hidden="true">↗</span></a>
      </header>

      <main id="portfolio-main">
        <section id="top" className="portfolio-hero">
          <div className="portfolio-hero-copy">
            <div className="portfolio-overline"><span className="portfolio-live-dot"/> DEMONSTRAÇÃO DE ENGENHARIA DE IA</div>
            <h1>IA que responde.<br/><em>E mostra de onde veio.</em></h1>
            <p>Assistente financeiro com IA, ferramentas Java e respostas baseadas em evidências. Esta demonstração reproduz execuções reais registradas com dados fictícios.</p>
            <a className="portfolio-primary-link" href="#scenarios">Explorar os cenários <ArrowDown size={17}/></a>
          </div>
          <aside className="portfolio-hero-note" aria-label="Como funciona esta demonstração">
            <span className="portfolio-note-icon"><ShieldCheck size={20}/></span>
            <span className="portfolio-note-kicker">REPRODUÇÃO SEM PROVEDOR</span>
            <strong>Execução previamente registrada de um sistema real com dados sintéticos.</strong>
            <p>Nenhuma chamada ao modelo é realizada nesta demonstração.</p>
          </aside>
        </section>

        <section id="scenarios" className="portfolio-section portfolio-demo-section" aria-labelledby="scenarios-title">
          <div className="portfolio-section-heading"><div><span className="portfolio-overline">EXPERIÊNCIAS GUIADAS</span><h2 id="scenarios-title">Siga a evidência.</h2></div><p>Escolha um caso e avance pelas etapas registradas. A navegação é manual; não há digitação simulada nem chamadas em tempo real.</p></div>
          <div className="portfolio-demo-grid">
            <nav className="portfolio-scenario-list" aria-label="Cenários de demonstração">
              {portfolioScenarios.map((item) => <ScenarioCard key={item.id} scenario={item} selected={item.id === scenario.id} onSelect={() => chooseScenario(item.id)}/>)}
            </nav>
            <article className="portfolio-run-card" aria-labelledby="run-title">
              <div className="portfolio-run-head"><div><span className="portfolio-overline">{scenario.eyebrow}</span><h3 id="run-title">{scenario.title}</h3></div><span className={`portfolio-mode-badge ${scenario.execution === 'AI_TOOL_LOOP' ? 'mode-ai' : 'mode-deterministic'}`}>{scenario.execution}</span></div>
              <div className="portfolio-question"><span className="portfolio-question-label">PERGUNTA ORIGINAL</span><p>{scenario.question}</p></div>
              <ol className="portfolio-step-list" aria-label="Etapas da execução registrada">
                {scenario.steps.map((item, index) => <li key={item.title} className={index <= stepIndex ? 'step-revealed' : ''} aria-current={index === stepIndex ? 'step' : undefined}><span className="portfolio-step-marker">{index < stepIndex ? <Check size={13}/> : String(index + 1).padStart(2, '0')}</span><span>{item.title}</span></li>)}
              </ol>
              <section className="portfolio-current-step" aria-live="polite" aria-atomic="true">
                <div className={`portfolio-step-icon step-${step.kind}`} aria-hidden="true">{step.kind === 'database' ? <Database size={18}/> : step.kind === 'result' ? <ShieldCheck size={18}/> : step.kind === 'model' ? <Sparkles size={18}/> : step.kind === 'tool' ? <Workflow size={18}/> : <span>Q</span>}</div>
                <div><span className="portfolio-step-caption">ETAPA {stepIndex + 1} DE {scenario.steps.length}</span><h4>{step.title}</h4><p>{step.detail}</p></div>
              </section>
              <div className="portfolio-step-controls">
                <button type="button" className="portfolio-control secondary" onClick={() => advance(-1)} disabled={stepIndex === 0}><ArrowLeft size={15}/> Anterior</button>
                {stepIndex < scenario.steps.length - 1 ? <button type="button" className="portfolio-control primary" onClick={() => advance(1)}>Próxima etapa <ArrowRight size={15}/></button> : <span className="portfolio-complete"><Check size={15}/> Execução registrada</span>}
              </div>
              {stepIndex === scenario.steps.length - 1 && <div className="portfolio-result-area">
                <div className="portfolio-answer"><div className="portfolio-answer-heading"><span><Check size={15}/></span><strong>Resposta fundamentada · {scenario.status} / {scenario.execution}</strong></div><p>{scenario.answer}</p><small>{scenario.answerNote}</small></div>
                <section className="portfolio-evidence" aria-labelledby="evidence-title"><div className="portfolio-evidence-heading"><div><span className="portfolio-overline">RASTREABILIDADE</span><h4 id="evidence-title">Evidências verificadas <span>{scenario.references.length}</span></h4></div><span className="portfolio-verified"><ShieldCheck size={15}/> backend</span></div>
                  <ol>{scenario.references.map((reference, index) => <li key={`${reference.id ?? reference.label}-${index}`}><span className="portfolio-evidence-index">{String(index + 1).padStart(2, '0')}</span><span className="portfolio-evidence-copy"><strong>{reference.label}</strong><small>{reference.detail}</small></span></li>)}</ol>
                </section>
              </div>}
              <details className="portfolio-technical-details"><summary>Detalhes técnicos da execução <ChevronDown size={16}/></summary><dl><div><dt>Tipo real</dt><dd><code>{scenario.execution}</code></dd></div><div><dt>Provedor configurado</dt><dd>{scenario.provider}</dd></div><div><dt>Modelo</dt><dd>{scenario.model ?? 'Não registrado; não houve resposta do modelo'}</dd></div><div><dt>Resultado upstream</dt><dd>{scenario.providerOutcome}</dd></div><div><dt>Ferramentas escolhidas pelo modelo</dt><dd>{scenario.modelTools.length ? scenario.modelTools.map((tool) => <code key={tool}>{tool}</code>) : 'Nenhuma'}</dd></div><div><dt>Ferramentas Java executadas</dt><dd>{scenario.backendTools.map((tool) => <code key={tool}>{tool}</code>)}</dd></div><div><dt>Roundtrips / duração</dt><dd>{scenario.roundtrips} · {scenario.latency}</dd></div><div><dt>Tokens</dt><dd>{scenario.tokens}</dd></div><div><dt>Registro</dt><dd>{scenario.observedAt}</dd></div></dl><div className="portfolio-reference-ids"><strong>IDs técnicos das referências</strong><ul>{scenario.references.map((reference) => reference.id ? <li key={reference.id}><code>{reference.id}</code></li> : <li key={reference.label}>{reference.label}: ID individual não preservado no registro original</li>)}</ul></div><p className="portfolio-provenance">Fonte: {scenario.provenance}</p></details>
            </article>
          </div>
        </section>

        <section id="architecture" className="portfolio-section portfolio-architecture-section" aria-labelledby="architecture-title">
          <div className="portfolio-section-heading"><div><span className="portfolio-overline">ARQUITETURA REAL</span><h2 id="architecture-title">Do pedido à evidência.</h2></div><p>O portfólio reproduz registros. O sistema real abaixo é executado localmente e mantém o EvidenceRAG original.</p></div>
          <div className="portfolio-architecture-flow" role="list" aria-label="Fluxo da arquitetura de produção local">
            {['React', 'Spring Boot', 'Spring AI', 'Tool Calling', 'PostgreSQL', 'Validação de evidências'].map((part, index) => <div className="portfolio-flow-part" role="listitem" key={part}><span>{String(index + 1).padStart(2, '0')}</span><strong>{part}</strong>{index < 5 && <ArrowRight className="portfolio-flow-arrow" size={16}/>}</div>)}
          </div>
          <div className="portfolio-architecture-notes">
            <article><span className="portfolio-note-symbol"><Database size={18}/></span><h3>Dados estruturados</h3><p>Ferramentas Java consultam contas, transações e alertas fictícios no PostgreSQL; cálculos e filtros determinísticos ficam no backend.</p></article>
            <article><span className="portfolio-note-symbol"><Layers3 size={18}/></span><h3>Recuperação documental</h3><p>O EvidenceRAG original usa embeddings e pgvector para trechos documentais. As políticas bancárias demonstrativas usam PostgreSQL full-text — não um índice vetorial dedicado.</p></article>
            <article><span className="portfolio-note-symbol"><ShieldCheck size={18}/></span><h3>Resposta com limites</h3><p>O backend valida referências contra os resultados recuperados. Falhas do provedor e insuficiência de dados são estados distintos; o fallback é identificado como determinístico.</p></article>
          </div>
        </section>

        <section id="engineering" className="portfolio-engineering">
          <div><span className="portfolio-overline">ENGENHARIA</span><h2>Um sistema real.<br/>Uma demo sem custo de inferência.</h2><p>O código demonstra Java 21, Spring Boot, Spring AI, React, TypeScript, PostgreSQL, pgvector, Flyway, testes JUnit/Testcontainers e Vitest. Consulte a implementação e a documentação para detalhes e limites de cada execução.</p></div>
          <a className="portfolio-github-large" href="https://github.com/dhionson2/evidencerag" target="_blank" rel="noreferrer"><Github size={20}/> Explorar repositório <span aria-hidden="true">↗</span></a>
        </section>
      </main>
      <footer className="portfolio-footer"><span>EvidenceRAG · Interactive AI Engineering Demo</span><span>Dados exclusivamente sintéticos · nenhuma operação financeira real</span><a href="#top">Voltar ao início ↑</a></footer>
    </div>
  );
}
