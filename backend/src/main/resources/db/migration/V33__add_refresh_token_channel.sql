-- V33: Refresh token satırına giriş kanalı (channel) sütunu eklenir (T-041B / S-018)
-- Değerler: 'pwd' (e-posta+şifre), 'otp' (SMS OTP)
-- Varsayılan: 'pwd' (güvenli yöne düşüş)

ALTER TABLE refresh_tokens ADD COLUMN channel VARCHAR(10) NOT NULL DEFAULT 'pwd';
