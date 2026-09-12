package com.hankabakc.analyzepanel.membership.controller;

import com.hankabakc.analyzepanel.auth.dto.UserDto;
import com.hankabakc.analyzepanel.core.audit.annotation.AuditAction;
import com.hankabakc.analyzepanel.core.model.ApiResponse;
import com.hankabakc.analyzepanel.membership.dto.ActivityDto;
import com.hankabakc.analyzepanel.membership.dto.ClassDto;
import com.hankabakc.analyzepanel.membership.dto.ClassStudentRequest;
import com.hankabakc.analyzepanel.membership.dto.CreateClassRequest;
import com.hankabakc.analyzepanel.membership.dto.CreateUserRequest;
import com.hankabakc.analyzepanel.membership.dto.CreatedUserResponse;
import com.hankabakc.analyzepanel.membership.dto.PairingDto;
import com.hankabakc.analyzepanel.membership.dto.PairingRequest;
import com.hankabakc.analyzepanel.membership.service.MembershipService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * MembershipController: Kullanıcı yönetimi, onay süreçleri ve 
 * öğretmen-öğrenci eşleşmelerini yöneten API katmanıdır.
 */
@RestController
@RequestMapping("/api/v1/membership")
public class MembershipController {

    private final MembershipService membershipService;
    private final com.hankabakc.analyzepanel.membership.service.ActivityService activityService;
    private final com.hankabakc.analyzepanel.membership.service.ClassService classService;

    public MembershipController(MembershipService membershipService, com.hankabakc.analyzepanel.membership.service.ActivityService activityService, com.hankabakc.analyzepanel.membership.service.ClassService classService) {
        this.membershipService = membershipService;
        this.activityService = activityService;
        this.classService = classService;
    }

