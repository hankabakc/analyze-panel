-- T-040: Yönetici adlandırılmış sınıf oluşturup öğrencileri sınıfa ekleyip çıkarabilsin.
--
-- Bugüne kadar "sınıf" yalnızca app_users.grade tam sayısıydı (5-12, T-016/S-010).
-- Bu göç onu DEĞİŞTİRMEZ: grade "kaçıncı sınıf" (seviye), buradaki classes ise
-- adlandırılmış gruptur (örn. "12-A"). İkisi yan yana yaşar.

CREATE TABLE classes (
    id         UUID PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

-- Aynı adda iki sınıf olmasın; yönetici hangi "12-A"ya eklediğini bilemez.
CREATE UNIQUE INDEX idx_classes_name_unique ON classes (LOWER(name));

-- Öğrencinin sınıfı. Bir öğrenci en fazla bir sınıfta olur.
--
-- ON DELETE SET NULL bilinçlidir: sınıf silindiğinde öğrenciler SİLİNMEZ, yalnızca
-- sınıfsız kalır. Öğrencinin karne/analiz verisi zaten bu sütuna bağlı değildir,
-- dolayısıyla sınıftan çıkarmak hiçbir analiz verisini etkilemez (T-040 çekirdek şartı).
ALTER TABLE app_users
    ADD COLUMN class_id UUID REFERENCES classes(id) ON DELETE SET NULL;

CREATE INDEX idx_app_users_class ON app_users (class_id);
