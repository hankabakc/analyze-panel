package com.hankabakc.analyzepanel.sitecontent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * SiteContent: Karşılama sitesindeki tek bir doldurulabilir alanın değeri (T-063A / S-026).
 *
 * <p>Anahtar, {@code SiteContentFields} izin listesindeki bir alandır. Değer düz metindir.</p>
 */
@Entity
@Table(name = "site_content")
public class SiteContent {

    @Id
    @Column(name = "content_key", length = 100)
    private String key;

    @Column(name = "content_value", nullable = false, columnDefinition = "TEXT")
    private String value = "";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected SiteContent() {
    }

    public SiteContent(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }
}
