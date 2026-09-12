import { describe, expect, it } from "vitest";
import fs from "fs";
import path from "path";

describe("Yayın Öncesi Site Meta ve Robots Yapılandırması (T-074)", () => {
  const publicDir = path.resolve(__dirname, "../../../public");
  const indexHtmlPath = path.resolve(__dirname, "../../../index.html");

  it("robots.txt dosyası panel adreslerini taramaya kapatır, kurumsal sayfaları açık tutar", () => {
    const robotsPath = path.join(publicDir, "robots.txt");
    expect(fs.existsSync(robotsPath)).toBe(true);

    const content = fs.readFileSync(robotsPath, "utf-8");
    // Kapatılan panel adresleri
    expect(content).toContain("Disallow: /raporlar");
    expect(content).toContain("Disallow: /liderlik");
    expect(content).toContain("Disallow: /analysis/");
    expect(content).toContain("Disallow: /sifre-degistir");
    expect(content).toContain("Disallow: /api/");

    // Açık olan kurumsal sayfalar
    expect(content).toContain("Allow: /");
    expect(content).toContain("Allow: /karsilama");
    expect(content).toContain("Allow: /ozellikler");
    expect(content).toContain("Allow: /hakkinda");
    expect(content).toContain("Allow: /sss");
    expect(content).toContain("Allow: /iletisim");
    expect(content).toContain("Allow: /gizlilik");
    expect(content).toContain("Allow: /giris");
  });

  it("favicon.svg dosyası mevcuttur ve geçerli bir SVG ikonudur", () => {
    const faviconPath = path.join(publicDir, "favicon.svg");
    expect(fs.existsSync(faviconPath)).toBe(true);

    const svgContent = fs.readFileSync(faviconPath, "utf-8");
    expect(svgContent).toContain("<svg");
    expect(svgContent).toContain("</svg>");
  });

  it("index.html dosyasında favicon, description, Open Graph ve Twitter etiketleri eksiksizdir", () => {
    expect(fs.existsSync(indexHtmlPath)).toBe(true);
    const html = fs.readFileSync(indexHtmlPath, "utf-8");

    // Favicon
    expect(html).toContain('rel="icon" type="image/svg+xml" href="/favicon.svg"');

    // Başlık ve Description
    expect(html).toContain("<title>AnalyzePanel - Algoritma Destekli Eğitim Analiz ve Öğrenci Takip Platformu</title>");
    expect(html).toContain('name="description"');
    expect(html).not.toContain("AI Destekli");

    // Open Graph
    expect(html).toContain('property="og:type" content="website"');
    expect(html).toContain('property="og:title"');
    expect(html).toContain('property="og:description"');
    expect(html).toContain('property="og:image" content="/favicon.svg"');
    expect(html).toContain('property="og:locale" content="tr_TR"');

    // Twitter
    expect(html).toContain('name="twitter:card" content="summary"');
    expect(html).toContain('name="twitter:title"');
    expect(html).toContain('name="twitter:description"');
    expect(html).toContain('name="twitter:image" content="/favicon.svg"');
  });
});
