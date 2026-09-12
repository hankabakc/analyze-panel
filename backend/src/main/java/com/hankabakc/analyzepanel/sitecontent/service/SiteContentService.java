package com.hankabakc.analyzepanel.sitecontent.service;

import com.hankabakc.analyzepanel.auth.entity.AppUser;
import com.hankabakc.analyzepanel.core.security.SecurityUtils;
import com.hankabakc.analyzepanel.sitecontent.dto.SiteContentFieldDto;
import com.hankabakc.analyzepanel.sitecontent.entity.SiteContent;
import com.hankabakc.analyzepanel.sitecontent.repository.SiteContentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SiteContentService: Karşılama sitesi içeriğini okur ve yöneticinin değişikliklerini kaydeder (T-063A / S-026).
 *
 * <ul>
 *   <li>Okuma herkese açıktır; izin listesindeki her anahtar döner, doldurulmamışlar boş metindir.</li>
 *   <li>Yazma: önce bütün alanlar doğrulanır (izin listesi + azami uzunluk), sonra kaydedilir; biri
 *       geçersizse hiçbiri yazılmaz (ENG-12 §2.1, §2.3).</li>
 * </ul>
 */
@Service
public class SiteContentService {

    private final SiteContentRepository repository;
    private final SecurityUtils securityUtils;

    public SiteContentService(SiteContentRepository repository, SecurityUtils securityUtils) {
        this.repository = repository;
        this.securityUtils = securityUtils;
    }

    /**
     * getAll: İzin listesindeki bütün alanları tanım sırasıyla döner; doldurulmamışlar "" olur.
     */
    @Transactional(readOnly = true)
    public Map<String, String> getAll() {
        Map<String, String> stored = new HashMap<>();
        for (SiteContent content : repository.findAll()) {
            stored.put(content.getKey(), content.getValue());
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : SiteContentFields.maxLengths().keySet()) {
            result.put(key, stored.getOrDefault(key, ""));
        }
        return result;
    }

    /**
     * getFields: Doldurulabilir alanlar ve azami uzunlukları (yönetici editörü için).
     */
    public List<SiteContentFieldDto> getFields() {
        return SiteContentFields.maxLengths().entrySet().stream()
                .map(field -> new SiteContentFieldDto(field.getKey(), field.getValue()))
                .toList();
    }

    /**
     * update: Gönderilen alanları doğrular ve kaydeder; güncel içeriğin tamamını döner.
     *
     * @param values anahtar → yeni değer
     */
    @Transactional
    public Map<String, String> update(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Değiştirilecek en az bir alan gönderilmelidir.");
        }

        Map<String, String> cleaned = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            Integer maxLength = SiteContentFields.maxLengths().get(entry.getKey());
            if (maxLength == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bilinmeyen içerik alanı gönderildi.");
            }
            String value = SiteContentFields.clean(entry.getValue());
            if (value.length() > maxLength) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "'" + entry.getKey() + "' alanı en fazla " + maxLength + " karakter olabilir.");
            }
            cleaned.put(entry.getKey(), value);
        }

        AppUser currentUser = securityUtils.getCurrentUser();
        UUID updatedBy = currentUser != null ? currentUser.getId() : null;
        Instant now = Instant.now();

        Map<String, SiteContent> existing = new HashMap<>();
        for (SiteContent content : repository.findAllById(cleaned.keySet())) {
            existing.put(content.getKey(), content);
        }

        List<SiteContent> toSave = new ArrayList<>();
        cleaned.forEach((key, value) -> {
            SiteContent content = existing.getOrDefault(key, new SiteContent(key));
            content.setValue(value);
            content.setUpdatedAt(now);
            content.setUpdatedBy(updatedBy);
            toSave.add(content);
        });
        repository.saveAll(toSave);

        return getAll();
    }
}
