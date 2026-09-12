/**
 * ==============================================================================
 * ⚠️ ANALYZEPANEL - E2E KİMLİK DOĞRULAMA VE VERİTABANI SIFIRLAMA TESTİ
 * ==============================================================================
 * 
 * ⚠️ DİKKAT (YIKICI İŞLEM / DESTRUCTIVE ACTION):
 * Bu betik test ortamını izole ve tekrarlanabilir kılmak amacıyla aşağıdaki
 * tabloları ve verileri SİLER / SIFIRLAR:
 *   - `otp_codes` tablosundaki tüm kayıtlar SİLİNİR.
 *   - `refresh_tokens` tablosundaki tüm kayıtlar SİLİNİR.
 *   - `app_users` tablosundaki MANAGER harici tüm kullanıcılar SİLİNİR.
 *   - MANAGER kullanıcısının şifresi `ADMIN_PASSWORD` ile verilen değere güncellenir.
 * 
 * 📋 ÖN KOŞULLAR:
 * 1. PostgreSQL veritabanı çalışıyor olmalıdır (Docker: `analyzepanel-db`).
 * 2. Backend servisi ayakta olmalıdır (Varsayılan: `http://localhost:8081`).
 * 3. Frontend Vite dev sunucusu ayakta olmalıdır (Varsayılan: `http://localhost:5174`).
 * 4. Sistemde Google Chrome tarayıcısı kurulu olmalıdır.
 * 5. `ADMIN_PASSWORD` ortam değişkeni ZORUNLUDUR (ENG-10 §1).
 * 
 * 🚀 ÇALIŞTIRMA:
 *   ADMIN_PASSWORD="<yonetici_sifresi>" npm run verify:auth:reset-db
 *   veya:
 *   ADMIN_PASSWORD="<yonetici_sifresi>" node scripts/verify-auth.mjs [BACKEND_LOG_PATH]
 * 
 * ⚙️ ORTAM DEĞİŞKENLERİ:
 *   ADMIN_PASSWORD  - [ZORUNLU] Yönetici test şifresi (Sabit değer yasaktır)
 *   ADMIN_EMAIL     - [İsteğe Bağlı] Yönetici e-postası (Varsayılan: admin@admin.com)
 *   FRONTEND_URL    - [İsteğe Bağlı] Frontend adresi (Varsayılan: http://localhost:5174)
 *   BACKEND_URL     - [İsteğe Bağlı] Backend adresi  (Varsayılan: http://localhost:8081)
 *   CHROME_PATH     - [İsteğe Bağlı] Özel Chrome binary yolu
 * ==============================================================================
 */

import puppeteer from 'puppeteer-core';
import { execSync, spawnSync } from 'child_process';
import fs from 'fs';

// -------------------------------------------------------------
// KONFİGÜRASYON VE ORTAM DEĞİŞKENLERİ (ENG-10 §1, APP-03 §4)
// -------------------------------------------------------------
const FRONTEND_URL = process.env.FRONTEND_URL || 'http://localhost:5174';
const BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8081';
const ADMIN_EMAIL = process.env.ADMIN_EMAIL || 'admin@admin.com';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD;

// K1: ADMIN_PASSWORD ortam değişkeni zorunludur, sabit varsayılan değer yasaktır
if (!ADMIN_PASSWORD) {
  console.error("\n==================================================================");
  console.error("❌ HATA: ADMIN_PASSWORD ortam değişkeni tanımlı değil!");
  console.error("Güvenlik protokolü (ENG-10 §1) gereği bu betik içinde sabit şifre barındırılamaz.");
  console.error("Kullanım: ADMIN_PASSWORD='<sifre>' npm run verify:auth:reset-db");
  console.error("==================================================================\n");
  process.exit(1);
}

// Chrome çalıştırılabilir dosya yolu tespit mekanizması
const CHROME_PATH = process.env.CHROME_PATH || [
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
  '/usr/bin/google-chrome',
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
].find(p => fs.existsSync(p)) || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';

let BACKEND_LOG_PATH = process.argv[2] || 'backend.log';

