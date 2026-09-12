-- V22: Hesap kilidi sütunlarının kaldırılması (T-008 / K4)
-- Kaba kuvvet koruması telefon bazlı kademeli gecikmeye taşındığı için lock_time ve failed_login_attempts sütunları kaldırılır.

ALTER TABLE app_users DROP COLUMN IF EXISTS lock_time;
ALTER TABLE app_users DROP COLUMN IF EXISTS failed_login_attempts;
