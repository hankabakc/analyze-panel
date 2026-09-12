package com.hankabakc.analyzepanel.studyplan.service;

import com.hankabakc.analyzepanel.analysis.repository.TopicDetailRepository;
import com.hankabakc.analyzepanel.auth.entity.AppUser;
import com.hankabakc.analyzepanel.auth.enums.UserRole;
import com.hankabakc.analyzepanel.auth.repository.AppUserRepository;
import com.hankabakc.analyzepanel.core.security.SecurityUtils;
import com.hankabakc.analyzepanel.membership.service.MembershipService;
import com.hankabakc.analyzepanel.studyplan.dto.CreateStudyPlanItemRequest;
import com.hankabakc.analyzepanel.studyplan.dto.CreateStudyPlanRequest;
import com.hankabakc.analyzepanel.studyplan.dto.StudyPlanItemResponse;
import com.hankabakc.analyzepanel.studyplan.dto.StudyPlanResponse;
import com.hankabakc.analyzepanel.studyplan.entity.StudyPlan;
import com.hankabakc.analyzepanel.studyplan.entity.StudyPlanItem;
import com.hankabakc.analyzepanel.studyplan.repository.StudyPlanItemRepository;
import com.hankabakc.analyzepanel.studyplan.repository.StudyPlanRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hankabakc.analyzepanel.auth.dto.UserDto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * StudyPlanService: Çalışma planı oluşturma, sorgulama ve tamamlama iş kurallarını yürütür.
 * 
 * <p>Mühendislik Standartları ve Güvenlik:</p>
 * <ul>
 *   <li>Pure Java Politikası: Lombok kullanılmaz, yalnızca Constructor Injection uygulanır.</li>
 *   <li>ENG-11 §3.1 & APP-03 §3: IDOR ve BOLA koruması gereği her uç noktada veri sahipliği ve öğretmen-öğrenci eşleşmesi ayrı ayrı doğrulanır.</li>
 *   <li>APP-01 §2.1 & §2.7: Konu uydurulmaz; plandaki konular öğrencinin ayrıştırılmış konuları arasında bulunmak zorundadır.</li>
 *   <li>Zaman Damgası Güvenliği: Görev tamamlama zamanı sunucuda Instant.now() ile damgalanır.</li>
 * </ul>
 */
@Service
public class StudyPlanService {

    private final StudyPlanRepository studyPlanRepository;
    private final StudyPlanItemRepository studyPlanItemRepository;
    private final TopicDetailRepository topicDetailRepository;
    private final MembershipService membershipService;
    private final SecurityUtils securityUtils;
    private final AppUserRepository appUserRepository;

    public StudyPlanService(StudyPlanRepository studyPlanRepository,
                            StudyPlanItemRepository studyPlanItemRepository,
                            TopicDetailRepository topicDetailRepository,
                            MembershipService membershipService,
                            SecurityUtils securityUtils,
                            AppUserRepository appUserRepository) {
        this.studyPlanRepository = studyPlanRepository;
        this.studyPlanItemRepository = studyPlanItemRepository;
        this.topicDetailRepository = topicDetailRepository;
        this.membershipService = membershipService;
        this.securityUtils = securityUtils;
        this.appUserRepository = appUserRepository;
    }

    /**
     * Öğretmen tarafından bir öğrenciye yeni bir çalışma planı atar.
     *
     * @param request Çalışma planı istek gövdesi
     * @return Oluşturulan çalışma planı yanıtı
     */
    @Transactional
    public StudyPlanResponse createPlan(CreateStudyPlanRequest request) {
        AppUser currentUser = securityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Oturum bulunamadı veya geçersiz.");
        }

        // Rol kontrolü: Yalnızca öğretmenler plan oluşturabilir
        if (currentUser.getRole() != UserRole.TEACHER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Yalnızca öğretmenler çalışma planı oluşturabilir.");
        }

