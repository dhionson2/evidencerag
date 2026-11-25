import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { EvidencePanel } from './EvidencePanel';

describe('EvidencePanel', () => {
  it('shows source name, page, original excerpt and stable chunk id', () => {
    render(<EvidencePanel citations={[{ documentId: 'doc-1', documentName: 'policy.md', chunkId: 'chunk-123', pageNumber: 2, similarity: 0.9, excerpt: 'A equipe responde em cinco dias úteis.' }]} />);
    expect(screen.getByText('policy.md')).toBeInTheDocument();
    expect(screen.getByText('Página 2')).toBeInTheDocument();
    expect(screen.getByText('A equipe responde em cinco dias úteis.')).toBeInTheDocument();
    expect(screen.getByText('chunk-123')).toBeInTheDocument();
  });
  it('renders no evidence card for an empty list', () => {
    const { container } = render(<EvidencePanel citations={[]} />);
    expect(container).toBeEmptyDOMElement();
  });
});
