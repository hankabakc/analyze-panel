# AnalyzePanel

Dershane ve okullar için deneme sınavı analiz uygulaması. Öğretmen öğrencinin karnesini PDF olarak yükler; uygulama karnedeki sonuçları okur, konu bazında eksikleri ve sınavdan sınava gelişimi gösterir.

## Karne okuma

Karneler taranmış resim değil, metin içeren PDF'ler. Bu yüzden OCR ya da yapay zeka servisi kullanılmıyor: Apache PDFBox ile PDF'teki metin ve her kelimenin sayfadaki konumu okunuyor. Doğru, yanlış ve boş sayıları kelimelerin x koordinatına göre doğru sütuna yerleştiriliyor. Öğrencinin adı ve numarası okunmuyor. Kod: `backend/src/main/java/com/hankabakc/analyzepanel/analysis/service/SinavzaKarneParser.java`

Testlerde gerçek karne yerine kodla üretilen sahte karneler kullanılıyor.

## Backend

Java, Spring Boot, Spring Data JPA, Spring Security, PostgreSQL, Flyway, Apache PDFBox, MapStruct. Arayüz React ile yazıldı.

| Paket | İçerik |
| --- | --- |
| `analysis` | Karne yükleme, ayrıştırma, konu bazlı analiz ve gelişim |
| `auth` | Giriş, access token + HttpOnly çerezde refresh token |
| `membership` | Yönetici, öğretmen ve öğrenci rolleri; sınıf ve öğretmen–öğrenci eşleşmesi |
| `studyplan` | Öğretmenin öğrenciye haftalık çalışma planı vermesi |
| `psychtest` | Öğrencilere kaygı ölçeği atanması ve puanlanması |
| `core` | Hata yönetimi (`@RestControllerAdvice`), istek sınırlama, kişisel verilerin şifrelenmesi |

Öğretmen yalnızca kendisine bağlı öğrencilerin verisini görebilir; bu kontrol serviste yapılıyor. Şema Flyway migrasyonlarıyla yönetiliyor.

## Çalıştırma

Gerekenler: JDK 25, Node.js, Docker.

```bash
cp .env.example .env
cp backend/src/main/resources/application.properties.example backend/src/main/resources/application.properties
docker compose up -d                 # PostgreSQL

cd backend && ./mvnw spring-boot:run # API, port 8081
cd ../frontend && npm install && npm run dev   # arayüz, http://localhost:5174
```

İlk yönetici hesabı komut satırından açılır:

```bash
java -jar app.jar --create-admin=yonetici@kurum.com --name="Ad Soyad"
```

## Testler

```bash
cd backend && ./mvnw test
cd ../frontend && npm test
```

GitHub Actions her push'ta testleri çalıştırır.

## Lisans

MIT
