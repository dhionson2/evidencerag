-- Banking demo data is shared, deterministic and synthetic. Session state is isolated below.
CREATE TABLE bank_demo_customer (
    id varchar(40) PRIMARY KEY,
    display_name varchar(100) NOT NULL,
    scenario_code varchar(20) NOT NULL UNIQUE
);

CREATE TABLE bank_demo_account (
    id varchar(40) PRIMARY KEY,
    customer_id varchar(40) NOT NULL REFERENCES bank_demo_customer(id),
    account_type varchar(40) NOT NULL,
    currency char(3) NOT NULL DEFAULT 'BRL',
    opening_balance numeric(14,2) NOT NULL,
    current_balance numeric(14,2) NOT NULL
);

CREATE TABLE bank_demo_policy (
    id varchar(40) PRIMARY KEY,
    version varchar(20) NOT NULL,
    title varchar(160) NOT NULL,
    section varchar(80) NOT NULL,
    effective_on date NOT NULL,
    rule_text text NOT NULL
);

CREATE TABLE bank_demo_credit_proposal (
    id varchar(40) PRIMARY KEY,
    company_name varchar(120) NOT NULL,
    requested_amount numeric(14,2) NOT NULL,
    proposed_term_months integer NOT NULL,
    contract_term_months integer NOT NULL,
    required_document_count integer NOT NULL,
    received_document_count integer NOT NULL,
    missing_document varchar(160),
    policy_id varchar(40) NOT NULL REFERENCES bank_demo_policy(id)
);

CREATE TABLE bank_demo_alert (
    id varchar(40) PRIMARY KEY,
    code varchar(40) NOT NULL UNIQUE,
    transaction_id varchar(40) NOT NULL,
    related_proposal_id varchar(40) REFERENCES bank_demo_credit_proposal(id),
    rule_code varchar(40) NOT NULL,
    summary text NOT NULL
);

CREATE TABLE bank_demo_transaction (
    id varchar(40) PRIMARY KEY,
    account_id varchar(40) NOT NULL REFERENCES bank_demo_account(id),
    occurred_at timestamptz NOT NULL,
    description varchar(180) NOT NULL,
    category varchar(60) NOT NULL,
    amount numeric(14,2) NOT NULL CHECK (amount > 0),
    direction varchar(10) NOT NULL CHECK (direction IN ('CREDIT', 'DEBIT')),
    alert_id varchar(40) REFERENCES bank_demo_alert(id) DEFERRABLE INITIALLY DEFERRED
);

ALTER TABLE bank_demo_alert ADD CONSTRAINT bank_demo_alert_transaction_fk
    FOREIGN KEY (transaction_id) REFERENCES bank_demo_transaction(id) DEFERRABLE INITIALLY DEFERRED;
CREATE INDEX bank_demo_transaction_account_date_idx ON bank_demo_transaction(account_id, occurred_at DESC);

CREATE TABLE bank_demo_session (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    selected_mode varchar(20) NOT NULL CHECK (selected_mode IN ('CLIENT', 'ANALYST')),
    CHECK (expires_at = created_at + interval '4 hours')
);

CREATE TABLE bank_demo_session_overlay (
    session_id uuid NOT NULL REFERENCES bank_demo_session(id) ON DELETE CASCADE,
    overlay_key varchar(100) NOT NULL,
    payload jsonb NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (session_id, overlay_key)
);

