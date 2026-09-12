-- V43: Sık filtrelenen ve birleştirilen sorgular için performans indeksleri (T-076 / S-027)

-- 1. Kullanıcı rolü ve statüsü üzerinden filtreleme (getActiveTeachers, getActiveStudents, countByRoleAndStatus)
CREATE INDEX IF NOT EXISTS idx_app_users_role_status ON app_users(role, status);

-- 2. Sınıf bazlı öğrenci sorgulamaları (sınıf sıralama ve listeleme)
CREATE INDEX IF NOT EXISTS idx_app_users_class_id ON app_users(class_id);

-- 3. Rapor onay statüsü ve öğrenci kimliği birleşik sorguları (Sıralama panosu ve karne özetleri)
CREATE INDEX IF NOT EXISTS idx_analysis_reports_status_student ON analysis_reports(status, student_id);

-- 4. Öğrenci raporlarının kronolojik listelenmesi (getStudentReports)
CREATE INDEX IF NOT EXISTS idx_analysis_reports_student_processed ON analysis_reports(student_id, processed_at DESC);
