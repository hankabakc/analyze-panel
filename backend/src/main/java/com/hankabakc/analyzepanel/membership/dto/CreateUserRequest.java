package com.hankabakc.analyzepanel.membership.dto;

import com.hankabakc.analyzepanel.auth.enums.UserRole;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * CreateUserRequest: Yöneticinin yeni öğretmen veya öğrenci hesabı açması için veri transfer nesnesi (DTO).
 * T-056: Telefon alanı sistemden tamamen kaldırılmıştır.
 */
public record CreateUserRequest(
    @NotBlank(message = "Ad soyad boş bırakılamaz")
    String fullName,

    @NotNull(message = "Rol seçimi zorunludur")
    UserRole role,

    @Min(value = 5, message = "Sınıf seviyesi en az 5 olmalıdır.")
    @Max(value = 12, message = "Sınıf seviyesi en fazla 12 olabilir.")
    Integer grade
) {
}
