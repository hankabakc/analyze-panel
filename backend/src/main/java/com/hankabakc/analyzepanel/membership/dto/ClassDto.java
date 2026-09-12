package com.hankabakc.analyzepanel.membership.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * ClassDto: Adlandırılmış bir sınıf ve içindeki öğrenciler (T-040).
 *
 * <p>APP-01 §2.2: Yanıtta öğrencinin telefonu veya e-postası yer almaz; sınıf yönetimi için
 * ad ve seviye yeterlidir.</p>
 */
public record ClassDto(
    UUID id,
    String name,
    Instant createdAt,
    List<ClassStudentDto> students
) {
    /** ClassStudentDto: Sınıftaki bir öğrenci. {@code grade} T-016'daki seviye bilgisidir. */
    public record ClassStudentDto(UUID id, String fullName, Integer grade) {}
}
