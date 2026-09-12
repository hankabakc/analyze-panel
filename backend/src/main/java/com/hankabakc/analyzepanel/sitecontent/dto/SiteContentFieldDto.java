package com.hankabakc.analyzepanel.sitecontent.dto;

/**
 * SiteContentFieldDto: Doldurulabilir bir alanın anahtarı ve azami uzunluğu (T-063A).
 * Yönetici editörü sınırları buradan okur (APP-01 §2.1).
 */
public record SiteContentFieldDto(String key, int maxLength) {
}
