-- V14: Raporların Kümülatif Analiz Durumunu Takip Etme
ALTER TABLE analysis_reports ADD COLUMN cumulative_status VARCHAR(20) DEFAULT 'NOT_INCLUDED'; -- INCLUDED, NOT_INCLUDED
