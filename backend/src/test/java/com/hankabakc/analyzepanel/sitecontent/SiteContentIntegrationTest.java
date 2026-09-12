package com.hankabakc.analyzepanel.sitecontent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hankabakc.analyzepanel.auth.entity.AppUser;
import com.hankabakc.analyzepanel.auth.enums.UserRole;
import com.hankabakc.analyzepanel.auth.enums.UserStatus;
import com.hankabakc.analyzepanel.auth.repository.AppUserRepository;
import com.hankabakc.analyzepanel.core.audit.repository.AuditLogRepository;
import com.hankabakc.analyzepanel.sitecontent.dto.UpdateSiteContentRequest;
import com.hankabakc.analyzepanel.sitecontent.entity.SiteContent;
import com.hankabakc.analyzepanel.sitecontent.repository.SiteContentRepository;
import com.hankabakc.analyzepanel.sitecontent.service.SiteContentFields;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SiteContentIntegrationTest: Karşılama sitesi içerik uçları (T-063A / S-026).
 *
 * <p>Test yalnızca {@link #TOUCHED_KEYS} anahtarlarına yazar; her testten önce bu anahtarların
 * mevcut değeri alınır, sonra birebir geri yüklenir. Yöneticinin doldurduğu gerçek içerik bozulmaz.</p>
 */
@SpringBootTest
class SiteContentIntegrationTest {

    private static final List<String> TOUCHED_KEYS =
            List.of("home.heroTitle", "faq.1.question", "contact.note", "privacy.body");

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private SiteContentRepository contentRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private MockMvc mockMvc;
    private AppUser manager;
    private AppUser teacher;
    private AppUser student;
    private final List<UUID> createdUserIds = new ArrayList<>();
    private final Map<String, Optional<SiteContent>> snapshot = new HashMap<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        TOUCHED_KEYS.forEach(key -> snapshot.put(key, contentRepository.findById(key)));
        manager = createUser("sitecontent_mgr_", UserRole.MANAGER);
        teacher = createUser("sitecontent_tch_", UserRole.TEACHER);
        student = createUser("sitecontent_std_", UserRole.STUDENT);
    }

    @AfterEach
    void tearDown() {
        snapshot.forEach((key, previous) -> {
            if (previous.isPresent()) {
                contentRepository.save(previous.get());
            } else if (contentRepository.existsById(key)) {
                contentRepository.deleteById(key);
            }
        });
        snapshot.clear();
        auditLogRepository.deleteByUserIdIn(createdUserIds);
        createdUserIds.forEach(userRepository::deleteById);
        createdUserIds.clear();
    }

    @Test
    @DisplayName("Anonim ziyaretçi içeriği okur; izin listesindeki bütün anahtarlar döner")
    void testAnonymousGet_ReturnsAllWhitelistedKeys() throws Exception {
        mockMvc.perform(get("/api/v1/site-content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.length()", is(SiteContentFields.maxLengths().size())))
                .andExpect(jsonPath("$.data['home.heroTitle']").exists())
                .andExpect(jsonPath("$.data['faq.8.answer']").exists());
    }

    @Test
    @DisplayName("Yönetici alanları kaydeder; değer kalıcıdır, herkese açık okumada görünür ve kaydedeni tutar")
    void testManagerPut_PersistsAndIsPublic() throws Exception {
        mockMvc.perform(asManager(putContent(Map.of("home.heroTitle", "Test Başlık", "faq.1.question", "Soru bir?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data['home.heroTitle']", is("Test Başlık")));

        mockMvc.perform(get("/api/v1/site-content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data['home.heroTitle']", is("Test Başlık")))
                .andExpect(jsonPath("$.data['faq.1.question']", is("Soru bir?")));

        SiteContent saved = contentRepository.findById("home.heroTitle").orElseThrow();
        assertEquals(manager.getId(), saved.getUpdatedBy());
    }

    @Test
    @DisplayName("Öğretmen ve öğrenci içerik yazamaz (403); değer değişmez")
    void testTeacherAndStudentPut_Forbidden() throws Exception {
        String before = currentValue("home.heroTitle");

        mockMvc.perform(putContent(Map.of("home.heroTitle", "Öğretmen denemesi"))
                        .with(user(teacher.getEmail()).roles("TEACHER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(putContent(Map.of("home.heroTitle", "Öğrenci denemesi"))
                        .with(user(student.getEmail()).roles("STUDENT")))
                .andExpect(status().isForbidden());

        assertEquals(before, currentValue("home.heroTitle"));
    }

    @Test
    @DisplayName("Oturumsuz yazma isteği reddedilir (401)")
    void testAnonymousPut_Unauthorized() throws Exception {
        mockMvc.perform(putContent(Map.of("home.heroTitle", "Anonim denemesi")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("İzin listesinde olmayan anahtar 400 alır ve istekteki geçerli alan da yazılmaz")
    void testUnknownKey_Returns400AndNothingSaved() throws Exception {
        String before = currentValue("home.heroTitle");
        Map<String, String> values = new LinkedHashMap<>();
        values.put("home.heroTitle", "Yazılmamalı");
        values.put("admin.secret", "x");

        mockMvc.perform(asManager(putContent(values)))
                .andExpect(status().isBadRequest());

        assertEquals(before, currentValue("home.heroTitle"));
    }

    @Test
    @DisplayName("Azami uzunluğu aşan değer 400 alır")
    void testTooLongValue_Returns400() throws Exception {
        int max = SiteContentFields.maxLengths().get("contact.note");

        mockMvc.perform(asManager(putContent(Map.of("contact.note", "a".repeat(max + 1)))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asManager(putContent(Map.of("contact.note", "a".repeat(max)))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("HTML içeren değer düz metin olarak saklanır ve aynen döner (kaçış gösterimde yapılır)")
    void testHtmlValue_StoredAsPlainText() throws Exception {
        String html = "<script>alert(1)</script><b>kalın</b>";

        mockMvc.perform(asManager(putContent(Map.of("privacy.body", html))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/site-content"))
                .andExpect(jsonPath("$.data['privacy.body']", is(html)));
    }

    @Test
    @DisplayName("Satır sonları \\n'e indirilir, kontrol karakterleri ayıklanır, kenar boşlukları kırpılır")
    void testValueIsCleaned() throws Exception {
        mockMvc.perform(asManager(putContent(Map.of("contact.note", "  Satır1\r\nSatır2  \t"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data['contact.note']", is("Satır1\nSatır2")));
    }

    @Test
    @DisplayName("Alan listesi yalnızca yöneticiye açık; öğretmen 403, oturumsuz 401")
    void testFieldsEndpoint_ManagerOnly() throws Exception {
        mockMvc.perform(get("/api/v1/site-content/fields").with(user(manager.getEmail()).roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(SiteContentFields.maxLengths().size())))
                .andExpect(jsonPath("$.data[*].key", hasItem("privacy.body")));
        mockMvc.perform(get("/api/v1/site-content/fields").with(user(teacher.getEmail()).roles("TEACHER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/site-content/fields"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Alan gönderilmeyen istek (values yok veya boş) 400 alır")
    void testEmptyRequest_Returns400() throws Exception {
        mockMvc.perform(asManager(put("/api/v1/site-content").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asManager(putContent(Map.of())))
                .andExpect(status().isBadRequest());
    }

    private MockHttpServletRequestBuilder putContent(Map<String, String> values) throws Exception {
        return put("/api/v1/site-content")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdateSiteContentRequest(values)));
    }

    private MockHttpServletRequestBuilder asManager(MockHttpServletRequestBuilder request) {
        return request.with(user(manager.getEmail()).roles("MANAGER"));
    }

    private String currentValue(String key) {
        return contentRepository.findById(key).map(SiteContent::getValue).orElse("");
    }

    private AppUser createUser(String prefix, UserRole role) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        AppUser saved = userRepository.save(new AppUser(
                prefix + suffix + "@test.local",
                prefix + suffix,
                role,
                UserStatus.ACTIVE,
                role == UserRole.STUDENT ? 8 : null,
                "$2a$12$e0MYzXyjpJS7Pd0RVvHwHeFvX4e26tA7l9aI0M1E5jJ5m4m5a5m5a",
                false
        ));
        createdUserIds.add(saved.getId());
        return saved;
    }
}
