-- V21: "Beni Hatırla" (30 Günlük Oturum) Desteği (T-007 / K4)
-- Oturumun kalıcı mı yoksa oturum çerezi mi olduğunu rotasyonda da korumak için remembered alanı eklenir.

ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS remembered BOOLEAN NOT NULL DEFAULT FALSE;
