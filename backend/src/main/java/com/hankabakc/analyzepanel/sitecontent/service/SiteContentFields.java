package com.hankabakc.analyzepanel.sitecontent.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SiteContentFields: Karşılama sitesinde doldurulabilecek alanların izin listesi (T-063A / S-026).
 *
 * <p>ENG-12 §2.1: Yalnızca burada tanımlı anahtarlar yazılabilir; her anahtarın azami uzunluğu da
 * burada, tek yerde durur. İstemci sınırları {@code GET /api/v1/site-content/fields} ucundan okur,
 * kendi kopyasını tutmaz (APP-01 §2.1).</p>
 *
 * <p>Özellik açıklamaları yalnızca uygulamada gerçekten bulunan yedi modül içindir (S-026).</p>
 */
public final class SiteContentFields {

    /** SSS sayfasındaki soru-cevap yuvası sayısı. */
    public static final int FAQ_SLOTS = 8;

    private static final Map<String, Integer> MAX_LENGTHS = build();

    private SiteContentFields() {
    }

    private static Map<String, Integer> build() {
        Map<String, Integer> fields = new LinkedHashMap<>();
        fields.put("site.institutionName", 100);

        fields.put("home.heroTitle", 150);
        fields.put("home.heroSubtitle", 500);
        fields.put("home.intro", 2000);

        fields.put("features.reportAnalysis", 1000);
        fields.put("features.progressTracking", 1000);
        fields.put("features.goals", 1000);
        fields.put("features.studyPlan", 1000);
        fields.put("features.ranking", 1000);
        fields.put("features.psychTests", 1000);
        fields.put("features.management", 1000);

        fields.put("about.body", 5000);
        fields.put("about.mission", 2000);
        fields.put("about.vision", 2000);

        for (int i = 1; i <= FAQ_SLOTS; i++) {
            fields.put("faq." + i + ".question", 300);
            fields.put("faq." + i + ".answer", 2000);
        }

        fields.put("contact.address", 500);
        fields.put("contact.phone", 50);
        fields.put("contact.email", 150);
        fields.put("contact.hours", 300);
        fields.put("contact.note", 1000);

        fields.put("privacy.body", 20000);

        fields.put("footer.note", 300);
        return Collections.unmodifiableMap(fields);
    }

    /**
     * maxLengths: İzinli anahtarlar ve azami uzunlukları (tanım sırasıyla).
     */
    public static Map<String, Integer> maxLengths() {
        return MAX_LENGTHS;
    }

    /**
     * clean: Değeri düz metin olarak saklanacak hâle getirir.
     * Satır sonları {@code \n}'e indirilir, satır sonu ve sekme dışındaki kontrol karakterleri
     * (ör. PostgreSQL'in reddettiği NUL) ayıklanır, baştaki ve sondaki boşluk kırpılır.
     * HTML'e dokunulmaz; kaçış gösterim anında yapılır (ENG-12 §2.2).
     */
    public static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\p{Cntrl}&&[^\n\t]]", "")
                .strip();
    }
}
