-- T-030E: exam_summaries.exam_date metin sütunundan gerçek tarih tipine geçer.
--
-- Sorun: sütun VARCHAR(50) ve içerik 'dd.MM.yyyy' biçimindeydi. "ORDER BY exam_date"
-- metin sıralaması yapıyordu; 09.02.2026 satırı 11.01.2026'dan önce geliyordu.
-- Sınav tarihçesi tablosu ve "Sınav Net Gelişimi" grafiği bu yüzden yanlış sırada çiziliyordu.
--
-- Dönüştürülemeyen bir satır varsa to_date hata verir ve migrasyon durur; bozuk tarih
-- sessizce atılmaz (APP-01 §2.7).
ALTER TABLE exam_summaries
    ALTER COLUMN exam_date TYPE date
    USING to_date(exam_date, 'DD.MM.YYYY');
