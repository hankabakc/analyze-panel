package com.hankabakc.analyzepanel.analysis.service;

import com.hankabakc.analyzepanel.analysis.dto.AnalysisResponse;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SinavzaKarneParser: "SINAVZA Birleştirilmiş Karne" PDF'lerini yapay zeka kullanmadan,
 * doğrudan PDF'in kendi metin ve koordinat verisinden okuyan ayrıştırıcıdır.
 *
 * <p>Bu karneler taranmış görüntü değil dijital metin PDF'idir; dolayısıyla OCR'a gerek yoktur.
 * Sayfa iki kolonlu akar (önce sol kolon baştan sona, sonra sağ kolon) ve satırlardaki
 * SS/D/Y/B/Baş.(%) değerleri boşlukla değil x koordinatıyla ayrılır - bir değer boş
 * bırakıldığında metin olarak hiçbir iz kalmadığı için kolon ataması koordinattan yapılır.</p>
 *
 * <p><b>Anonimlik:</b> Ayrıştırma yalnızca sınav listesi ve konu tablosu bölümlerini okur.
 * Karnenin tepesindeki "Ad Soyad" ve "Numara" satırlarına hiç dokunulmaz.</p>
 */
@Service
public class SinavzaKarneParser {

    private final LgsScoreCalculator lgsCalculator;
    private final PerformanceBandCalculator bandCalculator;

    /**
     * T-028B: Ayrıştırıcı katsayıyı kendi {@code @Value} satırından okumaz. Katsayının varsayılanı
     * yalnızca {@link LgsScoreCalculator} içinde, eşiklerin varsayılanı yalnızca
     * {@link PerformanceBandCalculator} içinde yaşar (ENG-01 §1.2). Kolaylık kurucuları
     * kaldırıldı; onlar sabitleri üretim koduna geri getiriyordu.
     */
    public SinavzaKarneParser(LgsScoreCalculator lgsCalculator, PerformanceBandCalculator bandCalculator) {
        this.lgsCalculator = lgsCalculator;
        this.bandCalculator = bandCalculator;
    }

    /** Kolon başlıklarındaki sayısal alanlar. "BAS" = Baş.(%) sütunu. */
    private static final List<String> NUM_COLS = List.of("SS", "D", "Y", "B", "BAS");

    /**
     * Karnede kullanılan fontta unicode karşılığı olmayan (U+0000 olarak gelen) ligatürler.
     * Hangisi olduğu, aynı fonttaki gerçek harflerin genişliğinden çalışma anında kestirilir.
     */
    private static final List<String> LIGATURES = List.of("ti", "tt", "ft", "fi", "fl");

    /** Sınav listesi satırı: "3 OKYANUS MASTER KTT-2 ... 25.01.2026 79,01" */
    private static final Pattern EXAM_ROW =
            Pattern.compile("^(\\d+)(.+?)(\\d{2}\\.\\d{2}\\.\\d{4})\\s*(\\d+,\\d+)$");

    /** Ders adının sonundaki kod: "Türkçe( LGS-TRK )" -> "Türkçe" */
    private static final Pattern LESSON_CODE = Pattern.compile("\\(\\s*[^()]*\\)\\s*$");

    /** Satırları y eksenine göre gruplarken kullanılan tolerans (punto). */
    private static final float ROW_TOLERANCE = 3f;

    /** Aynı satırdaki harfleri ayrı kelimelere bölen yatay boşluk eşiği (punto). */
    private static final float TOKEN_GAP = 1.2f;

    /** Ana konu ile alt konuyu ayıran girinti farkı (punto). Karnede fark ~6.8'dir. */
    private static final float INDENT_STEP = 3f;

    /** PDF'ten okunan tek bir karakter ve konumu. */
    private record Glyph(String text, float x0, float x1, float top, float size, boolean bold) {}

    /** Bir satırdaki bitişik karakterlerden oluşan kelime. */
    private record Tok(String text, float x0, float x1, boolean bold) {}

