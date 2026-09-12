package com.hankabakc.analyzepanel.psychtest.service;

import com.hankabakc.analyzepanel.psychtest.dto.PsychTestItemDto;
import com.hankabakc.analyzepanel.psychtest.dto.PsychTestOptionDto;
import com.hankabakc.analyzepanel.psychtest.dto.PsychTestScaleInfoDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * StaiScaleDefinition: STAI (State-Trait Anxiety Inventory - Durumluk ve Sürekli Kaygı Envanteri)
 * 40 maddelik ölçek tanımı, ters maddeler ve puanlama algoritması.
 * 
 * Puanlama Kuralı (PLAN.md T-052A):
 * - Madde 1-20: Durumluk Kaygı (State Anxiety). Sabit = 50. Ters Maddeler: 1, 2, 5, 8, 10, 11, 15, 16, 19, 20.
 * - Madde 21-40: Sürekli Kaygı (Trait Anxiety). Sabit = 35. Ters Maddeler: 21, 26, 27, 30, 33, 36, 39.
 * - Puan = (Doğrudan maddeler toplamı) - (Ters maddeler toplamı) + Sabit.
 * - Puan aralığı: 20 - 80.
 */
@Component
public class StaiScaleDefinition {

    public static final String TEST_CODE = "STAI";
    public static final String TEST_TITLE = "STAI (Durumluk - Sürekli Kaygı Envanteri)";
    public static final String TEST_DESCRIPTION = "Öğrencinin anlık (durumluk) ve genel (sürekli) kaygı düzeylerini ölçmeye yönelik 40 maddelik psikolojik değerlendirme envanteri.";

    public static final Set<Integer> STATE_REVERSE_ITEMS = Set.of(1, 2, 5, 8, 10, 11, 15, 16, 19, 20);
    public static final Set<Integer> TRAIT_REVERSE_ITEMS = Set.of(21, 26, 27, 30, 33, 36, 39);

    public static final int STATE_CONSTANT = 50;
    public static final int TRAIT_CONSTANT = 35;

    private static final String[] ITEM_TEXTS = {
        /* 1 */ "Şu anda sakinim",
        /* 2 */ "Kendimi emniyette hissediyorum",
        /* 3 */ "Şu anda sinirlerim gergin",
        /* 4 */ "Pişmanlık duyuyorum",
        /* 5 */ "Kendimi huzurlu hissediyorum",
        /* 6 */ "Şu anda hiç keyfim yok",
        /* 7 */ "Başıma geleceklerden endişe duyuyorum",
        /* 8 */ "Kendimi dinlenmiş hissediyorum",
        /* 9 */ "Şu anda kaygılıyım",
        /* 10 */ "Kendimi rahat hissediyorum",
        /* 11 */ "Kendime güvenim var",
        /* 12 */ "Şu anda asabım bozuk",
        /* 13 */ "Çok heyecanlıyım",
        /* 14 */ "Kendimi şaşırmış hissediyorum",
        /* 15 */ "Kendimi gevşemiş hissediyorum",
        /* 16 */ "Şu anda hayatımdan memnunum",
        /* 17 */ "Şu anda endişeliyim",
        /* 18 */ "Kendimi allak bullak hissediyorum",
        /* 19 */ "Şu anda neşeliyim",
        /* 20 */ "Şu anda keyfim yerinde",
        /* 21 */ "Genellikle keyfim yerindedir",
        /* 22 */ "Genellikle çabuk yorulurum",
        /* 23 */ "Genellikle kolayca ağlarım",
        /* 24 */ "Başkaları kadar mutlu olmak isterdim",
        /* 25 */ "Çabuk karar veremediğim için fırsatları kaçırırım",
        /* 26 */ "Kendimi dinlenmiş hissederim",
        /* 27 */ "Genellikle sakin, kendime hakim ve soğukkanlıyım",
        /* 28 */ "Güçlüklerin üstesinden gelemeyeceğim kadar biriktiğini hissederim",
        /* 29 */ "Önemsiz şeyler hakkında çok endişelenirim",
        /* 30 */ "Genellikle mutluyumdur",
        /* 31 */ "Her şeyi ciddiye alır ve dert edinirim",
        /* 32 */ "Kendime güvenim azdır",
        /* 33 */ "Genellikle kendimi emniyette hissederim",
        /* 34 */ "Güçlüklerle karşılaşmaktan kaçınırım",
        /* 35 */ "Kendimi kederli hissederim",
        /* 36 */ "Genellikle hayatımdan memnunumdur",
        /* 37 */ "Önemsiz düşünceler beni rahatsız eder",
        /* 38 */ "Hayal kırıklıklarını o kadar ciddiye alırım ki unutamam",
        /* 39 */ "Aklı başında bir insanım",
        /* 40 */ "Son zamanlarda kafama takılan konular beni tedirgin eder"
    };

