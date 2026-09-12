-- V27: Şifre sıfırlama ve şifre değişikliğinde eski erişim token'larını geçersiz kılmak için zaman damgası (T-013B / B-46)
-- ENG-11 §2.2, §2.4 & APP-01 §2.1

ALTER TABLE app_users 
ADD COLUMN credentials_invalidated_at TIMESTAMPTZ;
