package com.hankabakc.analyzepanel.membership.dto;

import jakarta.validation.constraints.NotBlank;

/** CreateClassRequest: Yeni sınıf açma isteği (T-040). */
public record CreateClassRequest(
    @NotBlank(message = "Sınıf adı boş bırakılamaz.")
    String name
) {}
