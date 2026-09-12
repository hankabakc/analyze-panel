package com.hankabakc.analyzepanel.sitecontent.controller;

import com.hankabakc.analyzepanel.core.audit.annotation.AuditAction;
import com.hankabakc.analyzepanel.core.model.ApiResponse;
import com.hankabakc.analyzepanel.sitecontent.dto.SiteContentFieldDto;
import com.hankabakc.analyzepanel.sitecontent.dto.UpdateSiteContentRequest;
import com.hankabakc.analyzepanel.sitecontent.service.SiteContentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * SiteContentController: Karşılama sitesi içerik uçları (T-063A / S-026).
 *
 * <ul>
 *   <li>{@code GET /api/v1/site-content} — herkese açık (SecurityConfig).</li>
 *   <li>{@code GET /api/v1/site-content/fields} — yalnızca yönetici.</li>
 *   <li>{@code PUT /api/v1/site-content} — yalnızca yönetici; denetim kaydına yazılır.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/site-content")
public class SiteContentController {

    private final SiteContentService siteContentService;

    public SiteContentController(SiteContentService siteContentService) {
        this.siteContentService = siteContentService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> getContent() {
        return ResponseEntity.ok(ApiResponse.success(siteContentService.getAll(), "Site içeriği getirildi."));
    }

    @GetMapping("/fields")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ApiResponse<List<SiteContentFieldDto>>> getFields() {
        return ResponseEntity.ok(ApiResponse.success(siteContentService.getFields(), "İçerik alanları getirildi."));
    }

    @PutMapping
    @PreAuthorize("hasRole('MANAGER')")
    @AuditAction("SITE_CONTENT_UPDATE")
    public ResponseEntity<ApiResponse<Map<String, String>>> updateContent(
            @Valid @RequestBody UpdateSiteContentRequest request) {
        Map<String, String> updated = siteContentService.update(request.values());
        return ResponseEntity.ok(ApiResponse.success(updated, "Site içeriği kaydedildi."));
    }
}