        // Öğrencinin sistemde varlığı kontrol edilir
        AppUser student = appUserRepository.findById(request.studentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Öğrenci bulunamadı."));

        if (student.getRole() != UserRole.STUDENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Çalışma planı yalnızca öğrencilere atanabilir.");
        }

        // Öğretmen - öğrenci eşleşme kontrolü (APP-03 §3)
        if (!membershipService.isTeacherOfStudent(currentUser.getId(), request.studentId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu öğrenciye çalışma planı atama yetkiniz yok.");
        }

        // Konu uydurulmaz kuralı (APP-01 §2.1, §2.7):
        // Plandaki tüm konular, öğrencinin analiz raporlarından ayrıştırılmış gerçek konular olmalıdır.
        List<String> validTopics = topicDetailRepository.findDistinctTopicNamesByStudentId(request.studentId());
        for (CreateStudyPlanItemRequest itemReq : request.items()) {
            String topicNameTrimmed = itemReq.topicName() != null ? itemReq.topicName().trim() : "";
            boolean topicExists = validTopics.stream()
                    .anyMatch(t -> t.trim().equalsIgnoreCase(topicNameTrimmed));

            if (!topicExists) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Geçersiz konu: '" + itemReq.topicName() + "'. Konu, öğrencinin ayrıştırılmış konuları arasında bulunmalıdır.");
            }
        }

        // Ana plan kaydını oluştur
        UUID planId = UUID.randomUUID();
        StudyPlan plan = new StudyPlan(
                planId,
                request.studentId(),
                currentUser.getId(),
                request.dueDate(),
                Instant.now()
        );
        studyPlanRepository.save(plan);

        // Plan kalemlerini oluştur
        List<StudyPlanItemResponse> itemResponses = new ArrayList<>();
        for (CreateStudyPlanItemRequest itemReq : request.items()) {
            UUID itemId = UUID.randomUUID();
            StudyPlanItem item = new StudyPlanItem(
                    itemId,
                    planId,
                    itemReq.topicName().trim(),
                    itemReq.questionCount(),
                    null
            );
            studyPlanItemRepository.save(item);
            itemResponses.add(new StudyPlanItemResponse(
                    item.getId(),
                    item.getPlanId(),
                    item.getTopicName(),
                    item.getQuestionCount(),
                    item.getCompletedAt(),
                    item.getTeacherSeenAt()
            ));
        }

        return new StudyPlanResponse(
                plan.getId(),
                plan.getStudentId(),
                plan.getTeacherId(),
                plan.getDueDate(),
                plan.getCreatedAt(),
                itemResponses
        );
    }

    /**
     * Belirli bir öğrenciye ait çalışma planlarını listeler.
     *
     * @param studentId Hedef öğrencinin kimliği
     * @return Çalışma planları listesi
     */
    @Transactional(readOnly = true)
    public List<StudyPlanResponse> getPlansForStudent(UUID studentId) {
        AppUser currentUser = securityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Oturum bulunamadı veya geçersiz.");
        }

        // Erişim kontrolü (IDOR / BOLA - ENG-11 §3.1, APP-03 §3):
        if (currentUser.getRole() == UserRole.STUDENT) {
            if (!currentUser.getId().equals(studentId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sadece kendi çalışma planınızı görüntüleyebilirsiniz.");
            }
        } else if (currentUser.getRole() == UserRole.TEACHER) {
            if (!membershipService.isTeacherOfStudent(currentUser.getId(), studentId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu öğrencinin çalışma planını görüntüleme yetkiniz yok.");
            }
        } else if (currentUser.getRole() != UserRole.MANAGER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu işlem için yetkiniz bulunmamaktadır.");
        }

        List<StudyPlan> plans = studyPlanRepository.findAllByStudentIdOrderByCreatedAtDesc(studentId);
        if (plans.isEmpty()) {
            return List.of();
        }

        // T-060 / ENG-06: Kalemler N+1 döngüsü yerine tek bir IN sorgusu ile çekilir
        List<UUID> planIds = plans.stream().map(StudyPlan::getId).toList();
        Map<UUID, List<StudyPlanItem>> itemsByPlanId = studyPlanItemRepository.findAllByPlanIdIn(planIds).stream()
                .collect(Collectors.groupingBy(StudyPlanItem::getPlanId));

        List<StudyPlanResponse> responses = new ArrayList<>(plans.size());
        for (StudyPlan plan : plans) {
            List<StudyPlanItem> items = itemsByPlanId.getOrDefault(plan.getId(), List.of());
            List<StudyPlanItemResponse> itemResponses = items.stream()
                    .map(item -> new StudyPlanItemResponse(
                            item.getId(),
                            item.getPlanId(),
                            item.getTopicName(),
                            item.getQuestionCount(),
                            item.getCompletedAt(),
                            item.getTeacherSeenAt()
                    ))
                    .toList();

            responses.add(new StudyPlanResponse(
                    plan.getId(),
                    plan.getStudentId(),
                    plan.getTeacherId(),
                    plan.getDueDate(),
                    plan.getCreatedAt(),
                    itemResponses
            ));
        }

        return responses;
    }

    /**
     * Öğrencinin bir çalışma planı kalemini tamamlandı olarak işaretlemesini sağlar.
     *
     * @param itemId Tamamlanan kalemin kimliği
     * @return Güncellenmiş çalışma planı kalemi yanıtı
     */
    @Transactional
    public StudyPlanItemResponse completeItem(UUID itemId) {
        AppUser currentUser = securityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Oturum bulunamadı veya geçersiz.");
        }

        StudyPlanItem item = studyPlanItemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Çalışma planı kalemi bulunamadı."));

        StudyPlan plan = studyPlanRepository.findById(item.getPlanId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "İlişkili çalışma planı bulunamadı."));

