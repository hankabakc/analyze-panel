# 📊 ANALYZEPANEL
### Algoritma Destekli Eğitim Analiz, Karne Değerlendirme ve Öğrenci Takip Platformu

[![Java](https://img.shields.io/badge/Java-25_(Virtual_Threads)-orange?style=for-the-badge&logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.2-6DB33F?style=for-the-badge&logo=springboot)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-336791?style=for-the-badge&logo=postgresql)](https://www.postgresql.org/)
[![React](https://img.shields.io/badge/React-19.2-61DAFB?style=for-the-badge&logo=react)](https://react.dev/)
[![Tailwind CSS](https://img.shields.io/badge/Tailwind_4.0-38B2AC?style=for-the-badge&logo=tailwind-css)](https://tailwindcss.com/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?style=for-the-badge&logo=docker)](https://www.docker.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](https://opensource.org/licenses/MIT)

---

## 🌐 İçindekiler / Table of Contents
- [🇹🇷 Türkçe](#-türkçe)
  - [Genel Bakış](#-genel-bakış)
  - [Temel Modüller ve Yetenekler](#-temel-modüller-ve-yetenekler)
  - [Yazılım Mimarisi ve Güvenlik Standartları](#-yazılım-mimarisi-ve-güvenlik-standartları)
  - [Hızlı Kurulum (Yerel Geliştirme)](#-hızlı-kurulum-yerel-geliştirme)
  - [Docker ile Canlı Dağıtım (Production)](#-docker-ile-canlı-dağıtım-production)
  - [Yönetici Kilitlenme Kurtarma (CLI)](#-yönetici-kilitlenme-kurtarma-cli)
  - [Testlerin Çalıştırılması](#-testlerin-çalıştırılması)
- [🇺🇸 English (Summary)](#-english-summary)
- [Lisans](#-lisans)

---

## 🇹🇷 Türkçe

### 📝 Genel Bakış
**ANALYZEPANEL**, dershaneler, kurs merkezleri ve okullar için geliştirilmiş; ham öğrenci deneme karnelerini doğrudan ayrıştırarak konu bazında akademik eksikleri, kümülatif gelişimi, çalışma planlarını ve psikolojik ölçekleri takip eden yüksek güvenlikli bir eğitim yönetim platformudur.

Sistem, PDF karneleri harici bir yapay zeka servisine bağımlı olmadan, doğrudan kendi yerleşik metin ayrıştırıcısı ile **çevrimdışı, sıfır analiz maliyetiyle ve milisaniyeler içerisinde** okur.

---

### ✨ Temel Modüller ve Yetenekler

| Modül | Açıklama |
| :--- | :--- |
| **`analysis`** | **Sıfır Maliyetli Karne Ayrıştırma:** Apache PDFBox ile yerel PDF katmanından sınav netlerini, doğru/yanlışları ve ders analizlerini anında okur. Kümülatif gelişim grafikleri ve sınıf içi anonim sıralama üretir. |
| **`auth`** | **Nükleer Zırh Kimlik Doğrulama:** JWT (Access Token), HttpOnly `Secure` Refresh Token, BCrypt hashleme, brute-force korumalı Rate Limiting ve global Web Application Firewall (WAF). |
| **`membership`** | **Hiyerarşik Rol Yönetimi:** `MANAGER`, `TEACHER` ve `STUDENT` rolleri. Sınıf yönetimi, öğretmen-öğrenci eşleştirmeleri ve hesap yönetimi. |
| **`studyplan`** | **Dinamik Çalışma Planları:** Öğretmenlerin öğrencilere özel haftalık ders çalışma ve hedef listeleri hazırlamasını, görüldü takibini sağlar. |
| **`psychtest`** | **Psikolojik Ölçek Takibi:** Yöneticinin öğrencilere STAI (Durumluk-Sürekli Kaygı Ölçeği) atayabilmesini, puanlama ve seviye (Düşük/Orta/Yüksek) analizlerini sağlar. |
| **`sitecontent`** | **Yerleşik CMS:** Karşılama sitesi (Ana Sayfa, Özellikler, Hakkında, SSS, İletişim, KVKK) metinlerinin yönetici panelinden dinamik yönetimi. |
| **`core`** | **Çekirdek Güvenlik & Gözlemlenebilirlik:** AES-256 PII şifreleme, Correlation ID izleme, Sentry hata takibi (kişisel veri arındırmalı) ve `audit_logs` denetim izi. |

---

### 🛡️ Yazılım Mimarisi ve Güvenlik Standartları

* **Modüler Monolit (Modular Monolith):** Bağımsız modüller, net servis sınırları ve temiz paket mimarisi.
* **Java 25 Virtual Threads (Project Loom):** Yüksek eşzamanlılık ve asgari bellek tüketimi.
* **Pure Java Politikası:** Kod tabanında **Lombok kesinlikle kullanılmaz**. Tüm Getter, Setter, Builder ve Record yapıları standart Java diliyle manuel yazılmıştır.
* **Hassas Veri Koruması (PII Encryption):** Öğrenci ve öğretmen isimleri, e-postaları ve telefon numaraları veritabanında **AES-256 (256-bit)** ile şifrelenmiş olarak saklanır.
* **IDOR ve Yetki Koruması:** Öğretmen yalnızca kendisine eşleşmiş öğrencilerin akademik detaylarını görebilir; sunucu seviyesinde sıkı sahiplik denetimi yapılır.
* **Sır İzolasyonu (Zero Secrets in Code):** Kod tabanında hiçbir API Key, şifre veya secret bulunmaz; tüm yapılandırmalar ortam değişkenlerinden (`.env`) okunur.

---

### 🔧 Hızlı Kurulum (Yerel Geliştirme)

#### Gereksinimler
- **Java 25 SDK** (Eclipse Temurin önerilir)
- **Node.js 22 LTS** & **npm**
- **Docker** & **Docker Compose**

#### 1. Depoyu Klonlayın
```bash
git clone https://github.com/hankabakc/analyzepanel.git
cd analyzepanel
```

#### 2. Çevre Değişkenlerini Hazırlayın
```bash
cp .env.example .env
cp backend/src/main/resources/application.properties.example backend/src/main/resources/application.properties
```

#### 3. Veritabanını Başlatın (PostgreSQL 17 - Port 5433)
```bash
docker compose up -d
```

#### 4. Backend Servisini Başlatın (Port 8081)
```bash
cd backend
./mvnw spring-boot:run
```

#### 5. Frontend Arayüzünü Başlatın (Port 5174)
```bash
cd ../frontend
npm install
npm run dev
```

Tarayıcınızdan `http://localhost:5174` adresine giderek uygulamaya erişebilirsiniz.

---

### 🐳 Docker ile Canlı Dağıtım (Production)

Tek komutla `PostgreSQL 17`, `Spring Boot Backend` ve `Caddy Ters Vekil (Frontend)` servislerini üretim modunda ayağa kaldırmak için:

```bash
# 1. Ortam değişkenlerini düzenleyin (Sırlarınızı belirleyin)
cp .env.example .env
nano .env

# 2. Üretim yığınını başlatın
docker compose -f docker-compose.prod.yml up -d --build

# 3. İlk kurulum sonrası yönetici şifresini alın (ENG-10 §1 / T-074)
# Güvenlik gereği ilk kurulum şifresi günlüklere basılmaz. Konteyner ayağa kalktıktan hemen sonra:
docker exec analyzepanel-backend java -jar app.jar --recover-admin=yonetici@kurum.com
# Veya sıfırdan bilinçli bir yönetici açmak için:
docker exec analyzepanel-backend java -jar app.jar --create-admin=yonetici@kurum.com --name="Kurum Müdürü"
```

* Caddy, dış ağa yalnızca HTTP (Port 80) ve HTTPS (Port 443) portlarını açar.
* PostgreSQL veritabanı portu dış dünyaya tamamen kapalıdır; yalnızca konteynerler arası iç ağda çalışır.
* Backend loopback sağlık kontrolleri Caddy üzerinden dış dünyaya `403 Forbidden` ile korunur.

---

### 🚨 Yönetici Kilitlenme Kurtarma (CLI)

Yönetici şifresini unuttuğunda veya hesap kilitlendiğinde web arayüzünden doğrudan kurtarma yapılmaz. Sunucu yöneticisi terminal üzerinden güvenli geçici şifre üretebilir:

```bash
# Mevcut bir yöneticinin şifresini sıfırlama:
docker exec analyzepanel-backend java -jar app.jar --recover-admin=yonetici@kurum.com

# Yeni bir yönetici hesabı oluşturma:
docker exec analyzepanel-backend java -jar app.jar --create-admin=yeni@kurum.com --name="Yedek Müdür"
```

> ⚠️ **Önemli Güvenlik Kuralı:** Geçici şifrenin Docker günlük dosyalarına sızmaması için komut **mutlaka `docker exec` ile** çalıştırılmalıdır. Komut, geçici şifreyi yalnızca terminale bir kez yazar; günlüğe düşmez ve bir daha gösterilmez.

---

### 📦 Şifreli Yedekleme ve Geri Yükleme (Disaster Recovery)

Öğrenci sınav sonuçları ve karne PDF'leri kişisel veri niteliğindedir (`APP-03 §4`). Sistem yedekleri OpenSSL AES-256-CBC ile şifrelenir (`ENG-10 §1`).

```bash
# Şifreli yedek alma:
export BACKUP_ENCRYPTION_KEY="kuruma-ozel-guclu-yedekleme-parolasi"
./deploy/backup/backup.sh   # Windows için: .\deploy\backup\backup.ps1

# Güvenli hedef veritabanına test geri yüklemesi (temiz makinede otomatik oluşturur):
./deploy/backup/restore.sh ./backups/backup_YYYYMMDD_HHMMSS.tar.gz.enc analyzepanel_restore_test
# Windows için: .\deploy\backup\restore.ps1 -BackupFile .\backups\backup_YYYYMMDD_HHMMSS.tar.gz.enc -TargetDb analyzepanel_restore_test
```

Saklama politikası betiklerin içindedir: son 7 gün ve en az 14 yedek tutulur (`RETENTION_DAYS`, `RETENTION_COUNT`). Yedek `BACKUP_ENCRYPTION_KEY` olmadan ne alınır ne açılır.

---

### 🧪 Testlerin Çalıştırılması

Tüm backend birim/entegrasyon testleri ve frontend testleri %100 yeşil bayrakla çalışmaktadır:

```bash
# Backend testleri (234 test, 0 failure, 0 skipped):
cd backend
./mvnw test

# Frontend testleri (56 test, 15 test dosyası) ve TypeScript tip denetimi:
cd ../frontend
npm test
npm run build
```

---

## 🇺🇸 English (Summary)

**ANALYZEPANEL** is an enterprise-grade educational analytics and report card evaluation platform designed for schools, tutoring centers, and educational academies.

### Key Highlights
- **Zero-Cost Local PDF Extraction:** Direct examination report parsing powered by Apache PDFBox. 100% offline, deterministic, and free of external AI fees.
- **Modern Modular Monolith:** Built with **Java 25 (Virtual Threads)**, **Spring Boot 4.0.2**, **PostgreSQL 17 (Flyway)**, and **React 19.2 (Tailwind 4)**.
- **Enterprise Security:** AES-256 PII database encryption, HMAC-SHA256 signatures, device-binding cookies, WAF filters, and OWASP Top 10 compliance.
- **Production-Ready Docker:** Multi-stage Docker builds orchestrating Caddy (Reverse Proxy), Spring Boot Backend, and PostgreSQL with a single command.

---

## 📄 Lisans

Bu proje [MIT Lisansı](LICENSE) altında lisanslanmıştır. Detaylar için `LICENSE` dosyasına göz atabilirsiniz.
