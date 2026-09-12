-- T-037 (S-017 → B): Yönetici "kim ne kadar aktif" ve "öğretmen öğrencisinin verisine baktı mı"
-- sorularını cevaplayabilsin diye iki şey kaydedilir: son giriş zamanı ve rapor görüntülemeleri.

-- 1) Son giriş zamanı. Varsayılan yok; hiç girmemiş kullanıcıda NULL kalır ve arayüzde
--    "hiç giriş yapmadı" olarak gösterilir. Sıfır tarih uydurulmaz (IST-02 §2).
ALTER TABLE app_users ADD COLUMN last_login_at TIMESTAMPTZ;

-- 2) Rapor görüntüleme kaydı: kim, hangi öğrencinin, hangi raporuna, ne zaman baktı.
--    Kullanıcı veya rapor silinirse kayıt da silinir (ON DELETE CASCADE) — silinen öğrencinin
--    verisi geride kalmaz (APP-03 §4).
CREATE TABLE report_views (
    id          UUID PRIMARY KEY,
    viewer_id   UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    student_id  UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    report_id   UUID NOT NULL REFERENCES analysis_reports(id) ON DELETE CASCADE,
    viewed_at   TIMESTAMPTZ NOT NULL
);

-- Yönetici ekranı "bu öğretmen bu öğrenciye baktı mı / en son ne zaman" sorusunu sorar.
CREATE INDEX idx_report_views_viewer_student ON report_views (viewer_id, student_id, viewed_at DESC);
-- Saklama süresi dolan kayıtların temizlenmesi için (süre S-005 ile birlikte kararlaştırılacak).
CREATE INDEX idx_report_views_viewed_at ON report_views (viewed_at);
