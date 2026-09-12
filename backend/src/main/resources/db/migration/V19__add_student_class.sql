-- V19: Öğrenci sınıf/şube bilgisini saklamak için app_users tablosuna student_class kolonu eklenir.
-- Yalnızca öğrenciler için doldurulur (role = STUDENT), öğretmenlerde NULL kalır.

ALTER TABLE app_users ADD COLUMN student_class VARCHAR(20);
