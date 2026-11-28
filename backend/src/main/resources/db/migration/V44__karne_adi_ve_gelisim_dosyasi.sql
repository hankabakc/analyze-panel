-- Karne / gelişim dosyası sadeleştirmesi.
--
-- 1) Her yüklenen karnenin adı artık kapsadığı tarih aralığı ("Karne · 07.09 – 21.09.2026 (2 deneme)").
--    Eski ad "Birleştirilmiş Karne (N Deneme)", öğrencinin tüm karnelerini toplayan gelişim
--    dosyasıyla karışıyordu. Biçim SinavzaKarneParser.title ile aynıdır.
UPDATE analysis_reports ar
SET exam_title = 'Karne · '
    || CASE
         WHEN s.mn = s.mx THEN to_char(s.mn, 'DD.MM.YYYY')
         WHEN date_part('year', s.mn) = date_part('year', s.mx)
              THEN to_char(s.mn, 'DD.MM') || ' – ' || to_char(s.mx, 'DD.MM.YYYY')
         ELSE to_char(s.mn, 'DD.MM.YYYY') || ' – ' || to_char(s.mx, 'DD.MM.YYYY')
       END
    || CASE WHEN s.c > 1 THEN ' (' || s.c || ' deneme)' ELSE '' END
FROM (
    SELECT report_id, MIN(exam_date) AS mn, MAX(exam_date) AS mx, COUNT(*) AS c
    FROM exam_summaries
    WHERE exam_date IS NOT NULL
    GROUP BY report_id
) s
WHERE s.report_id = ar.id;

-- 2) "Gelişim dosyasına ekle" adımı kaldırıldı: onaylanan karne gelişim dosyasına zaten
--    kendiliğinden dahildi, bu sütun yalnızca bir etiketi değiştiriyordu.
ALTER TABLE analysis_reports DROP COLUMN cumulative_status;
