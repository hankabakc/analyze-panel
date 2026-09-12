-- V11: 2025 LGS Taban Puanları ve Referans Okul Kütüphanesi (Düzeltilmiş)
CREATE TABLE IF NOT EXISTS reference_schools (
    id UUID PRIMARY KEY,
    city VARCHAR(50) NOT NULL,
    school_name VARCHAR(255) NOT NULL,
    school_type VARCHAR(100), -- Fen Lisesi, Anadolu Lisesi vb.
    base_score DECIMAL(7,4), -- DÜZELTİLDİ: 500.0000 gibi değerleri desteklemesi için DECIMAL(7,4) yapıldı
    percentile DECIMAL(5,2), -- 2025 Yüzdelik Dilimi
    last_updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Örnek veri seti
INSERT INTO reference_schools (id, city, school_name, school_type, base_score, percentile) VALUES
(gen_random_uuid(), 'İstanbul', 'İstanbul Erkek Lisesi', 'Anadolu Lisesi (Almanca)', 500.0000, 0.04),
(gen_random_uuid(), 'İstanbul', 'Kabataş Erkek Lisesi', 'Anadolu Lisesi (Almanca)', 500.0000, 0.05),
(gen_random_uuid(), 'İstanbul', 'Kabataş Erkek Lisesi', 'Anadolu Lisesi (İngilizce)', 497.4200, 0.05),
(gen_random_uuid(), 'Ankara', 'Ankara Fen Lisesi', 'Fen Lisesi', 494.5096, 0.05),
(gen_random_uuid(), 'İstanbul', 'Atatürk Fen Lisesi', 'Fen Lisesi', 493.9643, 0.06),
(gen_random_uuid(), 'İzmir', 'İzmir Fen Lisesi', 'Fen Lisesi', 493.9643, 0.07),
(gen_random_uuid(), 'İstanbul', 'Cağaloğlu Anadolu Lisesi', 'Anadolu Lisesi', 491.6500, 0.11),
(gen_random_uuid(), 'Bursa', 'Tofaş Fen Lisesi', 'Fen Lisesi', 489.3815, 0.15),
(gen_random_uuid(), 'İstanbul', 'Kadıköy Anadolu Lisesi', 'Anadolu Lisesi', 485.2804, 0.35),
(gen_random_uuid(), 'Ankara', 'Ankara Atatürk Anadolu Lisesi', 'Anadolu Lisesi', 482.1500, 0.45),
(gen_random_uuid(), 'İzmir', 'Bornova Anadolu Lisesi', 'Anadolu Lisesi', 480.9000, 0.52),
(gen_random_uuid(), 'İstanbul', 'Beşiktaş Anadolu Lisesi', 'Anadolu Lisesi', 478.3358, 0.65),
(gen_random_uuid(), 'Adana', 'Adana Fen Lisesi', 'Fen Lisesi', 488.2000, 0.18),
(gen_random_uuid(), 'Antalya', 'Antalya Fen Lisesi', 'Fen Lisesi', 487.5000, 0.22),
(gen_random_uuid(), 'Kayseri', 'Kayseri Fen Lisesi', 'Fen Lisesi', 485.1000, 0.38),
(gen_random_uuid(), 'Kocaeli', 'Kocaeli Fen Lisesi', 'Fen Lisesi', 484.4000, 0.42),
(gen_random_uuid(), 'Samsun', 'Samsun Garip Zeycan Yıldırım Fen Lisesi', 'Fen Lisesi', 483.9000, 0.48),
(gen_random_uuid(), 'Eskişehir', 'Eskişehir Fatih Fen Lisesi', 'Fen Lisesi', 482.7000, 0.55),
(gen_random_uuid(), 'Denizli', 'Erbakır Fen Lisesi', 'Fen Lisesi', 481.5000, 0.62),
(gen_random_uuid(), 'Gaziantep', 'Vehbi Dinçerler Fen Lisesi', 'Fen Lisesi', 480.2000, 0.75);
