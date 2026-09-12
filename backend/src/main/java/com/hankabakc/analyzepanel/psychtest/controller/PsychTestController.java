package com.hankabakc.analyzepanel.psychtest.controller;

import com.hankabakc.analyzepanel.core.model.ApiResponse;
import com.hankabakc.analyzepanel.psychtest.dto.AssignPsychTestRequest;
import com.hankabakc.analyzepanel.psychtest.dto.AssignPsychTestResponse;
import com.hankabakc.analyzepanel.psychtest.dto.BourdonResultResponse;
import com.hankabakc.analyzepanel.psychtest.dto.BourdonStartResponse;
import com.hankabakc.analyzepanel.psychtest.dto.BourdonSubmissionRequest;
import com.hankabakc.analyzepanel.psychtest.dto.MarkPsychResultsSeenRequest;
import com.hankabakc.analyzepanel.psychtest.dto.MyPsychTestAssignmentResponse;
import com.hankabakc.analyzepanel.psychtest.dto.PsychTestResultResponse;
import com.hankabakc.analyzepanel.psychtest.dto.PsychTestScaleInfoDto;
import com.hankabakc.analyzepanel.psychtest.dto.PsychTestSubmitResponse;
import com.hankabakc.analyzepanel.psychtest.dto.SubmitPsychTestRequest;
import com.hankabakc.analyzepanel.psychtest.dto.UnseenPsychTestCountsResponse;
import com.hankabakc.analyzepanel.psychtest.service.PsychTestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * PsychTestController: Psikolojik testler (STAI, Bourdon Dikkat Testi) REST API uç noktaları.
 * 
 * Güvenlik ve Mimari Standartlar:
 * - ENG-07: REST API Tasarımı ve Versiyonlama (/api/v1/).
 * - ENG-11 §3.1: IDOR / BOLA yetkilendirme kontrolleri servis katmanında sıkı biçimde uygulanır.
 * - APP-03 §4: Özel Nitelikli Kişisel Veri Açık İstisnası.
 * - APP-01 §2.1: Süre ve iş kuralları sunucu tarafında doğrulanır.
 * - Tüm yanıtlar standart ApiResponse<T> zarfı içinde sunulur.
 */
@RestController
@RequestMapping("/api/v1/psych-tests")
public class PsychTestController {

    private final PsychTestService psychTestService;

    public PsychTestController(PsychTestService psychTestService) {
        this.psychTestService = psychTestService;
    }

