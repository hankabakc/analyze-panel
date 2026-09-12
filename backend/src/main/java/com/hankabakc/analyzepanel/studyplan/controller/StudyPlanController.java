package com.hankabakc.analyzepanel.studyplan.controller;

import com.hankabakc.analyzepanel.core.model.ApiResponse;
import com.hankabakc.analyzepanel.studyplan.dto.CreateStudyPlanRequest;
import com.hankabakc.analyzepanel.studyplan.dto.StudyPlanItemResponse;
import com.hankabakc.analyzepanel.studyplan.dto.StudyPlanResponse;
import com.hankabakc.analyzepanel.studyplan.service.StudyPlanService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * StudyPlanController: Çalışma planı işlemlerini dış dünyaya açan REST uç noktalarıdır.
 * 
 * <p>Mühendislik Standartları:</p>
 * <ul>
 *   <li>Pure Java Politikası: Constructor injection kullanılmıştır.</li>
 *   <li>API Standardı: Tüm yanıtlar ApiResponse&lt;T&gt; zarfında döner.</li>
 *   <li>ENG-07: REST standartlarına uygun uç nokta hiyerarşisi.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/study-plans")
public class StudyPlanController {

    private final StudyPlanService studyPlanService;

    public StudyPlanController(StudyPlanService studyPlanService) {
        this.studyPlanService = studyPlanService;
    }

    /**
     * Öğretmenin öğrenciye çalışma planı atadığı uç noktadır.
     *
     * @param request Çalışma planı oluşturma isteği
     * @return Oluşturulan çalışma planı
     */
    @PostMapping
    public ResponseEntity<ApiResponse<StudyPlanResponse>> createPlan(
            @Valid @RequestBody CreateStudyPlanRequest request) {
        StudyPlanResponse response = studyPlanService.createPlan(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Çalışma planı başarıyla oluşturuldu."));
    }

    /**
     * Belirli bir öğrenciye ait çalışma planlarını getiren uç noktadır.
     *
     * @param studentId Hedef öğrencinin kimliği
     * @return Çalışma planları listesi
     */
    @GetMapping("/student/{studentId}")
    public ResponseEntity<ApiResponse<List<StudyPlanResponse>>> getPlansForStudent(
            @PathVariable UUID studentId) {
        List<StudyPlanResponse> responses = studyPlanService.getPlansForStudent(studentId);
        return ResponseEntity.ok(ApiResponse.success(responses, "Çalışma planları başarıyla getirildi."));
    }

    /**
     * Öğrencinin belirli bir çalışma planı kalemini tamamlandı olarak işaretlediği uç noktadır.
     *
     * @param itemId Tamamlanan kalemin kimliği
     * @return Güncellenmiş çalışma planı kalemi
     */
    @PostMapping("/items/{itemId}/complete")
    public ResponseEntity<ApiResponse<StudyPlanItemResponse>> completeItem(
            @PathVariable UUID itemId) {
        StudyPlanItemResponse response = studyPlanService.completeItem(itemId);
        return ResponseEntity.ok(ApiResponse.success(response, "Çalışma planı kalemi tamamlandı olarak işaretlendi."));
    }

    /**
     * T-050D: Öğretmenin çalışma planını görüldü olarak işaretlediği uç noktadır.
     *
     * @param planId Görüldü yapılacak çalışma planının kimliği
     * @return Başarı yanıtı
     */
    @PostMapping("/{planId}/seen")
    public ResponseEntity<ApiResponse<Void>> markPlanAsSeen(
            @PathVariable UUID planId) {
        studyPlanService.markPlanAsSeen(planId);
        return ResponseEntity.ok(ApiResponse.success(null, "Çalışma planı görüldü olarak işaretlendi."));
    }

    /**
     * T-050D: Öğretmenin öğrencileri için okunmamış tamamlanan görev sayılarını getiren uç noktadır.
     *
     * @return {studentId: okunmamışAdet} haritası
     */
    @GetMapping("/unseen-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnseenCounts() {
        Map<String, Long> counts = studyPlanService.getUnseenCounts();
        return ResponseEntity.ok(ApiResponse.success(counts, "Okunmamış görev sayıları başarıyla getirildi."));
    }
}
