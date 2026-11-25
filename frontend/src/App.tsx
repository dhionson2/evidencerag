import { useEffect, useRef, useState, type FormEvent } from 'react';
import { ArrowUp, BookOpen, Check, FilePlus2, FileText, KeyRound, LoaderCircle, MessageSquareText, ShieldCheck, Trash2, UploadCloud, X } from 'lucide-react';
import { askQuestion, listDocuments, removeDocument, uploadDocument, type DocumentItem, type QueryResult } from './lib/api';
import { EvidencePanel } from './components/EvidencePanel';

export default function App() {
  const publicDemo = import.meta.env.VITE_DEMO_MODE === 'true';
  const [documents, setDocuments] = useState<DocumentItem[]>([]);
  const [question, setQuestion] = useState('');
  const [result, setResult] = useState<QueryResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');
  const [tokenOpen, setTokenOpen] = useState(false);
  const [token, setToken] = useState(sessionStorage.getItem('evidencerag-token') ?? '');
  const fileInput = useRef<HTMLInputElement>(null);

  const refresh = async () => { try { setDocuments(await listDocuments()); } catch (e) { setError((e as Error).message); } };
  useEffect(() => { if (!publicDemo) void refresh(); }, [publicDemo]);
  const onUpload = async (files: FileList | null) => {
    if (!files?.length) return;
    setError(''); setUploading(true);
    try { for (const file of Array.from(files)) await uploadDocument(file); await refresh(); setResult(null); }
    catch (e) { setError((e as Error).message); }
    finally { setUploading(false); if (fileInput.current) fileInput.current.value = ''; }
  };
  const onAsk = async (event: FormEvent) => {
    event.preventDefault(); if (!question.trim() || (!publicDemo && !documents.length)) return;
    setBusy(true); setError(''); setResult(null);
    try { setResult(await askQuestion(question.trim())); } catch (e) { setError((e as Error).message); }
    finally { setBusy(false); }
  };
  const onDelete = async (doc: DocumentItem) => {
    if (!window.confirm(`Excluir “${doc.filename}” e seus vetores?`)) return;
    try { await removeDocument(doc.id); await refresh(); }
    catch (e) { setError((e as Error).message); }
  };
  const saveToken = () => { if (token) sessionStorage.setItem('evidencerag-token', token); else sessionStorage.removeItem('evidencerag-token'); setTokenOpen(false); void refresh(); };
  const grounding = result?.status === 'GROUNDED';

  return <div className="app-shell">
    <header className="topbar"><a className="brand" href="#top"><span className="brand-mark"><BookOpen size={19}/></span><span>Evidence<span>RAG</span></span></a><div className="top-actions"><span className="local-badge"><span className="green-dot"/> {publicDemo ? 'demo pública' : 'ambiente local'}</span>{!publicDemo && <button className="icon-button" aria-label="Configurar acesso" onClick={() => setTokenOpen(true)}><KeyRound size={17}/></button>}</div></header>
    <main id="top" className="main-layout">
      <section className="intro"><div className="intro-copy"><div className="kicker"><ShieldCheck size={15}/> RESPOSTAS COM RASTREABILIDADE</div><h1>Encontre respostas<br/><em>nos seus documentos.</em></h1><p>Faça perguntas a contratos, procedimentos e políticas. Cada resposta mostra os trechos que a sustentam — e sinaliza quando faltam evidências.</p><div className="trust-points"><span><Check size={15}/> Fontes verificáveis</span><span><Check size={15}/> Conteúdo não confiável isolado</span><span><Check size={15}/> Sem resposta sem evidência</span></div></div><div className="intro-art" aria-hidden="true"><div className="art-orbit orbit-one"/><div className="art-orbit orbit-two"/><div className="art-page"><div className="art-page-head"><span/><span/></div><div className="art-line wide"/><div className="art-line"/><div className="art-line short"/><div className="art-highlight"><span/><span/></div><div className="art-line wide"/><div className="art-line short"/></div><div className="art-spark spark-a">✳</div><div className="art-spark spark-b">✦</div><div className="art-tag"><span className="green-dot"/> evidência localizada</div></div></section>

      <div className="workspace-grid">
        <aside className="sidebar">
          {publicDemo ? <><section className="surface docs-surface"><div className="section-heading"><div><span className="eyebrow">BASE DE CONHECIMENTO</span><h2>Corpus demonstrativo</h2></div><span className="count-pill" aria-label="Somente leitura">RO</span></div><p className="demo-corpus-note">Esta demo consulta apenas documentos fictícios pré-indexados. O envio, a listagem e a exclusão de documentos estão desativados.</p></section><div className="privacy-note"><ShieldCheck size={16}/><span><strong>Dados fictícios</strong>Não envie informações pessoais. Consultas e trechos recuperados são enviados ao provedor de IA configurado.</span></div></> : <>
          <section className="surface docs-surface"><div className="section-heading"><div><span className="eyebrow">BASE DE CONHECIMENTO</span><h2>Documentos</h2></div><span className="count-pill">{documents.length}</span></div>
            <button className="upload-zone" onClick={() => fileInput.current?.click()} disabled={uploading}><span className="upload-icon">{uploading ? <LoaderCircle className="spin"/> : <UploadCloud/>}</span><strong>{uploading ? 'Indexando documento…' : 'Adicionar documentos'}</strong><small>Arraste ou selecione PDF e Markdown<br/>até 10 MB por arquivo</small><span className="upload-cta"><FilePlus2 size={14}/> escolher arquivos</span></button>
            <input ref={fileInput} type="file" accept=".pdf,.md,.markdown,application/pdf,text/markdown,text/plain" multiple hidden onChange={(e) => void onUpload(e.target.files)}/>
            <div className="doc-list">{documents.length === 0 ? <div className="empty-docs"><FileText size={21}/><span>Nenhum documento indexado</span><small>Adicione um arquivo para começar.</small></div> : documents.map(doc => <article className="doc-row" key={doc.id}><span className="doc-type">{doc.mediaType === 'application/pdf' ? 'PDF' : 'MD'}</span><div className="doc-info"><strong title={doc.filename}>{doc.filename}</strong><small>{doc.chunkCount} trechos · {new Date(doc.createdAt).toLocaleDateString('pt-BR')}</small></div><button className="quiet-button" aria-label={`Excluir ${doc.filename}`} onClick={() => void onDelete(doc)}><Trash2 size={15}/></button></article>)}</div>
          </section>
          <div className="privacy-note"><ShieldCheck size={16}/><span><strong>Uso responsável</strong>Documentos podem conter instruções maliciosas. O copiloto trata o conteúdo como dado e não executa comandos.</span></div>
          </>}
        </aside>

        <section className="conversation surface"><div className="section-heading chat-heading"><div><span className="eyebrow">CONSULTA DOCUMENTAL</span><h2>O que você quer saber?</h2></div><span className="ai-status"><span className="green-dot"/> RAG ativo</span></div>
          <form className="ask-form" onSubmit={onAsk}><label htmlFor="question">Pergunte em linguagem natural</label><div className="question-box"><textarea id="question" rows={3} maxLength={2000} value={question} onChange={e => setQuestion(e.target.value)} placeholder={publicDemo ? 'Pergunte sobre os documentos fictícios da demonstração.' : documents.length ? 'Ex.: Qual é o prazo previsto para responder uma solicitação?' : 'Adicione documentos para habilitar as consultas.'} disabled={(!publicDemo && !documents.length) || busy}/><div className="question-footer"><span>{question.length}/2000</span><button type="submit" disabled={!question.trim() || (!publicDemo && !documents.length) || busy} aria-label="Enviar pergunta">{busy ? <LoaderCircle className="spin" size={18}/> : <ArrowUp size={19}/>}</button></div></div><div className="form-hint"><MessageSquareText size={14}/> A resposta será limitada ao conteúdo recuperado.</div></form>
          {error && <div className="error-banner" role="alert"><span>{error}</span><button onClick={() => setError('')} aria-label="Fechar erro"><X size={16}/></button></div>}
          {busy && <div className="loading-card"><LoaderCircle className="spin"/><span>{publicDemo ? 'A demonstração pode estar iniciando após um período sem uso. Buscando evidências…' : 'Buscando evidências nos documentos…'}</span></div>}
          {result && !busy && <div className={`answer-card ${grounding ? 'grounded' : 'insufficient'}`}><div className="answer-state"><span className="state-icon">{grounding ? <Check size={16}/> : <ShieldCheck size={16}/>}</span><div><strong>{grounding ? 'Resposta fundamentada' : 'Evidência insuficiente'}</strong><small>Execução {result.runId}</small></div><span className="status-chip">{grounding ? 'COM FONTES' : 'SEM FONTES'}</span></div><p className="answer-text">{result.answer}</p></div>}
          {result && grounding && <EvidencePanel citations={result.citations}/>}
          {!result && !busy && <div className="welcome-state"><div className="welcome-icon"><BookOpen size={22}/></div><strong>{publicDemo ? 'Explore o corpus fictício' : 'Sua análise começa aqui'}</strong><p>{publicDemo ? 'Envie uma pergunta sobre os documentos de demonstração. As fontes recuperadas aparecerão abaixo da resposta.' : 'Adicione os documentos relevantes e envie uma pergunta. As fontes recuperadas aparecerão abaixo da resposta.'}</p></div>}
        </section>
      </div>
      <footer className="footer"><span>EvidenceRAG <span>·</span> demonstração de análise documental</span><span>Respostas podem exigir revisão humana.</span></footer>
    </main>
    {!publicDemo && tokenOpen && <div className="modal-backdrop" role="presentation" onMouseDown={e => { if (e.target === e.currentTarget) setTokenOpen(false); }}><section className="token-modal" role="dialog" aria-modal="true" aria-labelledby="token-title"><button className="modal-close" onClick={() => setTokenOpen(false)} aria-label="Fechar"><X size={18}/></button><span className="modal-icon"><KeyRound size={19}/></span><h2 id="token-title">Acesso local</h2><p>Se a API estiver configurada com <code>APP_API_TOKEN</code>, informe o mesmo valor. O token fica apenas nesta aba do navegador.</p><label htmlFor="api-token">Token da API</label><input id="api-token" type="password" autoComplete="off" value={token} onChange={e => setToken(e.target.value)} placeholder="Opcional no modo local"/><button className="primary-button" onClick={saveToken}>Salvar configuração</button></section></div>}
  </div>;
}
