-- V9: AI Hata Takibi İçin Ham Cevap ve Doğrulama Alanları Eklendi
ALTER TABLE analysis_reports ADD COLUMN raw_ai_response TEXT;
ALTER TABLE analysis_reports ADD COLUMN validation_errors TEXT;
