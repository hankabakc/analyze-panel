package com.hankabakc.analyzepanel.analysis.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * AnalysisReport: PDF analizi sonucunda oluşan ana rapor kaydıdır.
 * Bir öğrencinin tek bir sınav karnesine ait tüm verileri bu başlık altında toplanır.
 */
@Entity
@Table(name = "analysis_reports")
public class AnalysisReport {

    @Id
    private UUID id;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "exam_title")
    private String examTitle;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "status", length = 20)
    private String status; // PROCESSING, PENDING_APPROVAL, APPROVED, REJECTED, FAILED

    @Column(name = "intended_exam_count")
    private Integer intendedExamCount; // 5 veya 10

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "raw_ai_response")
    private String rawAiResponse;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "validation_errors")
    private String validationErrors;

    @Column(name = "report_type", length = 20)
    private String reportType; // SINGLE, SUMMARY

    @Column(name = "mentor_feedback", columnDefinition = "TEXT")
    private String mentorFeedback;

    @Column(name = "future_projection", columnDefinition = "TEXT")
    private String futureProjection;

    @Column(name = "strategic_priority", columnDefinition = "TEXT")
    private String strategicPriority;

    @Column(name = "teacher_action_plan", columnDefinition = "TEXT")
    private String teacherActionPlan;

    @Column(name = "cumulative_status", length = 20)
    private String cumulativeStatus; // INCLUDED, NOT_INCLUDED

    /* No-args constructor (JPA için gerekli) */
    public AnalysisReport() {
    }

    /* Full constructor */
    public AnalysisReport(UUID id, UUID studentId, String fileName, String examTitle, Integer intendedExamCount) {
        this.id = id;
        this.studentId = studentId;
        this.fileName = fileName;
        this.examTitle = examTitle;
        this.processedAt = LocalDateTime.now();
        this.status = "PROCESSING";
        this.intendedExamCount = intendedExamCount;
        this.reportType = "SINGLE"; // Varsayılan
        this.cumulativeStatus = "NOT_INCLUDED";
    }

    public String getCumulativeStatus() { return cumulativeStatus; }
    public void setCumulativeStatus(String cumulativeStatus) { this.cumulativeStatus = cumulativeStatus; }

    /* Getter ve Setter Metotları */
    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }

    public String getMentorFeedback() { return mentorFeedback; }
    public void setMentorFeedback(String mentorFeedback) { this.mentorFeedback = mentorFeedback; }

    public String getFutureProjection() { return futureProjection; }
    public void setFutureProjection(String futureProjection) { this.futureProjection = futureProjection; }

    public String getStrategicPriority() { return strategicPriority; }
    public void setStrategicPriority(String strategicPriority) { this.strategicPriority = strategicPriority; }

    public String getTeacherActionPlan() { return teacherActionPlan; }
    public void setTeacherActionPlan(String teacherActionPlan) { this.teacherActionPlan = teacherActionPlan; }

    public Integer getIntendedExamCount() { return intendedExamCount; }
    public void setIntendedExamCount(Integer intendedExamCount) { this.intendedExamCount = intendedExamCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRawAiResponse() { return rawAiResponse; }
    public void setRawAiResponse(String rawAiResponse) { this.rawAiResponse = rawAiResponse; }

    public String getValidationErrors() { return validationErrors; }
    public void setValidationErrors(String validationErrors) { this.validationErrors = validationErrors; }

    /* Getter ve Setter Metotları */
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getExamTitle() { return examTitle; }
    public void setExamTitle(String examTitle) { this.examTitle = examTitle; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AnalysisReport that = (AnalysisReport) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
