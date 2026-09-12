# ==========================================
# ANALYZEPANEL Backend Dockerfile (T-071)
# Çok Aşamalı (Multi-Stage) Üretim İmajı
# ==========================================

# ------------------------------------------
# Aşama 1: Derleme (Builder)
# ------------------------------------------
FROM eclipse-temurin:25-jdk-noble AS builder
WORKDIR /build

# Maven wrapper ve proje bağımlılık tanımları
COPY .mvn .mvn
COPY mvnw mvnw.cmd pom.xml ./
RUN chmod +x mvnw

# Bağımlılıkları önbelleğe alma (Docker layer caching)
RUN ./mvnw dependency:go-offline -B || true

# Kaynak kodları kopyalama ve derleme
COPY src src
RUN ./mvnw clean package -DskipTests -B

# ------------------------------------------
# Aşama 2: Çalışma Zamanı (Runner)
# ------------------------------------------
FROM eclipse-temurin:25-jre-noble AS runner
WORKDIR /app

# Sağlık kontrolü (healthcheck) için curl kurulumu
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# Güvenlik standardı: Kök olmayan (non-root) kullanıcı oluşturma
RUN groupadd -r appgroup && useradd -r -g appgroup appuser

# Kalıcı karne yükleme dizini oluşturma ve sahiplik atama (APP-01 §1.2)
RUN mkdir -p /var/lib/analyzepanel/uploads && \
    chown -R appuser:appgroup /var/lib/analyzepanel/uploads

# Derlenen JAR dosyasını kopyalama
COPY --from=builder /build/target/*.jar app.jar
RUN chown appuser:appgroup app.jar

# Süreç kök kullanıcıyla çalışmaz (ENG-10 §1)
USER appuser

# Zaman dilimi UTC ve JVM bellek yapılandırması
ENV TZ=UTC
EXPOSE 8081

# Konteyner içi sağlık kontrolü: T-070'te yalnızca loopback'e tahsis edilen ucu yoklar (IST-01 §3.2)
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -f http://127.0.0.1:8081/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
