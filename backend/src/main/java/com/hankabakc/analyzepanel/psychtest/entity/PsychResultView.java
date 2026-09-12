package com.hankabakc.analyzepanel.psychtest.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * PsychResultView: Psikolojik test tamamlama sonucunun bir kullanıcı (öğretmen veya yönetici)
 * tarafından ne zaman görüntülendiğini saklayan JPA varlığı (T-062).
 * 
 * Standartlar:
 * - Pure Java: Lombok yasaktır.
 * - APP-03 §4: Özel Nitelikli Kişisel Veri Açık İstisnası.
 * - Görüldü kaydı kişi başına bağımsızdır: Bir öğretmenin görmesi diğer öğretmenin veya yöneticinin
 *   sayacını etkilemez.
 */
@Entity
@Table(name = "psych_result_views")
public class PsychResultView {

    @EmbeddedId
    private PsychResultViewId id;

    @Column(name = "seen_at", nullable = false)
    private Instant seenAt;

    public PsychResultView() {
    }

    public PsychResultView(PsychResultViewId id, Instant seenAt) {
        this.id = id;
        this.seenAt = seenAt;
    }

    public PsychResultViewId getId() {
        return id;
    }

    public void setId(PsychResultViewId id) {
        this.id = id;
    }

    public Instant getSeenAt() {
        return seenAt;
    }

    public void setSeenAt(Instant seenAt) {
        this.seenAt = seenAt;
    }
}
