-- V20: Refresh Token Rotasyonu ve Yeniden Kullanım Tespiti (T-006 / ENG-11 §2.3)
-- Eski refresh token'ların silinmeyip damgalanması (replaced_at) için sütun eklenir.

ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS replaced_at TIMESTAMP;
