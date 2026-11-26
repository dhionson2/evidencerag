ALTER TABLE bank_demo_session_message
    ALTER COLUMN body TYPE text;

ALTER TABLE bank_demo_session_message
    ADD COLUMN experience_mode varchar(20) NOT NULL DEFAULT 'CLIENT'
        CHECK (experience_mode IN ('CLIENT', 'ANALYST')),
    ADD COLUMN outcome varchar(30),
    ADD COLUMN answer_mode varchar(40),
    ADD COLUMN provider_issue varchar(40),
    ADD COLUMN evidence jsonb NOT NULL DEFAULT '[]'::jsonb;

CREATE INDEX bank_demo_session_message_context_idx
    ON bank_demo_session_message(session_id, experience_mode, created_at, id);
