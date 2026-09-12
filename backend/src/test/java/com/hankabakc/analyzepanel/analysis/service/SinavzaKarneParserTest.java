package com.hankabakc.analyzepanel.analysis.service;

import com.hankabakc.analyzepanel.analysis.dto.AnalysisResponse;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SinavzaKarneParserTest: Ayrıştırıcıyı gerçek karnelerle doğrular.
 *
 * <p>Karneler öğrenci verisi içerdiği için depoya eklenmez; test yüklenmiş dosyaları
 * <code>uploads/analysis</code> altında arar, hiç dosya yoksa kendini atlar.</p>
 *
 * <p>Doğrulama karnenin kendi aritmetiğine dayanır: Doğru + Yanlış + Boş her zaman
 * ders toplamına eşittir ve yaprak konuların soru sayıları da aynı toplamı verir.
 * Kolon ataması kayarsa bu eşitlikler bozulur, yani test sessizce geçmez.</p>
 */
class SinavzaKarneParserTest {

    private static final Path UPLOADS = Path.of("uploads", "analysis");

    // T-028B: Katsayı ve eşikler artık üretim kurucusunda gömülü değil; test kendi değerlerini açıkça verir.
    private final SinavzaKarneParser parser = new SinavzaKarneParser(
            new LgsScoreCalculator(4.44, 100.0), new PerformanceBandCalculator(85.0, 50.0));

    @Test
    void yuklenmisKarnelerTutarliAyristirilir() throws IOException {
        List<Path> pdfs = findPdfs();
        Assumptions.assumeFalse(pdfs.isEmpty(), "uploads/analysis altında karne PDF'i yok, test atlandı.");

        int parsed = 0;
        for (Path pdf : pdfs) {
            AnalysisResponse result;
            try {
                result = parser.parse(Files.readAllBytes(pdf), 5);
            } catch (IllegalArgumentException notSupported) {
                continue; // baska bir formattaki dosya; bu test yalnizca taninanlari denetler
            }
            parsed++;

            assertTrue(result.validationErrors().isEmpty(),
                    pdf.getFileName() + " tutarsız ayrıştırıldı: " + result.validationErrors());

            List<AnalysisResponse.LessonDto> lessons = result.consolidatedResult().lessons();
            assertFalse(lessons.isEmpty(), pdf.getFileName() + " içinde ders bulunamadı.");

            for (AnalysisResponse.LessonDto lesson : lessons) {
                int lessonTotal = lesson.correct() + lesson.wrong() + lesson.empty();
                int topicTotal = lesson.topics().stream()
                        .mapToInt(AnalysisResponse.TopicDto::totalQuestions).sum();

                assertEquals(lessonTotal, topicTotal,
                        pdf.getFileName() + " / " + lesson.lessonName() + " konu toplamı ders toplamını tutmuyor.");

                for (AnalysisResponse.TopicDto topic : lesson.topics()) {
                    // Onarilamamis ligatur, konu adinda U+0000 olarak kalirdi.
                    assertFalse(topic.topicName().indexOf(0) >= 0,
                            pdf.getFileName() + " konu adında çözülmemiş ligatür var: " + topic.topicName());
                }
            }
        }

        Assumptions.assumeTrue(parsed > 0, "Tanınan formatta karne bulunamadı, test atlandı.");
    }

    private List<Path> findPdfs() throws IOException {
        if (!Files.isDirectory(UPLOADS)) return List.of();
        try (Stream<Path> files = Files.list(UPLOADS)) {
            return files.filter(p -> p.toString().toLowerCase().endsWith(".pdf")).toList();
        }
    }
}
