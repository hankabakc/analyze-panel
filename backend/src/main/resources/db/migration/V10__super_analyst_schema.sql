-- V10: Süper Analist AI İçin Gelişmiş Raporlama ve Hedef Altyapısı
-- 1. Rapor Türü ve Koçluk Metinleri
ALTER TABLE analysis_reports ADD COLUMN report_type VARCHAR(20) DEFAULT 'SINGLE'; -- SINGLE, SUMMARY
ALTER TABLE analysis_reports ADD COLUMN mentor_feedback TEXT; -- AI Koçluk Notları
ALTER TABLE analysis_reports ADD COLUMN future_projection TEXT; -- Gelecek Tahminleri
ALTER TABLE analysis_reports ADD COLUMN strategic_priority TEXT; -- En Kritik Gelişim Konusu

-- 2. Öğrenci Profil Tablosu (Hedefler İçin)
CREATE TABLE IF NOT EXISTS student_profiles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    target_school VARCHAR(255),
    target_score DECIMAL(5,2),
    current_level VARCHAR(50), -- BEGINNER, INTERMEDIATE, ADVANCED
    last_updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE
);

-- 3. Konu Bazlı Detaylarda SS (Soru Sayısı) ve Doğru/Yanlış Sayıları
-- Mevcut topic_details tablosuna yeni kolonlar ekleniyor (Eğer zaten varsa hata vermez)
ALTER TABLE topic_details ADD COLUMN total_questions INTEGER DEFAULT 0;
ALTER TABLE topic_details ADD COLUMN correct_count INTEGER DEFAULT 0;
ALTER TABLE topic_details ADD COLUMN wrong_count INTEGER DEFAULT 0;
