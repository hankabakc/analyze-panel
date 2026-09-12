package com.hankabakc.analyzepanel.analysis.service;

import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * ExamDate: Karnedeki sınav tarihinin metin biçimi ile veritabanındaki tarih tipi arasındaki tek dönüşüm noktası (T-030E).
 *
 * <p>Mühendislik Standartları:</p>
 * <ul>
 *   <li>ENG-01 §1.2 (DRY): Biçim ("dd.MM.yyyy") tek bir yerde tanımlıdır. Dört çağrı yeri
 *       (bir yazma, üç okuma) buradan geçer.</li>
 *   <li>APP-01 §2.7: Ayrıştırılamayan tarih sessizce geçilmez, istisna fırlatılır. Eskiden
 *       bozuk bir tarih metin olarak saklanıp yanlış sıralanıyordu.</li>
 *   <li>APP-01 §2.1: Biçimlendirme sunucuda yapılır; istemci tarih ayrıştırmaz.</li>
 * </ul>
 */
public final class ExamDate {

    /** Karnede ve arayüzde kullanılan görüntüleme biçimi. Veritabanı tipi artık `date`. */
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private ExamDate() {
    }

    /**
     * parse: Karneden okunan "dd.MM.yyyy" metnini tarihe çevirir.
     *
     * @param text Karnedeki tarih metni (null olabilir)
     * @return Karşılık gelen tarih, metin boşsa null
     * @throws IllegalArgumentException Metin bu biçimde ayrıştırılamıyorsa
     */
    public static LocalDate parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim(), DISPLAY);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Sınav tarihi okunamadı: " + text, e);
        }
    }

    /**
     * format: Veritabanından gelen tarih değerini arayüzün beklediği "dd.MM.yyyy" metnine çevirir.
     *
     * <p>Native sorgular sütunu {@link java.sql.Date} olarak döndürür; sürücü değişirse
     * {@link LocalDate} de gelebilir, ikisi de karşılanır.</p>
     *
     * @param value Sorgudan gelen tarih değeri (null olabilir)
     * @return Görüntülenecek metin, değer boşsa null
     */
    public static String format(Object value) {
        LocalDate date = switch (value) {
            case null -> null;
            case LocalDate localDate -> localDate;
            case Date sqlDate -> sqlDate.toLocalDate();
            default -> throw new IllegalArgumentException(
                    "Beklenmeyen sınav tarihi tipi: " + value.getClass().getName());
        };
        return date == null ? null : date.format(DISPLAY);
    }
}
