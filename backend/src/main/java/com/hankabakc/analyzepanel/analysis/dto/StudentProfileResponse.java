package com.hankabakc.analyzepanel.analysis.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * StudentProfileResponse: Öğrencinin hedefi (T-065).
 *
 * <p>Okul seçiliyse okulun adı ve (elle puan girilmemişse) taban puanı okul kaydından gelir.
 * Profil ve kullanıcı kimliği yanıta konmaz (APP-01 §2.2).</p>
 */
public record StudentProfileResponse(UUID targetSchoolId, String targetSchoolName, BigDecimal targetScore) {
}