// -------------------------------------------------------------
// YARDIMCI METOTLAR: NORMALİZASYON VE DOM ETKİLEŞİMİ
// -------------------------------------------------------------

/**
 * Türkçe harf ve büyük/küçük harf duyarsız metin normalizasyonu
 */
function norm(s) {
  return (s || '')
    .toLowerCase()
    .replace(/ı/g, 'i')
    .replace(/i̇/g, 'i')
    .replace(/İ/g, 'i')
    .replace(/I/g, 'i')
    .replace(/ş/g, 's')
    .replace(/Ş/g, 's')
    .replace(/ğ/g, 'g')
    .replace(/Ğ/g, 'g')
    .replace(/ü/g, 'u')
    .replace(/Ü/g, 'u')
    .replace(/ö/g, 'o')
    .replace(/Ö/g, 'o')
    .replace(/ç/g, 'c')
    .replace(/Ç/g, 'c')
    .replace(/[\s\n\r\t]+/g, ' ')
    .trim();
}

/**
 * React 19 Controlled Input bileşenlerine değer yazma ve olay tetikleme
 */
async function fillInput(page, selector, value) {
  await page.waitForSelector(selector, { timeout: 10000 });
  await page.evaluate((sel, val) => {
    const el = document.querySelector(sel);
    if (!el) throw new Error(`Selector ${sel} not found`);
    const nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value')?.set;
    if (nativeSetter) {
      nativeSetter.call(el, val);
    } else {
      el.value = val;
    }
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }, selector, value);
}

/**
 * Sayfa gövdesinde belirli bir metnin belirmesini bekleme
 */
async function waitForTextInBody(page, text, timeout = 10000) {
  const target = norm(text);
  await page.waitForFunction((t) => {
    function normFn(s) {
      return (s || '')
        .toLowerCase()
        .replace(/ı/g, 'i')
        .replace(/i̇/g, 'i')
        .replace(/İ/g, 'i')
        .replace(/I/g, 'i')
        .replace(/ş/g, 's')
        .replace(/Ş/g, 's')
        .replace(/ğ/g, 'g')
        .replace(/Ğ/g, 'g')
        .replace(/ü/g, 'u')
        .replace(/Ü/g, 'u')
        .replace(/ö/g, 'o')
        .replace(/Ö/g, 'o')
        .replace(/ç/g, 'c')
        .replace(/Ç/g, 'c')
        .replace(/[\s\n\r\t]+/g, ' ')
        .trim();
    }
    return normFn(document.body.innerText).includes(t);
  }, { timeout }, target);
}

/**
 * Metin içeriğine göre butona tıklama
 */
async function clickButtonWithText(page, text, waitForEnabled = true) {
  if (waitForEnabled) {
    await page.waitForFunction((btnText) => {
      function normFn(s) {
        return (s || '')
          .toLowerCase()
          .replace(/ı/g, 'i')
          .replace(/i̇/g, 'i')
          .replace(/İ/g, 'i')
          .replace(/I/g, 'i')
          .replace(/ş/g, 's')
          .replace(/Ş/g, 's')
          .replace(/ğ/g, 'g')
          .replace(/Ğ/g, 'g')
          .replace(/ü/g, 'u')
          .replace(/Ü/g, 'u')
          .replace(/ö/g, 'o')
          .replace(/Ö/g, 'o')
          .replace(/ç/g, 'c')
          .replace(/Ç/g, 'c')
          .replace(/[\s\n\r\t]+/g, ' ')
          .trim();
      }
      const target = normFn(btnText);
      const buttons = Array.from(document.querySelectorAll('button'));
      return buttons.some(b => normFn(b.innerText).includes(target) && !b.disabled);
    }, { timeout: 10000 }, text);
  }

  await page.evaluate((btnText) => {
    function normFn(s) {
      return (s || '')
        .toLowerCase()
        .replace(/ı/g, 'i')
        .replace(/i̇/g, 'i')
        .replace(/İ/g, 'i')
        .replace(/I/g, 'i')
        .replace(/ş/g, 's')
        .replace(/Ş/g, 's')
        .replace(/ğ/g, 'g')
        .replace(/Ğ/g, 'g')
        .replace(/ü/g, 'u')
        .replace(/Ü/g, 'u')
        .replace(/ö/g, 'o')
        .replace(/Ö/g, 'o')
        .replace(/ç/g, 'c')
        .replace(/Ç/g, 'c')
        .replace(/[\s\n\r\t]+/g, ' ')
        .trim();
    }
    const target = normFn(btnText);
    const buttons = Array.from(document.querySelectorAll('button'));
    const btn = buttons.find(b => normFn(b.innerText).includes(target));
    if (btn) btn.click();
  }, text);
}

