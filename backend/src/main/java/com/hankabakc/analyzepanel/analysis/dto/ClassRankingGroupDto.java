package com.hankabakc.analyzepanel.analysis.dto;

import java.util.List;
import java.util.UUID;

/**
 * ClassRankingGroupDto: Sınıf bazlı sıralama grubu (T-038B).
 *
 * <p>Her grup adlandırılmış bir sınıfa (örn. "12-A") veya henüz bir sınıfa
 * atanmamış öğrenciler için "Sınıfsız" grubuna karşılık gelir.
 * Sıra numaraları (rank) her grup içinde bağımsız olarak 1'den başlar.</p>
 *
 * @param classId Sınıf kimliği (sınıfsız öğrenciler için {@code null})
 * @param className Sınıf adı (örn. "12-A" veya "Sınıfsız")
 * @param entries Grup içindeki sıralama satırları
 */
public record ClassRankingGroupDto(
        UUID classId,
        String className,
        List<RankingEntryDto> entries
) {}
