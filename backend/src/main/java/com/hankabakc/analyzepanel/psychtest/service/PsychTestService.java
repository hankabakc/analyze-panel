package com.hankabakc.analyzepanel.psychtest.service;

import com.hankabakc.analyzepanel.auth.entity.AppUser;
import com.hankabakc.analyzepanel.auth.enums.UserRole;
import com.hankabakc.analyzepanel.auth.repository.AppUserRepository;
import com.hankabakc.analyzepanel.core.security.SecurityUtils;
import com.hankabakc.analyzepanel.membership.service.MembershipService;
import com.hankabakc.analyzepanel.psychtest.dto.AssignPsychTestRequest;
import com.hankabakc.analyzepanel.psychtest.dto.AssignPsychTestResponse;
import com.hankabakc.analyzepanel.psychtest.dto.BourdonBlockScoreDto;
import com.hankabakc.analyzepanel.psychtest.dto.BourdonMarkedCell;
import com.hankabakc.analyzepanel.psychtest.dto.BourdonResultResponse;
import com.hankabakc.analyzepanel.psychtest.dto.BourdonStartResponse;
import com.hankabakc.analyzepanel.psychtest.dto.BourdonSubmissionRequest;
import com.hankabakc.analyzepanel.psychtest.dto.MyPsychTestAssignmentResponse;
import com.hankabakc.analyzepanel.psychtest.dto.PsychTestAnswerItemDto;
import com.hankabakc.analyzepanel.psychtest.dto.PsychTestResultResponse;
import com.hankabakc.analyzepanel.psychtest.dto.PsychTestScaleInfoDto;
import com.hankabakc.analyzepanel.psychtest.dto.PsychTestSubmitResponse;
import com.hankabakc.analyzepanel.psychtest.dto.SubmitPsychTestRequest;
import com.hankabakc.analyzepanel.psychtest.dto.UnseenPsychTestCountsResponse;
import com.hankabakc.analyzepanel.psychtest.entity.BourdonResponse;
import com.hankabakc.analyzepanel.psychtest.entity.PsychTestAssignment;
import com.hankabakc.analyzepanel.psychtest.entity.PsychTestResponse;
import com.hankabakc.analyzepanel.psychtest.entity.PsychTestStatus;
import com.hankabakc.analyzepanel.psychtest.repository.BourdonResponseRepository;
import com.hankabakc.analyzepanel.psychtest.repository.PsychResultViewRepository;
import com.hankabakc.analyzepanel.psychtest.repository.PsychTestAssignmentRepository;
import com.hankabakc.analyzepanel.psychtest.repository.PsychTestResponseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * PsychTestService: Psikolojik ölçeklerin (STAI ve Bourdon Dikkat Testi) atama, doldurma,
 * puanlama ve sonuç görüntüleme iş mantığını merkezi olarak yönetir.
 * 
 * Güvenlik ve Gizlilik Prensipleri:
 * - APP-03 §4: Özel Nitelikli Veri (Ruh Sağlığı/Psikolojik Testler) Açık İstisnası.
 * - Yalnızca YÖNETİCİ test atayabilir.
 * - Öğrenci kendi puanlarını asla göremez (puansız DTO'lar iletilir).
 * - Öğretmen yalnızca eşleştiği öğrencilerin sonuçlarını görebilir.
 * - APP-01 §2.1: Puanlama ve süre kontrolü sunucu tarafında yapılır.
 */
@Service
public class PsychTestService {

    private final PsychTestAssignmentRepository assignmentRepository;
    private final PsychTestResponseRepository responseRepository;
    private final BourdonResponseRepository bourdonResponseRepository;
    private final PsychResultViewRepository psychResultViewRepository;
    private final AppUserRepository appUserRepository;
    private final MembershipService membershipService;
    private final SecurityUtils securityUtils;
    private final StaiScaleDefinition staiScaleDefinition;
    private final BourdonScaleDefinition bourdonScaleDefinition;
    private final int staiMediumMin;
    private final int staiHighMin;

    public PsychTestService(PsychTestAssignmentRepository assignmentRepository,
                            PsychTestResponseRepository responseRepository,
                            BourdonResponseRepository bourdonResponseRepository,
                            PsychResultViewRepository psychResultViewRepository,
                            AppUserRepository appUserRepository,
                            MembershipService membershipService,
                            SecurityUtils securityUtils,
                            StaiScaleDefinition staiScaleDefinition,
                            BourdonScaleDefinition bourdonScaleDefinition,
                            @org.springframework.beans.factory.annotation.Value("${application.psych-test.stai.medium-min:40}") int staiMediumMin,
                            @org.springframework.beans.factory.annotation.Value("${application.psych-test.stai.high-min:60}") int staiHighMin) {
        this.assignmentRepository = assignmentRepository;
        this.responseRepository = responseRepository;
        this.bourdonResponseRepository = bourdonResponseRepository;
        this.psychResultViewRepository = psychResultViewRepository;
        this.appUserRepository = appUserRepository;
        this.membershipService = membershipService;
        this.securityUtils = securityUtils;
        this.staiScaleDefinition = staiScaleDefinition;
        this.bourdonScaleDefinition = bourdonScaleDefinition;
        this.staiMediumMin = staiMediumMin;
        this.staiHighMin = staiHighMin;
    }

    /**
     * assignTest: Yöneticinin öğrencilere psikolojik test (STAI veya BOURDON) atamasını sağlar.
     * Açık (PENDING veya IN_PROGRESS) ataması bulunan öğrenciler atlanır, mükerrer açık atama engellenir.
     */
    @Transactional
    public AssignPsychTestResponse assignTest(AssignPsychTestRequest request) {
        AppUser currentUser = securityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Oturum bulunamadı veya geçersiz.");
        }

        if (currentUser.getRole() != UserRole.MANAGER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Yalnızca yöneticiler psikolojik ölçek atayabilir.");
        }

        String testCode = request.testCode() != null ? request.testCode().trim().toUpperCase() : "";
        if (!testCode.equals(StaiScaleDefinition.TEST_CODE) && !testCode.equals(BourdonScaleDefinition.TEST_CODE)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçersiz test kodu: " + request.testCode());
        }

        if (request.studentIds() == null || request.studentIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "En az bir öğrenci seçilmelidir.");
        }

        List<UUID> assignedStudentIds = new ArrayList<>();
        List<UUID> skippedStudentIds = new ArrayList<>();

        for (UUID studentId : request.studentIds()) {
            AppUser student = appUserRepository.findById(studentId).orElse(null);
            if (student == null || student.getRole() != UserRole.STUDENT) {
                skippedStudentIds.add(studentId);
                continue;
            }

            // Açık atama kontrolü (PENDING veya IN_PROGRESS)
            boolean hasActive = assignmentRepository.existsByStudentIdAndTestCodeAndStatus(
                    studentId,
                    testCode,
                    PsychTestStatus.PENDING
            );

            if (!hasActive) {
                hasActive = assignmentRepository.existsByStudentIdAndTestCodeAndStatus(
                        studentId,
                        testCode,
                        PsychTestStatus.IN_PROGRESS
                );
            }

            if (hasActive) {
                skippedStudentIds.add(studentId);
            } else {
                PsychTestAssignment assignment = new PsychTestAssignment(
                        UUID.randomUUID(),
                        testCode,
                        studentId,
                        currentUser.getId(),
                        Instant.now(),
                        PsychTestStatus.PENDING
                );
                assignmentRepository.save(assignment);
                assignedStudentIds.add(studentId);
            }
        }

        String message = String.format("%d öğrenciye test atandı, %d öğrenci atlandı.",
                assignedStudentIds.size(), skippedStudentIds.size());

        return new AssignPsychTestResponse(assignedStudentIds, skippedStudentIds, message);
    }

    /**
     * getMyAssignments: Öğrencinin kendisine atanan testleri listeler.
     * APP-03 §4: Öğrenciye kesinlikle puan dönülmez (MyPsychTestAssignmentResponse puansızdır).
     */
    @Transactional(readOnly = true)
    public List<MyPsychTestAssignmentResponse> getMyAssignments() {
        AppUser currentUser = securityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Oturum bulunamadı veya geçersiz.");
        }

        if (currentUser.getRole() != UserRole.STUDENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Yalnızca öğrenciler kendi test atamalarını görüntüleyebilir.");
        }

        List<PsychTestAssignment> assignments = assignmentRepository.findAllByStudentIdOrderByAssignedAtDesc(currentUser.getId());

        return assignments.stream()
                .map(a -> {
                    String title = a.getTestCode().equalsIgnoreCase(BourdonScaleDefinition.TEST_CODE)
                            ? BourdonScaleDefinition.TEST_TITLE
                            : StaiScaleDefinition.TEST_TITLE;
                    return new MyPsychTestAssignmentResponse(
                            a.getId(),
                            a.getTestCode(),
                            title,
                            a.getAssignedAt(),
                            a.getStatus().name(),
                            a.getCompletedAt()
                    );
                })
                .toList();
    }

    /**
     * getScaleItems: STAI test maddelerini ve seçeneklerini döner.
     * Yalnızca açık ataması olan öğrenci veya yönetici çekebilir.
     */
    @Transactional(readOnly = true)
    public PsychTestScaleInfoDto getScaleItems(String testCode) {
        AppUser currentUser = securityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Oturum bulunamadı veya geçersiz.");
        }

        if (testCode == null || !testCode.equalsIgnoreCase(StaiScaleDefinition.TEST_CODE)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçersiz test kodu: " + testCode);
        }

        if (currentUser.getRole() == UserRole.MANAGER) {
            return staiScaleDefinition.getScaleInfo();
        }

        if (currentUser.getRole() == UserRole.STUDENT) {
            boolean hasPending = assignmentRepository.existsByStudentIdAndTestCodeAndStatus(
                    currentUser.getId(),
                    StaiScaleDefinition.TEST_CODE,
                    PsychTestStatus.PENDING
            );
            if (!hasPending) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu ölçeğin maddelerini görüntülemek için aktif bir atamanız bulunmalıdır.");
            }
            return staiScaleDefinition.getScaleInfo();
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu işlem için yetkiniz bulunmamaktadır.");
    }

    /**
     * submitTest: Öğrencinin STAI ölçeğini 40 cevapla tamamlayıp teslim etmesini sağlar.
     * Sunucu tarafında puanlama yapılır (APP-01 §2.1).
     * Öğrenciye puan dönülmez (APP-03 §4).
     */
    @Transactional
    public PsychTestSubmitResponse submitTest(UUID assignmentId, SubmitPsychTestRequest request) {
        AppUser currentUser = securityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Oturum bulunamadı veya geçersiz.");
        }

        PsychTestAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Test ataması bulunamadı."));

        // Sahiplik ve rol kontrolü (ENG-11 §3.1)
        if (currentUser.getRole() != UserRole.STUDENT || !assignment.getStudentId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sadece kendinize ait test atamasını doldurabilirsiniz.");
        }

        if (!assignment.getTestCode().equalsIgnoreCase(StaiScaleDefinition.TEST_CODE)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu uç yalnızca STAI ölçeği teslimi içindir.");
        }

        // Durum kontrolü: Zaten tamamlanmışsa tekrar doldurulamaz
        if (assignment.getStatus() == PsychTestStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu test daha önce tamamlanmıştır ve tekrar gönderilemez.");
        }

        // Cevapların doğrulanması: 40 maddenin tamamı eksiksiz olmalı, cevaplar 1..4 arasında olmalı
        if (request == null || request.answers() == null || request.answers().size() != 40) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tüm soruların (40 madde) eksiksiz cevaplanması zorunludur.");
        }

        Map<Integer, Integer> answersMap = new HashMap<>(40);
        for (PsychTestAnswerItemDto ans : request.answers()) {
            if (ans.itemNo() < 1 || ans.itemNo() > 40) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçersiz madde numarası: " + ans.itemNo());
            }
            if (ans.answer() < 1 || ans.answer() > 4) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ans.itemNo() + ". madde için cevap 1 ile 4 arasında olmalıdır.");
            }
            if (answersMap.containsKey(ans.itemNo())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ans.itemNo() + ". madde birden fazla kez cevaplanamaz.");
            }
            answersMap.put(ans.itemNo(), ans.answer());
        }

        if (answersMap.size() != 40) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tüm soruların (40 madde) cevaplanması zorunludur.");
        }

        // Puanların sunucuda hesaplanması (APP-01 §2.1)
        int stateScore = staiScaleDefinition.calculateStateScore(answersMap);
        int traitScore = staiScaleDefinition.calculateTraitScore(answersMap);

        // Yanıtların kaydedilmesi
        List<PsychTestResponse> responses = new ArrayList<>(40);
        for (Map.Entry<Integer, Integer> entry : answersMap.entrySet()) {
            responses.add(new PsychTestResponse(
                    UUID.randomUUID(),
                    assignment.getId(),
                    entry.getKey(),
                    entry.getValue()
            ));
        }
        responseRepository.saveAll(responses);

        // Atama durumunun güncellenmesi
        Instant now = Instant.now();
        assignment.setStatus(PsychTestStatus.COMPLETED);
        assignment.setCompletedAt(now);
        assignment.setStateScore(stateScore);
        assignment.setTraitScore(traitScore);
        assignmentRepository.save(assignment);

        return new PsychTestSubmitResponse(
                assignment.getId(),
                PsychTestStatus.COMPLETED.name(),
                now,
                "Test başarıyla tamamlandı."
        );
    }

    /**
     * startBourdonTest: Öğrencinin Bourdon dikkat testini başlatmasını sağlar.
     * Sunucu tarafında started_at damgası vurulur ve durum IN_PROGRESS yapılır (APP-01 §2.1).
     * İstemciden hiçbir zaman kabul edilmez.
     */
    @Transactional
    public BourdonStartResponse startBourdonTest(UUID assignmentId) {
        AppUser currentUser = securityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Oturum bulunamadı veya geçersiz.");
        }

        PsychTestAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Test ataması bulunamadı."));

        // Sahiplik ve rol kontrolü (ENG-11 §3.1)
        if (currentUser.getRole() != UserRole.STUDENT || !assignment.getStudentId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sadece kendinize ait test atamasını başlatabilirsiniz.");
        }

        if (!assignment.getTestCode().equalsIgnoreCase(BourdonScaleDefinition.TEST_CODE)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu test bir Bourdon dikkat testi değildir.");
        }

        if (assignment.getStatus() == PsychTestStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu test daha önce tamamlanmıştır.");
        }

        if (assignment.getStatus() == PsychTestStatus.PENDING) {
            assignment.setStatus(PsychTestStatus.IN_PROGRESS);
            assignment.setStartedAt(Instant.now());
            assignmentRepository.save(assignment);
        }
        // T-053F / T-060 / APP-01 §2.1: IN_PROGRESS durumunda test reddedilmez; aynı ızgarayı ve ilk started_at damgasını döndürür.
        // T-060: Kalan süre sunucuda hesaplanır; istemci cihaz saatine bağımlı kalmaz.
        long elapsedSeconds = Duration.between(assignment.getStartedAt(), Instant.now()).toSeconds();
        int remainingSeconds = Math.max(0, (int) (BourdonScaleDefinition.DURATION_SECONDS - elapsedSeconds));

        return new BourdonStartResponse(
                assignment.getId(),
                assignment.getStartedAt(),
                BourdonScaleDefinition.DURATION_SECONDS,
                remainingSeconds,
                bourdonScaleDefinition.getGridRows()
        );
    }

    /**
     * submitBourdonTest: Öğrencinin işaretlediği hücreleri teslim etmesini sağlar.
     * - Süre doğrulaması sunucuda yapılır: started_at üzerinden 180 sn + 5 sn tolerans = 185 sn aşılmışsa reddedilir.
     * - Puanlama sunucuda yapılır: 3 blok bazında doğru, atlanan ve yanlış işaretler hesaplanır.
     * - Öğrenciye puan dönülmez (APP-03 §4).
     */
    @Transactional
    public PsychTestSubmitResponse submitBourdonTest(UUID assignmentId, BourdonSubmissionRequest request) {
        AppUser currentUser = securityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Oturum bulunamadı veya geçersiz.");
        }

        PsychTestAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Test ataması bulunamadı."));

        // Sahiplik ve rol kontrolü (ENG-11 §3.1)
        if (currentUser.getRole() != UserRole.STUDENT || !assignment.getStudentId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sadece kendinize ait test atamasını teslim edebilirsiniz.");
        }

        if (!assignment.getTestCode().equalsIgnoreCase(BourdonScaleDefinition.TEST_CODE)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu uç yalnızca Bourdon dikkat testi teslimi içindir.");
        }

        if (assignment.getStatus() == PsychTestStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu test daha önce tamamlanmıştır.");
        }

        if (assignment.getStartedAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Test henüz başlatılmamış.");
        }

        Instant now = Instant.now();
        long elapsedSeconds = Math.max(0, Duration.between(assignment.getStartedAt(), now).toSeconds());
        boolean timedOut = elapsedSeconds > BourdonScaleDefinition.DURATION_SECONDS;

        // İşaretlenen hücrelerin toplanması ve koordinat doğrulaması
        Set<String> markedSet = new HashSet<>();
        if (request != null && request.markedCells() != null) {
            for (BourdonMarkedCell cell : request.markedCells()) {
                if (cell.row() < 0 || cell.row() >= BourdonScaleDefinition.TOTAL_ROWS ||
                        cell.col() < 0 || cell.col() >= BourdonScaleDefinition.COLUMNS_PER_ROW) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Geçersiz hücre koordinatı: row=" + cell.row() + ", col=" + cell.col());
                }
                markedSet.add(cell.row() + ":" + cell.col());
            }
        }

        // Blok bazlı puanlama motoru
        int[] bCorrect = new int[3];
        int[] bOmitted = new int[3];
        int[] bIncorrect = new int[3];

        for (int r = 0; r < BourdonScaleDefinition.TOTAL_ROWS; r++) {
            int blockIdx = r / BourdonScaleDefinition.ROWS_PER_BLOCK;
            for (int c = 0; c < BourdonScaleDefinition.COLUMNS_PER_ROW; c++) {
                boolean isTarget = bourdonScaleDefinition.isTargetAt(r, c);
                boolean isMarked = markedSet.contains(r + ":" + c);

                if (isTarget) {
                    if (isMarked) {
                        bCorrect[blockIdx]++;
                    } else {
                        bOmitted[blockIdx]++;
                    }
                } else {
                    if (isMarked) {
                        bIncorrect[blockIdx]++;
                    }
                }
            }
        }

        int totalCorrect = bCorrect[0] + bCorrect[1] + bCorrect[2];
        int totalOmitted = bOmitted[0] + bOmitted[1] + bOmitted[2];
        int totalIncorrect = bIncorrect[0] + bIncorrect[1] + bIncorrect[2];

        String markedCellsStr = String.join(",", markedSet);

        BourdonResponse response = new BourdonResponse(
                UUID.randomUUID(),
                assignment.getId(),
                assignment.getStartedAt(),
                now,
                (int) elapsedSeconds,
                timedOut,
                markedCellsStr,
                bCorrect[0], bOmitted[0], bIncorrect[0],
                bCorrect[1], bOmitted[1], bIncorrect[1],
                bCorrect[2], bOmitted[2], bIncorrect[2],
                totalCorrect, totalOmitted, totalIncorrect,
                now
        );
        bourdonResponseRepository.save(response);

        assignment.setStatus(PsychTestStatus.COMPLETED);
        assignment.setCompletedAt(now);
        assignmentRepository.save(assignment);

        return new PsychTestSubmitResponse(
                assignment.getId(),
                PsychTestStatus.COMPLETED.name(),
                now,
                "Bourdon dikkat testi başarıyla tamamlandı."
        );
    }

    /**
     * getBourdonResult: Yönetici veya öğrenciyle eşleşen öğretmenin Bourdon sonucunu görmesini sağlar.
     * Öğrenci bu uca erişemez (403).
     */
    @Transactional(readOnly = true)
    public BourdonResultResponse getBourdonResult(UUID assignmentId) {
        AppUser currentUser = securityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Oturum bulunamadı veya geçersiz.");
        }

        if (currentUser.getRole() == UserRole.STUDENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Öğrenciler test sonuçlarını görüntüleyemez.");
        }

        PsychTestAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Test ataması bulunamadı."));

        if (!assignment.getTestCode().equalsIgnoreCase(BourdonScaleDefinition.TEST_CODE)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu test bir Bourdon dikkat testi değildir.");
        }

        if (currentUser.getRole() == UserRole.TEACHER) {
            if (!membershipService.isTeacherOfStudent(currentUser.getId(), assignment.getStudentId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu öğrencinin test sonuçlarını görüntüleme yetkiniz yok.");
            }
        } else if (currentUser.getRole() != UserRole.MANAGER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu işlem için yetkiniz bulunmamaktadır.");
        }

        BourdonResponse response = bourdonResponseRepository.findByAssignmentId(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Test yanıtı bulunamadı."));

        String studentName = appUserRepository.findById(assignment.getStudentId())
                .map(AppUser::getFullName)
                .orElse("Bilinmeyen Öğrenci");

        BourdonBlockScoreDto b1 = new BourdonBlockScoreDto(1, response.getB1Correct(), response.getB1Omitted(), response.getB1Incorrect(), BourdonScaleDefinition.BLOCK_1_TARGET_COUNT);
        BourdonBlockScoreDto b2 = new BourdonBlockScoreDto(2, response.getB2Correct(), response.getB2Omitted(), response.getB2Incorrect(), BourdonScaleDefinition.BLOCK_2_TARGET_COUNT);
        BourdonBlockScoreDto b3 = new BourdonBlockScoreDto(3, response.getB3Correct(), response.getB3Omitted(), response.getB3Incorrect(), BourdonScaleDefinition.BLOCK_3_TARGET_COUNT);

        String observationNote = generateObservationNote(response);

        return new BourdonResultResponse(
                assignment.getId(),
                studentName,
                response.getStartedAt(),
                response.getSubmittedAt(),
                response.getDurationSeconds(),
                response.isTimedOut(),
                b1, b2, b3,
                response.getTotalCorrect(),
                response.getTotalOmitted(),
                response.getTotalIncorrect(),
                BourdonScaleDefinition.TOTAL_TARGET_COUNT,
                observationNote
        );
    }

    /**
     * getBourdonGrid: Aktif Bourdon ataması olan öğrenci veya yöneticinin ızgara satırlarını almasını sağlar.
     */
    @Transactional(readOnly = true)
    public List<String> getBourdonGrid() {
        AppUser currentUser = securityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Oturum bulunamadı veya geçersiz.");
        }

        if (currentUser.getRole() == UserRole.STUDENT) {
            boolean hasActive = assignmentRepository.existsByStudentIdAndTestCodeAndStatus(
                    currentUser.getId(),
                    BourdonScaleDefinition.TEST_CODE,
                    PsychTestStatus.PENDING
            ) || assignmentRepository.existsByStudentIdAndTestCodeAndStatus(
                    currentUser.getId(),
                    BourdonScaleDefinition.TEST_CODE,
                    PsychTestStatus.IN_PROGRESS
            );

            if (!hasActive) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Izgarayı görüntülemek için aktif bir Bourdon test atamanız bulunmalıdır.");
            }
        } else if (currentUser.getRole() != UserRole.MANAGER && currentUser.getRole() != UserRole.TEACHER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu işlem için yetkiniz bulunmamaktadır.");
        }

        return bourdonScaleDefinition.getGridRows();
    }

    /**
     * getAllResults: Yöneticinin tüm test sonuçlarını puanlarıyla birlikte listelemesini sağlar.
     */
    @Transactional(readOnly = true)
    public List<PsychTestResultResponse> getAllResults() {
        AppUser currentUser = securityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Oturum bulunamadı veya geçersiz.");
        }

        if (currentUser.getRole() != UserRole.MANAGER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Yalnızca yöneticiler tüm test sonuçlarını listeleyebilir.");
        }

        List<PsychTestAssignment> assignments = assignmentRepository.findAllByOrderByAssignedAtDesc();
        return mapToResultResponses(assignments);
    }

    /**
     * getStudentResults: Belirli bir öğrenciye ait test sonuçlarını döndürür.
     * Yönetici tüm öğrencileri, öğretmen yalnızca eşleştiği öğrencileri görebilir (APP-03 §3, ENG-11 §3.1).
     * Öğrenci test sonuçlarını sorgulayamaz (APP-03 §4).
     */
    @Transactional(readOnly = true)
    public List<PsychTestResultResponse> getStudentResults(UUID studentId) {
        AppUser currentUser = securityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Oturum bulunamadı veya geçersiz.");
        }

        if (currentUser.getRole() == UserRole.STUDENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Öğrenciler test sonuçlarını görüntüleyemez.");
        }

        if (currentUser.getRole() == UserRole.TEACHER) {
            if (!membershipService.isTeacherOfStudent(currentUser.getId(), studentId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu öğrencinin test sonuçlarını görüntüleme yetkiniz yok.");
            }
        } else if (currentUser.getRole() != UserRole.MANAGER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu işlem için yetkiniz bulunmamaktadır.");
        }

        List<PsychTestAssignment> assignments = assignmentRepository.findAllByStudentIdOrderByAssignedAtDesc(studentId);
        return mapToResultResponses(assignments);
    }

    /**
     * getUnseenCounts: Çağıran kullanıcının (öğretmen veya yönetici) henüz görmediği tamamlanmış
     * psikolojik test sayılarını tek sorguda döner (T-062, ENG-06).
     * - Öğrenci: 403 Forbidden
     * - Öğretmen: Yalnızca eşleştiği öğrenciler için görülmemiş tamamlanmış test sayıları ({studentId: adet}) ve toplamı
     * - Yönetici: Sistemdeki tüm öğrenciler için görülmemiş tamamlanmış test sayıları ({studentId: adet}) ve toplamı
     *
     * @return UnseenPsychTestCountsResponse (toplam görülmemiş ve öğrenci bazlı sayılar)
     */
    @Transactional(readOnly = true)
    public UnseenPsychTestCountsResponse getUnseenCounts() {
        AppUser currentUser = securityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Oturum bulunamadı veya geçersiz.");
        }

        if (currentUser.getRole() == UserRole.STUDENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Öğrenciler sayım bilgisine erişemez.");
        }

        Map<String, Long> studentCounts = new LinkedHashMap<>();
        long totalUnseen = 0L;

        if (currentUser.getRole() == UserRole.TEACHER) {
            List<Object[]> rows = psychResultViewRepository.countUnseenCompletedForTeacher(currentUser.getId());
            for (Object[] row : rows) {
                UUID studentId = (UUID) row[0];
                long count = ((Number) row[1]).longValue();
                studentCounts.put(studentId.toString(), count);
                totalUnseen += count;
            }
        } else if (currentUser.getRole() == UserRole.MANAGER) {
            List<Object[]> rows = psychResultViewRepository.countUnseenCompletedForManager(currentUser.getId());
            for (Object[] row : rows) {
                UUID studentId = (UUID) row[0];
                long count = ((Number) row[1]).longValue();
                studentCounts.put(studentId.toString(), count);
                totalUnseen += count;
            }
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu işlem için yetkiniz bulunmamaktadır.");
        }

        return new UnseenPsychTestCountsResponse(totalUnseen, studentCounts);
    }

    /**
     * markResultsAsSeen: Tamamlanmış test sonuçlarını çağıran kullanıcı için görüldü olarak işaretler (T-062 / T-062B / T-062C).
     * - Öğrenci: 403 Forbidden
     * - Öğretmen: Yalnızca eşleştiği öğrencinin sonuçlarını görüldü yapabilir (studentId zorunlu, eşleşmemişse 403)
     * - Yönetici: Yalnızca gönderdiği tamamlanmış atama kimlikleri işaretlenir; kimlik gelmezse 400 (T-062C).
     *   "Ekranda listelenen" kuralı sunucuda korunur (APP-01 §2.1); toptan veya öğrenci bazında işaretleme yolu yoktur.
     * - Mükerrer çağrılarda ilk damga korunur (ON CONFLICT DO NOTHING).
     *
     * @param studentId Öğretmenin görüldü yapacağı öğrencinin kimliği
     * @param assignmentIds Yöneticinin ekranda listelenen ve görüldü işaretlemek istediği atama kimlikleri
     */
    @Transactional
    public void markResultsAsSeen(UUID studentId, List<UUID> assignmentIds) {
        AppUser currentUser = securityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Oturum bulunamadı veya geçersiz.");
        }

        if (currentUser.getRole() == UserRole.STUDENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Öğrenciler görüldü işlemi yapamaz.");
        }

        if (currentUser.getRole() == UserRole.TEACHER) {
            if (studentId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Öğrenci kimliği belirtilmelidir.");
            }
            if (!membershipService.isTeacherOfStudent(currentUser.getId(), studentId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu öğrencinin test sonuçlarını görüldü olarak işaretleme yetkiniz yok.");
            }
            psychResultViewRepository.markStudentResultsAsSeen(studentId, currentUser.getId());
        } else if (currentUser.getRole() == UserRole.MANAGER) {
            if (assignmentIds == null || assignmentIds.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Görüldü işaretlenecek sonuç kimlikleri belirtilmelidir.");
            }
            psychResultViewRepository.markSpecificResultsAsSeen(assignmentIds, currentUser.getId());
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu işlem için yetkiniz bulunmamaktadır.");
        }
    }

    /**
     * calculateStaiLevel: STAI puanına karşılık gelen kaygı seviyesi etiketini üretir (T-061 / S-024).
     * APP-01 §2.1: Seviye hesabı sunucuda tek yerde yapılır.
     * Eşikler application.properties üzerinden yapılandırılır (varsayılan: Düşük 20–39, Orta 40–59, Yüksek 60–80).
     *
     * @param score 20–80 aralığındaki STAI puanı (null ise null döner)
     * @return "LOW", "MEDIUM", "HIGH" veya null
     */
    public String calculateStaiLevel(Integer score) {
        if (score == null) {
            return null;
        }
        if (score >= staiHighMin) {
            return "HIGH";
        }
        if (score >= staiMediumMin) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private List<PsychTestResultResponse> mapToResultResponses(List<PsychTestAssignment> assignments) {
        if (assignments.isEmpty()) {
            return List.of();
        }

        List<UUID> studentIds = assignments.stream()
                .map(PsychTestAssignment::getStudentId)
                .distinct()
                .toList();

        Map<UUID, String> studentNameMap = appUserRepository.findAllById(studentIds).stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getFullName));

        List<UUID> completedBourdonAssignmentIds = assignments.stream()
                .filter(a -> a.getTestCode().equalsIgnoreCase(BourdonScaleDefinition.TEST_CODE)
                        && a.getStatus() == PsychTestStatus.COMPLETED)
                .map(PsychTestAssignment::getId)
                .toList();

        Map<UUID, BourdonResponse> bourdonResponseMap = completedBourdonAssignmentIds.isEmpty()
                ? Map.of()
                : bourdonResponseRepository.findAllByAssignmentIdIn(completedBourdonAssignmentIds).stream()
                        .collect(Collectors.toMap(BourdonResponse::getAssignmentId, r -> r));

        return assignments.stream()
                .map(a -> {
                    BourdonResponse br = bourdonResponseMap.get(a.getId());
                    boolean isCompletedStai = a.getTestCode().equalsIgnoreCase(StaiScaleDefinition.TEST_CODE)
                            && a.getStatus() == PsychTestStatus.COMPLETED;
                    String stateLevel = isCompletedStai ? calculateStaiLevel(a.getStateScore()) : null;
                    String traitLevel = isCompletedStai ? calculateStaiLevel(a.getTraitScore()) : null;

                    return new PsychTestResultResponse(
                            a.getId(),
                            a.getStudentId(),
                            studentNameMap.getOrDefault(a.getStudentId(), "Bilinmeyen Öğrenci"),
                            a.getTestCode(),
                            a.getStatus().name(),
                            a.getAssignedAt(),
                            a.getCompletedAt(),
                            a.getStateScore(),
                            a.getTraitScore(),
                            stateLevel,
                            traitLevel,
                            br != null ? br.getTotalCorrect() : null,
                            br != null ? br.getTotalOmitted() : null,
                            br != null ? br.getTotalIncorrect() : null,
                            br != null ? br.getDurationSeconds() : null,
                            br != null ? br.isTimedOut() : null
                    );
                })
                .toList();
    }

    /**
     * generateObservationNote: Bourdon blok verilerinden üçüncü şahıs nötr gözlem notu üretir.
     * APP-03 §5: Kesinlikle tanı, kategorik etiket veya sıralama içermez.
     * T-060: Eşik toplam hedef sayısına göre oransal belirlenir; blok eğilimi hata oranları üzerinden incelenir.
     */
    private String generateObservationNote(BourdonResponse response) {
        int totalTargets = response.getTotalCorrect() + response.getTotalOmitted();
        int totalMarked = response.getTotalCorrect() + response.getTotalIncorrect();

        // T-060 (f): Eşik hedef sayısına göre oransaldır (işaretlenen < hedeflerin yarısı -> az işaret)
        if (totalTargets > 0 && totalMarked < (totalTargets + 1) / 2) {
            return "İşaretlenen hücre sayısı beklenenin altındadır.";
        }

        int b1Targets = response.getB1Correct() + response.getB1Omitted();
        int b1Errors = response.getB1Omitted() + response.getB1Incorrect();
        int b3Targets = response.getB3Correct() + response.getB3Omitted();
        int b3Errors = response.getB3Omitted() + response.getB3Incorrect();

        double b1ErrorRate = b1Targets > 0 ? (double) b1Errors / b1Targets : 0.0;
        double b3ErrorRate = b3Targets > 0 ? (double) b3Errors / b3Targets : 0.0;

        if (b3ErrorRate > b1ErrorRate + 0.15) {
            return "Testin son bölümünde hata ve atlama eğiliminde artış gözlenmiştir.";
        } else if (b1ErrorRate > b3ErrorRate + 0.15) {
            return "İlk bölümde hata ve atlama sayısı daha yüksekken sonraki bölümlerde azalma gözlenmiştir.";
        } else {
            return "Bölümler arasında dengeli bir işaretleme dağılımı gözlenmiştir.";
        }
    }
}
