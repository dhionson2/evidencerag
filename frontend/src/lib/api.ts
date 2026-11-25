export type EvidenceCitation = {
  documentId: string; documentName: string; chunkId: string;
  pageNumber: number | null; similarity: number | null; excerpt: string;
};
export type QueryResult = { runId: string; status: 'GROUNDED' | 'INSUFFICIENT_EVIDENCE'; answer: string; citations: EvidenceCitation[] };
export type DocumentItem = { id: string; filename: string; mediaType: string; sha256: string; chunkCount: number; createdAt: string };
const base = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';
const publicDemo = import.meta.env.VITE_DEMO_MODE === 'true';
function headers(): HeadersInit {
  if (publicDemo) return {};
  const token = sessionStorage.getItem('evidencerag-token');
  return token ? { 'X-Api-Key': token } : {};
}
async function handle<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const body = await response.json().catch(() => null) as { message?: string } | null;
    if (response.status === 429) throw new Error(body?.message ?? 'Limite de consultas atingido. Aguarde um minuto e tente novamente.');
    if (response.status === 503) throw new Error(body?.message ?? 'O serviço está iniciando ou temporariamente ocupado. Aguarde um pouco e tente novamente.');
    throw new Error(body?.message ?? `Falha na API (${response.status}).`);
  }
  return response.status === 204 ? undefined as T : response.json() as Promise<T>;
}
export async function listDocuments(): Promise<DocumentItem[]> {
  return handle(await fetch(`${base}/api/documents`, { headers: headers() }));
}
export async function uploadDocument(file: File): Promise<DocumentItem> {
  const form = new FormData(); form.append('file', file);
  return handle(await fetch(`${base}/api/documents`, { method: 'POST', headers: headers(), body: form }));
}
export async function removeDocument(id: string): Promise<void> {
  return handle(await fetch(`${base}/api/documents/${id}`, { method: 'DELETE', headers: headers() }));
}
export async function askQuestion(question: string): Promise<QueryResult> {
  try {
    return await handle(await fetch(`${base}/api/query`, {
      method: 'POST', headers: { ...headers(), 'Content-Type': 'application/json' },
      body: JSON.stringify({ question }), signal: AbortSignal.timeout(90_000),
    }));
  } catch (error) {
    if (error instanceof DOMException && error.name === 'TimeoutError') {
      throw new Error('A consulta demorou mais que o esperado. O serviço pode estar iniciando; tente novamente.', { cause: error });
    }
    if (error instanceof TypeError) throw new Error('Não foi possível conectar à API. Ela pode estar iniciando; tente novamente em instantes.', { cause: error });
    throw error;
  }
}
