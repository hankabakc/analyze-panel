-- V18: Parola alanının kaldırılması. Giriş sistemi telefon + SMS OTP modeline taşındı.
ALTER TABLE app_users DROP COLUMN IF EXISTS password;
