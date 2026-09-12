package com.hankabakc.analyzepanel.infrastructure.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * StorageService: Uygulama genelindeki dosya yükleme (upload) ve saklama işlemlerini yönetir.
 */
@Service
public class StorageService {

    // T-069: Karne dosyalarının yeri yapılandırmadan gelir; konteynerde kalıcı diske bağlanır.
    private final Path rootLocation;

    /**
     * StorageService Constructor: Gerekli dizin yapısını kontrol eder ve yoksa oluşturur.
     */
    public StorageService(@Value("${app.storage.path:uploads/analysis}") String storagePath) {
        this.rootLocation = Paths.get(storagePath);
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("Dosya saklama dizini oluşturulamadı!", e);
        }
    }

    /**
     * store: Gelen PDF dosyasını doğrular ve güvenli bir isimle saklar.
     * OWASP 2026: Unrestricted File Upload (A04) zafiyetine karşı koruma sağlar.
     */
    public String store(MultipartFile file) {
        try {
            if (file.isEmpty()) {
                throw new RuntimeException("Boş dosya yüklenemez.");
            }

            // 1. MIME Type Doğrulaması (Gerçek PDF kontrolü)
            String contentType = file.getContentType();
            if (contentType == null || !contentType.equalsIgnoreCase("application/pdf")) {
                throw new RuntimeException("Sadece PDF dosyaları yüklenebilir.");
            }

            // 2. Dosya İsmi Sanitizasyonu ve Path Traversal Koruması
            String originalFileName = file.getOriginalFilename();
            if (originalFileName == null || originalFileName.contains("..")) {
                throw new RuntimeException("Geçersiz dosya adı.");
            }

            // 3. Benzersiz ve Rastgele Dosya İsmi (Girilmesi imkansız hale getirir)
            String extension = ".pdf";
            String safeFileName = UUID.randomUUID().toString() + extension;
            
            Path destinationFile = rootLocation.resolve(Paths.get(safeFileName))
                    .normalize().toAbsolutePath();

            // Dosyayı hedef konuma kopyala
            Files.copy(file.getInputStream(), destinationFile, StandardCopyOption.REPLACE_EXISTING);

            return safeFileName;
        } catch (IOException e) {
            throw new RuntimeException("Dosya kaydedilirken bir hata oluştu!", e);
        }
    }
}
