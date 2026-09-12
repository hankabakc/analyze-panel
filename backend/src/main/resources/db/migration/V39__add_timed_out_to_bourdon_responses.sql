-- T-053D: Bourdon testi süre aşımı göstergesi (süre dolduğunda öğrenci kilitlenmesini önleme)
-- S-021 / APP-01 §2.1: Süre aşılsa dahi teslim kabul edilir ve süre aşımı olarak işaretlenir.

ALTER TABLE bourdon_responses ADD COLUMN IF NOT EXISTS timed_out BOOLEAN NOT NULL DEFAULT FALSE;