    /** Kolon başlığı satırından çıkarılan düzen bilgisi. */
    private record Layout(int headerRow, List<Map<String, Float>> cols, List<Float> blockEnds) {}

    /** Ayrıştırılmış tek bir tablo satırı. */
    private record Row(String name, Map<String, Integer> values, float x0, boolean bold) {}

    /** Ders başlığı ve altındaki yaprak konular. */
    private record Lesson(String name, Map<String, Integer> values, List<Row> topics) {}

    /**
     * parse: Karneyi okuyup panelin beklediği AnalysisResponse yapısını döndürür.
     *
     * <p>Format tanınmazsa {@link IllegalArgumentException} fırlatır; rapor o zaman
     * "Analiz Başarısız" olarak işaretlenir. Ayrı bir "destekliyor mu" kontrolü yoktur,
     * çünkü bu kontrol PDF'i ikinci kez baştan ayrıştırmak demek olurdu.</p>
     *
     * @param pdfBytes           karne dosyası
     * @param fallbackExamCount  karnede sınav listesi bulunamazsa kullanılacak deneme sayısı
     */
    public AnalysisResponse parse(byte[] pdfBytes, Integer fallbackExamCount) {
        List<List<Glyph>> pages = readPages(pdfBytes);
        if (pages.isEmpty()) {
            throw new IllegalArgumentException("PDF boş veya okunamadı.");
        }

        List<AnalysisResponse.ExamSummaryDto> exams = readExamList(pages.get(0));

        Layout layout = findLayout(rowsOf(pages.get(0)));
        if (layout == null) {
            throw new IllegalArgumentException(
                    "Bu PDF tanınan bir karne formatında değil (desteklenen: SINAVZA Birleştirilmiş Karne). "
                    + "Farklı bir yayınevinin karnesiyse o düzen için ayrıştırıcı eklenmelidir.");
        }

        List<Lesson> lessons = readLessons(pages, layout);
        if (lessons.isEmpty()) {
            throw new IllegalArgumentException("Karnede ders/konu tablosu bulunamadı.");
        }

        int examCount = exams.isEmpty()
                ? (fallbackExamCount != null ? fallbackExamCount : 1)
                : exams.size();

        // T-023: Öncelik sırası tek bir yerde (TopicPriority) hesaplanır; metin özeti de
        // aynı listeden türetilir. Kural iki yerde yaşasaydı zamanla ayrışırdı (ENG-01 §1.2).
        List<AnalysisResponse.LessonDto> lessonDtos = toLessonDtos(lessons);
        AnalysisResponse.PriorityListsDto priorityLists = TopicPriority.of(lessonDtos, lgsCalculator.getPuanPerNet());

        return new AnalysisResponse(
                null, null,
                "Birleştirilmiş Karne (" + examCount + " Deneme)",
                null, null,
                examCount,
                validate(lessons),
                examCount > 1 ? "SUMMARY" : "SINGLE",
                null,                            // mentorFeedback
                null,                            // futureProjection: tek karneden trend çıkmaz
                TopicPriority.strategicPriority(lessonDtos), // strategicPriority (T-039)
                TopicPriority.teacherActionPlan(List.of(), priorityLists.byPoints()), // teacherActionPlan (T-039)
                null, null,                      // hedef okul, hedef puan
                exams,
                new AnalysisResponse.ConsolidatedResultDto(lessonDtos),
                TopicPriority.asText(priorityLists.byPoints()),
                null,
                priorityLists,
                null
        );
    }

    // ---------------------------------------------------------------- PDF okuma

