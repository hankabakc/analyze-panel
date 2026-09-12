package com.hankabakc.analyzepanel.auth.service;

/**
 * PasswordBreachChecker: Şifre değiştirme veya oluşturma süreçlerinde, seçilen şifrenin
 * daha önce bilinen veri ihlallerinde (HaveIBeenPwned vb.) sızdırılıp sızdırılmadığını
 * denetleyen port arayüzüdür.
 * 
 * <p>Mühendislik Standartları:</p>
 * <ul>
 *   <li>APP-01 §2.6 & K3: Mock-önce yaklaşımı ile arayüz soyutlanır.</li>
 *   <li>APP-01 §2.4: Servis hatası durumunda şifre kabul edilmeli, kullanıcı engellenmemelidir.</li>
 * </ul>
 */
public interface PasswordBreachChecker {

    /**
     * isBreached: Şifrenin sızdırılmış veritabanlarında bulunup bulunmadığını kontrol eder.
     *
     * @param password Kontrol edilecek düz metin şifre
     * @return true ise şifre sızdırılmıştır (kabul edilmemeli), false ise temizdir
     */
    boolean isBreached(String password);
}
