package com.hankabakc.analyzepanel.analysis.service;

/**
 * TopicStatus: Bir konunun soru sayılarından durumunu türetir (T-027 / B-58).
 *
 * <p>Bu kural eskiden {@code SinavzaKarneParser} ve {@code AnalysisService} içinde
 * birebir aynı gövdeyle iki kez yaşıyordu. Biri değiştiğinde tekil karne ile kümülatif
 * görünüm aynı konuya farklı durum verirdi (ENG-01 §1.2).</p>
 */
public final class TopicStatus {

    /** Konuda yanlış varsa "WRONG", hiç doğru yoksa "EMPTY", aksi hâlde "CORRECT". */
    public static final String WRONG = "WRONG";
    public static final String EMPTY = "EMPTY";
    public static final String CORRECT = "CORRECT";

    private TopicStatus() {
    }

    /**
     * of: Konu durumunu belirler.
     *
     * <p>Sıra önemlidir: tek bir yanlış bile konuyu {@code WRONG} yapar; yanlış yoksa ve
     * hiç doğru çıkmamışsa konu boş bırakılmıştır ({@code EMPTY}); geri kalan hâller
     * {@code CORRECT}'tir. Soru sayısı sıfır olan konu da {@code CORRECT} sayılır çünkü
     * karnede o konudan soru sorulmamıştır.</p>
     */
    public static String of(int total, int correct, int wrong) {
        if (wrong > 0) return WRONG;
        if (correct == 0 && total > 0) return EMPTY;
        return CORRECT;
    }
}
