-- V30: app_users tablosundaki status sütununun DEFAULT 'PENDING' kısıtlamasını kaldırır (T-035A).
-- T-035 ile onay bekleyen kullanıcı akışı kaldırıldığından, statü her zaman kod tarafından açıkça belirlenmelidir.
ALTER TABLE app_users ALTER COLUMN status DROP DEFAULT;
