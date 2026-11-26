-- Keep the account balance and all transaction/alert links unchanged. Move only the
-- demonstration timestamp so the existing October debits are genuinely historical.
UPDATE bank_demo_transaction AS tx
SET occurred_at = TIMESTAMPTZ '2026-11-01 12:00:00+00'
FROM bank_demo_alert AS alert
WHERE alert.code = 'ALERT-4800'
  AND tx.id = alert.transaction_id;

DO $$
BEGIN
    IF (SELECT count(*) FROM bank_demo_transaction AS tx
        JOIN bank_demo_alert AS alert ON alert.transaction_id = tx.id
        WHERE alert.code = 'ALERT-4800'
          AND tx.amount = 4800.00
          AND tx.direction = 'DEBIT'
          AND tx.occurred_at = TIMESTAMPTZ '2026-11-01 12:00:00+00') <> 1 THEN
        RAISE EXCEPTION 'Expected exactly one linked ALERT-4800 debit at the deterministic demo timestamp';
    END IF;
END $$;
