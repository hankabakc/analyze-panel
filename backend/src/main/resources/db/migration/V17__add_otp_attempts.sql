-- OtpCode.attempts (OTP brute-force sayacı) için migration yazılmamıştı.
ALTER TABLE otp_codes ADD COLUMN IF NOT EXISTS attempts INTEGER NOT NULL DEFAULT 0;