    /**
     * POST /api/v1/psych-tests/assignments
     * Yöneticinin öğrencilere test atamasını sağlar.
     */
    @PostMapping("/assignments")
    public ResponseEntity<ApiResponse<AssignPsychTestResponse>> assignTest(
            @RequestBody AssignPsychTestRequest request) {
        AssignPsychTestResponse response = psychTestService.assignTest(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Test atama işlemi tamamlandı."));
    }

    /**
     * GET /api/v1/psych-tests/my-assignments
     * Öğrencinin kendisine atanan testleri listeler.
     * Puan alanları öğrenciye dönülmez (APP-03 §4).
     */
    @GetMapping("/my-assignments")
    public ResponseEntity<ApiResponse<List<MyPsychTestAssignmentResponse>>> getMyAssignments() {
        List<MyPsychTestAssignmentResponse> assignments = psychTestService.getMyAssignments();
        return ResponseEntity.ok(ApiResponse.success(assignments, "Test atamaları başarıyla getirildi."));
    }

    /**
     * GET /api/v1/psych-tests/items?testCode=STAI
     * STAI ölçek madde metinlerini ve seçeneklerini döndürür.
     * Yalnızca açık ataması olan öğrenci veya yönetici erişebilir.
     */
    @GetMapping("/items")
    public ResponseEntity<ApiResponse<PsychTestScaleInfoDto>> getScaleItems(
            @RequestParam(name = "testCode", defaultValue = "STAI") String testCode) {
        PsychTestScaleInfoDto scaleInfo = psychTestService.getScaleItems(testCode);
        return ResponseEntity.ok(ApiResponse.success(scaleInfo, "Ölçek maddeleri başarıyla getirildi."));
    }

    /**
     * POST /api/v1/psych-tests/assignments/{id}/submit
     * Öğrencinin STAI ölçeğini doldurup göndermesini sağlar.
     * Sunucu tarafında puanlanır; öğrenciye puan dönülmez.
     */
    @PostMapping("/assignments/{id}/submit")
    public ResponseEntity<ApiResponse<PsychTestSubmitResponse>> submitTest(
            @PathVariable UUID id,
            @RequestBody SubmitPsychTestRequest request) {
        PsychTestSubmitResponse response = psychTestService.submitTest(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Test başarıyla teslim edildi."));
    }

    /**
     * POST /api/v1/psych-tests/assignments/{id}/start
     * Öğrencinin Bourdon dikkat testini başlatmasını sağlar.
     * Sunucuda started_at damgası vurulur ve ızgara satırları dönülür (APP-01 §2.1).
     */
    @PostMapping("/assignments/{id}/start")
    public ResponseEntity<ApiResponse<BourdonStartResponse>> startBourdonTest(
            @PathVariable UUID id) {
        BourdonStartResponse response = psychTestService.startBourdonTest(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Bourdon testi başarıyla başlatıldı."));
    }

    /**
     * POST /api/v1/psych-tests/assignments/{id}/submit-bourdon
     * Öğrencinin Bourdon testinde işaretlediği hücreleri göndermesini sağlar.
     * 3 dakikalık süre ve puanlama sunucuda doğrulanır; öğrenciye puan dönülmez.
     */
    @PostMapping("/assignments/{id}/submit-bourdon")
    public ResponseEntity<ApiResponse<PsychTestSubmitResponse>> submitBourdonTest(
            @PathVariable UUID id,
            @RequestBody BourdonSubmissionRequest request) {
        PsychTestSubmitResponse response = psychTestService.submitBourdonTest(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Bourdon testi başarıyla teslim edildi."));
    }

    /**
     * GET /api/v1/psych-tests/assignments/{id}/bourdon-result
     * Bourdon dikkat testinin blok bazlı ve toplam sonuçlarını döndürür.
     * Yönetici ve öğrenciyle eşleşen öğretmen erişebilir; öğrenciye 403 döner.
     */
    @GetMapping("/assignments/{id}/bourdon-result")
    public ResponseEntity<ApiResponse<BourdonResultResponse>> getBourdonResult(
            @PathVariable UUID id) {
        BourdonResultResponse response = psychTestService.getBourdonResult(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Bourdon test sonucu başarıyla getirildi."));
    }

    /**
     * GET /api/v1/psych-tests/bourdon/grid
     * Bourdon dikkat testi 30 satırlık harf ızgarasını döndürür.
     */
    @GetMapping("/bourdon/grid")
    public ResponseEntity<ApiResponse<List<String>>> getBourdonGrid() {
        List<String> grid = psychTestService.getBourdonGrid();
        return ResponseEntity.ok(ApiResponse.success(grid, "Bourdon harf ızgarası başarıyla getirildi."));
    }

    /**
     * GET /api/v1/psych-tests/results
     * Yöneticinin tüm test sonuçlarını puanlarıyla birlikte listelemesini sağlar.
     */
    @GetMapping("/results")
    public ResponseEntity<ApiResponse<List<PsychTestResultResponse>>> getAllResults() {
        List<PsychTestResultResponse> results = psychTestService.getAllResults();
        return ResponseEntity.ok(ApiResponse.success(results, "Tüm test sonuçları başarıyla getirildi."));
    }

    /**
     * GET /api/v1/psych-tests/results/student/{studentId}
     * Belirli bir öğrencinin test sonuçlarını listeler.
     * Yönetici tüm öğrencileri, öğretmen yalnızca eşleştiği öğrencileri görebilir (APP-03 §3).
     * Öğrenci bu uca erişemez (403).
     */
    @GetMapping("/results/student/{studentId}")
    public ResponseEntity<ApiResponse<List<PsychTestResultResponse>>> getStudentResults(
            @PathVariable UUID studentId) {
        List<PsychTestResultResponse> results = psychTestService.getStudentResults(studentId);
        return ResponseEntity.ok(ApiResponse.success(results, "Öğrenci test sonuçları başarıyla getirildi."));
    }

    /**
     * GET /api/v1/psych-tests/unseen-counts
     * Öğretmen veya yöneticinin henüz görmediği tamamlanmış test sayılarını döner (T-062).
     * - Yan etki üretmez (ENG-07 §1.1).
     * - Tek sorgu ile N+1 olmadan çalışır (ENG-06).
     * - Öğrenci erişemez (403).
     */
    @GetMapping("/unseen-counts")
    public ResponseEntity<ApiResponse<UnseenPsychTestCountsResponse>> getUnseenCounts() {
        UnseenPsychTestCountsResponse response = psychTestService.getUnseenCounts();
        return ResponseEntity.ok(ApiResponse.success(response, "Görülmemiş test sayıları başarıyla getirildi."));
    }

    /**
     * POST /api/v1/psych-tests/results/mark-seen
     * Tamamlanmış test sonuçlarını çağıran kullanıcı için görüldü olarak işaretler (T-062).
     * - Öğretmen yalnızca eşleştiği öğrencinin sonuçlarını işaretleyebilir (studentId zorunlu, eşleşmemişse 403).
     * - Yönetici yalnızca gönderdiği tamamlanmış atama kimliklerini işaretler; assignmentIds zorunlu, yoksa 400 (T-062C).
     * - Öğrenci erişemez (403).
     * - Mükerrer çağrılarda ilk damga korunur (ON CONFLICT DO NOTHING).
     */
    @PostMapping("/results/mark-seen")
    public ResponseEntity<ApiResponse<Void>> markResultsAsSeen(
            @RequestBody(required = false) MarkPsychResultsSeenRequest request) {
        UUID studentId = request != null ? request.studentId() : null;
        List<UUID> assignmentIds = request != null ? request.assignmentIds() : null;
        psychTestService.markResultsAsSeen(studentId, assignmentIds);
        return ResponseEntity.ok(ApiResponse.success(null, "Test sonuçları görüldü olarak işaretlendi."));
    }
}


