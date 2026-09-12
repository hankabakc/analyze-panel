-- V12: Öğrenci Profili ile Referans Okul Bağlantısı
ALTER TABLE student_profiles ADD COLUMN target_school_id UUID;
ALTER TABLE student_profiles ADD CONSTRAINT fk_profile_school FOREIGN KEY (target_school_id) REFERENCES reference_schools(id);
