-- V4: AI Destekli Analiz Sistemi Şeması
-- Bu şema, PDF'ten gelen verilerin hiyerarşik olarak saklanmasını sağlar.

-- 1. Ana Analiz Raporu Tablosu
CREATE TABLE analysis_reports (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    exam_title VARCHAR(255), -- AI'ın PDF'ten bulacağı sınav adı (Örn: Özdebir TYT)
    processed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_report_student FOREIGN KEY (student_id) REFERENCES app_users(id) ON DELETE CASCADE
);

-- 2. Ders Bazlı Analiz Özetleri
CREATE TABLE lesson_analyses (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL,
    lesson_name VARCHAR(100) NOT NULL, -- Örn: Matematik, Türkçe
    correct_count INTEGER DEFAULT 0,
    wrong_count INTEGER DEFAULT 0,
    empty_count INTEGER DEFAULT 0,
    success_rate DECIMAL(5,2), -- Başarı yüzdesi (%)
    CONSTRAINT fk_lesson_report FOREIGN KEY (report_id) REFERENCES analysis_reports(id) ON DELETE CASCADE
);

-- 3. Konu Bazlı Derin Analiz Detayları
CREATE TABLE topic_details (
    id UUID PRIMARY KEY,
    lesson_analysis_id UUID NOT NULL,
    topic_name VARCHAR(255) NOT NULL, -- Örn: Üslü Sayılar, Paragraf Yapısı
    status VARCHAR(20), -- CORRECT, WRONG, EMPTY
    ai_suggestion TEXT, -- AI'ın bu konu özelindeki destek önerisi
    CONSTRAINT fk_topic_lesson FOREIGN KEY (lesson_analysis_id) REFERENCES lesson_analyses(id) ON DELETE CASCADE
);

-- 4. AI Genel Feedback (Metinsel Yorumlar)
CREATE TABLE ai_global_feedback (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL,
    content TEXT NOT NULL, -- AI'ın genel değerlendirme ve yol haritası metni
    CONSTRAINT fk_feedback_report FOREIGN KEY (report_id) REFERENCES analysis_reports(id) ON DELETE CASCADE
);

-- Performans için index tanımları
CREATE INDEX idx_reports_student ON analysis_reports(student_id);
CREATE INDEX idx_lesson_report ON lesson_analyses(report_id);
CREATE INDEX idx_topic_lesson ON topic_details(lesson_analysis_id);
