package com.hankabakc.analyzepanel.psychtest.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * PsychResultViewId: psych_result_views tablosu için birleşik birincil anahtar (Composite Primary Key).
 * 
 * Standartlar:
 * - Pure Java: Lombok yasaktır, tüm metotlar ve yapıcılar manuel yazılır.
 * - (assignment_id, viewer_id) çifti her inceleyen kişinin kendine ait görüldü damgasını bağımsız kılar (T-062).
 */
@Embeddable
public class PsychResultViewId implements Serializable {

    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;

    @Column(name = "viewer_id", nullable = false)
    private UUID viewerId;

    public PsychResultViewId() {
    }

    public PsychResultViewId(UUID assignmentId, UUID viewerId) {
        this.assignmentId = assignmentId;
        this.viewerId = viewerId;
    }

    public UUID getAssignmentId() {
        return assignmentId;
    }

    public void setAssignmentId(UUID assignmentId) {
        this.assignmentId = assignmentId;
    }

    public UUID getViewerId() {
        return viewerId;
    }

    public void setViewerId(UUID viewerId) {
        this.viewerId = viewerId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PsychResultViewId that = (PsychResultViewId) o;
        return Objects.equals(assignmentId, that.assignmentId) &&
                Objects.equals(viewerId, that.viewerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(assignmentId, viewerId);
    }
}
