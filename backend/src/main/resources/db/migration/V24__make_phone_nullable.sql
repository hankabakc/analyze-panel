-- V24: Telefonu olmayan öğrencilerin sisteme eklenebilmesi için phone_number sütunundaki NOT NULL kısıtını kaldırır (T-010 / K1, K2).
-- PostgreSQL'de NULL değerler UNIQUE kısıtını ihlal etmez, dolayısıyla birden çok telefonsuz kullanıcı güvenle eklenebilir.

ALTER TABLE app_users ALTER COLUMN phone_number DROP NOT NULL;
