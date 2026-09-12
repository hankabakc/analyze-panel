-- AppUser varlığına eklenen alanlar için migration yazılmamıştı; şema doğrulaması
-- "missing column [failed_login_attempts]" ile patlıyordu. Bu migration şemayı
-- entity ile hizalar.

-- Hesap kilitleme alanları (AppUser.failedLoginAttempts / AppUser.lockTime)
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS lock_time TIMESTAMP;

-- PII alanları PiiConverter ile AES şifrelenip Base64 olarak saklanıyor; şifreli
-- değer düz metinden çok daha uzun olduğu için eski genişlikler (100 / 15 / 100)
-- taşmaya yol açıyordu. Entity'deki length = 500 ile eşitlenir.
ALTER TABLE app_users ALTER COLUMN email TYPE VARCHAR(500);
ALTER TABLE app_users ALTER COLUMN phone_number TYPE VARCHAR(500);
ALTER TABLE app_users ALTER COLUMN full_name TYPE VARCHAR(500);