/**
 * Üst bardaki kırmızı çıkış butonuna güvenli tıklama
 */
async function clickLogout(page) {
  await page.waitForFunction(() => {
    const btn = document.querySelector('button.text-red-400');
    return !!btn;
  }, { timeout: 10000 });

  await page.evaluate(() => {
    const btn = document.querySelector('button.text-red-400');
    if (btn) btn.click();
  });

  // Giriş ekranının yüklendiğini bekle (marka + giriş input'u)
  await page.waitForFunction(() => {
    const text = document.body.innerText;
    const hasBrand = text.includes('ANALİZ') || text.includes('Analiz');
    const hasAuthInput = document.querySelector('input[type="tel"]') !== null || document.querySelector('input[type="email"]') !== null;
    return hasBrand && hasAuthInput;
  }, { timeout: 10000 });

  await new Promise(r => setTimeout(r, 500));
}

/**
 * Backend logundan veya veritabanından son üretilen OTP kodunu ayıklama
 */
function getLatestOtpFromLog(phone = '0000000000') {
  // 1. Log dosyasından okumayı dene
  if (fs.existsSync(BACKEND_LOG_PATH)) {
    const content = fs.readFileSync(BACKEND_LOG_PATH, 'utf8');
    const lines = content.split('\n');
    for (let i = lines.length - 1; i >= 0; i--) {
      const line = lines[i];
      if (line.includes(phone) && line.includes('Doğrulama Kodu:')) {
        const match = line.match(/Doğrulama Kodu:\s*([0-9]{6})/);
        if (match) return match[1];
      }
    }
  }

  // 2. Veritabanından (PostgreSQL) son OTP kodunu sorgula (En güvenilir fallback)
  try {
    const dbOtp = execSync('docker exec analyzepanel-db psql -U postgres -d analyzepanel_db -t -A -c "SELECT code FROM otp_codes WHERE is_used = false ORDER BY created_at DESC LIMIT 1;"').toString().trim();
    if (dbOtp && dbOtp.length === 6) {
      return dbOtp;
    }
  } catch (dbErr) {
    console.warn("⚠️ DB OTP sorgulama uyarısı:", dbErr.message);
  }

  throw new Error(`OTP kodu ne log dosyasından (${BACKEND_LOG_PATH}) ne de veritabanından okunamadı!`);
}