        // Sahiplik ve Rol kontrolü: Yalnızca kalemin ait olduğu öğrenci tamamlayabilir
        if (currentUser.getRole() != UserRole.STUDENT || !plan.getStudentId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sadece kendi çalışma planınızdaki görevleri tamamlayabilirsiniz.");
        }

        // Henüz tamamlanmamışsa sunucu zamanıyla damgala (idempotent)
        if (item.getCompletedAt() == null) {
            item.setCompletedAt(Instant.now());
            item = studyPlanItemRepository.save(item);
        }

        return new StudyPlanItemResponse(
                item.getId(),
                item.getPlanId(),
                item.getTopicName(),
                item.getQuestionCount(),
                item.getCompletedAt(),
                item.getTeacherSeenAt()
        );
    }

    /**
     * T-050D: Bir çalışma planının tamamlanmış kalemlerini öğretmen tarafından görüldü olarak işaretler.
     * İdempotenttir; daha önce damgalanmış kalemlerin damgasını değiştirmez.
     *
     * @param planId Görüldü yapılacak çalışma planının kimliği
     */
    @Transactional
    public void markPlanAsSeen(UUID planId) {
        AppUser currentUser = securityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Oturum bulunamadı veya geçersiz.");
        }

        if (currentUser.getRole() != UserRole.TEACHER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Yalnızca öğretmenler görüldü damgası basabilir.");
        }

        StudyPlan plan = studyPlanRepository.findById(planId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Çalışma planı bulunamadı."));

        // Eşleşme ve Yetki Denetimi (IDOR / BOLA - ENG-11 §3.1, APP-03 §3):
        // Yalnızca planın eşleştiği öğretmen çağırabilir; eşleşmeyen öğretmen veya öğrenci 403 Forbidden alır.
        if (!membershipService.isTeacherOfStudent(currentUser.getId(), plan.getStudentId())
                || !plan.getTeacherId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu çalışma planını görüldü olarak işaretleme yetkiniz yok.");
        }

        List<StudyPlanItem> items = studyPlanItemRepository.findAllByPlanId(planId);
        Instant now = Instant.now();
        boolean changed = false;
        for (StudyPlanItem item : items) {
            // Yalnızca tamamlanmış VE henüz görülmemiş kalemlere damga basılır
            if (item.getCompletedAt() != null && item.getTeacherSeenAt() == null) {
                item.setTeacherSeenAt(now);
                changed = true;
            }
        }

        if (changed) {
            studyPlanItemRepository.saveAll(items);
        }
    }

    /**
     * T-050D: Çağıran öğretmenin eşleştiği öğrencileri için tamamlanmış ancak görülmemiş kalem sayılarını döner.
     * Tek çağrıda toplu sonuç verir (ENG-06). Başka öğretmenin öğrencileri kesinlikle haritada yer almaz.
     *
     * @return {studentId: okunmamışAdet} haritası
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getUnseenCounts() {
        AppUser currentUser = securityUtils.getCurrentUser();
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Oturum bulunamadı veya geçersiz.");
        }

        if (currentUser.getRole() != UserRole.TEACHER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Yalnızca öğretmenler okunmamış ilerleme sayısını sorgulayabilir.");
        }

        // Çağıran öğretmenin eşleştiği tüm öğrencileri al
        List<UserDto> students = membershipService.getStudentsOfTeacher(currentUser.getId());
        Map<String, Long> counts = new LinkedHashMap<>();
        for (UserDto s : students) {
            counts.put(s.id().toString(), 0L);
        }

        List<Object[]> rows = studyPlanItemRepository.countUnseenCompletedItemsByTeacherId(currentUser.getId());
        for (Object[] row : rows) {
            UUID studentId = (UUID) row[0];
            Long count = ((Number) row[1]).longValue();
            if (counts.containsKey(studentId.toString())) {
                counts.put(studentId.toString(), count);
            }
        }

        return counts;
    }
}
