package com.hankabakc.analyzepanel.membership.dto;

import com.hankabakc.analyzepanel.auth.dto.UserDto;

/**
 * CreatedUserResponse: Yöneticinin yeni bir kullanıcı oluşturması sonucunda dönen özel DTO nesnesidir (T-010 / K3).
 * 
 * <p>Mühendislik Standartları:</p>
 * <ul>
 *   <li>APP-01 §2.2 & K3: Üretilen geçici ilk şifre (generatedPassword) yalnızca oluşturma anında tek seferlik
 *       bu DTO ile döndürülür; UserDto içine konulmaz ve veritabanında düz metin olarak saklanmaz.</li>
 *   <li>ENG-11 §1.1 & K5: Yeni kullanıcı mustChangePassword = true olarak doğar.</li>
 * </ul>
 */
public record CreatedUserResponse(
        UserDto user,
        String generatedPassword
) {
}
