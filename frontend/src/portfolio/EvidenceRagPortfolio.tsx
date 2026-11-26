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
          <div className="portfolio-section-heading"><div><span className="portfolio-overline">ARQUITETURA REAL</span><h2 id="architecture-title">Do pedido à evidência.</h2></div><p>O diagrama mostra o sistema bancário em execução local. Esta página é estática e apenas reproduz execuções registradas.</p></div>
          <figure className="portfolio-architecture-map" aria-labelledby="architecture-map-title">
            <figcaption className="portfolio-map-caption"><span>CAMINHO DE UMA CONSULTA BANCÁRIA</span><strong id="architecture-map-title">O backend controla o acesso aos dados e confere cada fonte antes de responder.</strong></figcaption>
            <ol className="portfolio-architecture-flow" aria-label="Etapas da pergunta até a consulta de dados">
              {[
                { title: 'React', label: 'INTERFACE', detail: 'Recebe a pergunta e mostra resposta, tipo de execução e fontes.' },
                { title: 'Spring Boot', label: 'CONTROLE', detail: 'Valida sessão, modo Cliente/Analista e limites da interação.' },
                { title: 'Spring AI', label: 'ORQUESTRAÇÃO', detail: 'Envia contexto autorizado ao provedor e recebe a decisão de ferramenta.' },
                { title: 'Ferramenta Java', label: 'EXECUÇÃO TIPADA', detail: 'Valida parâmetros e escopo da sessão; não aceita SQL do modelo.' },
                { title: 'PostgreSQL', label: 'DADOS FICTÍCIOS', detail: 'Consulta as linhas necessárias para a ferramenta selecionada.' },
              ].map((node, index, nodes) => <li className="portfolio-architecture-node" key={node.title}>
                <div className="portfolio-node-top"><span>{String(index + 1).padStart(2, '0')}</span><small>{node.label}</small></div>
                <strong>{node.title}</strong><p>{node.detail}</p>
                {index < nodes.length - 1 && <ArrowRight className="portfolio-node-arrow" size={18} aria-hidden="true"/>}
              </li>)}
            </ol>

            <div className="portfolio-tool-cycle" aria-label="O resultado da ferramenta retorna ao modelo e a resposta é verificada pelo backend">
              <div className="portfolio-cycle-heading"><span className="portfolio-cycle-icon"><Workflow size={18}/></span><div><strong>O ciclo de tool calling</strong><p>Uma chamada pode fazer mais de uma rodada; a resposta da ferramenta volta ao modelo.</p></div></div>
              <ol className="portfolio-cycle-steps">
                <li><span>1</span><div><strong>Modelo pede uma ferramenta</strong><small>Somente ferramentas permitidas para aquele modo.</small></div></li>
                <li><span>2</span><div><strong>Java consulta o PostgreSQL</strong><small>A sessão define quais registros podem ser lidos.</small></div></li>
                <li><span>3</span><div><strong>Resultado retorna ao modelo</strong><small>O modelo redige a resposta usando o retorno recebido.</small></div></li>
                <li><span>4</span><div><strong>Backend valida as referências</strong><small>IDs precisam corresponder às evidências recuperadas.</small></div></li>
              </ol>
              <div className="portfolio-answer-modes"><span className="portfolio-mode-badge mode-ai">AI_TOOL_LOOP · ferramenta escolhida pelo modelo</span><span className="portfolio-mode-badge mode-deterministic">DETERMINISTIC_FALLBACK · cálculo Java identificado como determinístico</span></div>
            </div>

            <div className="portfolio-data-paths" aria-label="Caminhos distintos de recuperação de dados">
              <article className="portfolio-data-path banking-path"><div className="portfolio-data-path-heading"><span className="portfolio-note-symbol"><Database size={18}/></span><div><span className="portfolio-overline">DADOS ESTRUTURADOS</span><h3>Sandbox bancário</h3></div></div>
                <div className="portfolio-mini-flow"><span>Ferramenta Java</span><ArrowRight size={15}/><span>PostgreSQL</span></div>
                <p>Contas, movimentações, alertas e propostas fictícias. O backend aplica o escopo da sessão e calcula totais; nenhuma operação financeira real é executada.</p>
                <div className="portfolio-data-note"><strong>Políticas bancárias</strong><span>Busca PostgreSQL full-text, com versão e seção; não usa um índice vetorial dedicado.</span></div>
              </article>
              <article className="portfolio-data-path rag-path"><div className="portfolio-data-path-heading"><span className="portfolio-note-symbol"><Layers3 size={18}/></span><div><span className="portfolio-overline">RAG DOCUMENTAL ORIGINAL</span><h3>EvidenceRAG</h3></div></div>
                <div className="portfolio-mini-flow"><span>Pergunta</span><ArrowRight size={15}/><span>Embedding</span><ArrowRight size={15}/><span>pgvector</span><ArrowRight size={15}/><span>Trechos</span></div>
                <p>O retrieval documental usa embeddings e pgvector. O backend valida os trechos recuperados antes de compor citações; provider/modelo de embeddings é independente do chat.</p>
              </article>
            </div>
          </figure>
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
