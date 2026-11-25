import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import App from './App';
vi.mock('./lib/api', () => ({ listDocuments: vi.fn().mockResolvedValue([]), uploadDocument: vi.fn(), removeDocument: vi.fn(), askQuestion: vi.fn() }));
afterEach(() => { cleanup(); vi.clearAllMocks(); });
describe('App', () => {
  it('introduces EvidenceRAG and disables queries before documents are indexed', async () => {
    render(<App />);
    expect(await screen.findByText(/Encontre respostas/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Enviar pergunta' })).toBeDisabled();
    expect(screen.getByText('Nenhum documento indexado')).toBeInTheDocument();
  });
});
