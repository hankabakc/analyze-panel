-- V5: Analiz raporlarına öğretmen onay mekanizması eklenmesi
ALTER TABLE analysis_reports ADD COLUMN status VARCHAR(20) DEFAULT 'PENDING_APPROVAL';

-- Mevcut raporlar varsa (geliştirme aşamasında) onları da bu statüye çekelim
UPDATE analysis_reports SET status = 'PENDING_APPROVAL' WHERE status IS NULL;
