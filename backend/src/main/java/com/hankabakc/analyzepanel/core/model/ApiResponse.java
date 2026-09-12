package com.hankabakc.analyzepanel.core.model;

import java.time.LocalDateTime;

/* 
   Tüm API cevapları için standart zarf yapısı.
   Immutability için Java record kullanılmıştır.
*/
public record ApiResponse<T>(
    boolean success,
    String message,
    T data,
    LocalDateTime timestamp
) {
    /* Başarılı cevaplar için yardımcı metot */
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, message, data, LocalDateTime.now());
    }

    /* Hatalı cevaplar için yardımcı metot */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, LocalDateTime.now());
    }
}
