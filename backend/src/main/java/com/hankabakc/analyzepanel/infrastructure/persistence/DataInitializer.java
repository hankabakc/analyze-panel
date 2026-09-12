package com.hankabakc.analyzepanel.infrastructure.persistence;

import com.hankabakc.analyzepanel.auth.entity.AppUser;
import com.hankabakc.analyzepanel.auth.enums.UserRole;
import com.hankabakc.analyzepanel.auth.enums.UserStatus;
import com.hankabakc.analyzepanel.auth.repository.AppUserRepository;
import com.hankabakc.analyzepanel.auth.service.PasswordGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * DataInitializer: Uygulama ilk ayağa kalktığında sistem yöneticisi hesabını hazır hale getirir.
 * 
 * <p>Mühendislik Standartları:</p>
 * <ul>
 *   <li>T-009 / K6: Yeni yönetici oluşturulurken PasswordGenerator ile güvenli okunabilir şifre üretilir,
 *       BCrypt cost 12 ile hash'lenir ve must_change_password = true yazılır.</li>
 *   <li>T-070 / S-027: Şifreler ve kimlik bilgileri loglara/konsola yazılmaz; SLF4J Logger kullanılır (ENG-10 §1).</li>
 * </ul>
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final AppUserRepository userRepository;
    private final PasswordGenerator passwordGenerator;
    private final PasswordEncoder passwordEncoder;

    @Value("${application.security.initial-admin-email:admin@admin.com}")
    private String adminEmail;

    public DataInitializer(AppUserRepository userRepository,
                           PasswordGenerator passwordGenerator,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordGenerator = passwordGenerator;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * run: Sistem yöneticisi hesabı yoksa oluşturur, varsa dokunmaz.
     */
    @Override
    @Transactional
    public void run(String... args) {
        checkDuplicatePasswordHashes();

        if (userRepository.findByEmail(adminEmail).isPresent()) {
            log.info(">> Sistem Yöneticisi zaten mevcut: {}", adminEmail);
            return;
        }

        String rawPassword = passwordGenerator.generateReadablePassword();
        String encodedPassword = passwordEncoder.encode(rawPassword);

        AppUser admin = new AppUser(
            adminEmail,
            "Sistem Yöneticisi",
            UserRole.MANAGER,
            UserStatus.ACTIVE,
            null,
            encodedPassword,
            true // K6: İlk girişte şifre değiştirme zorunluluğu
        );
        
        userRepository.save(admin);
        log.info(">> Sistem Yöneticisi Başarıyla Hazırlandı: {} (must_change_password=true)", adminEmail);
    }

    /**
     * checkDuplicatePasswordHashes: Veritabanında mükerrer (kopya) şifre hash'i bulunup bulunmadığını denetler (T-041 / ENG-10 §1).
     *
     * <p>Rastgele tuzlu BCrypt şifreleyicide iki farklı kullanıcının aynı hash değerine sahip olması
     * matematiksel olarak imkansızdır. Eğer aynı hash birden fazla satırda varsa, bu dışarıdan toplu SQL
     * müdahalesi yapıldığını gösterir ve güvenlik uyarısı verilir.</p>
     */
    private void checkDuplicatePasswordHashes() {
        long totalUsers = userRepository.count();
        if (totalUsers > 0) {
            long distinctHashes = userRepository.countDistinctPasswordHashes();
            if (distinctHashes < totalUsers) {
                log.error("🚨 [GÜVENLİK UYARISI] app_users tablosunda mükerrer şifre hash'i tespit edildi! Toplam Kullanıcı: {}, Benzersiz Hash: {}", 
                        totalUsers, distinctHashes);
            }
        }
    }
}
