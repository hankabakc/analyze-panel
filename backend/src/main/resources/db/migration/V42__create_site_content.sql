-- V42: Karşılama sitesi içerikleri (T-063A / S-026)
-- Yönetici panelinden doldurulan düz metin alanları. Hangi anahtarların var olabileceği ve azami
-- uzunlukları uygulamadaki izin listesinde (SiteContentFields) tek yerde tanımlıdır; tablo yalnızca
-- doldurulmuş değerleri tutar, doldurulmamış alan satır olarak bulunmaz.

CREATE TABLE site_content (
    content_key   VARCHAR(100) PRIMARY KEY,
    content_value TEXT NOT NULL DEFAULT '',
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    UUID REFERENCES app_users(id) ON DELETE SET NULL
);
