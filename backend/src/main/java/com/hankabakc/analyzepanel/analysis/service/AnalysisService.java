package com.hankabakc.analyzepanel.analysis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hankabakc.analyzepanel.analysis.dto.AnalysisResponse;
import com.hankabakc.analyzepanel.analysis.entity.*;
import com.hankabakc.analyzepanel.analysis.repository.*;
import com.hankabakc.analyzepanel.auth.enums.UserRole;
import com.hankabakc.analyzepanel.infrastructure.storage.StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * AnalysisService: Analiz raporlarının yönetimini, dosya yükleme süreçlerini
 * ve öğrenci profil hedeflerini koordine eden servis katmanıdır.
 */
@Service
public class AnalysisService {

    private final AnalysisReportRepository reportRepository;
    private final LessonAnalysisRepository lessonRepository;
    private final TopicDetailRepository topicRepository;
    private final AiGlobalFeedbackRepository feedbackRepository;
    private final StudentProfileRepository profileRepository;
    private final ReferenceSchoolRepository schoolRepository;
    private final StorageService storageService;
    private final SinavzaKarneParser karneParser;
    private final ObjectMapper objectMapper;
    private final jakarta.persistence.EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;
    private final com.hankabakc.analyzepanel.core.security.SecurityUtils securityUtils;
    private final com.hankabakc.analyzepanel.membership.service.MembershipService membershipService;
    private final LgsScoreCalculator lgsCalculator;
    private final PerformanceBandCalculator bandCalculator;
    private final com.hankabakc.analyzepanel.membership.service.ActivityService activityService;

    public AnalysisService(AnalysisReportRepository reportRepository, 
                           LessonAnalysisRepository lessonRepository,
                           TopicDetailRepository topicRepository,
                           AiGlobalFeedbackRepository feedbackRepository,
                           StudentProfileRepository profileRepository,
                           ReferenceSchoolRepository schoolRepository,
                           StorageService storageService,
                           SinavzaKarneParser karneParser,
                           ObjectMapper objectMapper,
                           jakarta.persistence.EntityManager entityManager,
                           TransactionTemplate transactionTemplate,
                           com.hankabakc.analyzepanel.core.security.SecurityUtils securityUtils,
                           com.hankabakc.analyzepanel.membership.service.MembershipService membershipService,
                           LgsScoreCalculator lgsCalculator,
                           PerformanceBandCalculator bandCalculator,
                           com.hankabakc.analyzepanel.membership.service.ActivityService activityService) {
        this.reportRepository = reportRepository;
        this.lessonRepository = lessonRepository;
        this.topicRepository = topicRepository;
        this.feedbackRepository = feedbackRepository;
        this.profileRepository = profileRepository;
        this.schoolRepository = schoolRepository;
        this.storageService = storageService;
        this.karneParser = karneParser;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
        this.transactionTemplate = transactionTemplate;
        this.securityUtils = securityUtils;
        this.membershipService = membershipService;
        this.lgsCalculator = lgsCalculator;
        this.bandCalculator = bandCalculator;
        this.activityService = activityService;
    }

    /**
     * validateAccess: IDOR/BOLA koruması için merkezi yetki ve veri sahipliği kontrolü (T-024 / B-54).
     * 
     * <p>Mühendislik Standartları:</p>
     * <ul>
     *   <li>ENG-11 §3.1: İstemciden gelen hedef kimlik oturumdaki kullanıcının yetki sınırlarıyla doğrulanır.</li>
     *   <li>APP-01 §2.1 & §2.2: Yetki kararı sunucuda verilir; dize yerine enum karşılaştırması kullanılır.</li>
     *   <li>ENG-07 §2: Yetkisiz erişim denemeleri 403 Forbidden, oturumsuz çağrılar 401 Unauthorized döner.</li>
     * </ul>
     */
    private void validateAccess(UUID targetStudentId) {
        var currentUser = securityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Oturum bulunamadı veya geçersiz.");
        }

        if (currentUser.getRole() == UserRole.MANAGER) {
            return;
        }

