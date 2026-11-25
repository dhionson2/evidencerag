import { FileText, Hash, MapPin } from 'lucide-react';
import type { EvidenceCitation } from '../lib/api';
export function EvidencePanel({ citations }: { citations: EvidenceCitation[] }) {
  if (!citations.length) return null;
  return <section className="evidence-panel" aria-label="Referências documentais">
    <div className="section-heading"><div><span className="eyebrow">FONTES UTILIZADAS</span><h2>Evidências da resposta</h2></div><span className="count-pill">{citations.length} {citations.length === 1 ? 'trecho' : 'trechos'}</span></div>
    <div className="evidence-list">{citations.map((citation) => <article className="evidence-card" key={citation.chunkId}>
      <div className="evidence-meta"><span><FileText size={15}/>{citation.documentName}</span>{citation.pageNumber && <span><MapPin size={14}/>Página {citation.pageNumber}</span>}</div>
      <blockquote>{citation.excerpt}</blockquote>
      <div className="chunk-id"><Hash size={13}/><code>{citation.chunkId}</code></div>
    </article>)}</div>
  </section>;
}
