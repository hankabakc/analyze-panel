package com.hankabakc.analyzepanel.auth.cli;

import com.hankabakc.analyzepanel.auth.entity.AppUser;
import com.hankabakc.analyzepanel.auth.enums.UserRole;
import com.hankabakc.analyzepanel.auth.enums.UserStatus;
import com.hankabakc.analyzepanel.auth.repository.AppUserRepository;
import com.hankabakc.analyzepanel.auth.service.PasswordGenerator;
import com.hankabakc.analyzepanel.auth.service.RefreshTokenService;
import com.hankabakc.analyzepanel.core.audit.entity.AuditLog;
import com.hankabakc.analyzepanel.core.audit.repository.AuditLogRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * AdminRecoveryRunner: Sunucu üzerinden doğrudan çalıştırılan yönetici kurtarma ve yönetim aracıdır (T-075).
 * 
 * <p>Mühendislik ve Güvenlik Standartları:</p>
 * <ul>
 *   <li>IST-01 §3.2: Bu kurtarma yolunun hiçbir HTTP API ucu yoktur; yalnızca sunucuya doğrudan erişimi olan
 *       yetkili sistem işletmeni CLI üzerinden tetikleyebilir.</li>
 *   <li>ENG-10 §1: Geçici şifreler loglara veya dosyalara KESİNLİKLE yazılmaz; yalnızca çalıştıran yöneticinin
 *       terminaline tek seferlik basılır.</li>
 *   <li>ENG-11 §1.2 & §2.4: Kurtarma sonrasında kullanıcının must_change_password bayrağı açılır ve önceki tüm
 *       oturumları (JWT ve Refresh Token) geçersiz kılınır.</li>
 *   <li>ENG-11 §3.3: İşlem audit_logs tablosuna denetim kaydı olarak düşülür.</li>
 * </ul>
 */
@Component
public class AdminRecoveryRunner implements ApplicationRunner {

    private final AppUserRepository userRepository;
    private final PasswordGenerator passwordGenerator;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuditLogRepository auditLogRepository;
    private final TransactionTemplate transactionTemplate;

    private Consumer<Integer> exitHandler = System::exit;

