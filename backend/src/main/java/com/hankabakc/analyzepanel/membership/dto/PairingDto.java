package com.hankabakc.analyzepanel.membership.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * PairingDto: Bir öğrenci ve öğretmen arasındaki aktif eşleşme detaylarını taşıyan DTO yapısıdır (T-036).
 * Immutability için Java record tercih edilmiştir.
 * 
 * @param studentId   Eşleştirilmiş öğrencinin UUID'si
 * @param studentName Öğrencinin adı soyadı
 * @param studentEmail Öğrencinin e-posta adresi
 * @param studentGrade Öğrencinin sınıf seviyesi (5-12)
 * @param teacherId   Eşleştirilmiş öğretmenin UUID'si
 * @param teacherName Öğretmenin adı soyadı
 * @param teacherEmail Öğretmenin e-posta adresi
 * @param pairedAt    Eşleşmenin kurulduğu zaman damgası
 */
public record PairingDto(
    UUID studentId,
    String studentName,
    String studentEmail,
    Integer studentGrade,
    UUID teacherId,
    String teacherName,
    String teacherEmail,
    Instant pairedAt
) {}
