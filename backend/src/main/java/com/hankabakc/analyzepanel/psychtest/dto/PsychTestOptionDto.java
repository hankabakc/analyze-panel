package com.hankabakc.analyzepanel.psychtest.dto;

/**
 * PsychTestOptionDto: Ölçek maddesi seçenek değeri ve etiketini taşır.
 */
public record PsychTestOptionDto(
        int value,
        String label
) {
}
