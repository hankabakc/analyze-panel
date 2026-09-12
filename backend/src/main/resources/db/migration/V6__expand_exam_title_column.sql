-- V6: Exam title sütun uzunluğunun artırılması
-- AI'dan gelen uzun hata mesajları veya detaylı sınav isimleri için kısıt kaldırılıyor.

ALTER TABLE analysis_reports ALTER COLUMN exam_title TYPE TEXT;
