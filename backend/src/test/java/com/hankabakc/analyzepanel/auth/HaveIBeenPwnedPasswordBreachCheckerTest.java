package com.hankabakc.analyzepanel.auth;

import com.hankabakc.analyzepanel.auth.service.HaveIBeenPwnedPasswordBreachChecker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HaveIBeenPwnedPasswordBreachCheckerTest: Canlı sızıntı denetiminin karar mantığını ağa çıkmadan sınar
 * (T-069 / IST-08 §1.1). Servis yanıtı testte verilir; gerçek HTTP çağrısı yapılmaz.
 *
 * <p>"password" şifresinin SHA-1 özeti herkesçe bilinen sabittir:
 * 5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8 → önek 5BAA6, kalan 1E4C9B93F3F0682250B6CF8331B7EE68FD8.</p>
 */
class HaveIBeenPwnedPasswordBreachCheckerTest {

    private static final String PASSWORD = "password";
    private static final String PREFIX = "5BAA6";
    private static final String SUFFIX = "1E4C9B93F3F0682250B6CF8331B7EE68FD8";

    private static HaveIBeenPwnedPasswordBreachChecker checkerWith(UnaryOperator<String> fetcher) {
        try {
            Constructor<HaveIBeenPwnedPasswordBreachChecker> constructor =
                    HaveIBeenPwnedPasswordBreachChecker.class.getDeclaredConstructor(UnaryOperator.class);
            constructor.setAccessible(true);
            return constructor.newInstance(fetcher);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("T-069: Sızdırılmış şifre listede bulunur ve reddedilir")
    void testBreachedPassword_IsDetected() {
        var checker = checkerWith(prefix -> "0000000000000000000000000000000000A:12\r\n" + SUFFIX + ":9659365");
        assertTrue(checker.isBreached(PASSWORD));
    }

    @Test
    @DisplayName("T-069: Listede olmayan şifre temiz sayılır")
    void testCleanPassword_IsAccepted() {
        var checker = checkerWith(prefix -> "0000000000000000000000000000000000A:12\r\n0000000000000000000000000000000000B:3");
        assertFalse(checker.isBreached("Bu-Sifre-Sizmadi-2026!"));
    }

    @Test
    @DisplayName("T-069 / APP-01 §2.4: Servis hata verirse şifre kabul edilir (fail-open)")
    void testServiceFailure_AcceptsPassword() {
        var checker = checkerWith(prefix -> {
            throw new IllegalStateException("Sızıntı denetim servisine ulaşılamadı");
        });
        assertFalse(checker.isBreached(PASSWORD), "Dış servis çökünce kullanıcı engellenmemeli");
    }

    @Test
    @DisplayName("T-069 / APP-03 §4: Servise yalnızca özetin ilk 5 hanesi gider, şifre veya tam özet gitmez")
    void testOnlyHashPrefixLeavesTheApplication() {
        List<String> sent = new ArrayList<>();
        var checker = checkerWith(prefix -> {
            sent.add(prefix);
            return "";
        });

        checker.isBreached(PASSWORD);

        assertEquals(List.of(PREFIX), sent);
        assertFalse(sent.getFirst().contains(PASSWORD));
        assertFalse(sent.getFirst().contains(SUFFIX));
    }

    @Test
    @DisplayName("T-069: Boş şifre servise hiç sorulmaz")
    void testBlankPassword_SkipsService() {
        List<String> sent = new ArrayList<>();
        var checker = checkerWith(prefix -> {
            sent.add(prefix);
            return "";
        });

        assertFalse(checker.isBreached("   "));
        assertFalse(checker.isBreached(null));
        assertTrue(sent.isEmpty());
    }
}
