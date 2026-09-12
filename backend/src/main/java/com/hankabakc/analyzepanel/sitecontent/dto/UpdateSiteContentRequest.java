package com.hankabakc.analyzepanel.sitecontent.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * UpdateSiteContentRequest: Yöneticinin değiştirdiği alanlar (anahtar → yeni değer) (T-063A).
 * Anahtarların izin listesinde olup olmadığı ve uzunlukları sunucuda denetlenir (ENG-12 §2.1, §2.3).
 */
public record UpdateSiteContentRequest(
        @NotNull(message = "Değiştirilecek alanlar gönderilmelidir.")
        Map<String, String> values
) {
}
