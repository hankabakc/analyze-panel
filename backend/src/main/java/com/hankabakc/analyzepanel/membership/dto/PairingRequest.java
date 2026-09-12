package com.hankabakc.analyzepanel.membership.dto;

import java.util.UUID;

/**
 * PairingRequest: Bir öğrenci ve bir öğretmen arasındaki eşleşme isteğini taşıyan DTO yapısıdır.
 * Immutability için Java record tercih edilmiştir.
 */
public record PairingRequest(
    UUID studentId, // Eşlenecek öğrencinin UUID'si
    UUID teacherId  // Eşlenecek öğretmenin UUID'si
) {}
