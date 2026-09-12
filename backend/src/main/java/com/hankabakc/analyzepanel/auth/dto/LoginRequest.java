package com.hankabakc.analyzepanel.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * LoginRequest: E-posta ve şifre ile oturum açma istek parametrelerini temsil eden değişmez (record) DTO'dur.
 * 
 * <p>Mühendislik Standartları:</p>
 * <ul>
 *   <li>T-009 / K1: Telefonu olmayan öğrencilerin e-posta + şifre ile giriş yapabilmesini sağlar.</li>
 *   <li>ENG-11 §1.2: Giriş hatalarında ayrıştırıcı olmayan tek hata mesajı dönülür.</li>
 *   <li>T-033: rememberMe isteğe bağlıdır; gönderilmezse veya null gelirse false sayılır.</li>
 * </ul>
 */
public record LoginRequest(
        @NotBlank(message = "E-posta alanı boş bırakılamaz.")
        String email,

        @NotBlank(message = "Şifre alanı boş bırakılamaz.")
        String password,

        Boolean rememberMe
) {
    /**
     * isRememberMe: Alan hiç gönderilmediğinde de {@code null} gönderildiğinde de {@code false} sayar.
     *
     * <p>T-033: Alan eskiden ilkel {@code boolean} idi; gövdede {@code "rememberMe": null} gelen istek
     * Jackson'da eşlenemeyip <b>HTTP 400</b> ile reddediliyordu. Alan hiç gönderilmediğinde
     * veya {@code null} geldiğinde güvenli varsayılan olarak {@code false} sayılır (ENG-07, ENG-03 §2).</p>
     */
    public boolean isRememberMe() {
        return Boolean.TRUE.equals(rememberMe);
    }
}
