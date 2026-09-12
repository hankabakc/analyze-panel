import puppeteer from "puppeteer-core";
import http from "http";
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const distDir = path.resolve(__dirname, "../dist");
const CHROME_PATH = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";

const mimeTypes = {
  ".html": "text/html",
  ".js": "text/javascript",
  ".css": "text/css",
  ".svg": "image/svg+xml",
  ".txt": "text/plain",
  ".json": "application/json",
};

const server = http.createServer((req, res) => {
  let reqPath = req.url.split("?")[0];
  let filePath = path.join(distDir, reqPath === "/" ? "index.html" : reqPath);

  if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
    filePath = path.join(distDir, "index.html");
  }

  const ext = path.extname(filePath);
  const contentType = mimeTypes[ext] || "application/octet-stream";

  try {
    const data = fs.readFileSync(filePath);
    res.writeHead(200, { "Content-Type": contentType });
    res.end(data);
  } catch (err) {
    res.writeHead(404);
    res.end("Not found");
  }
});

server.listen(4173, async () => {
  console.log("Sunucu http://localhost:4173 üzerinde başlatıldı.");
  let browser;
  try {
    // 1. HTTP kontrolleri
    console.log("1. /favicon.svg ve /robots.txt kontrol ediliyor...");
    const faviconRes = await fetch("http://localhost:4173/favicon.svg");
    if (faviconRes.status !== 200) throw new Error(`Favicon 200 değil: ${faviconRes.status}`);
    const faviconType = faviconRes.headers.get("content-type");
    if (!faviconType.includes("image/svg+xml")) throw new Error(`Favicon content-type geçersiz: ${faviconType}`);
    console.log("  ✅ /favicon.svg başarılı (200, image/svg+xml)");

    const robotsRes = await fetch("http://localhost:4173/robots.txt");
    if (robotsRes.status !== 200) throw new Error(`Robots.txt 200 değil: ${robotsRes.status}`);
    const robotsText = await robotsRes.text();
    if (!robotsText.includes("Disallow: /raporlar") || !robotsText.includes("Allow: /")) {
      throw new Error("Robots.txt kuralları eksik!");
    }
    console.log("  ✅ /robots.txt başarılı (200, panel kapalı, genel açık)");

    // 2. Puppeteer ile 404 sayfası ve mobil taşma kontrolü
    console.log("2. Puppeteer başlatılıyor (375x812)...");
    browser = await puppeteer.launch({
      executablePath: CHROME_PATH,
      headless: true,
      args: ["--no-sandbox", "--disable-setuid-sandbox"],
    });

    const page = await browser.newPage();
    await page.setViewport({ width: 375, height: 812, deviceScaleFactor: 2 });

    console.log("  Bilinmeyen adres (/olmayan-sayfa-404-test) ziyaret ediliyor...");
    await page.goto("http://localhost:4173/olmayan-sayfa-404-test", { waitUntil: "networkidle0" });

    // Başlık ve metin kontrolü
    const pageTitle = await page.title();
    console.log(`  Sayfa Başlığı: ${pageTitle}`);
    if (!pageTitle.includes("404")) throw new Error(`Başlıkta 404 yok: ${pageTitle}`);

    const h1Text = await page.$eval("h1", (el) => el.textContent);
    console.log(`  H1 Başlığı: ${h1Text}`);
    if (!h1Text.includes("Bulunamadı")) throw new Error(`H1 metni geçersiz: ${h1Text}`);

    // Yatay taşma kontrolü (IST-02 §1.1)
    const overflow = await page.evaluate(() => {
      const scrollWidth = document.documentElement.scrollWidth;
      const clientWidth = document.documentElement.clientWidth;
      return scrollWidth - clientWidth;
    });
    console.log(`  Yatay taşma: ${overflow}px`);
    if (overflow > 0) throw new Error(`Mobil ekranda yatay taşma var: ${overflow}px`);
    console.log("  ✅ Mobil 375x812 görünümünde 0 px yatay taşma doğrulandı.");

    // Ekran görüntüsü al
    const screenshotPath = path.resolve(__dirname, "../../scratch/t074_404_mobile.png");
    await page.screenshot({ path: screenshotPath, fullPage: true });
    console.log(`  ✅ Ekran görüntüsü kaydedildi: ${screenshotPath}`);

    // 3. index.html meta etiketleri kontrolü
    console.log("3. index.html meta etiketleri kontrol ediliyor...");
    const description = await page.$eval('meta[name="description"]', (el) => el.getAttribute("content"));
    console.log(`  Description: ${description}`);
    if (!description || description.includes("AI")) throw new Error("Description geçersiz!");

    const ogTitle = await page.$eval('meta[property="og:title"]', (el) => el.getAttribute("content"));
    console.log(`  OG Title: ${ogTitle}`);
    if (!ogTitle) throw new Error("og:title bulunamadı!");

    const twitterCard = await page.$eval('meta[name="twitter:card"]', (el) => el.getAttribute("content"));
    console.log(`  Twitter Card: ${twitterCard}`);
    if (twitterCard !== "summary") throw new Error(`twitter:card summary değil: ${twitterCard}`);

    console.log("  ✅ Tüm meta etiketleri eksiksiz ve kurallara uygun.");

    console.log("\n🎉 TÜM T-074 KABUL KRİTERLERİ BAŞARIYLA DOĞRULANDI!");
  } catch (err) {
    console.error("❌ Doğrulama hatası:", err);
    process.exitCode = 1;
  } finally {
    if (browser) await browser.close();
    server.close();
  }
});
