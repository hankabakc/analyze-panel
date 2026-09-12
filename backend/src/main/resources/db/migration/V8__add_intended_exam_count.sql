-- V8: Raporun kaç denemelik olduğunu saklayan sütun eklenmesi
ALTER TABLE analysis_reports ADD COLUMN intended_exam_count INTEGER DEFAULT 5;
