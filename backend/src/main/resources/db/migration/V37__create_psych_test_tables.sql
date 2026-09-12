-- T-052A: Psikolojik Ölçek (STAI) Atama ve Cevap Tabloları
-- APP-03 §4: Özel Nitelikli Kişisel Veri (Ruh Sağlığı / Kaygı Ölçeği) Açık İstisnası

CREATE TABLE IF NOT EXISTS psych_test_assignments (
    id UUID PRIMARY KEY,
    test_code VARCHAR(50) NOT NULL,
    student_id UUID NOT NULL REFERENCES app_users(id),
    assigned_by UUID NOT NULL REFERENCES app_users(id),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    completed_at TIMESTAMPTZ NULL,
    state_score INTEGER NULL,
    trait_score INTEGER NULL
);

CREATE INDEX IF NOT EXISTS idx_psych_assignments_student_status ON psych_test_assignments(student_id, status);
CREATE INDEX IF NOT EXISTS idx_psych_assignments_test_code ON psych_test_assignments(test_code);

CREATE TABLE IF NOT EXISTS psych_test_responses (
    id UUID PRIMARY KEY,
    assignment_id UUID NOT NULL REFERENCES psych_test_assignments(id) ON DELETE CASCADE,
    item_no INTEGER NOT NULL,
    answer INTEGER NOT NULL,
    CONSTRAINT uq_psych_responses_assignment_item UNIQUE (assignment_id, item_no),
    CONSTRAINT chk_psych_responses_item_no CHECK (item_no >= 1 AND item_no <= 40),
    CONSTRAINT chk_psych_responses_answer CHECK (answer >= 1 AND answer <= 4)
);

CREATE INDEX IF NOT EXISTS idx_psych_responses_assignment_id ON psych_test_responses(assignment_id);