    /**
     * createUser: Yöneticinin yeni bir öğretmen veya öğrenci oluşturmasını sağlar (T-010).
     */
    @PostMapping("/users")
    @PreAuthorize("hasRole('MANAGER')")
    @AuditAction("CREATE_USER")
    public ApiResponse<CreatedUserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        CreatedUserResponse response = membershipService.createUser(request);
        return ApiResponse.success(response, "Kullanıcı başarıyla oluşturuldu.");
    }

    /**
     * getTeachers: Sistemdeki tüm aktif öğretmenleri getirir.
     */
    @GetMapping("/teachers")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<List<UserDto>> getTeachers() {
        List<UserDto> teachers = membershipService.getActiveTeachers();
        if (teachers.size() > 100) teachers = teachers.subList(0, 100);
        return ApiResponse.success(teachers, "Aktif öğretmenler listesi getirildi.");
    }

    /**
     * getStudents: Sistemdeki tüm aktif öğrencileri getirir.
     */
    @GetMapping("/students")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<List<UserDto>> getStudents() {
        List<UserDto> students = membershipService.getActiveStudents();
        if (students.size() > 100) students = students.subList(0, 100);
        return ApiResponse.success(students, "Aktif öğrenciler listesi getirildi.");
    }

    /**
     * getUnpairedStudents: Sistemde henüz hiçbir eğitmenle eşleştirilmemiş aktif öğrencileri listeler (T-021).
     */
    @GetMapping("/unpaired-students")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<List<UserDto>> getUnpairedStudents() {
        List<UserDto> students = membershipService.getUnpairedStudents();
        if (students.size() > 100) students = students.subList(0, 100);
        return ApiResponse.success(students, "Eşleşmemiş öğrenciler listesi getirildi.");
    }

    /**
     * getMyStudents: Giriş yapan öğretmenin kendi öğrencilerini listelemesini sağlar.
     */
    @GetMapping("/my-students")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<List<UserDto>> getMyStudents(@RequestAttribute("userId") String userId) {
        List<UserDto> students = membershipService.getStudentsOfTeacher(UUID.fromString(userId));
        return ApiResponse.success(students, "Öğrenci listeniz getirildi.");
    }

    /**
     * getMyTeachers: Giriş yapan öğrencinin kendi öğretmenlerini listelemesini sağlar.
     */
    @GetMapping("/my-teachers")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<List<UserDto>> getMyTeachers(@RequestAttribute("userId") String userId) {
        List<UserDto> teachers = membershipService.getTeachersOfStudent(UUID.fromString(userId));
        return ApiResponse.success(teachers, "Eğitmen listeniz getirildi.");
    }

    /**
     * getClasses: Sınıfları ve içindeki öğrencileri listeler (T-040). Yalnızca yönetici.
     */
    @GetMapping("/classes")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<List<ClassDto>> getClasses() {
        return ApiResponse.success(classService.getAllClasses(), "Sınıflar getirildi.");
    }

    /**
     * createClass: Yeni sınıf açar (T-040). Yalnızca yönetici.
     */
    @PostMapping("/classes")
    @PreAuthorize("hasRole('MANAGER')")
    @AuditAction("CLASS_CREATE")
    public ApiResponse<ClassDto> createClass(@Valid @RequestBody CreateClassRequest request) {
        return ApiResponse.success(classService.createClass(request.name()), "Sınıf oluşturuldu.");
    }

    /**
     * deleteClass: Sınıfı siler (T-040). İçindeki öğrenciler silinmez, sınıfsız kalır.
     */
    @DeleteMapping("/classes/{classId}")
    @PreAuthorize("hasRole('MANAGER')")
    @AuditAction("CLASS_DELETE")
    public ApiResponse<Void> deleteClass(@PathVariable UUID classId) {
        classService.deleteClass(classId);
        return ApiResponse.success(null, "Sınıf silindi; öğrenciler sınıfsız kaldı.");
    }

    /**
     * addStudentToClass: Öğrenciyi sınıfa ekler (T-040).
     */
    @PostMapping("/classes/{classId}/students")
    @PreAuthorize("hasRole('MANAGER')")
    @AuditAction("CLASS_STUDENT_ADD")
    public ApiResponse<Void> addStudentToClass(@PathVariable UUID classId,
                                               @Valid @RequestBody ClassStudentRequest request) {
        classService.addStudent(classId, request.studentId());
        return ApiResponse.success(null, "Öğrenci sınıfa eklendi.");
    }

    /**
     * removeStudentFromClass: Öğrenciyi sınıftan çıkarır (T-040).
     * Yalnızca sınıf bağı kopar; öğrencinin karne ve analiz verisi etkilenmez.
     */
    @DeleteMapping("/classes/{classId}/students/{studentId}")
    @PreAuthorize("hasRole('MANAGER')")
    @AuditAction("CLASS_STUDENT_REMOVE")
    public ApiResponse<Void> removeStudentFromClass(@PathVariable UUID classId, @PathVariable UUID studentId) {
        classService.removeStudent(classId, studentId);
        return ApiResponse.success(null, "Öğrenci sınıftan çıkarıldı; verisi korundu.");
    }

    /**
     * getActivityOverview: Kullanıcıların aktiflik özetini döndürür (T-037 / S-017 → B).
     *
     * <p>Her kullanıcı için son giriş zamanı ve rapor görüntüleme sayısı; öğretmenler için ayrıca
     * eşleştiği her öğrenciye <b>en son ne zaman baktığı</b> (hiç bakmadıysa boş) döner.
     * Yalnızca yöneticiye açıktır (ENG-11 §3.1).</p>
     */
    @GetMapping("/activity")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<List<ActivityDto>> getActivityOverview() {
        return ApiResponse.success(activityService.getActivityOverview(), "Aktiflik özeti getirildi.");
    }

    /**
     * getPairings: Sistemdeki tüm aktif öğretmen-öğrenci eşleşmelerini listeler (T-036).
     */
    @GetMapping("/pairings")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<List<PairingDto>> getPairings() {
        List<PairingDto> pairings = membershipService.getAllPairings();
        return ApiResponse.success(pairings, "Aktif eşleşmeler listesi başarıyla getirildi.");
    }

    /**
     * pair: Bir öğrenci ve öğretmeni birbirine bağlar.
     */
    @PostMapping("/pair")
    @PreAuthorize("hasRole('MANAGER')")
    @AuditAction("STUDENT_TEACHER_PAIR")
    public ApiResponse<Void> pairStudentTeacher(@RequestBody PairingRequest request) {
        membershipService.pairStudentWithTeacher(request);
        return ApiResponse.success(null, "Eşleştirme işlemi başarıyla tamamlandı.");
    }

    /**
     * unpair: Bir öğrenci ile öğretmen arasındaki eşleştirmeyi sonlandırır (T-036).
     * Yalnızca student_teacher_pairings tablosundaki satır silinir; analiz veya karne verilerine dokunulmaz.
     */
    @PostMapping("/unpair")
    @PreAuthorize("hasRole('MANAGER')")
    @AuditAction("STUDENT_TEACHER_UNPAIR")
    public ApiResponse<Void> unpairStudentTeacher(@RequestBody PairingRequest request) {
        membershipService.unpairStudentWithTeacher(request);
        return ApiResponse.success(null, "Eşleştirme başarıyla sonlandırıldı.");
    }

    /**
     * resetUserPassword: Yöneticinin bir öğretmen veya öğrenci için yeni geçici şifre üretmesini sağlar (T-013).
     * 
     * <p>Güvenlik Standartları:</p>
     * <ul>
     *   <li>Yalnızca MANAGER rolüne açıktır (ENG-11 §3.1).</li>
     *   <li>Denetim izi için @AuditAction("PASSWORD_RESET") ile işaretlenir (APP-03 §4).</li>
     *   <li>Üretilen geçici şifre yalnızca bu yanıtta döner (APP-01 §2.2, ENG-10 §1).</li>
     * </ul>
     */
    @PostMapping("/users/{id}/password/reset")
    @PreAuthorize("hasRole('MANAGER')")
    @AuditAction("PASSWORD_RESET")
    public ApiResponse<com.hankabakc.analyzepanel.membership.dto.ResetPasswordResponse> resetUserPassword(@PathVariable UUID id) {
        com.hankabakc.analyzepanel.membership.dto.ResetPasswordResponse response = membershipService.resetUserPassword(id);
        return ApiResponse.success(response, "Kullanıcı için yeni geçici şifre başarıyla üretildi.");
    }

    /**
     * getManagersCount: Sistemdeki aktif yönetici sayısını döndürür (T-075).
     * Yönetici panelinde tek yönetici kaldığında güvenlik uyarısı gösterilmesi amacıyla kullanılır.
     */
    @GetMapping("/managers/count")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponse<Long> getManagersCount() {
        long count = membershipService.getActiveManagerCount();
        return ApiResponse.success(count, "Aktif yönetici sayısı getirildi.");
    }
}
