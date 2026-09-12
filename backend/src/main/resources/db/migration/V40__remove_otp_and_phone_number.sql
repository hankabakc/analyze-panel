-- V40: SMS OTP ve telefon alanının kaldırılması (T-056 / ENG-13 / APP-03 §4)
-- Kullanıcılar artık yalnızca e-posta + şifre ile sisteme erişir; telefon ve OTP tabloları düşürülür.

-- 1. OTP kodları tablosunu kaldır
DROP TABLE IF EXISTS otp_codes CASCADE;

-- 2. app_users tablosundaki telefon sütununu kaldır (veri minimizasyonu)
ALTER TABLE app_users DROP COLUMN IF EXISTS phone_number;

-- 3. refresh_tokens tablosundaki giriş kanalı sütununu kaldır
ALTER TABLE refresh_tokens DROP COLUMN IF EXISTS channel;
