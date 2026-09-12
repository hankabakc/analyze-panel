package com.hankabakc.analyzepanel.sitecontent.repository;

import com.hankabakc.analyzepanel.sitecontent.entity.SiteContent;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * SiteContentRepository: Karşılama sitesi içerik değerleri (T-063A).
 */
public interface SiteContentRepository extends JpaRepository<SiteContent, String> {
}
