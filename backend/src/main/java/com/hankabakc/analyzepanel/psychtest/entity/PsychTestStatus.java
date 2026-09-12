package com.hankabakc.analyzepanel.psychtest.entity;

/**
 * PsychTestStatus: Psikolojik ölçek atamasının güncel durumunu belirtir.
 * PENDING: Öğrenciye atandı, henüz doldurulmadı.
 * COMPLETED: Öğrenci tarafından dolduruldu, sunucu tarafında puanlandı.
 */
public enum PsychTestStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED
}
