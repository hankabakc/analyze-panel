-- V7: 5'li deneme özeti için tablo eklenmesi
CREATE TABLE exam_summaries (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL,
    exam_name VARCHAR(255),
    exam_date VARCHAR(50),
    total_score DECIMAL(5,2),
    CONSTRAINT fk_exam_report FOREIGN KEY (report_id) REFERENCES analysis_reports(id) ON DELETE CASCADE
);

CREATE INDEX idx_exam_summaries_report ON exam_summaries(report_id);