    private static final List<PsychTestOptionDto> STATE_OPTIONS = List.of(
            new PsychTestOptionDto(1, "Hiç"),
            new PsychTestOptionDto(2, "Biraz"),
            new PsychTestOptionDto(3, "Çok"),
            new PsychTestOptionDto(4, "Tamamıyla")
    );

    private static final List<PsychTestOptionDto> TRAIT_OPTIONS = List.of(
            new PsychTestOptionDto(1, "Hemen hiçbir zaman"),
            new PsychTestOptionDto(2, "Bazen"),
            new PsychTestOptionDto(3, "Çok zaman"),
            new PsychTestOptionDto(4, "Hemen her zaman")
    );

    /**
     * getScaleInfo: STAI ölçeğinin 40 maddesini ve seçeneklerini içeren bilgi paketini döndürür.
     */
    public PsychTestScaleInfoDto getScaleInfo() {
        List<PsychTestItemDto> items = new ArrayList<>(40);
        for (int i = 1; i <= 40; i++) {
            String text = ITEM_TEXTS[i - 1];
            String section = (i <= 20) ? "STATE" : "TRAIT";
            List<PsychTestOptionDto> options = (i <= 20) ? STATE_OPTIONS : TRAIT_OPTIONS;
            items.add(new PsychTestItemDto(i, text, section, options));
        }
        return new PsychTestScaleInfoDto(TEST_CODE, TEST_TITLE, TEST_DESCRIPTION, Collections.unmodifiableList(items));
    }

    /**
     * calculateStateScore: Madde 1-20 arasındaki cevaplara göre Durumluk Kaygı Puanını hesaplar.
     * Formül: Doğrudan Maddeler Toplamı - Ters Maddeler Toplamı + 50
     */
    public int calculateStateScore(Map<Integer, Integer> answers) {
        int directSum = 0;
        int reverseSum = 0;

        for (int itemNo = 1; itemNo <= 20; itemNo++) {
            Integer ans = answers.get(itemNo);
            if (ans == null) {
                throw new IllegalArgumentException(itemNo + ". madde cevabı eksik.");
            }
            if (STATE_REVERSE_ITEMS.contains(itemNo)) {
                reverseSum += ans;
            } else {
                directSum += ans;
            }
        }

        return directSum - reverseSum + STATE_CONSTANT;
    }

    /**
     * calculateTraitScore: Madde 21-40 arasındaki cevaplara göre Sürekli Kaygı Puanını hesaplar.
     * Formül: Doğrudan Maddeler Toplamı - Ters Maddeler Toplamı + 35
     */
    public int calculateTraitScore(Map<Integer, Integer> answers) {
        int directSum = 0;
        int reverseSum = 0;

        for (int itemNo = 21; itemNo <= 40; itemNo++) {
            Integer ans = answers.get(itemNo);
            if (ans == null) {
                throw new IllegalArgumentException(itemNo + ". madde cevabı eksik.");
            }
            if (TRAIT_REVERSE_ITEMS.contains(itemNo)) {
                reverseSum += ans;
            } else {
                directSum += ans;
            }
        }

        return directSum - reverseSum + TRAIT_CONSTANT;
    }
}
