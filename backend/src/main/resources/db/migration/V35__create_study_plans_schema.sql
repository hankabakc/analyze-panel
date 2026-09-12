-- V35: Çalışma Planı (Study Plans) Şeması
-- Öğretmenin öğrenciye konu bazlı soru hedefi ve teslim tarihi ataması (T-050A)

CREATE TABLE study_plans (
    id UUID PRIMARY KEY,
    student_id UUID NOT NULL,
    teacher_id UUID NOT NULL,
    due_date DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_study_plans_student FOREIGN KEY (student_id) REFERENCES app_users(id),
    CONSTRAINT fk_study_plans_teacher FOREIGN KEY (teacher_id) REFERENCES app_users(id)
);

CREATE INDEX idx_study_plans_student_id ON study_plans(student_id);
CREATE INDEX idx_study_plans_teacher_id ON study_plans(teacher_id);

CREATE TABLE study_plan_items (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL,
    topic_name VARCHAR(255) NOT NULL,
    question_count INTEGER NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT fk_study_plan_items_plan FOREIGN KEY (plan_id) REFERENCES study_plans(id) ON DELETE CASCADE
);

CREATE INDEX idx_study_plan_items_plan_id ON study_plan_items(plan_id);
