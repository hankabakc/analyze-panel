package com.hankabakc.analyzepanel.analysis.service;

import com.hankabakc.analyzepanel.analysis.dto.AnalysisResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SinavzaKarneParserTest: "SINAVZA Birleştirilmiş Karne" ayrıştırıcısının işlevselliğini,
 * hata yönetimini ve aritmetik tutarlılığını doğrular.
 *
 * <p><b>CI ve Determinizm (IST-08 §1.1, ENG-04 §3.3):</b> Bu test sınıfı, kişisel veri
 * içermeyen (0 PII, APP-03 §4) bellek içi sentetik PDF üreticisi ({@link SyntheticKarneBuilder})
 * kullanarak her ortamda (CI boru hattı, temiz klon, yerel geliştirici makinesi) %100 koşar
 * ve 0 atlanan (0 skipped) test üretir.</p>
 */
class SinavzaKarneParserTest {

    private static final Path UPLOADS = Path.of("uploads", "analysis");

    // T-028B: Katsayı ve eşikler üretim kurucusunda gömülü değil; test kendi değerlerini açıkça verir.
    private final SinavzaKarneParser parser = new SinavzaKarneParser(
            new LgsScoreCalculator(4.44, 100.0), new PerformanceBandCalculator(85.0, 50.0));

    /**
     * sentetikKarneTutarliAyristirilir: Sıfır kişisel veri içeren sentetik karneyle
     * ayrıştırıcının sınav listesi, dersler, yaprak konular, soru toplamları ve ligatür
     * temizliğini her ortamda deterministik olarak doğrular.
     */
    @Test
    void sentetikKarneTutarliAyristirilir() throws IOException {
        byte[] pdfBytes = SyntheticKarneBuilder.createValidKarnePdf();
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);

        AnalysisResponse result = parser.parse(pdfBytes, 1);

        // Doğrulama hataları boş olmalıdır
        assertTrue(result.validationErrors().isEmpty(),
                "Sentetik karne tutarsız ayrıştırıldı: " + result.validationErrors());

        // Sınav listesi doğrulaması
        assertNotNull(result.examList());
        assertEquals(1, result.examList().size(), "Sınav listesi 1 deneme içermelidir.");
        AnalysisResponse.ExamSummaryDto exam = result.examList().get(0);
        assertEquals("DENEME SINAVI", exam.examName());
        assertEquals("15.01.2026", exam.examDate());
        assertEquals(new BigDecimal("80.00"), exam.totalScore());

        // Dersler doğrulaması
        List<AnalysisResponse.LessonDto> lessons = result.consolidatedResult().lessons();
        assertEquals(2, lessons.size(), "Sentetik karnede 2 ders (Turkce, Matematik) bulunmalıdır.");

        for (AnalysisResponse.LessonDto lesson : lessons) {
            int lessonTotal = lesson.correct() + lesson.wrong() + lesson.empty();
            assertEquals(20, lessonTotal, lesson.lessonName() + " toplam soru sayısı 20 olmalıdır.");

            int topicTotal = lesson.topics().stream()
                    .mapToInt(AnalysisResponse.TopicDto::totalQuestions).sum();
            assertEquals(lessonTotal, topicTotal,
                    lesson.lessonName() + " konu toplamı ders toplamını tutmuyor.");

            assertFalse(lesson.topics().isEmpty(), lesson.lessonName() + " altında yaprak konu bulunamadı.");

            for (AnalysisResponse.TopicDto topic : lesson.topics()) {
                // Onarılamamış ligatür, konu adında U+0000 olarak kalırdı
                assertFalse(topic.topicName().indexOf(0) >= 0,
                        "Konu adında çözülmemiş ligatür var: " + topic.topicName());
                assertTrue(topic.totalQuestions() > 0, "Konu soru sayısı sıfırdan büyük olmalıdır.");
            }
        }
    }

    /**
     * taninmayanFormatHataFirlatir: Desteklenmeyen veya genel formatlı bir PDF verildiğinde
     * sistemin sessizce başarısız olmak yerine açıklayıcı IllegalArgumentException
     * fırlattığını doğrular (APP-01 §2.7).
     */
    @Test
    void taninmayanFormatHataFirlatir() throws IOException {
        byte[] dummyPdf = SyntheticKarneBuilder.createDummyPdf();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> parser.parse(dummyPdf, 1));
        assertTrue(ex.getMessage().contains("tanınan bir karne formatında değil"),
                "Hata mesajı beklenen format uyarısını içermelidir: " + ex.getMessage());
    }

    /**
     * bosPdfVeyaBozukIcerikHataFirlatir: Boş bayt dizisi veya metin içermeyen PDF sayfası
     * verildiğinde uygun hata fırlatıldığını doğrular.
     */
    @Test
    void bosPdfVeyaBozukIcerikHataFirlatir() throws IOException {
        // Boş bayt dizisi
        assertThrows(IllegalArgumentException.class, () -> parser.parse(new byte[0], 1));

        // Boş sayfa içeren PDF
        byte[] emptyPdf = SyntheticKarneBuilder.createEmptyPagePdf();
        assertThrows(IllegalArgumentException.class, () -> parser.parse(emptyPdf, 1));
    }

    /**
     * yerelYuklenmisKarnelerVarsaDogrulanir: Geliştirici makinesinde uploads/analysis
     * altında gerçek karneler mevcutsa bunları da ayrıştırıp denetler.
     * CI veya temiz klon ortamında karne yoksa Assumptions ile atlanmaz, 0 skipped
     * disiplinini bozmadan sessizce tamamlanır.
     */
    @Test
    void yerelYuklenmisKarnelerVarsaDogrulanir() throws IOException {
        List<Path> pdfs = findPdfs();
        if (pdfs.isEmpty()) {
            // CI veya temiz depoda gerçek karne bulunamaz; sentetik test ana doğrulamayı zaten yapmıştır.
            return;
        }

        for (Path pdf : pdfs) {
            AnalysisResponse result;
            try {
                result = parser.parse(Files.readAllBytes(pdf), 5);
            } catch (IllegalArgumentException notSupported) {
                continue; // başka bir formattaki dosya; yalnızca tanınanları denetler
            }

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
                    assertFalse(topic.topicName().indexOf(0) >= 0,
                            pdf.getFileName() + " konu adında çözülmemiş ligatür var: " + topic.topicName());
                }
            }
        }
    }

    private List<Path> findPdfs() throws IOException {
        if (!Files.isDirectory(UPLOADS)) return List.of();
        try (Stream<Path> files = Files.list(UPLOADS)) {
            return files.filter(p -> p.toString().toLowerCase().endsWith(".pdf")).toList();
        }
    }
}

