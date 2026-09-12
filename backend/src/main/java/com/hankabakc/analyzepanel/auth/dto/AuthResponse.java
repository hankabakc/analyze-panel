package com.hankabakc.analyzepanel.auth.dto;

/**
 * AuthResponse: Başarılı giriş/kayıt sonrası dönen cevap nesnesi.
 */
public record AuthResponse(
    String token, 
    UserDto user, // Entity yerine DTO kullanıldı (Güvenlik)
    String fullName, 
    String role
) {}

