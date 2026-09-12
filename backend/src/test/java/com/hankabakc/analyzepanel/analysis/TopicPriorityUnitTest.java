package com.hankabakc.analyzepanel.analysis;

import com.hankabakc.analyzepanel.analysis.dto.AnalysisResponse;
import com.hankabakc.analyzepanel.analysis.service.TopicPriority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopicPriorityUnitTest {

    private static final double PUAN_PER_NET = 4.44;

    @Test
    @DisplayName("T-026/T-030: byPoints sıralaması kaçırılan soru/puan sayısına göre azalan yapılır")
    void testByPoints_PrioritizesHigherLostPointsOverLowerRate() {
        // Konu A: 2 soruda 1 doğru (%50 başarı, 1 kayıp soru = ≈4.4 puan)
        // Konu B: 5 soruda 3 doğru (%60 başarı, 2 kayıp soru = ≈8.9 puan)
        AnalysisResponse.TopicDto topicA = new AnalysisResponse.TopicDto(
                UUID.randomUUID(), "Konu A", "WRONG", null, 2, 1, 1);
        AnalysisResponse.TopicDto topicB = new AnalysisResponse.TopicDto(
                UUID.randomUUID(), "Konu B", "WRONG", null, 5, 3, 2);

        AnalysisResponse.LessonDto lesson = new AnalysisResponse.LessonDto(
                UUID.randomUUID(), "Matematik", 4, 3, 0, BigDecimal.valueOf(57.1), List.of(topicA, topicB));

        AnalysisResponse.PriorityListsDto result = TopicPriority.of(List.of(lesson), PUAN_PER_NET);

        assertEquals(2, result.byPoints().size());
        assertEquals("Konu B", result.byPoints().get(0).topicName(), "Daha fazla puan kazandıracak konu (2 kayıp) ilk sırada olmalıdır.");
        assertEquals(8.9, result.byPoints().get(0).lostPoints());
        assertEquals(5, result.byPoints().get(0).totalQuestions());
        assertEquals(3, result.byPoints().get(0).correctCount());

        assertEquals("Konu A", result.byPoints().get(1).topicName());
        assertEquals(4.4, result.byPoints().get(1).lostPoints());
    }

    @Test
    @DisplayName("T-030: byRate sıralaması başarı oranına göre artan (en düşük oran en başa) yapılır")
    void testByRate_PrioritizesLowestSuccessRateFirst() {
        // Konu A: 2 soruda 1 doğru (%50 başarı, 1 kayıp soru)
        // Konu B: 5 soruda 3 doğru (%60 başarı, 2 kayıp soru)
        AnalysisResponse.TopicDto topicA = new AnalysisResponse.TopicDto(
                UUID.randomUUID(), "Konu A", "WRONG", null, 2, 1, 1);
        AnalysisResponse.TopicDto topicB = new AnalysisResponse.TopicDto(
                UUID.randomUUID(), "Konu B", "WRONG", null, 5, 3, 2);

        AnalysisResponse.LessonDto lesson = new AnalysisResponse.LessonDto(
                UUID.randomUUID(), "Matematik", 4, 3, 0, BigDecimal.valueOf(57.1), List.of(topicA, topicB));

        AnalysisResponse.PriorityListsDto result = TopicPriority.of(List.of(lesson), PUAN_PER_NET);

        assertEquals(2, result.byRate().size());
        // Orana göre sıralamada %50 (Konu A), %60'ın (Konu B) önüne geçmelidir
        assertEquals("Konu A", result.byRate().get(0).topicName(), "En düşük başarı oranına sahip konu ilk sırada olmalıdır.");
        assertEquals(50.0, result.byRate().get(0).successRate());

        assertEquals("Konu B", result.byRate().get(1).topicName());
        assertEquals(60.0, result.byRate().get(1).successRate());
    }

    @Test
    @DisplayName("T-030: Her iki liste de (byPoints ve byRate) sunucuda en fazla 5 elemanla sınırlandırılır")
    void testListsCappedAtFive() {
        List<AnalysisResponse.TopicDto> topics = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            topics.add(new AnalysisResponse.TopicDto(
                    UUID.randomUUID(), "Konu " + i, "WRONG", null, 4, 2, 2));
        }

        AnalysisResponse.LessonDto lesson = new AnalysisResponse.LessonDto(
                UUID.randomUUID(), "Matematik", 16, 16, 0, BigDecimal.valueOf(50.0), topics);

        AnalysisResponse.PriorityListsDto result = TopicPriority.of(List.of(lesson), PUAN_PER_NET);

        assertEquals(5, result.byPoints().size(), "byPoints en fazla 5 satır olmalıdır.");
        assertEquals(5, result.byRate().size(), "byRate en fazla 5 satır olmalıdır.");
    }

    @Test
    @DisplayName("T-030 Determinizm Testi (APP-01 §2.7): Aynı girdi karıştırılmış sırayla verilse dahi iki liste de aynı sırayı üretir")
    void testDeterminism_ShuffledInputProducesIdenticalLists() {
        AnalysisResponse.TopicDto t1 = new AnalysisResponse.TopicDto(UUID.randomUUID(), "DNA Eşlenmesi", "WRONG", null, 2, 1, 1); // Fen, %50
        AnalysisResponse.TopicDto t2 = new AnalysisResponse.TopicDto(UUID.randomUUID(), "Zekat Faydaları", "WRONG", null, 2, 1, 1); // Din, %50
        AnalysisResponse.TopicDto t3 = new AnalysisResponse.TopicDto(UUID.randomUUID(), "Cebirsel İfadeler", "WRONG", null, 2, 1, 1); // Mat, %50
        AnalysisResponse.TopicDto t4 = new AnalysisResponse.TopicDto(UUID.randomUUID(), "Günlük İşler", "WRONG", null, 2, 1, 1); // İngilizce, %50
        AnalysisResponse.TopicDto t5 = new AnalysisResponse.TopicDto(UUID.randomUUID(), "Basınç", "WRONG", null, 6, 4, 2); // Fen, 2 kayıp
        AnalysisResponse.TopicDto t6 = new AnalysisResponse.TopicDto(UUID.randomUUID(), "Çaprazlamalar", "WRONG", null, 5, 3, 2); // Fen, 2 kayıp

        AnalysisResponse.LessonDto l1 = new AnalysisResponse.LessonDto(UUID.randomUUID(), "Din Kültürü", 1, 1, 0, BigDecimal.valueOf(50), List.of(t2));
        AnalysisResponse.LessonDto l2 = new AnalysisResponse.LessonDto(UUID.randomUUID(), "Fen Bilimleri", 8, 4, 0, BigDecimal.valueOf(66), List.of(t1, t5, t6));
        AnalysisResponse.LessonDto l3 = new AnalysisResponse.LessonDto(UUID.randomUUID(), "İngilizce", 1, 1, 0, BigDecimal.valueOf(50), List.of(t4));
        AnalysisResponse.LessonDto l4 = new AnalysisResponse.LessonDto(UUID.randomUUID(), "Matematik", 1, 1, 0, BigDecimal.valueOf(50), List.of(t3));

        List<AnalysisResponse.LessonDto> originalOrder = List.of(l1, l2, l3, l4);
        AnalysisResponse.PriorityListsDto result1 = TopicPriority.of(originalOrder, PUAN_PER_NET);

        // Sıralamayı ters/karışık sırayla veriyoruz
        List<AnalysisResponse.LessonDto> shuffledLessons = new ArrayList<>(originalOrder);
        Collections.shuffle(shuffledLessons);

        AnalysisResponse.PriorityListsDto result2 = TopicPriority.of(shuffledLessons, PUAN_PER_NET);

        assertEquals(result1.byPoints().size(), result2.byPoints().size());
        for (int i = 0; i < result1.byPoints().size(); i++) {
            assertEquals(result1.byPoints().get(i).topicName(), result2.byPoints().get(i).topicName(),
                    "byPoints[" + i + "] deterministik olmalı ve girdi sırasından etkilenmemelidir.");
        }

        assertEquals(result1.byRate().size(), result2.byRate().size());
        for (int i = 0; i < result1.byRate().size(); i++) {
            assertEquals(result1.byRate().get(i).topicName(), result2.byRate().get(i).topicName(),
                    "byRate[" + i + "] deterministik olmalı ve girdi sırasından etkilenmemelidir.");
        }
    }

    @Test
    @DisplayName("T-026: Kronik konular kümesindeki konular chronic=true olarak işaretlenir")
    void testChronicMarking() {
        AnalysisResponse.TopicDto topic = new AnalysisResponse.TopicDto(
                UUID.randomUUID(), "Çaprazlamalar", "WRONG", null, 5, 3, 2);
        AnalysisResponse.LessonDto lesson = new AnalysisResponse.LessonDto(
                UUID.randomUUID(), "Fen Bilimleri", 3, 2, 0, BigDecimal.valueOf(60.0), List.of(topic));

        Set<String> chronicTopics = Set.of("Çaprazlamalar");
        AnalysisResponse.PriorityListsDto result = TopicPriority.of(List.of(lesson), PUAN_PER_NET, chronicTopics);

        assertEquals(1, result.byPoints().size());
        assertTrue(result.byPoints().get(0).chronic(), "Kronik konu chronic=true olmalıdır.");
    }

    @Test
    @DisplayName("T-026: Tekil modda veya kronik olmayan konularda chronic=false olur")
    void testNonChronicMarking() {
        AnalysisResponse.TopicDto topic = new AnalysisResponse.TopicDto(
                UUID.randomUUID(), "Basınç", "WRONG", null, 6, 4, 2);
        AnalysisResponse.LessonDto lesson = new AnalysisResponse.LessonDto(
                UUID.randomUUID(), "Fen Bilimleri", 4, 2, 0, BigDecimal.valueOf(66.7), List.of(topic));

        AnalysisResponse.PriorityListsDto result = TopicPriority.of(List.of(lesson), PUAN_PER_NET);

        assertEquals(1, result.byPoints().size());
        assertFalse(result.byPoints().get(0).chronic(), "Tekil veya kronik olmayan konuda chronic=false olmalıdır.");
    }

    @Test
    @DisplayName("T-026: asText çıktısı yaklaşık puanı ve kronik durumunu içerir")
    void testAsTextFormatting() {
        AnalysisResponse.PriorityTopicDto dto = new AnalysisResponse.PriorityTopicDto(
                "Fen Bilimleri", "Çaprazlamalar", 5, 3, 2, 0, 60.0, 8.9, true);

        String text = TopicPriority.asText(List.of(dto));

        assertTrue(text.contains("Çaprazlamalar"));
        assertTrue(text.contains("≈8,9 puan") || text.contains("≈8.9 puan"), "Puan bilgisi metinde yer almalıdır.");
        assertTrue(text.contains("Son 2 denemede de yanlış"), "Kronik bilgisi metinde yer almalıdır.");
    }

    @Test
    @DisplayName("T-029/T-030: asText metodu metin özetini en fazla 5 konu ile sınırlar")
    void testAsText_LimitsSummaryToFiveItems() {
        List<AnalysisResponse.PriorityTopicDto> dtos = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            dtos.add(new AnalysisResponse.PriorityTopicDto(
                    "Matematik", "Konu " + i, 4, 2, 2, 0, 50.0, 8.9, false));
        }

        String text = TopicPriority.asText(dtos);

        assertTrue(text.contains("Konu 1"));
        assertTrue(text.contains("Konu 5"));
        assertFalse(text.contains("Konu 6"), "asText 6. konuyu içermemelidir (en fazla 5 satır özetlenir).");
        assertFalse(text.contains("Konu 8"), "asText 8. konuyu içermemelidir.");
    }
}
