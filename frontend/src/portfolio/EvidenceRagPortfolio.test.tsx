import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { EvidenceRagPortfolio } from './EvidenceRagPortfolio';

describe('EvidenceRagPortfolio', () => {
  afterEach(() => { cleanup(); vi.restoreAllMocks(); });

  it('reproduces three documented scenarios and labels AI vs deterministic execution honestly', () => {
    render(<EvidenceRagPortfolio/>);
    expect(screen.getByText('Nenhuma chamada ao modelo é realizada nesta demonstração.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /A última compra/ })).toHaveAttribute('aria-pressed', 'true');

    const next = screen.getByRole('button', { name: /Próxima etapa/ });
    for (let i = 0; i < 4; i++) fireEvent.click(next);
    expect(screen.getAllByText(/GROUNDED \/ AI_TOOL_LOOP/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/R\$ 4\.800,00 em 01\/11\/2026/).length).toBeGreaterThan(0);
    expect(screen.getAllByText('listDemoTransactions').length).toBeGreaterThan(0);
    expect(screen.getByText('47d88dae-0de0-faca-d415-7508c544c5df')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /Maiores despesas/ }));
    expect(screen.getAllByText('DETERMINISTIC_FALLBACK').length).toBeGreaterThan(0);
    for (let i = 0; i < 4; i++) fireEvent.click(screen.getByRole('button', { name: /Próxima etapa/ }));
    expect(screen.getByText(/O crédito de salário foi excluído/)).toBeInTheDocument();
    expect(screen.getAllByText('summarizeDemoSpending').length).toBeGreaterThan(0);

    fireEvent.click(screen.getByRole('button', { name: /Compra sinalizada/ }));
    for (let i = 0; i < 4; i++) fireEvent.click(screen.getByRole('button', { name: /Próxima etapa/ }));
    expect(screen.getAllByText(/não comprova fraude/).length).toBeGreaterThan(0);
    expect(screen.getByText(/não foi executada nesta interação/)).toBeInTheDocument();
    expect(screen.getAllByText('getFraudAlertContext').length).toBeGreaterThan(0);
  });

  it('does not make network requests when scenarios are selected or replayed', () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    render(<EvidenceRagPortfolio/>);
    fireEvent.click(screen.getByRole('button', { name: /Maiores despesas/ }));
    fireEvent.click(screen.getByRole('button', { name: /Próxima etapa/ }));
    fireEvent.click(screen.getByRole('button', { name: /Compra sinalizada/ }));
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('supports keyboard-operable scenario controls and labeled technical disclosure', () => {
    render(<EvidenceRagPortfolio/>);
    const navigation = screen.getByRole('navigation', { name: 'Cenários de demonstração' });
    expect(within(navigation).getAllByRole('button')).toHaveLength(3);
    expect(screen.getByRole('navigation', { name: 'Navegação principal' })).toBeInTheDocument();
    expect(screen.getByText('Detalhes técnicos da execução')).toBeInTheDocument();
  });
});