CREATE TABLE bank_demo_session_note (
    id uuid PRIMARY KEY,
    session_id uuid NOT NULL REFERENCES bank_demo_session(id) ON DELETE CASCADE,
    body varchar(2000) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE bank_demo_session_message (
    id uuid PRIMARY KEY,
    session_id uuid NOT NULL REFERENCES bank_demo_session(id) ON DELETE CASCADE,
    role varchar(20) NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
    body varchar(8000) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO bank_demo_customer(id, display_name, scenario_code) VALUES
    ('CUST-001', 'Cliente Exemplo A', 'SC-01'),
    ('CUST-002', 'Cliente Exemplo B', 'SC-02');

INSERT INTO bank_demo_account(id, customer_id, account_type, opening_balance, current_balance) VALUES
    ('ACC-001', 'CUST-001', 'Conta corrente', 5600.00, 5654.00),
    ('ACC-002', 'CUST-002', 'Conta corrente', 8500.00, 12928.00);

INSERT INTO bank_demo_policy(id, version, title, section, effective_on, rule_text) VALUES
    ('POL-CRED', '2.1', 'Política fictícia de crédito PJ', '4.2 Documentação', DATE '2026-01-15', 'A proposta exige cinco documentos: contrato social, demonstrativos financeiros, comprovante de endereço comercial, identificação do representante e autorização de consulta. O prazo informado no contrato deve corresponder ao prazo solicitado. Pendências devem ser encaminhadas para revisão humana; esta política não autoriza aprovação automática.'),
    ('POL-ALERT', '1.3', 'Regras fictícias de alertas transacionais', '2.1 Valor atípico', DATE '2026-02-01', 'Uma compra acima de R$ 3.500 pode ser sinalizada quando superar em pelo menos três vezes o maior débito individual dos 90 dias anteriores. A sinalização indica necessidade de revisão e não comprova fraude.'),
    ('POL-PRIV', '1.0', 'Política fictícia de privacidade da demonstração', '1.1 Dados sintéticos', DATE '2026-01-01', 'A demonstração utiliza exclusivamente dados fictícios. Não solicitar nem persistir credenciais ou documentos pessoais reais.'),
    ('POL-SESSION', '1.0', 'Política fictícia de sessão da demonstração', '3.1 Validade', DATE '2026-01-01', 'Cada demonstração anônima tem validade absoluta de quatro horas contadas da criação; atividade posterior não renova o prazo.'),
    ('POL-REVIEW', '1.0', 'Política fictícia de revisão humana', '5.1 Limites', DATE '2026-01-01', 'Alertas e análises de crédito são subsídios demonstrativos. Somente uma pessoa revisora fictícia pode registrar encaminhamento simulado na própria sessão.');

INSERT INTO bank_demo_credit_proposal(id, company_name, requested_amount, proposed_term_months, contract_term_months,
    required_document_count, received_document_count, missing_document, policy_id) VALUES
    ('CR-102', 'Comercial Exemplo Ltda.', 150000.00, 24, 36, 5, 4, 'Autorização de consulta', 'POL-CRED'),
    ('CR-103', 'Oficina Modelo S.A.', 80000.00, 18, 18, 5, 5, NULL, 'POL-CRED'),
    ('CR-104', 'Serviços Fictícios ME', 42000.00, 12, 12, 5, 3, 'Demonstrativos financeiros; identificação do representante', 'POL-CRED');

-- Two accounts × fifteen deterministic transactions = 30 synthetic records.
WITH seeds(account_id, txn_no, day_offset, description, category, amount, direction, alert_id) AS (
    SELECT 'ACC-001', s.n, (s.n * 2),
           (ARRAY['Salário fictício','Mercado Modelo','Transporte Demo','Farmácia Exemplo','Restaurante Central','Conta de energia','Assinatura de música','Livraria Modelo','Compra online Demo','Transferência simulada','Café da Praça','Plano de telefone','Academia Fictícia','Loja Esportiva','Compra eletrônica de demonstração'])[s.n],
           (ARRAY['Renda','Alimentação','Transporte','Saúde','Alimentação','Moradia','Assinaturas','Lazer','Compras','Renda','Alimentação','Serviços','Saúde','Compras','Compras'])[s.n],
           (ARRAY[6200,480,85,120,170,210,28,95,350,600,35,74,89,210,4800])[s.n]::numeric / 1,
           (ARRAY['CREDIT','DEBIT','DEBIT','DEBIT','DEBIT','DEBIT','DEBIT','DEBIT','DEBIT','CREDIT','DEBIT','DEBIT','DEBIT','DEBIT','DEBIT'])[s.n],
           CASE WHEN s.n = 15 THEN 'ALT-001' ELSE NULL END
    FROM generate_series(1,15) AS s(n)
    UNION ALL
    SELECT 'ACC-002', s.n, (s.n * 3),
           (ARRAY['Receita sintética','Mercado Bairro','Combustível Demo','Restaurante Modelo','Material de escritório','Serviço de nuvem','Transporte Demo','Papelaria Fictícia','Reembolso simulado','Conta de internet','Café da Praça','Manutenção exemplo','Seguro fictício','Compra de equipamento','Serviço contratado'])[s.n],
           (ARRAY['Renda','Alimentação','Transporte','Alimentação','Trabalho','Serviços','Transporte','Trabalho','Renda','Serviços','Alimentação','Serviços','Serviços','Trabalho','Renda'])[s.n],
           (ARRAY[3500,320,240,155,280,110,60,95,500,90,42,190,160,1300,3470])[s.n]::numeric / 1,
           (ARRAY['CREDIT','DEBIT','DEBIT','DEBIT','DEBIT','DEBIT','DEBIT','DEBIT','CREDIT','DEBIT','DEBIT','DEBIT','DEBIT','DEBIT','CREDIT'])[s.n],
           CASE WHEN s.n = 15 THEN 'ALT-002' ELSE NULL END
    FROM generate_series(1,15) AS s(n)
)
INSERT INTO bank_demo_transaction(id, account_id, occurred_at, description, category, amount, direction, alert_id)
SELECT md5('bank-demo-tx-' || account_id || '-' || txn_no)::uuid::text, account_id,
       timestamptz '2026-11-01 12:00:00+00' - (day_offset || ' days')::interval,
       description, category, amount, direction, alert_id
FROM seeds;

INSERT INTO bank_demo_alert(id, code, transaction_id, related_proposal_id, rule_code, summary) VALUES
    ('ALT-001', 'ALERT-4800', md5('bank-demo-tx-ACC-001-15')::uuid::text, NULL, 'HIGH_VALUE_VS_HISTORY', 'Compra de R$ 4.800 acima de R$ 3.500 e superior a três vezes o maior débito individual sintético dos 90 dias anteriores; requer revisão, sem indicar fraude comprovada.'),
    ('ALT-002', 'ALERT-3470', md5('bank-demo-tx-ACC-002-15')::uuid::text, NULL, 'RELATED_REVIEW_CONTEXT', 'Crédito sintético recebido de R$ 3.470, registrado como contexto para revisão demonstrativa.'),
    ('ALT-003', 'ALERT-REVIEW-CR102', md5('bank-demo-tx-ACC-001-1')::uuid::text, 'CR-102', 'RELATED_CASE_REVIEW', 'Caso de revisão fictício associado à proposta CR-102 para ilustrar análise conjunta.'),
    ('ALT-004', 'ALERT-CONTEXT', md5('bank-demo-tx-ACC-002-9')::uuid::text, NULL, 'CONTEXT_REQUIRED', 'Movimentação sintética que requer conferência de contexto; nenhuma conclusão automática.');

UPDATE bank_demo_transaction SET alert_id = 'ALT-001' WHERE id = md5('bank-demo-tx-ACC-001-15')::uuid::text;
UPDATE bank_demo_transaction SET alert_id = 'ALT-002' WHERE id = md5('bank-demo-tx-ACC-002-15')::uuid::text;
UPDATE bank_demo_transaction SET alert_id = 'ALT-003' WHERE id = md5('bank-demo-tx-ACC-001-1')::uuid::text;
UPDATE bank_demo_transaction SET alert_id = 'ALT-004' WHERE id = md5('bank-demo-tx-ACC-002-9')::uuid::text;
