package com.hankabakc.analyzepanel.analysis;

import com.hankabakc.analyzepanel.analysis.entity.ReferenceSchool;
import com.hankabakc.analyzepanel.analysis.repository.ReferenceSchoolRepository;
import com.hankabakc.analyzepanel.analysis.repository.StudentProfileRepository;
import com.hankabakc.analyzepanel.auth.entity.AppUser;
import com.hankabakc.analyzepanel.auth.enums.UserRole;
import com.hankabakc.analyzepanel.auth.enums.UserStatus;
import com.hankabakc.analyzepanel.auth.repository.AppUserRepository;
import com.hankabakc.analyzepanel.auth.service.RefreshTokenService;
import com.hankabakc.analyzepanel.core.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * StudentProfileIntegrationTest: Öğrenci hedef profilinin okunması (T-065).
 *
 * <p>Okul seçen öğrencinin panelinde okulun adı görünmüyordu: uç, okul adı olmayan ham profil kaydını dönüyordu.
 * Test kendi öğrencisini ve okulunu oluşturur, sonunda siler; mevcut hesaplara ve okullara dokunmaz.</p>
 */
@SpringBootTest
class StudentProfileIntegrationTest {

    private static final String STUDENT_EMAIL = "t065.profil@analyzepanel.local";
    private static final String SCHOOL_NAME = "T-065 Test Anadolu Lisesi";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private StudentProfileRepository profileRepository;

    @Autowired
    private ReferenceSchoolRepository schoolRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private MockMvc mockMvc;
    private AppUser student;
    private ReferenceSchool school;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanup();
        student = userRepository.save(new AppUser(STUDENT_EMAIL, "Profil Öğrenci", UserRole.STUDENT,
                UserStatus.ACTIVE, 8, "$2a$12$...", false));
        school = schoolRepository.save(new ReferenceSchool(UUID.randomUUID(), "Test İli", SCHOOL_NAME, "ANADOLU",
                new BigDecimal("452.50"), new BigDecimal("3.10")));
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        userRepository.findByEmail(STUDENT_EMAIL).ifPresent(u -> {
            profileRepository.findByUserId(u.getId()).ifPresent(profileRepository::delete);
            auditLogRepository.deleteByUserIdIn(List.of(u.getId()));
            refreshTokenService.deleteByUserId(u);
            userRepository.delete(u);
        });
        auditLogRepository.deleteByUserEmailIn(List.of(STUDENT_EMAIL));
        if (school != null) {
            schoolRepository.findById(school.getId()).ifPresent(schoolRepository::delete);
        }
    }

    @Test
    @DisplayName("T-065: Profili olmayan öğrenci boş hedef alır; okuma profil kaydı oluşturmaz")
    void noProfile_ReturnsEmptyTarget_AndCreatesNothing() throws Exception {
        readProfile()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetSchoolId").doesNotExist())
                .andExpect(jsonPath("$.data.targetSchoolName").doesNotExist())
                .andExpect(jsonPath("$.data.targetScore").doesNotExist());

        assertTrue(profileRepository.findByUserId(student.getId()).isEmpty(), "GET profil kaydı oluşturmamalı");
    }

    @Test
    @DisplayName("T-065: Okul seçen öğrenci okulun adını ve taban puanını alır; profil ve kullanıcı kimliği dönmez")
    void schoolSelected_ReturnsSchoolNameAndBaseScore() throws Exception {
        asStudent(post("/api/v1/analysis/profile").param("schoolId", school.getId().toString()))
                .andExpect(status().isOk());

        readProfile()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetSchoolId").value(school.getId().toString()))
                .andExpect(jsonPath("$.data.targetSchoolName").value(SCHOOL_NAME))
                .andExpect(jsonPath("$.data.targetScore").value(452.5))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.userId").doesNotExist());
    }

    @Test
    @DisplayName("T-065: Elle girilen hedef puan okulun taban puanının önüne geçer")
    void manualScore_OverridesSchoolBaseScore() throws Exception {
        asStudent(post("/api/v1/analysis/profile")
                .param("schoolId", school.getId().toString())
                .param("manualScore", "470"))
                .andExpect(status().isOk());

        readProfile()
                .andExpect(jsonPath("$.data.targetSchoolName").value(SCHOOL_NAME))
                .andExpect(jsonPath("$.data.targetScore").value(470));
    }

    private ResultActions readProfile() throws Exception {
        return asStudent(get("/api/v1/analysis/profile"));
    }

    private ResultActions asStudent(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request
                .requestAttr("userId", student.getId().toString())
                .requestAttr("role", "STUDENT")
                .with(user(STUDENT_EMAIL).roles("STUDENT"))
                .with(csrf()));
    }
}
