package com.hankabakc.analyzepanel.analysis.entity;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * <p><b>İsim uyarısı (T-039A):</b> Baştaki "Ai" eki tarihseldir; bu proje karne verisi için
 * <b>hiçbir dış AI servisine çağrı yapmaz</b> (APP-01 §1.1, §2.7). Buradaki içerik
 * {@code AnalysisService.generateGlobalStrategicSummary} tarafından doğrudan veritabanındaki
 * sayılardan, kural tabanlı ve deterministik olarak üretilir: aynı veri her zaman aynı metni verir.
 * Sınıf adı, mevcut şema ve kayıtlarla uyum bozulmasın diye değiştirilmedi.</p>
 *
 * AiGlobalFeedback: Bir analizin tamamı için üretilen 
 * genel metinsel değerlendirmeyi tutar.
 */
@Entity
@Table(name = "ai_global_feedback")
public class AiGlobalFeedback {

    @Id
    private UUID id;

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    public AiGlobalFeedback() {
    }

    public AiGlobalFeedback(UUID id, UUID reportId, String content) {
        this.id = id;
        this.reportId = reportId;
        this.content = content;
    }

    /* Getter ve Setter Metotları */
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getReportId() { return reportId; }
    public void setReportId(UUID reportId) { this.reportId = reportId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
