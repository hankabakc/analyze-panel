package com.hankabakc.analyzepanel.auth.dto;

import com.hankabakc.analyzepanel.auth.enums.UserRole;
import com.hankabakc.analyzepanel.auth.enums.UserStatus;
import java.util.UUID;

/**
 * UserDto: API üzerinden dış dünyaya açılan güvenli kullanıcı veri modelidir.
 * 
 * <p>Mühendislik Standartları:</p>
 * <ul>
 *   <li>OWASP 2026 / T-016 / S-010: Sensitive Data Exposure (A02) riskini engellemek için telefon bilgisi dönülmez;
 *       öğrencilere ait sınıf seviyesi bilgisi (grade: 5-12) eklenmiştir.</li>
 *   <li>T-009 / K5: mustChangePassword alanı ile ilk girişte şifre değiştirme zorunluluğu istemciye iletilir.</li>
 *   <li>T-041B / S-018: currentPasswordRequired alanı ile mevcut şifre zorunluluğu sunucudan istemciye bildirilir (ENG-11 §3.3).</li>
 * </ul>
 */
public record UserDto(
    UUID id,
    String email,
    String fullName,
    UserRole role,
    UserStatus status,
    Integer grade,
    boolean mustChangePassword,
    boolean currentPasswordRequired
) {
    public UserDto(UUID id, String email, String fullName, UserRole role, UserStatus status, Integer grade) {
        this(id, email, fullName, role, status, grade, false, true);
    }

    public UserDto(UUID id, String email, String fullName, UserRole role, UserStatus status, Integer grade, boolean mustChangePassword) {
        this(id, email, fullName, role, status, grade, mustChangePassword, true);
    }
}
