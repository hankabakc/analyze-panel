-- T-042: audit_logs düz metin e-posta taşımasın (user_id UUID ve PII temizliği).
--
-- app_users.email alanı PiiConverter (AES-256) ile şifreli tutulurken,
-- audit_logs.user_email alanına düz metin e-posta yazılması APP-01 §2.2 ve
-- APP-03 §4 (18 yaş altı veri minimizasyonu) kurallarını deliyordu.
--
-- Bu göç ile:
-- 1. audit_logs tablosuna user_id UUID sütunu ve indeksi eklenir.
-- 2. CASCADE KESİNLİKLE KULLANILMAZ: Kullanıcı silinse bile denetim kayıtları
--    silinmez (APP-03 §4: "kim gördü" sorusu cevapsız kalamaz).
-- 3. user_email sütunu NULL kabul edecek şekilde güncellenir.
-- 4. Mevcut satırlarda çözülebilen sistem kullanıcıları için user_id doldurulur.
-- 5. @ içeren tüm gerçek e-postalar temizlenir (NULL yapılır);
--    ANONYMOUS, user, anonymousUser gibi PII olmayan yer tutucular korunur.
-- 6. HİÇBİR SATIR SİLİNMEZ; koşu öncesi ve sonrası satır sayısı aynı kalır.

-- 1. user_id sütunu eklenir
ALTER TABLE audit_logs ADD COLUMN user_id UUID;

-- 2. İndeks oluşturulur
CREATE INDEX idx_audit_logs_user_id ON audit_logs (user_id);

-- 3. user_email sütunu nullable yapılır
ALTER TABLE audit_logs ALTER COLUMN user_email DROP NOT NULL;

-- 4. Mevcut sistem kullanıcılarının user_id değerleri doldurulur
UPDATE audit_logs SET user_id = 'c76bd5bb-5fdd-466a-9e6a-f1904a2e4aae'::uuid WHERE user_email = 'admin@admin.com';
UPDATE audit_logs SET user_id = 'e7098851-d9c0-4449-b590-320033280ca1'::uuid WHERE user_email = 'canberk.yildiz@analyzepanel.local';
UPDATE audit_logs SET user_id = 'b9a17326-0ec8-4dad-a771-c47416ec4571'::uuid WHERE user_email = 'ayse.ozturk@analyzepanel.local';
UPDATE audit_logs SET user_id = '2745ccf3-17ba-4dec-8291-dcf834b67292'::uuid WHERE user_email = 'bugra.han.kabakci@analyzepanel.local';

-- 5. Gerçek e-postalar temizlenir (@ içeren tüm değerler NULL yapılır)
UPDATE audit_logs SET user_email = NULL WHERE user_email LIKE '%@%';
