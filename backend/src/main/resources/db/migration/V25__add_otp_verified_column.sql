-- V25: OTP kodlarının başarılı doğrulanma durumunu (is_verified) iptal edilenlerden (is_used=true, attempts>=3) ayırma (T-014)
ALTER TABLE otp_codes ADD COLUMN is_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Mevcut verilerde is_used=true ve attempts < 3 olanları doğrulanmış kabul et (varsayılan geriye dönük uyum)
UPDATE otp_codes SET is_verified = TRUE WHERE is_used = TRUE AND attempts < 3;
