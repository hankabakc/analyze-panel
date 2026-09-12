package com.hankabakc.analyzepanel.membership.dto;

import com.hankabakc.analyzepanel.auth.dto.UserDto;

/**
 * ResetPasswordResponse: Yöneticinin kullanıcı şifresini sıfırlaması sonucu dönen özel DTO nesnesidir (T-013).
 * 
 * <p>Mühendislik ve Güvenlik Standartları:</p>
 * <ul>
 *   <li>APP-01 §2.2 & ENG-10 §1: Üretilen geçici yeni şifre (generatedPassword) yalnızca sıfırlama anında tek seferlik
 *       bu DTO ile yöneticinin istemcisine iletilir; UserDto içine konulmaz, veritabanında düz metin tutulmaz ve loglanmaz.</li>
 *   <li>ENG-11 §1.1 & K5: Kullanıcının mustChangePassword bayrağı true olarak güncellenir.</li>
 * </ul>
 */
public record ResetPasswordResponse(
        UserDto user,
        String generatedPassword
) {
}
