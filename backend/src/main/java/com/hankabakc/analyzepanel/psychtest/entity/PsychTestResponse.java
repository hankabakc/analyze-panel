package com.hankabakc.analyzepanel.psychtest.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * PsychTestResponse: Öğrencinin ölçekteki her bir maddeye verdiği yanıtı saklayan JPA varlığı.
 * Pure Java Politikası: Lombok kullanılmamıştır.
 */
@Entity
@Table(name = "psych_test_responses")
public class PsychTestResponse {

    @Id
    private UUID id;

    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;

    @Column(name = "item_no", nullable = false)
    private Integer itemNo;

    @Column(name = "answer", nullable = false)
    private Integer answer;

    public PsychTestResponse() {
    }

    public PsychTestResponse(UUID id, UUID assignmentId, Integer itemNo, Integer answer) {
        this.id = id;
        this.assignmentId = assignmentId;
        this.itemNo = itemNo;
        this.answer = answer;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAssignmentId() {
        return assignmentId;
    }

    public void setAssignmentId(UUID assignmentId) {
        this.assignmentId = assignmentId;
    }

    public Integer getItemNo() {
        return itemNo;
    }

    public void setItemNo(Integer itemNo) {
        this.itemNo = itemNo;
    }

    public Integer getAnswer() {
        return answer;
    }

    public void setAnswer(Integer answer) {
        this.answer = answer;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PsychTestResponse that = (PsychTestResponse) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
