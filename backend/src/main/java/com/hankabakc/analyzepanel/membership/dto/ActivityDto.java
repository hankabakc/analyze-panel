package com.hankabakc.analyzepanel.membership.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * ActivityDto: Yöneticinin aktiflik ekranı için tek bir kullanıcının özeti (T-037).
 *
 * <p>APP-01 §2.2 / S-008: Yanıtta telefon numarası yer almaz. Yönetici için e-posta istisnası
 * bu ekranda kullanılmıyor; aktiflik sorusu için ad ve rol yeterli.</p>
 *
 * @param lastLoginAt Hiç giriş yapılmadıysa {@code null} — "hiç girmedi" bilgisi uydurulmaz
 * @param viewedStudents Yalnızca öğretmenlerde dolu: eşleştiği öğrenciler ve her birine son bakış
 */
public record ActivityDto(
    UUID userId,
    String fullName,
    String role,
    Instant lastLoginAt,
    long reportViewCount,
    Instant lastReportViewAt,
    List<StudentViewDto> viewedStudents
) {
    /**
     * StudentViewDto: Öğretmenin eşleştiği bir öğrenci ve o öğrencinin verisine son bakış zamanı.
     *
     * @param lastViewedAt Öğretmen bu öğrencinin hiçbir raporunu açmadıysa {@code null}
     */
    public record StudentViewDto(UUID studentId, String studentName, Instant lastViewedAt) {}
}