    /** readPages: Her sayfanın karakterlerini konumlarıyla birlikte çıkarır. */
    private List<List<Glyph>> readPages(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            List<List<Glyph>> pages = new ArrayList<>();
            for (int i = 1; i <= document.getNumberOfPages(); i++) {
                List<Glyph> glyphs = new ArrayList<>();
                PDFTextStripper stripper = new PDFTextStripper() {
                    @Override
                    protected void writeString(String text, List<TextPosition> positions) {
                        for (TextPosition p : positions) {
                            String unicode = p.getUnicode();
                            if (unicode == null || unicode.isEmpty()) continue;
                            String font = p.getFont() != null ? p.getFont().getName() : null;
                            glyphs.add(new Glyph(
                                    unicode,
                                    p.getXDirAdj(),
                                    p.getXDirAdj() + p.getWidthDirAdj(),
                                    p.getYDirAdj(),
                                    p.getFontSizeInPt(),
                                    font != null && font.contains("Bold")));
                        }
                    }
                };
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                stripper.getText(document);
                pages.add(repairLigatures(glyphs));
            }
            return pages;
        } catch (IOException e) {
            throw new IllegalArgumentException("PDF dosyası okunamadı: " + e.getMessage(), e);
        }
    }

    /**
     * repairLigatures: Unicode eşlemesi olmayıp U+0000 olarak gelen ligatürleri onarır.
     *
     * <p>Aday ligatür genişlikleri, aynı belgedeki gerçek 't'/'i'/'f' harflerinin
     * genişliğinden hesaplanır; böylece font veya punto değişse de eşleme kendini
     * kalibre eder, sabit bir genişlik tablosuna bağlı kalmaz.</p>
     */
    private List<Glyph> repairLigatures(List<Glyph> glyphs) {
        Map<String, float[]> sums = new HashMap<>(); // harf -> {genislik toplami, adet}
        for (Glyph g : glyphs) {
            if (g.size() > 0 && ("t".equals(g.text()) || "i".equals(g.text()) || "f".equals(g.text()))) {
                float[] acc = sums.computeIfAbsent(g.text(), k -> new float[2]);
                acc[0] += (g.x1() - g.x0()) / g.size();
                acc[1]++;
            }
        }
        Map<String, Float> candidates = new LinkedHashMap<>();
        for (String lig : LIGATURES) {
            float[] a = sums.get(lig.substring(0, 1));
            float[] b = sums.get(lig.substring(1, 2));
            if (a != null && b != null) {
                candidates.put(lig, a[0] / a[1] + b[0] / b[1]);
            }
        }
        if (candidates.isEmpty()) return glyphs;

        List<Glyph> repaired = new ArrayList<>(glyphs.size());
        for (Glyph g : glyphs) {
            if (g.text().charAt(0) == 0 && g.size() > 0) {   // unicode eslemesi olmayan ligatur
                float ratio = (g.x1() - g.x0()) / g.size();
                String best = null;
                float bestDiff = Float.MAX_VALUE;
                for (Map.Entry<String, Float> c : candidates.entrySet()) {
                    float diff = Math.abs(c.getValue() - ratio);
                    if (diff < bestDiff) {
                        bestDiff = diff;
                        best = c.getKey();
                    }
                }
                repaired.add(new Glyph(best, g.x0(), g.x1(), g.top(), g.size(), g.bold()));
            } else {
                repaired.add(g);
            }
        }
        return repaired;
    }

    // ---------------------------------------------------------------- satır / kelime

    /** rowsOf: Karakterleri y koordinatına göre satırlara böler, her satırı soldan sağa sıralar. */
    private List<List<Glyph>> rowsOf(List<Glyph> glyphs) {
        Map<Integer, List<Glyph>> buckets = new java.util.TreeMap<>();
        for (Glyph g : glyphs) {
            buckets.computeIfAbsent(Math.round(g.top() / ROW_TOLERANCE), k -> new ArrayList<>()).add(g);
        }
        List<List<Glyph>> rows = new ArrayList<>();
        for (List<Glyph> row : buckets.values()) {
            row.sort((a, b) -> Float.compare(a.x0(), b.x0()));
            rows.add(row);
        }
        return rows;
    }

    /** tokensOf: Bir satırdaki karakterleri boşluğa ve yatay aralığa göre kelimelere ayırır. */
    private List<Tok> tokensOf(List<Glyph> row) {
        List<Tok> tokens = new ArrayList<>();
        List<Glyph> current = new ArrayList<>();
        for (Glyph g : row) {
            if (" ".equals(g.text())) {
                flush(current, tokens);
                continue;
            }
            if (!current.isEmpty() && g.x0() - current.get(current.size() - 1).x1() > TOKEN_GAP) {
                flush(current, tokens);
            }
            current.add(g);
        }
        flush(current, tokens);
        return tokens;
    }

    private void flush(List<Glyph> current, List<Tok> out) {
        if (current.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        boolean bold = false;
        for (Glyph g : current) {
            sb.append(g.text());
            bold |= g.bold();
        }
        out.add(new Tok(sb.toString(), current.get(0).x0(), current.get(current.size() - 1).x1(), bold));
        current.clear();
    }

    // ---------------------------------------------------------------- düzen tespiti

    /**
     * findLayout: "Konu Adı SS D Y B Baş.(%)" başlık satırını bulup her blok (sol/sağ kolon)
     * için sütun merkezlerini ve blok bitiş x'ini çıkarır. Bulamazsa null döner.
     */
    private Layout findLayout(List<List<Glyph>> rows) {
        for (int i = 0; i < rows.size(); i++) {
            List<Tok> tokens = tokensOf(rows.get(i));
            StringBuilder flat = new StringBuilder();
            for (Tok t : tokens) flat.append(t.text());
            String joined = flat.toString();
            String flatClean = joined.replace(" ", "");
            if (!joined.contains("SS") || (!flatClean.contains("KonuAdı") && !flatClean.contains("KonuAdi"))) continue;

            List<List<Tok>> blocks = new ArrayList<>();
            List<Tok> block = new ArrayList<>();
            for (Tok t : tokens) {
                if (t.text().startsWith("Konu") && !block.isEmpty()) {
                    blocks.add(block);
                    block = new ArrayList<>();
                }
                block.add(t);
            }
            blocks.add(block);

            List<Map<String, Float>> cols = new ArrayList<>();
            List<Float> ends = new ArrayList<>();
            for (List<Tok> b : blocks) {
                Map<String, Float> centers = new HashMap<>();
                float end = 0f;
                for (Tok t : b) {
                    String key = (t.text().startsWith("Baş") || t.text().startsWith("Bas")) ? "BAS" : t.text();
                    if (NUM_COLS.contains(key)) centers.put(key, (t.x0() + t.x1()) / 2);
                    end = Math.max(end, t.x1());
                }
                if (centers.containsKey("SS") && centers.containsKey("D")) {
                    cols.add(centers);
                    ends.add(end);
                }
            }
            if (!cols.isEmpty()) return new Layout(i, cols, ends);
        }
        return null;
    }

    // ---------------------------------------------------------------- içerik okuma

    /** readExamList: Karnenin üstündeki "HESAPLANAN DENEME SINAVLARI" tablosunu okur. */
    private List<AnalysisResponse.ExamSummaryDto> readExamList(List<Glyph> page) {
        List<AnalysisResponse.ExamSummaryDto> exams = new ArrayList<>();
        for (List<Glyph> row : rowsOf(page)) {
            StringBuilder sb = new StringBuilder();
            for (Glyph g : row) sb.append(g.text());
            Matcher m = EXAM_ROW.matcher(sb.toString().trim());
            if (m.matches()) {
                exams.add(new AnalysisResponse.ExamSummaryDto(
                        m.group(2).trim(),
                        m.group(3),
                        new BigDecimal(m.group(4).replace(',', '.'))));
            }
        }
        return exams;
    }

    /**
     * readLessons: Konu tablosunu ders ve yaprak konu düzeyinde okur.
     *
     * <p>Tablo iki kolon halinde akar; sol kolon sayfanın sonuna kadar okunup ardından sağ
     * kolona geçilir. Bir ders başlığı (kalın yazılmış satır) sol kolonun dibinde başlayıp
     * konuları sağ kolonda devam edebilir.</p>
     *
     * <p>Konular iki girinti seviyesindedir: girintisiz olanlar ana konu başlığı, girintili
     * olanlar yapraktır. Yalnızca yapraklar sayılır - ikisi birden toplanırsa her soru iki
     * kez sayılmış olur.</p>
     */
    private List<Lesson> readLessons(List<List<Glyph>> pages, Layout layout) {
        List<Lesson> lessons = new ArrayList<>();
        Lesson current = null;

        for (List<Glyph> page : pages) {
            List<List<Glyph>> rows = rowsOf(page);
            Layout pageLayout = findLayout(rows);
            List<List<Glyph>> body = pageLayout != null
                    ? rows.subList(pageLayout.headerRow() + 1, rows.size())
                    : rows;

            for (int bi = 0; bi < layout.cols().size(); bi++) {
                Map<String, Float> cols = layout.cols().get(bi);
                float lo = bi == 0 ? 0f : layout.blockEnds().get(bi - 1) + 2f;
                float hi = layout.blockEnds().get(bi) + 2f;

                List<Row> parsed = new ArrayList<>();
                for (List<Glyph> row : body) {
                    List<Glyph> part = new ArrayList<>();
                    for (Glyph g : row) {
                        if (g.x0() >= lo && g.x0() < hi) part.add(g);
                    }
                    Row r = parseRow(part, cols);
                    if (r != null) parsed.add(r);
                }
                if (parsed.isEmpty()) continue;

                // Girinti tabanı her blok için ayrı hesaplanır (sol kolon ~24pt, sağ kolon ~300pt).
                float base = Float.MAX_VALUE;
                for (Row r : parsed) base = Math.min(base, r.x0());

                String mainTopic = null;
                for (Row r : parsed) {
                    if (r.bold()) {                               // ders başlığı: "Türkçe( LGS-TRK )"
                        current = new Lesson(
                                LESSON_CODE.matcher(r.name()).replaceAll("").trim(),
                                r.values(),
                                new ArrayList<>());
                        mainTopic = null;
                        lessons.add(current);
                    } else if (current == null) {
                        continue;
                    } else if (r.x0() <= base + INDENT_STEP) {    // ana konu: yalnızca başlık
                        mainTopic = r.name();
                    } else {                                      // alt konu: sayılan yaprak
                        String name = mainTopic != null ? mainTopic + " - " + r.name() : r.name();
                        current.topics().add(new Row(name, r.values(), r.x0(), false));
                    }
                }
            }
        }
        return lessons;
    }

    /**
     * parseRow: Bir blok parçasını "konu adı + sayısal değerler" olarak çözer.
     *
     * <p>Sayı bölgesinin sol sınırı, SS merkezinden bir tam sütun adımı sola alınarak
     * bulunur. Kalın yazılan ders başlıklarında sayılar birkaç punto sola kaydığı için
     * sabit bir marj yeterli olmaz.</p>
     */
    private Row parseRow(List<Glyph> part, Map<String, Float> cols) {
        List<Tok> tokens = tokensOf(part);
        if (tokens.isEmpty()) return null;

        float numStart = cols.get("SS") - (cols.get("D") - cols.get("SS"));

        StringBuilder name = new StringBuilder();
        boolean bold = false;
        float nameX0 = Float.MAX_VALUE;
        Map<String, Integer> values = new HashMap<>();

        for (Tok t : tokens) {
            if (t.x1() <= numStart) {
                if (name.length() > 0) name.append(' ');
                name.append(t.text());
                bold |= t.bold();
                nameX0 = Math.min(nameX0, t.x0());
            } else if (t.x0() > numStart && isDigits(t.text())) {
                float mid = (t.x0() + t.x1()) / 2;
                String col = null;
                float bestDiff = Float.MAX_VALUE;
                for (String c : NUM_COLS) {
                    Float center = cols.get(c);
                    if (center == null) continue;
                    float diff = Math.abs(center - mid);
                    if (diff < bestDiff) {
                        bestDiff = diff;
                        col = c;
                    }
                }
                if (col != null) values.put(col, Integer.parseInt(t.text()));
            }
        }

        if (name.length() == 0 || !values.containsKey("SS")) return null;
        return new Row(name.toString(), values, nameX0, bold);
    }

    private boolean isDigits(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- doğrulama / dönüşüm

    /**
     * validate: Karnenin kendi iç tutarlılığını kullanarak ayrıştırmayı denetler.
     * Doğru + Yanlış + Boş her zaman Soru Sayısına, yaprak konuların toplamı da
     * ders toplamına eşit olmalıdır. Tutmayan ders varsa öğretmene uyarı olarak gider.
     */
    private String validate(List<Lesson> lessons) {
        StringBuilder errors = new StringBuilder();
        for (Lesson l : lessons) {
            int ss = value(l.values(), "SS");
            int dyb = value(l.values(), "D") + value(l.values(), "Y") + value(l.values(), "B");
            int topicSum = 0;
            for (Row t : l.topics()) topicSum += value(t.values(), "SS");

            if (dyb != ss) {
                errors.append(String.format("[%s] D+Y+B=%d, soru sayısı=%d. ", l.name(), dyb, ss));
            }
            if (!l.topics().isEmpty() && topicSum != ss) {
                errors.append(String.format("[%s] Konu toplamı=%d, ders toplamı=%d. ", l.name(), topicSum, ss));
            }
        }
        return errors.toString().trim();
    }

    private List<AnalysisResponse.LessonDto> toLessonDtos(List<Lesson> lessons) {
        List<AnalysisResponse.LessonDto> out = new ArrayList<>();
        for (Lesson l : lessons) {
            List<AnalysisResponse.TopicDto> topics = new ArrayList<>();
            for (Row t : l.topics()) {
                int total = value(t.values(), "SS");
                int correct = value(t.values(), "D");
                int wrong = value(t.values(), "Y");
                String band = bandCalculator != null ? bandCalculator.calculateBand(total, correct) : "WEAK";
                topics.add(new AnalysisResponse.TopicDto(
                        null, t.name(), TopicStatus.of(total, correct, wrong), null, total, correct, wrong, band));
            }
            out.add(new AnalysisResponse.LessonDto(
                    null,
                    l.name(),
                    value(l.values(), "D"),
                    value(l.values(), "Y"),
                    value(l.values(), "B"),
                    BigDecimal.valueOf(value(l.values(), "BAS")),
                    topics));
        }
        // T-030C: Dersler başarı oranına göre azalan, eşitlikte ders adına göre artan deterministik sırada sunulur
        out.sort(Comparator
                .comparing((AnalysisResponse.LessonDto l) -> l.successRate() != null ? l.successRate() : BigDecimal.ZERO, Comparator.reverseOrder())
                .thenComparing(AnalysisResponse.LessonDto::lessonName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    /**
     * weakestLessonSummary: En düşük başarılı dersi tek cümlede özetler.
     * Arayüzdeki "Stratejik Öncelik" kartını doldurur.
     *
     * <p>Karnenin kendi Baş.(%) sütunu kullanılır - doğru/soru oranı değil. Arayüz
     * ders kartlarında bu değeri gösterdiği için, farklı bir ölçü kullanmak "en zayıf
     * ders" ile ekrandaki en düşük yüzdenin çelişmesine yol açıyordu.</p>
     */
    private String weakestLessonSummary(List<Lesson> lessons) {
        Lesson weakest = null;
        int weakestRate = Integer.MAX_VALUE;
        for (Lesson l : lessons) {
            int rate = value(l.values(), "BAS");
            if (rate < weakestRate) {
                weakestRate = rate;
                weakest = l;
            }
        }
        if (weakest == null) return null;
        return String.format("%s dersine öncelik verilmeli (%%%d başarı).", weakest.name(), weakestRate);
    }


    private int value(Map<String, Integer> values, String key) {
        return values.getOrDefault(key, 0);
    }
}
