-- V23: Şifre altyapısı için password ve must_change_password sütunlarının eklenmesi (T-009)
-- ENG-11 §1.1 & ENG-08 §3.2

ALTER TABLE app_users 
ADD COLUMN password VARCHAR(255),
ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