        if (currentUser.getRole() == UserRole.TEACHER) {
            if (!membershipService.isTeacherOfStudent(currentUser.getId(), targetStudentId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu öğrencinin verilerine erişim yetkiniz yok.");
            }
            return;
        }

        if (currentUser.getRole() == UserRole.STUDENT) {
            if (!currentUser.getId().equals(targetStudentId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sadece kendi verilerinize erişebilirsiniz.");
            }
            return;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu işlem için yetkiniz bulunmamaktadır.");
    }

    @Transactional
    public UUID uploadAndProcess(UUID studentId, MultipartFile file, Integer examCount, String reportType) {
        validateAccess(studentId); // IDOR Kontrolü
        
        String fileName = storageService.store(file);
        // ... geri kalan kod aynı ...

        AnalysisReport report = new AnalysisReport(
            UUID.randomUUID(),
            studentId,
            fileName,
            "Analiz Ediliyor...",
            examCount
        );
        report.setStatus("PROCESSING"); // T-043C: Virtual Thread ayrıştırması sürerken durum PROCESSING olur
        report.setReportType(reportType);
        reportRepository.save(report);

        // Dosya içeriği istek iş parçacığı kapanmadan okunur; MultipartFile'ın geçici
        // dosyası istek bitince silinebileceği için arka plana taşınamaz.
        final byte[] pdfBytes;
        try {
            pdfBytes = file.getBytes();
        } catch (java.io.IOException e) {
            throw new RuntimeException("Yüklenen dosya okunamadı: " + e.getMessage(), e);
        }

        Thread.ofVirtual().start(() -> {
            try {
                // Veri PDF'in kendi metin ve koordinatlarından okunur: dış servis yok,
                // ücret yok, milisaniyeler sürer ve aynı dosya her zaman aynı sonucu verir.
                // Format tanınmazsa ayrıştırıcı hata fırlatır ve rapor "Analiz Başarısız"
                // olarak işaretlenir; öğretmen bunu görür.
                AnalysisResponse data = karneParser.parse(pdfBytes, examCount);
                String raw = objectMapper.writeValueAsString(data);

                String validationErrors = (data.validationErrors() != null && !data.validationErrors().isBlank())
                        ? data.validationErrors()
                        : validateAiData(data);

                transactionTemplate.execute(status -> {
                    saveAnalysisData(report.getId(), data, raw, validationErrors);
                    return null;
                });
            } catch (Exception e) {
                transactionTemplate.execute(status -> {
                    // T-051: Virtual thread ayrı bir transaction'da çalıştığı için ana thread'den
                    // gelen report detached durumdadır. Hibernate'in duplicate key (INSERT) hatası
                    // vermesini önlemek için veritabanından yönetilen entity çekilip güncellenir.
                    var managedReport = reportRepository.findById(report.getId()).orElse(report);
                    managedReport.setStatus("FAILED");
                    managedReport.setExamTitle("Analiz Başarısız");
                    managedReport.setValidationErrors(e.getMessage());
                    reportRepository.save(managedReport);
                    return null;
                });
            }
        });

        return report.getId();
    }

    @Transactional
    public void removeReport(UUID reportId) {
        AnalysisReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rapor bulunamadı."));
        validateAccess(report.getStudentId()); // T-024: Rapor silme öncesinde sahiplik/eşleşme doğrulaması
        reportRepository.delete(report);
    }


    @Transactional
    protected void saveAnalysisData(UUID reportId, AnalysisResponse data, String rawResponse, String validationErrors) {
        AnalysisReport report = reportRepository.findById(reportId).orElseThrow();
        report.setExamTitle(data.examTitle());
        report.setRawAiResponse(rawResponse);
        report.setValidationErrors(validationErrors);
        report.setReportType(data.reportType());
        report.setIntendedExamCount(data.intendedExamCount());
        report.setMentorFeedback(data.mentorFeedback());
        report.setFutureProjection(data.futureProjection());
        report.setStrategicPriority(data.strategicPriority());
        report.setTeacherActionPlan(data.teacherActionPlan());
        report.setStatus("PENDING_APPROVAL"); // T-043C: Ayrıştırma başarıyla tamamlandığında onay bekliyor durumuna geçer
        reportRepository.save(report);

        if (data.examList() != null) {
            for (var exam : data.examList()) {
                entityManager.createNativeQuery(
                    "INSERT INTO exam_summaries (id, report_id, exam_name, exam_date, total_score) VALUES (:id, :rid, :name, :date, :score)")
                    .setParameter("id", UUID.randomUUID()).setParameter("rid", reportId)
                    .setParameter("name", exam.examName()).setParameter("date", ExamDate.parse(exam.examDate())).setParameter("score", exam.totalScore()).executeUpdate();
            }
        }

        if (data.consolidatedResult() != null && data.consolidatedResult().lessons() != null) {
            for (var lessonDto : data.consolidatedResult().lessons()) {
                LessonAnalysis lesson = new LessonAnalysis(UUID.randomUUID(), reportId, lessonDto.lessonName(), lessonDto.correct(), lessonDto.wrong(), lessonDto.empty(), lessonDto.successRate());
                lessonRepository.save(lesson);
                if (lessonDto.topics() != null) {
                    for (var topicDto : lessonDto.topics()) {
                        TopicDetail topic = new TopicDetail(UUID.randomUUID(), lesson.getId(), topicDto.topicName(), topicDto.status(), topicDto.aiSuggestion(), topicDto.totalQuestions(), topicDto.correctCount(), topicDto.wrongCount());
                        topicRepository.save(topic);
                    }
                }
            }
        }

        AiGlobalFeedback feedback = new AiGlobalFeedback(UUID.randomUUID(), reportId, data.globalFeedback());
        feedbackRepository.save(feedback);
    }

    private String validateAiData(AnalysisResponse data) {
        StringBuilder errors = new StringBuilder();
        if (data.consolidatedResult() != null && data.consolidatedResult().lessons() != null) {
            for (var lesson : data.consolidatedResult().lessons()) {
                int totalCalculated = (lesson.correct() != null ? lesson.correct() : 0) + (lesson.wrong() != null ? lesson.wrong() : 0) + (lesson.empty() != null ? lesson.empty() : 0);
                if (totalCalculated == 0) errors.append(String.format("[%s] Soru sayısı 0. ", lesson.lessonName()));
            }
        }
        return errors.toString().trim();
    }

    public List<ReferenceSchool> searchSchools(String query) {
        return schoolRepository.searchSchools(query);
    }

    public StudentProfile getStudentProfile(UUID userId) {
        return profileRepository.findByUserId(userId)
                .orElseGet(() -> profileRepository.save(new StudentProfile(UUID.randomUUID(), userId)));
    }

    /**
     * getStudentProfileView: Öğrencinin hedefini okunur hâlde döner (T-065).
     *
     * <p>Okuma yan etkisizdir: profil yoksa kayıt oluşturulmaz, boş hedef döner (ENG-07 §1.1). Okul seçiliyse
     * adı ve elle puan girilmemişse taban puanı okul kaydından gelir — analiz detayıyla aynı kaynak.</p>
     */
    @Transactional(readOnly = true)
    public com.hankabakc.analyzepanel.analysis.dto.StudentProfileResponse getStudentProfileView(UUID userId) {
        StudentProfile profile = profileRepository.findByUserId(userId).orElse(null);
        if (profile == null) {
            return new com.hankabakc.analyzepanel.analysis.dto.StudentProfileResponse(null, null, null);
        }
        ReferenceSchool school = profile.getTargetSchoolId() == null ? null
                : schoolRepository.findById(profile.getTargetSchoolId()).orElse(null);
        java.math.BigDecimal score = profile.getTargetScore() != null ? profile.getTargetScore()
                : school != null ? school.getBaseScore() : null;
        return new com.hankabakc.analyzepanel.analysis.dto.StudentProfileResponse(
                profile.getTargetSchoolId(), school != null ? school.getSchoolName() : null, score);
    }

    @Transactional
    public void updateStudentProfile(UUID userId, UUID schoolId, java.math.BigDecimal manualScore) {
        StudentProfile profile = getStudentProfile(userId);
        profile.setTargetSchoolId(schoolId);
        profile.setTargetScore(manualScore);
        profile.setLastUpdatedAt(java.time.LocalDateTime.now());
        profileRepository.save(profile);
    }

    @Transactional(readOnly = true)
    public List<AnalysisReport> getStudentReportsForTeacher(UUID studentId) {
        validateAccess(studentId); // T-024: Öğretmen-öğrenci eşleşmesi doğrulanır
        return reportRepository.findAllByStudentIdOrderByProcessedAtDesc(studentId);
    }

    @Transactional(readOnly = true)
    public List<AnalysisReport> getStudentReportsForStudent(UUID studentId) { 
        return reportRepository.findAllByStudentIdOrderByProcessedAtDesc(studentId).stream()
                .filter(r -> "APPROVED".equals(r.getStatus())).toList(); 
    }

    @Transactional
    public String mergeReportsToCumulative(UUID studentId) {
        validateAccess(studentId); // IDOR Kontrolü
        List<AnalysisReport> pendingReports = reportRepository.findAllByStudentIdOrderByProcessedAtDesc(studentId)
                .stream().filter(r -> "APPROVED".equals(r.getStatus()) && "NOT_INCLUDED".equals(r.getCumulativeStatus())).toList();
        if (pendingReports.isEmpty()) return generateGlobalStrategicSummary(studentId);
        for (var report : pendingReports) {
            report.setCumulativeStatus("INCLUDED");
            reportRepository.save(report);
        }
        return generateGlobalStrategicSummary(studentId);
    }

    @Transactional(readOnly = true)
    public AnalysisResponse getStudentCumulativeProfile(UUID studentId) {
        validateAccess(studentId); // IDOR Kontrolü

        StudentProfile profile = profileRepository.findByUserId(studentId).orElse(null);
        String targetName = null; java.math.BigDecimal targetScore = null;
        if (profile != null && profile.getTargetSchoolId() != null) {
            ReferenceSchool school = schoolRepository.findById(profile.getTargetSchoolId()).orElse(null);
            if (school != null) { targetName = school.getSchoolName(); targetScore = school.getBaseScore(); }
        }

        @SuppressWarnings("unchecked")
        List<Object[]> approvedReports = entityManager.createNativeQuery(
            "SELECT ar.id FROM analysis_reports ar WHERE ar.student_id = :sid AND ar.status = 'APPROVED'")
            .setParameter("sid", studentId).getResultList();

        if (approvedReports.isEmpty()) {
            return new AnalysisResponse(null, null, "GENEL GELİŞİM DOSYASI", java.time.LocalDateTime.now(), "APPROVED", 0, null, "SUMMARY", "Henüz onaylanmış bir analiz raporu bulunmuyor.", null, "Henüz onaylanmış rapor bulunmuyor.", null, targetName, targetScore, List.of(), new AnalysisResponse.ConsolidatedResultDto(List.of()), "Henüz onaylanmış bir analiz raporu bulunmuyor.", new AnalysisResponse.TopicTrendDataDto(List.of(), List.of(), List.of(), List.of()), new AnalysisResponse.PriorityListsDto(List.of(), List.of()), null);
        }

        @SuppressWarnings("unchecked")
        List<Object[]> allExamsRows = entityManager.createNativeQuery(
            "SELECT es.exam_name, es.exam_date, es.total_score FROM exam_summaries es " +
            "JOIN analysis_reports ar ON es.report_id = ar.id WHERE ar.student_id = :sid AND ar.status = 'APPROVED' ORDER BY es.exam_date ASC")
            .setParameter("sid", studentId).getResultList();

        List<AnalysisResponse.ExamSummaryDto> examList = allExamsRows.stream()
            .map(row -> new AnalysisResponse.ExamSummaryDto((String) row[0], ExamDate.format(row[1]), (java.math.BigDecimal) row[2])).toList();

        List<AnalysisResponse.LessonDto> lessonDtos = cumulativeLessonDtos(studentId);

        // 4. KONU BAZLI TREND ANALİZİ (KRONİK MOTORU)
        AnalysisResponse.TopicTrendDataDto trendData = calculateTopicTrendData(studentId);
        java.util.Set<String> chronicSet = new java.util.HashSet<>(trendData.chronicTopics());
        AnalysisResponse.TargetComparisonDto targetComparison = calculateTargetComparison(targetName, targetScore, examList);

        return new AnalysisResponse(null, null, "GENEL GELİŞİM DOSYASI", java.time.LocalDateTime.now(), "APPROVED", examList.size(), null, "SUMMARY", null, null, null, null, targetName, targetScore, examList, new AnalysisResponse.ConsolidatedResultDto(lessonDtos), "Öğrencinin tüm zamanlardaki performans özetidir.", trendData, TopicPriority.of(lessonDtos, lgsCalculator.getPuanPerNet(), chronicSet), targetComparison);
    }

    /**
     * cumulativeLessonDtos: Öğrencinin tüm onaylı raporlarındaki ders ve konu toplamlarını üretir (T-027).
     *
     * <p><b>B-58/B-59:</b> Bu blok eskiden iki ayrı yerde birebir aynı gövdeyle duruyordu ve her ikisi de
     * ders başına iki sorgu açıyordu (1 + 2N istek). Artık tek bir yerde ve iki düz sorguda toplanıyor;
     * gruplama bellekte yapılıyor. Sorgu sayısı ders sayısından bağımsızdır (ENG-01 §1.2, ENG-06 §3).</p>
     */
    private List<AnalysisResponse.LessonDto> cumulativeLessonDtos(UUID studentId) {
        // T-027 (B-59): Kümülatif toplama eskiden ders başına iki ayrı sorgu açıyordu
        // (ders adları + her ders için özet + her ders için konu kırılımı = 1 + 2N istek).
        // Artık iki sorgu var: biri ders toplamları, biri konu toplamları. Gruplama bellekte.
        @SuppressWarnings("unchecked")
        List<Object[]> lessonRows = entityManager.createNativeQuery(
            "SELECT la.lesson_name, SUM(la.correct_count), SUM(la.wrong_count), SUM(la.empty_count), AVG(la.success_rate) " +
            "FROM lesson_analyses la JOIN analysis_reports ar ON la.report_id = ar.id " +
            "WHERE ar.student_id = :sid AND ar.status = 'APPROVED' " +
            "GROUP BY la.lesson_name")
            .setParameter("sid", studentId).getResultList();

        @SuppressWarnings("unchecked")
        List<Object[]> topicRows = entityManager.createNativeQuery(
            "SELECT la.lesson_name, td.topic_name, " +
            "       COALESCE(SUM(td.total_questions), 0), " +
            "       COALESCE(SUM(td.correct_count), 0), " +
            "       COALESCE(SUM(td.wrong_count), 0) " +
            "FROM topic_details td " +
            "JOIN lesson_analyses la ON td.lesson_analysis_id = la.id " +
            "JOIN analysis_reports ar ON la.report_id = ar.id " +
            "WHERE ar.student_id = :sid AND ar.status = 'APPROVED' " +
            "GROUP BY la.lesson_name, td.topic_name " +
            "ORDER BY la.lesson_name ASC, td.topic_name ASC")
            .setParameter("sid", studentId).getResultList();

        // Konular ders adına göre gruplanır; sorgu zaten ders+konu adına göre sıralı geldiği
        // için eklenme sırası korunur (LinkedHashMap).
        java.util.Map<String, List<AnalysisResponse.TopicDto>> topicsByLesson = new java.util.LinkedHashMap<>();
        for (Object[] tRow : topicRows) {
            String lessonName = (String) tRow[0];
            String tName = (String) tRow[1];
            int tTotal = ((Number) tRow[2]).intValue();
            int tCorrect = ((Number) tRow[3]).intValue();
            int tWrong = ((Number) tRow[4]).intValue();
            topicsByLesson.computeIfAbsent(lessonName, k -> new java.util.ArrayList<>())
                    .add(new AnalysisResponse.TopicDto(
                            UUID.randomUUID(), tName, TopicStatus.of(tTotal, tCorrect, tWrong), null,
                            tTotal, tCorrect, tWrong, bandCalculator.calculateBand(tTotal, tCorrect)));
        }

        List<AnalysisResponse.LessonDto> lessonDtos = lessonRows.stream().map(row -> {
            String lname = (String) row[0];
            int correct = toInt(row[1]);
            int wrong = toInt(row[2]);
            int empty = toInt(row[3]);
            java.math.BigDecimal success = row[4] != null
                    ? java.math.BigDecimal.valueOf(((Number) row[4]).doubleValue())
                    : java.math.BigDecimal.ZERO;

            return new AnalysisResponse.LessonDto(UUID.randomUUID(), lname, correct, wrong, empty, success,
                    topicsByLesson.getOrDefault(lname, List.of()));
        }).sorted(Comparator
                .comparing((AnalysisResponse.LessonDto l) -> l.successRate() != null ? l.successRate() : BigDecimal.ZERO, Comparator.reverseOrder())
                .thenComparing(AnalysisResponse.LessonDto::lessonName, String.CASE_INSENSITIVE_ORDER)
        ).toList();
        return lessonDtos;
    }

    /**
     * calculateTargetComparison: Öğrencinin sınav net ortalamasını LGS ölçeğine çevirip
     * hedef okul ile karşılaştırır (T-028 / APP-01 §2.1).
     */
    private AnalysisResponse.TargetComparisonDto calculateTargetComparison(
            String targetName, java.math.BigDecimal targetScore, List<AnalysisResponse.ExamSummaryDto> examList) {
        if (targetName == null || targetScore == null || examList == null || examList.isEmpty()) {
            return null;
        }
        java.util.OptionalDouble averageNet = averageNet(examList);
        if (averageNet.isEmpty()) {
            return null;
        }
        return lgsCalculator.compareWithTarget(targetName, targetScore, averageNet.getAsDouble());
    }

    /**
     * averageNet: Sınav listesinin net ortalamasını verir (T-028B).
     *
     * <p>Puanı okunamamış (null) sınav ortalamaya <b>katılmaz</b>. Eskiden 0 net sayılıp katılıyordu;
     * tek bir okunamayan sınav öğrencinin tahmini LGS puanını sessizce aşağı çekiyordu (IST-02 §2).
     * Hiç geçerli puan yoksa boş döner ve çağıran hedef karşılaştırması üretmez.</p>
     */
    static java.util.OptionalDouble averageNet(List<AnalysisResponse.ExamSummaryDto> examList) {
        if (examList == null) {
            return java.util.OptionalDouble.empty();
        }
        return examList.stream()
                .filter(e -> e.totalScore() != null)
                .mapToDouble(e -> e.totalScore().doubleValue())
                .average();
    }

    private AnalysisResponse.TopicTrendDataDto calculateTopicTrendData(UUID studentId) {
        @SuppressWarnings("unchecked")
        List<Object[]> topicRows = entityManager.createNativeQuery(
            "SELECT la.lesson_name, td.topic_name, td.status, ar.processed_at " +
            "FROM topic_details td " +
            "JOIN lesson_analyses la ON td.lesson_analysis_id = la.id " +
            "JOIN analysis_reports ar ON la.report_id = ar.id " +
            "WHERE ar.student_id = :sid AND ar.status = 'APPROVED' " +
            "ORDER BY td.topic_name, ar.processed_at ASC")
            .setParameter("sid", studentId).getResultList();

        java.util.Map<String, List<String>> topicMap = new java.util.HashMap<>();
        java.util.Map<String, String> topicToLesson = new java.util.HashMap<>();

        for (Object[] row : topicRows) {
            String lesson = (String) row[0];
            String topic = (String) row[1];
            String status = (String) row[2];
            topicMap.computeIfAbsent(topic, k -> new java.util.ArrayList<>()).add(status);
            topicToLesson.put(topic, lesson);
        }

        List<AnalysisResponse.TopicHistoryDto> heatmap = new java.util.ArrayList<>();
        List<String> chronic = new java.util.ArrayList<>();
        List<String> improved = new java.util.ArrayList<>();
        List<String> inconsistent = new java.util.ArrayList<>();

        topicMap.forEach((topic, history) -> {
            heatmap.add(new AnalysisResponse.TopicHistoryDto(topicToLesson.get(topic), topic, history));
            
            if (history.size() >= 2) {
                String last = history.get(history.size() - 1);
                String prev = history.get(history.size() - 2);

                if ("WRONG".equals(last) && "WRONG".equals(prev)) {
                    chronic.add(topic);
                } else if ("CORRECT".equals(last) && "WRONG".equals(prev)) {
                    improved.add(topic);
                } else if (history.contains("WRONG") && history.contains("CORRECT")) {
                    inconsistent.add(topic);
                }
            }
        });

        return new AnalysisResponse.TopicTrendDataDto(heatmap, chronic, improved, inconsistent);
    }

    @Transactional
    public void approveReport(UUID reportId) {
        AnalysisReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rapor bulunamadı."));
        validateAccess(report.getStudentId()); // IDOR Kontrolü

        // T-043B / B-81: Ayrıştırması başarısız olan veya onay beklemeyen raporlar onaylanamaz.
        if ("FAILED".equals(report.getStatus()) || (report.getExamTitle() != null && report.getExamTitle().startsWith("Analiz Başarısız"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ayrıştırması başarısız olan bir analiz raporu onaylanamaz.");
        }
        // T-043C: Ayrıştırması henüz sürmekte olan raporlar onaylanamaz (APP-01 §2.7).
        if ("PROCESSING".equals(report.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ayrıştırması henüz tamamlanmamış bir rapor onaylanamaz.");
        }
        if (!"PENDING_APPROVAL".equals(report.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Yalnızca onay bekleyen raporlar onaylanabilir.");
        }

        report.setStatus("APPROVED");
        reportRepository.save(report);
    }

    @Transactional
    public void rejectReport(UUID reportId) {
        AnalysisReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rapor bulunamadı."));
        validateAccess(report.getStudentId()); // IDOR Kontrolü
        // T-043C: Ayrıştırması henüz sürmekte olan rapor reddedilemez.
        if ("PROCESSING".equals(report.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ayrıştırması henüz tamamlanmamış bir rapor reddedilemez.");
        }
        report.setStatus("REJECTED");
        reportRepository.save(report);
    }

    /**
     * generateGlobalStrategicSummary: Onaylanmış tüm raporlardan öğrencinin genel
     * durumunu özetler.
     *
     * <p>Özet doğrudan veritabanındaki sayılardan hesaplanır; dış bir servise
     * gidilmez. Ders başarı oranları ve en çok yanlış yapılan konular zaten
     * veride durduğu için önerinin kendisi de veriden çıkar.</p>
     */
    @Transactional(readOnly = true)
    public String generateGlobalStrategicSummary(UUID studentId) {
        validateAccess(studentId); // IDOR Kontrolü

        @SuppressWarnings("unchecked")
        List<Object[]> lessonRows = entityManager.createNativeQuery(
            "SELECT la.lesson_name, SUM(la.correct_count), SUM(la.wrong_count), SUM(la.empty_count) " +
            "FROM lesson_analyses la JOIN analysis_reports ar ON la.report_id = ar.id " +
            "WHERE ar.student_id = :sid AND ar.status = 'APPROVED' " +
            "GROUP BY la.lesson_name")
            .setParameter("sid", studentId).getResultList();

        if (lessonRows.isEmpty()) return "Henüz onaylanmış bir analiz raporu bulunmuyor.";

        record LessonStat(String name, int correct, int total) {
            double rate() { return total == 0 ? 0 : correct * 100.0 / total; }
        }
        List<LessonStat> stats = new java.util.ArrayList<>();
        int totalQuestions = 0;
        int totalCorrect = 0;
        for (Object[] row : lessonRows) {
            int correct = toInt(row[1]);
            int total = correct + toInt(row[2]) + toInt(row[3]);
            stats.add(new LessonStat((String) row[0], correct, total));
            totalQuestions += total;
            totalCorrect += correct;
        }
        stats.sort(java.util.Comparator.comparingDouble(LessonStat::rate));

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Onaylanmış denemelerde toplam %d soruda %d doğru (%%%.0f genel başarı).%n%n",
                totalQuestions, totalCorrect, totalQuestions == 0 ? 0 : totalCorrect * 100.0 / totalQuestions));

        LessonStat weakest = stats.get(0);
        LessonStat strongest = stats.get(stats.size() - 1);
        sb.append(String.format("En çok desteğe ihtiyaç duyulan ders %s (%%%.0f).%n",
                weakest.name(), weakest.rate()));
        if (stats.size() > 1) {
            sb.append(String.format("En güçlü ders ise %s (%%%.0f).%n", strongest.name(), strongest.rate()));
        }

        @SuppressWarnings("unchecked")
        List<Object[]> topicRows = entityManager.createNativeQuery(
            "SELECT la.lesson_name, td.topic_name, SUM(td.wrong_count) AS yanlis " +
            "FROM topic_details td " +
            "JOIN lesson_analyses la ON td.lesson_analysis_id = la.id " +
            "JOIN analysis_reports ar ON la.report_id = ar.id " +
            "WHERE ar.student_id = :sid AND ar.status = 'APPROVED' AND td.wrong_count > 0 " +
            "GROUP BY la.lesson_name, td.topic_name " +
            "ORDER BY yanlis DESC LIMIT 5")
            .setParameter("sid", studentId).getResultList();

        if (!topicRows.isEmpty()) {
            sb.append("\nTekrar edilmesi gereken konular:\n");
            for (Object[] row : topicRows) {
                sb.append(String.format("• %s — %s (%d yanlış)%n", row[0], row[1], toInt(row[2])));
            }
        }
        return sb.toString().trim();
    }

    private int toInt(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }


    @Transactional(readOnly = true)
    public AnalysisResponse getAnalysisDetail(UUID reportId, boolean cumulative) {
        AnalysisReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rapor bulunamadı."));

        // T-037: Kimin hangi öğrencinin raporuna baktığı kaydedilir. Yetki kontrolünden SONRA
        // yazılır (aşağıdaki validateAccess) ve kendi transaction'ında çalışır; kaydın hatası
        // raporun açılmasını engellemez.
        validateAccess(report.getStudentId()); // IDOR Kontrolü

        var izleyen = securityUtils.getCurrentUser();
        if (!cumulative && izleyen != null && izleyen.getRole() == UserRole.STUDENT && !"APPROVED".equals(report.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Yalnızca onaylanmış analiz raporlarını görüntüleyebilirsiniz.");
        }
        if (izleyen != null) {
            activityService.recordReportView(izleyen.getId(), report.getStudentId(), report.getId());
        }
        
        UUID studentId = report.getStudentId();
        StudentProfile profile = profileRepository.findByUserId(studentId).orElse(null);
        String targetName = null; java.math.BigDecimal targetScore = null;
        if (profile != null && profile.getTargetSchoolId() != null) {
            ReferenceSchool school = schoolRepository.findById(profile.getTargetSchoolId()).orElse(null);
            if (school != null) { targetName = school.getSchoolName(); targetScore = school.getBaseScore(); }
        }

        if (cumulative) {
            // T-025 / B-55 & B-56: Kümülatif mod yalnızca ONAYLI ('APPROVED') raporları toplar.
            // Onaylı rapor yoksa tekil raporun sayılarına geri düşülmez (B-55); boş ve bilgilendirici durum dönülür.
            @SuppressWarnings("unchecked")
            List<Object[]> approvedReports = entityManager.createNativeQuery(
                "SELECT ar.id FROM analysis_reports ar WHERE ar.student_id = :sid AND ar.status = 'APPROVED'")
                .setParameter("sid", studentId).getResultList();

            if (approvedReports.isEmpty()) {
                // Onaylı rapor yok durumu (B-55)
                return new AnalysisResponse(
                    report.getId(),
                    report.getFileName(),
                    "GELİŞİM DOSYASI (Onaylı Rapor Bulunmuyor)",
                    report.getProcessedAt(),
                    report.getStatus(),
                    0,
                    null,
                    "SUMMARY",
                    "Henüz onaylanmış bir analiz raporu bulunmuyor.",
                    null,
                    "Henüz onaylanmış rapor bulunmuyor.",
                    "Öğrenciye ait onaylanmış analiz raporu bulunmadığı için gelişim dosyası oluşturulamadı.",
                    targetName,
                    targetScore,
                    List.of(),
                    new AnalysisResponse.ConsolidatedResultDto(List.of()),
                    "Henüz onaylanmış bir analiz raporu bulunmuyor.",
                    null,
                    new AnalysisResponse.PriorityListsDto(List.of(), List.of()),
                    null
                );
            }

            @SuppressWarnings("unchecked")
            List<Object[]> approvedExamsRows = entityManager.createNativeQuery(
                "SELECT es.exam_name, es.exam_date, es.total_score " +
                "FROM exam_summaries es " +
                "JOIN analysis_reports ar ON es.report_id = ar.id " +
                "WHERE ar.student_id = :sid AND ar.status = 'APPROVED' " +
                "ORDER BY es.exam_date ASC")
                .setParameter("sid", studentId).getResultList();

            List<AnalysisResponse.ExamSummaryDto> examList = approvedExamsRows.stream()
                .map(row -> new AnalysisResponse.ExamSummaryDto((String) row[0], ExamDate.format(row[1]), (java.math.BigDecimal) row[2]))
                .toList();

            List<AnalysisResponse.LessonDto> lessonDtos = cumulativeLessonDtos(studentId);

            AnalysisResponse.TopicTrendDataDto trendData = calculateTopicTrendData(studentId);
            java.util.Set<String> chronicSet = new java.util.HashSet<>(trendData.chronicTopics());
            String globalFeedback = generateGlobalStrategicSummary(studentId);
            AnalysisResponse.PriorityListsDto priorityLists = TopicPriority.of(lessonDtos, lgsCalculator.getPuanPerNet(), chronicSet);
            String strategicPriority = TopicPriority.strategicPriority(lessonDtos);
            String teacherActionPlan = TopicPriority.teacherActionPlan(trendData.chronicTopics(), priorityLists.byPoints());
            AnalysisResponse.TargetComparisonDto targetComparison = calculateTargetComparison(targetName, targetScore, examList);

            return new AnalysisResponse(
                report.getId(),
                report.getFileName(),
                "GELİŞİM DOSYASI",
                report.getProcessedAt(),
                "APPROVED",
                examList.size(),
                null,
                "SUMMARY",
                globalFeedback,
                null,
                strategicPriority,
                teacherActionPlan,
                targetName,
                targetScore,
                examList,
                new AnalysisResponse.ConsolidatedResultDto(lessonDtos),
                globalFeedback,
                trendData,
                priorityLists,
                targetComparison
            );
        }

        // Tekil Karne Modu (cumulative == false)
        @SuppressWarnings("unchecked")
        List<Object[]> examRows = entityManager.createNativeQuery(
            "SELECT exam_name, exam_date, total_score FROM exam_summaries WHERE report_id = :rid ORDER BY exam_date ASC")
            .setParameter("rid", reportId).getResultList();
        List<AnalysisResponse.ExamSummaryDto> examList = examRows.stream()
            .map(row -> new AnalysisResponse.ExamSummaryDto((String) row[0], ExamDate.format(row[1]), (java.math.BigDecimal) row[2]))
            .toList();

        List<LessonAnalysis> currentLessons = lessonRepository.findAllByReportId(reportId);
        List<AnalysisResponse.LessonDto> lessonDtos = currentLessons.stream().map(lesson -> {
            int correct = lesson.getCorrectCount();
            int wrong = lesson.getWrongCount();
            int empty = lesson.getEmptyCount();
            java.math.BigDecimal success = lesson.getSuccessRate();

            List<TopicDetail> topics = topicRepository.findAllByLessonAnalysisId(lesson.getId());
            List<AnalysisResponse.TopicDto> topicDtos = topics.stream().map(topic ->
                new AnalysisResponse.TopicDto(
                    topic.getId(),
                    topic.getTopicName(),
                    topic.getStatus(),
                    topic.getAiSuggestion(),
                    topic.getTotalQuestions(),
                    topic.getCorrectCount(),
                    topic.getWrongCount(),
                    bandCalculator.calculateBand(topic.getTotalQuestions(), topic.getCorrectCount())
                )
            ).toList();

            return new AnalysisResponse.LessonDto(lesson.getId(), lesson.getLessonName(), correct, wrong, empty, success, topicDtos);
        }).sorted(Comparator
                .comparing((AnalysisResponse.LessonDto l) -> l.successRate() != null ? l.successRate() : BigDecimal.ZERO, Comparator.reverseOrder())
                .thenComparing(AnalysisResponse.LessonDto::lessonName, String.CASE_INSENSITIVE_ORDER)
        ).toList();

        String globalFeedback = feedbackRepository.findByReportId(reportId).map(AiGlobalFeedback::getContent).orElse("Hazırlanıyor...");
        AnalysisResponse.TargetComparisonDto targetComparison = calculateTargetComparison(targetName, targetScore, examList);
        AnalysisResponse.PriorityListsDto priorityLists = TopicPriority.of(lessonDtos, lgsCalculator.getPuanPerNet());
        String strategicPriority = report.getStrategicPriority() != null
                ? report.getStrategicPriority()
                : TopicPriority.strategicPriority(lessonDtos);
        String teacherActionPlan = report.getTeacherActionPlan() != null
                ? report.getTeacherActionPlan()
                : TopicPriority.teacherActionPlan(List.of(), priorityLists.byPoints());

        return new AnalysisResponse(
            report.getId(),
            report.getFileName(),
            report.getExamTitle(),
            report.getProcessedAt(),
            report.getStatus(),
            report.getIntendedExamCount(),
            report.getValidationErrors(),
            report.getReportType(),
            report.getMentorFeedback(),
            report.getFutureProjection(),
            strategicPriority,
            teacherActionPlan,
            targetName,
            targetScore,
            examList,
            new AnalysisResponse.ConsolidatedResultDto(lessonDtos),
            globalFeedback,
            null,
            priorityLists,
            targetComparison
        );
    }
}