    public AdminRecoveryRunner(
            AppUserRepository userRepository,
            PasswordGenerator passwordGenerator,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService,
            AuditLogRepository auditLogRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.userRepository = userRepository;
        this.passwordGenerator = passwordGenerator;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.auditLogRepository = auditLogRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void setExitHandler(Consumer<Integer> exitHandler) {
        this.exitHandler = exitHandler;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean hasRecoverAdmin = args.containsOption("recover-admin");
        boolean hasCreateAdmin = args.containsOption("create-admin");

        if (!hasRecoverAdmin && !hasCreateAdmin) {
            return;
        }

        if (hasRecoverAdmin) {
            handleRecoverAdmin(args);
        } else {
            handleCreateAdmin(args);
        }
    }

    private void handleRecoverAdmin(ApplicationArguments args) {
        List<String> values = args.getOptionValues("recover-admin");
        if (values == null || values.isEmpty() || values.get(0).isBlank()) {
            System.err.println("🚨 Hata: '--recover-admin=<email>' parametresi geçerli bir e-posta adresi içermelidir.");
            exitHandler.accept(1);
            return;
        }

        String email = values.get(0).trim().toLowerCase();
        String rawPassword = passwordGenerator.generateReadablePassword(12);
        String encodedPassword = passwordEncoder.encode(rawPassword);

        boolean[] success = new boolean[]{false};

        transactionTemplate.executeWithoutResult(status -> {
            Optional<AppUser> userOpt = userRepository.findByEmail(email);

            if (userOpt.isEmpty()) {
                System.err.println("🚨 Hata: Belirtilen e-posta adresiyle kayıtlı bir kullanıcı bulunamadı: " + email);
                return;
            }

            AppUser user = userOpt.get();
            if (user.getRole() != UserRole.MANAGER) {
                System.err.println("🚨 Hata: Belirtilen kullanıcı yönetici (MANAGER) rolüne sahip değil: " + email);
                return;
            }

            // 1. Kullanıcı bilgilerini ve zorunlu şifre değişim bayrağını güncelle
            user.setPassword(encodedPassword);
            user.setMustChangePassword(true);
            user.setStatus(UserStatus.ACTIVE);
            user.setCredentialsInvalidatedAt(Instant.now());
            userRepository.save(user);

            // 2. Kullanıcının önceki tüm açık oturumlarını ve refresh token'larını geçersiz kıl
            refreshTokenService.deleteByUserId(user);

            // 3. Denetim loguna kaydet (Şifre ve PII loga girmez)
            AuditLog auditLog = new AuditLog(
                    "ADMIN_RECOVERY",
                    user.getId(),
                    "SYSTEM_CLI",
                    "127.0.0.1 (CLI)",
                    "Yönetici şifresi CLI kurtarma komutuyla sıfırlandı. must_change_password=true atandı."
            );
            auditLogRepository.save(auditLog);

            success[0] = true;
        });

        if (success[0]) {
            // Terminale tek seferlik güvenli çıktıyı bas
            printRecoverySuccessOutput(email, rawPassword);
            exitHandler.accept(0);
        } else {
            exitHandler.accept(1);
        }
    }

    private void handleCreateAdmin(ApplicationArguments args) {
        List<String> values = args.getOptionValues("create-admin");
        if (values == null || values.isEmpty() || values.get(0).isBlank()) {
            System.err.println("🚨 Hata: '--create-admin=<email>' parametresi geçerli bir e-posta adresi içermelidir.");
            exitHandler.accept(1);
            return;
        }

        String email = values.get(0).trim().toLowerCase();
        String resolvedName = "Sistem Yöneticisi";
        if (args.containsOption("name")) {
            List<String> nameValues = args.getOptionValues("name");
            if (nameValues != null && !nameValues.isEmpty() && !nameValues.get(0).isBlank()) {
                resolvedName = nameValues.get(0).trim();
            }
        }
        final String fullName = resolvedName;

        String rawPassword = passwordGenerator.generateReadablePassword(12);
        String encodedPassword = passwordEncoder.encode(rawPassword);

        boolean[] success = new boolean[]{false};

        transactionTemplate.executeWithoutResult(status -> {
            if (userRepository.findByEmail(email).isPresent()) {
                System.err.println("🚨 Hata: Bu e-posta adresiyle kayıtlı bir kullanıcı zaten mevcut: " + email);
                return;
            }

            // 1. Yeni yönetici kullanıcısını oluştur
            AppUser newAdmin = new AppUser(
                    email,
                    fullName,
                    UserRole.MANAGER,
                    UserStatus.ACTIVE,
                    null,
                    encodedPassword,
                    true
            );
            newAdmin.setCredentialsInvalidatedAt(Instant.now());
            AppUser savedAdmin = userRepository.save(newAdmin);

            // 2. Denetim loguna kaydet
            AuditLog auditLog = new AuditLog(
                    "ADMIN_CREATE",
                    savedAdmin.getId(),
                    "SYSTEM_CLI",
                    "127.0.0.1 (CLI)",
                    "Yeni yönetici hesabı CLI komutuyla oluşturuldu. must_change_password=true atandı."
            );
            auditLogRepository.save(auditLog);

            success[0] = true;
        });

        if (success[0]) {
            // Terminale tek seferlik çıktıyı bas
            printCreateSuccessOutput(email, fullName, rawPassword);
            exitHandler.accept(0);
        } else {
            exitHandler.accept(1);
        }
    }

    private void printRecoverySuccessOutput(String email, String rawPassword) {
        System.out.println();
        System.out.println("================================================================================");
        System.out.println("✅ [ANALYZEPANEL] YÖNETİCİ ŞİFRE KURTARMA İŞLEMİ BAŞARIYLA TAMAMLANDI");
        System.out.println("================================================================================");
        System.out.println("Yönetici E-Posta: " + email);
        System.out.println("Yeni Geçici Şifre: " + rawPassword);
        System.out.println();
        System.out.println("ÖNEMLİ GÜVENLİK NOTLARI (ENG-10 §1 / ENG-11 §1.2):");
        System.out.println("1. Bu şifre sistem loglarına KESİNLİKLE kaydedilmemiştir ve bir daha gösterilmeyecektir.");
        System.out.println("2. Yönetici sisteme giriş yaptığında şifresini zorunlu olarak değiştirecektir.");
        System.out.println("3. Önceki tüm açık oturumlar ve belirteçler güvenlik gereği iptal edilmiştir.");
        System.out.println("4. İşlem denetim günlüğüne (audit_logs) işlenmiştir.");
        System.out.println("================================================================================");
        System.out.println();
    }

    private void printCreateSuccessOutput(String email, String fullName, String rawPassword) {
        System.out.println();
        System.out.println("================================================================================");
        System.out.println("✅ [ANALYZEPANEL] YENİ YÖNETİCİ HESABI BAŞARIYLA OLUŞTURULDU");
        System.out.println("================================================================================");
        System.out.println("Yönetici E-Posta: " + email);
        System.out.println("Ad Soyad:        " + fullName);
        System.out.println("İlk Giriş Şifresi: " + rawPassword);
        System.out.println();
        System.out.println("ÖNEMLİ GÜVENLİK NOTLARI (ENG-10 §1 / ENG-11 §1.2):");
        System.out.println("1. Bu şifre sistem loglarına KESİNLİKLE kaydedilmemiştir ve bir daha gösterilmeyecektir.");
        System.out.println("2. Yönetici sisteme ilk giriş yaptığında şifresini zorunlu olarak değiştirecektir.");
        System.out.println("3. İşlem denetim günlüğüne (audit_logs) işlenmiştir.");
        System.out.println("================================================================================");
        System.out.println();
    }
}
