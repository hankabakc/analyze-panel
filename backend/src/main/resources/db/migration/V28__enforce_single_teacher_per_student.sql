-- V28: Bir öğrencinin tek bir öğretmenle eşleşebilmesini sağlamak için student_id üzerinde benzersizlik kısıtı (T-021).
-- Ön koşul: Mevcut veride mükerrer student_id eşleşmesi bulunmadığı doğrulanmıştır.

ALTER TABLE student_teacher_pairings
    ADD CONSTRAINT uq_student_teacher_pairings_student UNIQUE (student_id);
