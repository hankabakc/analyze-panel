-- AuditLog, BlacklistedToken ve RefreshToken varlıkları için migration yazılmamıştı;
-- şema doğrulaması "missing table [audit_logs]" ile patlıyordu.
-- Sütun adları Hibernate'in varsayılan snake_case stratejisiyle eşleşir.

CREATE TABLE IF NOT EXISTS audit_logs (
    id          UUID PRIMARY KEY,
    action      VARCHAR(255) NOT NULL,
    user_email  VARCHAR(255) NOT NULL,
    ip_address  VARCHAR(255) NOT NULL,
    details     VARCHAR(1000),
    timestamp   TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_timestamp ON audit_logs (timestamp);

CREATE TABLE IF NOT EXISTS blacklisted_tokens (
    id          UUID PRIMARY KEY,
    token       VARCHAR(1000) NOT NULL,
    expiry_date TIMESTAMP     NOT NULL
);

-- Süresi dolmuş kayıtların temizlenmesi bu sütun üzerinden yapılır.
CREATE INDEX IF NOT EXISTS idx_blacklisted_tokens_expiry ON blacklisted_tokens (expiry_date);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          UUID PRIMARY KEY,
    token       VARCHAR(255) NOT NULL UNIQUE,
    user_id     UUID,
    expiry_date TIMESTAMP    NOT NULL,
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES app_users (id) ON DELETE CASCADE
);
