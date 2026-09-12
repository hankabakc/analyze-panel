package com.hankabakc.analyzepanel.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * ChangePasswordRequest: Kullanıcının mevcut şifresini doğrulaması ve yeni şifresini belirlemesi
 * için kullanılan değişmez (record) DTO nesnesidir.
 * 
 * <p>Mühendislik Standartları:</p>
 * <ul>
 *   <li>ENG-11 §1.1 & NIST 800-63B (K2): Yeni şifre en az 12 karakter olmalıdır; anlamsız sembol/rakam
 *       karmaşıklık dayatması yapılmaz.</li>
 *   <li>İlk şifre tanımlamasında (şifresi olmayan kullanıcılar için) currentPassword null veya boş geçilebilir.</li>
 * </ul>
 */
public record ChangePasswordRequest(
        String currentPassword,

        @NotBlank(message = "Yeni şifre alanı zorunludur.")
        @Size(min = 12, message = "Yeni şifre en az 12 karakter olmalıdır.")
        String newPassword
) {
}
