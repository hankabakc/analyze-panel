#!/usr/bin/env bash
# ==============================================================================
# ANALYZEPANEL - GÜVENLİ ŞİFRELİ YEDEKLEME BETİĞİ (T-073 / S-027)
# ==============================================================================
# Kurallar: ENG-10 §1 (Sır İzolasyonu), APP-03 §4 (Kişisel Veri Zırhı)
#
# Bu betik:
# 1. PostgreSQL veritabanını pg_dump (-Fc formatı) ile döker.
# 2. uploads/ dizinini (karne PDF'leri) arşivler.
# 3. metadata.json ve sha256 bütünlük imzalarını üretir.
# 4. Tüm yedeği OpenSSL AES-256-CBC ile şifreler (açık metin dump bırakmaz).
# 5. Saklama politikasını (Retention Policy) uygulayarak eski yedekleri temizler.
# ==============================================================================

set -euo pipefail
export MSYS_NO_PATHCONV=1

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_DIR="${BACKUP_DIR:-./backups}"
CONTAINER_NAME="${CONTAINER_NAME:-analyzepanel-db}"
DB_NAME="${DB_NAME:-analyzepanel_db}"
DB_USER="${DB_USER:-postgres}"
UPLOADS_DIR="${UPLOADS_DIR:-./backend/uploads/analysis}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"
RETENTION_COUNT="${RETENTION_COUNT:-14}"

# 1. Güvenlik Denetimi: Şifreleme Anahtarı Zorunluluğu (ENG-10 §1)
if [ -z "${BACKUP_ENCRYPTION_KEY:-}" ]; then
    echo "🚨 HATA: 'BACKUP_ENCRYPTION_KEY' ortam değişkeni tanımlanmamış veya boş!" >&2
    echo "Kişisel veriler (öğrenci sınav sonuçları, karne PDF'leri) şifrelenmeden yedeklenemez (APP-03 §4)." >&2
    echo "Kullanım: BACKUP_ENCRYPTION_KEY='guclu_parola' $0" >&2
    exit 1
fi

mkdir -p "$BACKUP_DIR"

TMP_DIR=$(mktemp -d "/tmp/analyzepanel_backup_${TIMESTAMP}_XXXXXX")
trap 'rm -rf "$TMP_DIR"' EXIT

echo "================================================================================"
echo "📦 [ANALYZEPANEL] ŞİFRELİ YEDEKLEME BAŞLATILIYOR: ${TIMESTAMP}"
echo "================================================================================"
echo "Veritabanı: ${DB_NAME} (Konteyner: ${CONTAINER_NAME})"
echo "Dosya Deposu: ${UPLOADS_DIR}"
echo "Hedef Dizin: ${BACKUP_DIR}"

# 2. Veritabanı Yedeği (pg_dump Custom Binary Formats -Fc)
echo "⏳ 1/4 Veritabanı yedeği alınıyor (pg_dump -Fc)..."
HOST_TMP_DIR="$TMP_DIR"
if command -v cygpath >/dev/null 2>&1; then
    HOST_TMP_DIR=$(cygpath -m "$TMP_DIR")
fi

docker exec "$CONTAINER_NAME" pg_dump -U "$DB_USER" -Fc -d "$DB_NAME" -f "/tmp/db_${TIMESTAMP}.dump"
docker cp "${CONTAINER_NAME}:/tmp/db_${TIMESTAMP}.dump" "${HOST_TMP_DIR}/database.dump"
docker exec "$CONTAINER_NAME" rm -f "/tmp/db_${TIMESTAMP}.dump"

# 3. Dosya Deposu Arşivi (uploads/analysis)
echo "⏳ 2/4 Dosya deposu arşivleniyor..."
if [ -d "$UPLOADS_DIR" ] && [ "$(ls -A "$UPLOADS_DIR" 2>/dev/null)" ]; then
    FILE_COUNT=$(find "$UPLOADS_DIR" -type f 2>/dev/null | wc -l)
    tar -czf "${TMP_DIR}/uploads.tar.gz" -C "$UPLOADS_DIR" .
    echo "  ✓ ${FILE_COUNT} adet dosya arşivlendi."
else
    echo "  ⚠️ UYARI: uploads dizini bulunamadı veya içi boş (${UPLOADS_DIR})! Boş arşiv oluşturuluyor." >&2
    tar -czf "${TMP_DIR}/uploads.tar.gz" --files-from /dev/null
fi

