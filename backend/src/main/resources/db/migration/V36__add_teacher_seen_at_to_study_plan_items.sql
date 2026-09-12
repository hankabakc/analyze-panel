-- V36: Calisma Plani Kalemlerine Ogretmen Goruldu Zaman Damgasi (T-050D)
-- Ogrencinin tamamladigi gorevlerin ogretmen tarafindan gorulup gorulmedigini takip eder.

ALTER TABLE study_plan_items ADD COLUMN teacher_seen_at TIMESTAMP WITH TIME ZONE NULL;

CREATE INDEX idx_study_plan_items_unseen ON study_plan_items(plan_id, completed_at, teacher_seen_at);
