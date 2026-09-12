-- ==============================================================================
-- V26: Sınıf Alanı Dönüşümü - Serbest Metin -> 5-12 Tam Sayı (T-016)
-- student_class VARCHAR(20) sütunu düşürülür, yerine grade SMALLINT eklenir.
-- grade sütunu yalnızca öğrenciler için doludur, öğretmen ve yöneticilerde NULL kalır.
-- ==============================================================================

ALTER TABLE app_users DROP COLUMN IF EXISTS student_class;
ALTER TABLE app_users ADD COLUMN grade SMALLINT;
