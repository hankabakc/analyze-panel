package com.hankabakc.analyzepanel.psychtest.dto;

import java.util.List;
import java.util.UUID;

/**
 * MarkPsychResultsSeenRequest: Psikolojik test sonuçlarını görüldü olarak işaretleme isteği (T-062 / T-062B).
 * 
 * Standartlar:
 * - Immutability: DTO yapısı Java record tipindedir.
 *
 * @param studentId Sonuçları görüldü sayılacak öğrencinin kimliği (öğretmenler için zorunludur).
 * @param assignmentIds Yöneticinin ekranda listelenen ve görüldü işaretlemek istediği tamamlanmış atama kimlikleri
 *                      (yöneticiler için zorunludur; T-062B / T-062C).
 */
public record MarkPsychResultsSeenRequest(
        UUID studentId,
        List<UUID> assignmentIds
) {
    public MarkPsychResultsSeenRequest(UUID studentId) {
        this(studentId, null);
    }
}

