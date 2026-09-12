package com.hankabakc.analyzepanel.analysis.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * SyntheticKarneBuilder: CI boru hattı ve yerel birim testleri için sıfır kişisel veri (0 PII, APP-03 §4)
 * içeren, bellek içinde (in-memory) sentetik Sinavza karne PDF'leri üreten yardımcı sınıftır.
 *
 * <p>PDFBox standart Helvetica fontları (Standard14Fonts) kullanılarak oluşturulur; böylece
 * harici font dosyalarına, işletim sistemi font kütüphanelerine veya dosya sistemi erişimine
 * ihtiyaç duymadan milisaniyeler içinde deterministik test girdisi sağlar.</p>
 */
public final class SyntheticKarneBuilder {

    private SyntheticKarneBuilder() {
        // Yardımcı sınıf, örneklenemez
    }

    /**
     * createValidKarnePdf: Hem sınav özet tablosunu hem de ders ve yaprak konu hiyerarşisini
     * eksiksiz içeren, D+Y+B aritmetiği ve konu toplamları %100 tutarlı sentetik bir karne üretir.
     *
     * @return PDF dosyasının bayt dizisi
     * @throws IOException PDF oluşturma hatası durumunda
     */
    public static byte[] createValidKarnePdf() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // 1. Sınav listesi tablosu (EXAM_ROW deseniyle eşleşir)
                writeText(cs, font, 8, 30, 750, "1 DENEME SINAVI 15.01.2026 80,00");

                // 2. Tablo Sütun Başlıkları (findLayout tarafından tespit edilir)
                float y = 700;
                writeText(cs, fontBold, 8, 30, y, "Konu Adi");
                writeText(cs, fontBold, 8, 150, y, "SS");
                writeText(cs, fontBold, 8, 180, y, "D");
                writeText(cs, fontBold, 8, 210, y, "Y");
                writeText(cs, fontBold, 8, 240, y, "B");
                writeText(cs, fontBold, 8, 270, y, "Bas.(%)");

                // 3. Ders 1: Türkçe (Kalın satır -> ders başlığı)
                // SS=20, D=15, Y=3, B=2 (15+3+2=20), Bas=75
                y = 675;
                writeTableRow(cs, fontBold, 8, 30, y, "Turkce( LGS-TRK )", "20", "15", "3", "2", "75");

                // Ana Konu 1: Sözcükte Anlam (Girintisiz, x=30, değer yok -> ana konu başlığı)
                y = 655;
                writeText(cs, font, 8, 30, y, "Sozcukte Anlam");

                // Yaprak Konu 1.1: Gerçek ve Mecaz Anlam (Girintili, x=36, SS=10, D=8, Y=1, B=1, Bas=80)
                y = 635;
                writeTableRow(cs, font, 8, 36, y, "Gercek ve Mecaz Anlam", "10", "8", "1", "1", "80");

                // Yaprak Konu 1.2: Deyimler ve Atasözleri (Girintili, x=36, SS=10, D=7, Y=2, B=1, Bas=70)
                y = 615;
                writeTableRow(cs, font, 8, 36, y, "Deyimler ve Atasozleri", "10", "7", "2", "1", "70");

                // 4. Ders 2: Matematik (Kalın satır -> ikinci ders başlığı)
                // SS=20, D=10, Y=5, B=5 (10+5+5=20), Bas=50
                y = 590;
                writeTableRow(cs, fontBold, 8, 30, y, "Matematik( LGS-MAT )", "20", "10", "5", "5", "50");

                // Ana Konu 2: Sayilar ve Islemler (Girintisiz, x=30)
                y = 570;
                writeText(cs, font, 8, 30, y, "Sayilar ve Islemler");

                // Yaprak Konu 2.1: Carpanlar ve Katlar (Girintili, x=36, SS=10, D=5, Y=3, B=2, Bas=50)
                y = 550;
                writeTableRow(cs, font, 8, 36, y, "Carpanlar ve Katlar", "10", "5", "3", "2", "50");

                // Yaprak Konu 2.2: Uslu Ifadeler (Girintili, x=36, SS=10, D=5, Y=2, B=3, Bas=50)
                y = 530;
                writeTableRow(cs, font, 8, 36, y, "Uslu Ifadeler", "10", "5", "2", "3", "50");
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    /**
     * createDummyPdf: Geçerli bir PDF belgesi olan ancak Sinavza karne düzenine
     * sahip olmayan sıradan bir metin PDF'i üretir. Tanınmayan format testlerinde kullanılır.
     */
    public static byte[] createDummyPdf() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                writeText(cs, font, 12, 50, 700, "Bu dosya genel bir belgedir ve karne tablosu icermez.");
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    /**
     * createEmptyPagePdf: Hiçbir metin içermeyen boş bir PDF sayfası üretir.
     */
    public static byte[] createEmptyPagePdf() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private static void writeTableRow(PDPageContentStream cs, PDFont font, float fontSize,
                                      float nameX, float y, String name,
                                      String ss, String d, String yVal, String b, String bas) throws IOException {
        writeText(cs, font, fontSize, nameX, y, name);
        writeText(cs, font, fontSize, 150, y, ss);
        writeText(cs, font, fontSize, 180, y, d);
        writeText(cs, font, fontSize, 210, y, yVal);
        writeText(cs, font, fontSize, 240, y, b);
        writeText(cs, font, fontSize, 270, y, bas);
    }

    private static void writeText(PDPageContentStream cs, PDFont font, float fontSize,
                                  float x, float y, String text) throws IOException {
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }
}