# 4. Meta Veri ve Bütünlük İmzaları (Flyway Şema Sürümü ve Checksum)
echo "⏳ 3/4 Meta veriler ve SHA-256 özetleri oluşturuluyor..."
SCHEMA_VERSION=$(docker exec "$CONTAINER_NAME" psql -U "$DB_USER" -d "$DB_NAME" -t -A -c "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank DESC LIMIT 1;" 2>/dev/null || echo "Bilinmiyor")
USER_COUNT=$(docker exec "$CONTAINER_NAME" psql -U "$DB_USER" -d "$DB_NAME" -t -A -c "SELECT COUNT(*) FROM app_users;" 2>/dev/null || echo "0")
REPORT_COUNT=$(docker exec "$CONTAINER_NAME" psql -U "$DB_USER" -d "$DB_NAME" -t -A -c "SELECT COUNT(*) FROM analysis_reports;" 2>/dev/null || echo "0")

DB_SHA256=$(sha256sum "${TMP_DIR}/database.dump" | awk '{print $1}')
UPLOADS_SHA256=$(sha256sum "${TMP_DIR}/uploads.tar.gz" | awk '{print $1}')

cat <<EOF > "${TMP_DIR}/metadata.json"
{
  "timestamp": "${TIMESTAMP}",
  "database": "${DB_NAME}",
  "schema_version": "${SCHEMA_VERSION}",
  "record_counts": {
    "app_users": ${USER_COUNT},
    "analysis_reports": ${REPORT_COUNT}
  },
  "checksums": {
    "database_dump_sha256": "${DB_SHA256}",
    "uploads_tar_sha256": "${UPLOADS_SHA256}"
  }
}
EOF

# 5. AES-256-CBC ile Şifreleme (OpenSSL PBKDF2)
FINAL_BACKUP_FILE="${BACKUP_DIR}/backup_${TIMESTAMP}.tar.gz.enc"
echo "⏳ 4/4 Paket AES-256-CBC ile şifreleniyor..."

tar -czf - -C "$TMP_DIR" database.dump uploads.tar.gz metadata.json | \
    openssl enc -aes-256-cbc -pbkdf2 -salt -pass env:BACKUP_ENCRYPTION_KEY -out "$FINAL_BACKUP_FILE"

chmod 600 "$FINAL_BACKUP_FILE"
BACKUP_SIZE=$(du -h "$FINAL_BACKUP_FILE" | cut -f1)
BACKUP_SHA256=$(sha256sum "$FINAL_BACKUP_FILE" | awk '{print $1}')

echo "================================================================================"
echo "✅ ŞİFRELİ YEDEK BAŞARIYLA OLUŞTURULDU"
echo "================================================================================"
echo "Dosya: ${FINAL_BACKUP_FILE}"
echo "Boyut: ${BACKUP_SIZE}"
echo "Şema Sürümü: V${SCHEMA_VERSION}"
echo "Kullanıcı Sayısı: ${USER_COUNT} | Rapor Sayısı: ${REPORT_COUNT}"
echo "SHA-256: ${BACKUP_SHA256}"

# 6. Saklama Politikası (Retention Policy): Eski Yedekleri Temizle
echo ""
echo "🧹 Saklama politikası uygulanıyor (Kural: Son ${RETENTION_DAYS} gün, en az ${RETENTION_COUNT} adet yedek tutulur)..."
ALL_BACKUPS=($(ls -1t "${BACKUP_DIR}"/backup_*.tar.gz.enc 2>/dev/null || true))
TOTAL_BACKUPS=${#ALL_BACKUPS[@]}

if [ "$TOTAL_BACKUPS" -gt "$RETENTION_COUNT" ]; then
    DELETION_CANDIDATES=$(find "$BACKUP_DIR" -name "backup_*.tar.gz.enc" -type f -mtime "+${RETENTION_DAYS}" 2>/dev/null || true)
    KEPT_COUNT="$TOTAL_BACKUPS"
    for file in $DELETION_CANDIDATES; do
        if [ "$KEPT_COUNT" -gt "$RETENTION_COUNT" ]; then
            echo "Eski yedek siliniyor: $file"
            rm -f "$file"
            KEPT_COUNT=$((KEPT_COUNT - 1))
        fi
    done
fi

echo "✅ Saklama politikası tamamlandı. Mevcut yedek adedi: $(ls -1 "${BACKUP_DIR}"/backup_*.tar.gz.enc 2>/dev/null | wc -l)"
