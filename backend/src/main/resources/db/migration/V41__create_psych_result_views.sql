-- V41: Psikolojik Test Tamamlanma Bildirimi ("Görüldü" İzleme Tablosu) (T-062)
-- APP-03 §4: Özel Nitelikli Kişisel Veri Açık İstisnası
-- ENG-08 §3.2: Geriye dönük uyumlu şema genişletmesi
-- Görüldü bilgisi kişi başına bağımsız tutulur; bir kullanıcının görmesi diğerininkini etkilemez.

CREATE TABLE IF NOT EXISTS psych_result_views (
    assignment_id UUID NOT NULL REFERENCES psych_test_assignments(id) ON DELETE CASCADE,
    viewer_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (assignment_id, viewer_id)
);

CREATE INDEX IF NOT EXISTS idx_psych_result_views_viewer ON psych_result_views(viewer_id);
