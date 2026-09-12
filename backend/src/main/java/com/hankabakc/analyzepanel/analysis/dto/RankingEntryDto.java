package com.hankabakc.analyzepanel.analysis.dto;

/**
 * RankingEntryDto: Sıralama tablosunun tek satırı (T-038).
 *
 * <p><b>Bu kayıtta kimlik taşıyan hiçbir alan yoktur.</b> Öğrenci `id`'si, e-postası, telefonu veya
 * okulu buraya konulmaz (APP-02 §1, APP-01 §2.2). Görüntülenecek ad sunucuda karara bağlanır:
 * bakan kişinin görmeye hakkı olmadığı satırlarda {@code displayName} "3. Öğrenci" gibi
 * <b>sıraya bağlı</b> bir etikettir; kişiye bağlı kalıcı bir takma ad değildir (S-003 → Karar 2).</p>
 *
 * @param rank Sıradaki yeri (1'den başlar)
 * @param displayName Gerçek ad veya sıraya bağlı maskeli etiket — kararı sunucu verir
 * @param averageNet Onaylı denemelerdeki net ortalaması
 * @param examCount Ortalamaya giren deneme sayısı
 * @param isSelf Bu satır, isteği yapan öğrencinin kendisi mi
 */
public record RankingEntryDto(
    int rank,
    String displayName,
    double averageNet,
    int examCount,
    boolean isSelf
) {}