// -------------------------------------------------------------
// ANA TEST ÇALIŞTIRICI
// -------------------------------------------------------------
async function run() {
  console.log("==================================================================");
  console.log("🌐 ANALYZEPANEL GERÇEK TARAYICI (CHROME DOM & UI) E2E TESTİ");
  console.log("==================================================================");
  console.log(`-> Frontend URL: ${FRONTEND_URL}`);
  console.log(`-> Backend URL : ${BACKEND_URL}`);
  console.log(`-> Chrome Path : ${CHROME_PATH}`);

  // K3: Hedef veritabanı denetimi
  try {
    const currentDb = execSync('docker exec analyzepanel-db psql -U postgres -d analyzepanel_db -t -A -c "SELECT current_database();"').toString().trim();
    if (currentDb !== 'analyzepanel_db') {
      console.error(`❌ GÜVENLİK HATASI: Hedef veritabanı '${currentDb}' beklenen 'analyzepanel_db' değil!`);
      process.exit(1);
    }
  } catch (err) {
    console.error("❌ Veritabanı bağlantı hatası:", err.message);
    process.exit(1);
  }

  console.log("\n⚠️ ==================================================================");
  console.log("⚠️ DİKKAT: BU BETİK YIKICI (DESTRUCTIVE) BİR TEST BETİĞİDİR!");
  console.log("⚠️ Sıfırlanan Tablolar: 'otp_codes', 'refresh_tokens', 'app_users' (role != 'MANAGER')");
  console.log("⚠️ Hedef Veritabanı   : analyzepanel_db");
  console.log("⚠️ ==================================================================\n");

  // 0. Temizlik ve Yönetici Şifresini Parametreli Güvenli Şekilde Güncelleme (T-012, B-30, ENG-12 §1.1)
  try {
    const resetSql = `
      CREATE EXTENSION IF NOT EXISTS pgcrypto;
      DELETE FROM otp_codes;
      DELETE FROM refresh_tokens;
      DELETE FROM app_users WHERE role != 'MANAGER';
      UPDATE app_users SET must_change_password = false, password = crypt(:'pass', gen_salt('bf', 12)) WHERE role = 'MANAGER';
    `;

    const resetResult = spawnSync('docker', [
      'exec',
      '-i',
      '-e',
      `ADMIN_PASS=${ADMIN_PASSWORD}`,
      'analyzepanel-db',
      'sh',
      '-c',
      'psql -U postgres -d analyzepanel_db -v pass="$ADMIN_PASS" -v ON_ERROR_STOP=1'
    ], {
      input: resetSql,
      encoding: 'utf8'
    });

    if (resetResult.status !== 0) {
      throw new Error(resetResult.stderr || resetResult.stdout || `Sıfırlama işlemi ${resetResult.status} koduyla başarısız oldu.`);
    }
    console.log("-> Veritabanı başarıyla sıfırlandı ve yönetici şifresi güncellendi.");
  } catch (err) {
    console.error("❌ Veritabanı sıfırlama hatası:", err.message);
    process.exit(1);
  }

  console.log("-> IP hız sınırı penceresi için 20 sn bekleniyor...");
  await new Promise(r => setTimeout(r, 20000));

  const browser = await puppeteer.launch({
    executablePath: CHROME_PATH,
    headless: true,
    args: [
      '--no-sandbox',
      '--disable-setuid-sandbox',
      '--disable-dev-shm-usage',
      '--disable-gpu',
      '--window-size=1280,900'
    ]
  });

  const page = await browser.newPage();
  await page.setViewport({ width: 1280, height: 900 });

  page.on('console', msg => console.log('PAGE LOG:', msg.text()));
  page.on('response', res => {
    if (res.url().includes('/api/v1/')) {
      console.log(`API RESP: ${res.status()} ${res.url()}`);
    }
  });

  try {
    // -------------------------------------------------------------
    // ADIM 1: GİRİŞ EKRANI VE ÇİFT KANALLI SEKME SEÇİCİ (K1, B-26)
    // -------------------------------------------------------------
    console.log("\n1. Adım: Giriş ekranı açılıyor ve sekme yapısı inceleniyor...");
    await page.goto(FRONTEND_URL, { waitUntil: 'networkidle0', timeout: 15000 });
    await waitForTextInBody(page, 'ANALİZ SİSTEMİ', 10000);
    console.log("-> Giriş sayfası başarıyla yüklendi.");

    // Varsayılan sekmenin telefon (SMS OTP) olduğunu doğrula
    const isPhoneDefault = await page.evaluate(() => {
      const telInput = document.querySelector('input[type="tel"]');
      const emailInput = document.querySelector('input[type="email"]');
      return telInput !== null && emailInput === null;
    });
    console.log(`-> Varsayılan sekme telefon mu?: ${isPhoneDefault}`);
    if (!isPhoneDefault) throw new Error("Varsayılan sekme PHONE (SMS OTP) değil!");

    // E-Posta sekmesine geç
    console.log("-> 'E-Posta' sekmesine tıklanıyor...");
    await clickButtonWithText(page, 'E-Posta', true);
    await page.waitForSelector('input[type="email"]', { timeout: 5000 });

    const isEmailActive = await page.evaluate(() => {
      const emailInput = document.querySelector('input[type="email"]');
      const passInput = document.querySelector('input[type="password"]');
      return { email: emailInput !== null, password: passInput !== null };
    });
    console.log(`-> E-posta sekmesi aktif mi?: email=${isEmailActive.email}, password=${isEmailActive.password}`);
    if (!isEmailActive.email || !isEmailActive.password) throw new Error("E-posta sekmesi form elemanları eksik!");

    // SMS OTP sekmesine geri dön
    console.log("-> 'SMS OTP' sekmesine geri tıklanıyor...");
    await clickButtonWithText(page, 'SMS OTP', true);
    await page.waitForSelector('input[type="tel"]', { timeout: 5000 });
    console.log("✅ Adım 1 Başarılı: İki sekme sorunsuz çalışıyor, varsayılan sekme SMS OTP.");

    // -------------------------------------------------------------
    // ADIM 2: YÖNETİCİ GİRİŞİ VE TELEFONSUZ ÖĞRENCİ EKLEME (K2)
    // -------------------------------------------------------------
    console.log("\n2. Adım: Yönetici e-posta ile giriş yapıyor ve telefonsuz öğrenci ekliyor...");
    await clickButtonWithText(page, 'E-Posta', true);
    await page.waitForSelector('input[type="email"]', { timeout: 5000 });

    await fillInput(page, 'input[type="email"]', ADMIN_EMAIL);
    await fillInput(page, 'input[type="password"]', ADMIN_PASSWORD);
    await clickButtonWithText(page, 'Giriş Yap', true);

    // Yönetici paneli açılmasını bekle
    await waitForTextInBody(page, 'Yönetim Paneli', 10000);
    console.log("-> Yönetici paneli yüklendi.");

    // "Kullanıcı Ekle" sekmesine geç
    await clickButtonWithText(page, 'Kullanıcı Ekle', true);
    await waitForTextInBody(page, 'Yeni Kullanıcı Ekle', 10000);

    // Formu doldur (Telefonsuz Öğrenci - T-016: 12. sınıf radio butonu seçilir)
    await fillInput(page, 'input[placeholder*="Ayşe Öztürk"]', 'Canberk Yıldız');
    await page.click('input[value="12"]');

    // "Hesabı Oluştur" butonuna tıkla
    await clickButtonWithText(page, 'Hesabı Oluştur', true);

    // Tek seferlik şifre kartının DOM'a gelmesini bekle (loadData() sonrası)
    await waitForTextInBody(page, 'Giriş E-Postası', 10000);
    console.log("-> Tek seferlik kimlik kartı ekranda göründü!");

    // E-posta ve şifreyi DOM'dan oku
    const cardInfo = await page.evaluate(() => {
      const text = document.body.innerText;
      const emailMatch = text.match(/([a-zA-Z0-9._-]+@[a-zA-Z0-9._-]+\.[a-zA-Z]+)/);
      const passMatch = text.match(/İlk Şifre[^\n]*\n+([^\n\r]+)/i);
      const monoElements = Array.from(document.querySelectorAll('.font-mono'));
      return {
        email: monoElements[0]?.textContent?.trim() || (emailMatch ? emailMatch[1] : null),
        password: monoElements[1]?.textContent?.trim() || (passMatch ? passMatch[1]?.trim() : null),
        monoCount: monoElements.length
      };
    });

    console.log(`-> DOM'dan Okunan Bilgiler: E-posta=${cardInfo.email}, Şifre=${cardInfo.password}`);
    if (!cardInfo.email || !cardInfo.password) throw new Error("DOM'dan tek seferlik e-posta/şifre okunamadı!");
    console.log("✅ Adım 2 Başarılı: Telefonsuz öğrenci arayüzden oluşturuldu ve şifre kartı görüntülendi.");

    // -------------------------------------------------------------
    // ADIM 3: YÖNETİCİ ÇIKIŞI VE ÖĞRENCİ İLK GİRİŞİ
    // -------------------------------------------------------------
    console.log("\n3. Adım: Yönetici oturumu kapatıyor ve öğrenci geçici şifresiyle giriş yapıyor...");
    await clickLogout(page);
    console.log("-> Oturum kapatıldı, giriş ekranına dönüldü.");

    // E-Posta sekmesine geç
    await clickButtonWithText(page, 'E-Posta', true);
    await page.waitForSelector('input[type="email"]', { timeout: 5000 });

    await fillInput(page, 'input[type="email"]', cardInfo.email);
    await fillInput(page, 'input[type="password"]', cardInfo.password);

    await clickButtonWithText(page, 'Giriş Yap', true);

    // -------------------------------------------------------------
    // ADIM 4: PANEL AÇILMAZ -> ZORUNLU ŞİFRE DEĞİŞTİRME EKRANI GELİR (K3)
    // -------------------------------------------------------------
    console.log("\n4. Adım: Zorunlu şifre değiştirme ekranı denetleniyor...");
    await waitForTextInBody(page, 'Şifrenizi Belirleyin', 10000);
    
    // Panel veya navbar öğelerinin DOM'da OLMADIĞINI doğrula
    const isDashboardInDom = await page.evaluate(() => {
      const text = document.body.innerText;
      return text.includes('Kümülatif Analiz') || text.includes('Öğrenci Paneli');
    });
    if (isDashboardInDom) throw new Error("HATA: Dashboard şifre değiştirilmeden DOM'a yüklendi!");
    console.log("-> T-011 K3 İspatı: Dashboard yüklenmedi, doğrudan <MustChangePasswordScreen /> açıldı.");

    // F5 / Sayfa Yenileme Direnci Testi
    console.log("-> F5 (Sayfa yenileme) testi yapılıyor...");
    await page.reload({ waitUntil: 'networkidle0' });
    await waitForTextInBody(page, 'Şifrenizi Belirleyin', 10000);
    console.log("✅ Adım 4 Başarılı: F5 yenilemesinde de kullanıcı şifre değiştirme ekranında kilitli kaldı.");

    // -------------------------------------------------------------
    // ADIM 5: 11 KARAKTERLİ ŞİFRE VE VALİDASYON TESTİ (K5)
    // -------------------------------------------------------------
    console.log("\n5. Adım: 11 karakterli yetersiz şifre deneniyor...");
    await fillInput(page, 'input[placeholder*="12 karakterli"]', 'ShortPass12');
    await fillInput(page, 'input[placeholder*="tekrar yazınız"]', 'ShortPass12');

    const isButtonDisabled = await page.evaluate(() => {
      const buttons = Array.from(document.querySelectorAll('button'));
      const btn = buttons.find(b => b.innerText.includes('Şifremi Güncelle') || b.innerText.includes('SIFREMI GUNCELLE'));
      return btn ? btn.disabled : false;
    });
    console.log(`-> 11 karakterde buton pasif mi?: ${isButtonDisabled}`);
    if (!isButtonDisabled) {
      await clickButtonWithText(page, 'Şifremi Güncelle', false);
      await waitForTextInBody(page, 'en az 12 karakter', 5000);
    }
    console.log("✅ Adım 5 Başarılı: 12 karakterden kısa şifre istemci/sunucu tarafından engellendi.");

    // -------------------------------------------------------------
    // ADIM 6: 12+ KARAKTERLİ YENİ ŞİFRE İLE GÜNCELLEME VE PANELİN AÇILMASI
    // -------------------------------------------------------------
    console.log("\n6. Adım: 12+ karakterli güvenli yeni şifre belirleniyor...");
    const NEW_STUDENT_PASS = 'BrandNewSecureStudentPass2026';
    
    await fillInput(page, 'input[placeholder*="12 karakterli"]', NEW_STUDENT_PASS);
    await fillInput(page, 'input[placeholder*="tekrar yazınız"]', NEW_STUDENT_PASS);

    await clickButtonWithText(page, 'Şifremi Güncelle', true);

    // Öğrenci panelinin açılmasını bekle
    await waitForTextInBody(page, 'Canberk Yıldız', 10000);
    console.log("✅ Adım 6 Başarılı: Şifre güncellendi ve öğrenci paneli başarıyla açıldı.");

    // -------------------------------------------------------------
    // ADIM 7: YENİ ŞİFRE İLE TEKRAR GİRİŞ VE 'BENİ HATIRLA' ÇEREZ SÜRESİ
    // -------------------------------------------------------------
    console.log("\n7. Adım: Çıkış yapılıp yeni şifre ve 'Beni Hatırla' ile giriş test ediliyor...");
    await clickLogout(page);

    // E-posta sekmesine geç
    await clickButtonWithText(page, 'E-Posta', true);
    await page.waitForSelector('input[type="email"]', { timeout: 5000 });

    await fillInput(page, 'input[type="email"]', cardInfo.email);
    await fillInput(page, 'input[type="password"]', NEW_STUDENT_PASS);
    
    // "Beni Hatırla" kutusunu işaretle
    const rememberMeCheckbox = await page.$('input#emailRememberMe');
    if (rememberMeCheckbox) await rememberMeCheckbox.click();

    await clickButtonWithText(page, 'Giriş Yap', true);

    // Doğrudan Dashboard açılmalı (Şifre değiştirme ekranı GELMEMELİ)
    await waitForTextInBody(page, 'Canberk Yıldız', 10000);
    const hasPasswordPrompt = await page.evaluate(() => document.body.innerText.includes('Şifrenizi Belirleyin'));
    if (hasPasswordPrompt) throw new Error("HATA: İkinci girişte şifre değiştirme ekranı tekrar açıldı!");
    console.log("-> Yeni şifreyle doğrudan Dashboard açıldı.");

    // Çerezleri incele (Beni Hatırla: 30 gün = ~2592000 saniye)
    const cookies = await page.cookies();
    const refreshCookie = cookies.find(c => c.name === 'refresh_token');
    console.log(`-> refresh_token çerez bilgisi: expires=${refreshCookie?.expires}`);
    if (refreshCookie) {
      const nowSec = Date.now() / 1000;
      const daysLeft = (refreshCookie.expires - nowSec) / 86400;
      console.log(`-> Kalan çerez süresi: ${daysLeft.toFixed(1)} gün`);
      if (daysLeft < 25) throw new Error("Beni hatırla çerezi 30 gün olarak ayarlanmamış!");
    }
    console.log("✅ Adım 7 Başarılı: Yeni şifreyle şifre ekranı gelmeden girildi, 30 günlük çerez doğrulandı.");

    // -------------------------------------------------------------
    // ADIM 8: TELEFON + SMS OTP YOLUNUN ÇALIŞTIĞININ TEYİDİ
    // -------------------------------------------------------------
    console.log("\n8. Adım: Telefon sekmesinden SMS OTP girişi test ediliyor...");
    await clickLogout(page);

    // SMS OTP sekmesinde olunduğunu doğrula
    await fillInput(page, 'input[type="tel"]', '0000000000'); // Yönetici telefonu
    
    await clickButtonWithText(page, 'Doğrulama Kodu Gönder', true);

    // 6 haneli kod ekranını bekle
    await waitForTextInBody(page, 'Kodu Doğrula', 10000);
    console.log("-> OTP kod ekranı açıldı.");

    await new Promise(r => setTimeout(r, 600));
    const adminOtp = getLatestOtpFromLog('0000000000');
    console.log(`-> Logdan okunan OTP: ${adminOtp}`);

    await fillInput(page, 'input[placeholder="000000"]', adminOtp);
    await clickButtonWithText(page, 'Kodu Doğrula', true);

    // Yönetici paneli açılmasını bekle
    await waitForTextInBody(page, 'Yönetim Paneli', 10000);
    console.log("✅ Adım 8 Başarılı: Telefon + SMS OTP kanalı eksiksiz ve bağımsız olarak çalışıyor.");

    console.log("\n==================================================================");
    console.log("🎉 T-011B/T-011C/T-011D TÜM GERÇEK TARAYICI DOĞRULAMALARI %100 BAŞARILI!");
    console.log("==================================================================\n");

  } catch (err) {
    console.error("\n❌ Tarayıcı Test Hatası:", err);
    process.exit(1);
  } finally {
    await browser.close();
  }
}

run();
