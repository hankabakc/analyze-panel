-- V3: Öğretmen ve Öğrenci arasındaki Many-to-Many hiyerarşisini kuran tablo.
-- Bu tablo, bir öğrencinin birden fazla öğretmeni, bir öğretmenin de birden fazla öğrencisi olmasını sağlar.

CREATE TABLE student_teacher_pairings (
    student_id UUID NOT NULL,
    teacher_id UUID NOT NULL,
    paired_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- Aynı öğrenci ve öğretmen arasındaki mükerrer eşleşmeyi engellemek için composite primary key.
    PRIMARY KEY (student_id, teacher_id),
    
    -- İlişkisel bütünlük: Kullanıcı silindiğinde eşleşme de silinir (ON DELETE CASCADE).
    CONSTRAINT fk_student FOREIGN KEY (student_id) REFERENCES app_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_teacher FOREIGN KEY (teacher_id) REFERENCES app_users(id) ON DELETE CASCADE
);

-- Performans için index tanımları (Sorgularda hız kazandırır).
CREATE INDEX idx_pairings_student ON student_teacher_pairings(student_id);
CREATE INDEX idx_pairings_teacher ON student_teacher_pairings(teacher_id);
