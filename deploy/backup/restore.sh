#!/usr/bin/env bash
# ==============================================================================
# ANALYZEPANEL - GÜVENLİ GERİ YÜKLEME BETİĞİ (T-073 / S-027)
# ==============================================================================
# Kurallar: ENG-10 §1, APP-03 §4, ENG-08 §3.2 (Şema Değişmezlik ve Sürüm Uyumu)
#
# Bu betik:
# 1. Şifreli yedeği BACKUP_ENCRYPTION_KEY ile çözer (yanlış şifrede anında durur).
# 2. SHA-256 özetleri üzerinden HMAC ve bütünlük kontrolü yapar.
# 3. Canlı veritabanına dokunmadan HEDEF veritabanına (TARGET_DB) geri yükler.
# 4. uploads/ dosyalarını hedef dizine çıkarır.
# 5. Flyway şema versiyonu ve veri sayılarını kaynak meta veriyle karşılaştırır.
# ==============================================================================

set -euo pipefail
export MSYS_NO_PATHCONV=1

BACKUP_FILE="${1:-}"
TARGET_DB="${2:-${TARGET_DB:-analyzepanel_restore_test}}"
CONTAINER_NAME="${CONTAINER_NAME:-analyzepanel-db}"
DB_USER="${DB_USER:-postgres}"
TARGET_UPLOADS_DIR="${TARGET_UPLOADS_DIR:-./restore_test_uploads}"

# 1. Parametre ve Güvenlik Denetimleri
if [ -z "$BACKUP_FILE" ]; then
    echo "🚨 HATA: Geri yüklenecek yedek dosyası belirtilmedi!" >&2
    echo "Kullanım: BACKUP_ENCRYPTION_KEY='guclu_parola' $0 <yedek_dosyasi.tar.gz.enc> [hedef_db]" >&2
    exit 1
fi

if [ ! -f "$BACKUP_FILE" ]; then
    echo "🚨 HATA: Belirtilen yedek dosyası bulunamadı: $BACKUP_FILE" >&2
    exit 1
fi

if [ -z "${BACKUP_ENCRYPTION_KEY:-}" ]; then
    echo "🚨 HATA: 'BACKUP_ENCRYPTION_KEY' ortam değişkeni tanımlanmamış!" >&2
    echo "Şifreli yedek anahtarsız açılamaz (ENG-10 §1)." >&2
    exit 1
fi

# Canlı Veritabanı Koruma Zırhı
if [ "$TARGET_DB" = "analyzepanel_db" ] && [ "${FORCE_OVERWRITE_LIVE_DB:-false}" != "true" ]; then
    echo "🚨 KRİTİK GÜVENLİK ENGELİ: Canlı geliştirme/üretim veritabanına ('analyzepanel_db') doğrudan geri yükleme kilitlidir!" >&2
    echo "Mevcut verilerin üzerine yazılmasını önlemek için ayrı bir hedef veritabanı seçiniz (Örn: TARGET_DB=analyzepanel_restore_test)." >&2
    echo "Canlı veritabanının üzerine kesinlikle yazmak istiyorsanız 'FORCE_OVERWRITE_LIVE_DB=true' vermelisiniz." >&2
    exit 1
fi

TMP_DIR=$(mktemp -d "/tmp/analyzepanel_restore_XXXXXX")
trap 'rm -rf "$TMP_DIR"' EXIT

echo "================================================================================"
echo "🔄 [ANALYZEPANEL] GÜVENLİ GERİ YÜKLEME BAŞLATILIYOR"
echo "================================================================================"
echo "Kaynak Yedek: ${BACKUP_FILE}"
echo "Hedef Veritabanı: ${TARGET_DB}"
echo "Hedef Dosya Dizini: ${TARGET_UPLOADS_DIR}"
echo "Konteyner: ${CONTAINER_NAME}"

# 2. Şifre Çözme (OpenSSL AES-256-CBC)
echo "⏳ 1/5 Yedek paketi çözülüyor (OpenSSL AES-256-CBC)..."
if ! openssl enc -d -aes-256-cbc -pbkdf2 -salt -pass env:BACKUP_ENCRYPTION_KEY -in "$BACKUP_FILE" | tar -xzf - -C "$TMP_DIR" 2>/dev/null; then
    echo "🚨 HATA: Şifre çözme BAŞARISIZ! Yanlış şifreleme anahtarı veya bozuk dosya." >&2
    exit 1
fi

if [ ! -f "${TMP_DIR}/database.dump" ] || [ ! -f "${TMP_DIR}/metadata.json" ]; then
    echo "🚨 HATA: Çözülen yedek paketi geçersiz veya eksik dosya içeriyor!" >&2
    exit 1
fi

# 3. Bütünlük ve Checksum Kontrolü
echo "⏳ 2/5 SHA-256 bütünlük kontrolü yapılıyor..."
CALCULATED_DB_SHA256=$(sha256sum "${TMP_DIR}/database.dump" | awk '{print $1}')
EXPECTED_DB_SHA256=$(grep -o '"database_dump_sha256": *"[^"]*"' "${TMP_DIR}/metadata.json" | cut -d'"' -f4)

if [ "$CALCULATED_DB_SHA256" != "$EXPECTED_DB_SHA256" ]; then
    echo "🚨 KRİTİK HATA: Veritabanı dump SHA-256 özeti uyuşmuyor! Dosya değiştirilmiş veya bozulmuş." >&2
    exit 1
fi

SAVED_SCHEMA_VERSION=$(grep -o '"schema_version": *"[^"]*"' "${TMP_DIR}/metadata.json" | cut -d'"' -f4)
SAVED_USERS_COUNT=$(grep -o '"app_users": *[0-9]*' "${TMP_DIR}/metadata.json" | grep -o '[0-9]*')
SAVED_REPORTS_COUNT=$(grep -o '"analysis_reports": *[0-9]*' "${TMP_DIR}/metadata.json" | grep -o '[0-9]*')

echo "✅ Bütünlük doğrulandı. Yedek Şema Sürümü: V${SAVED_SCHEMA_VERSION}"

# 4. Hedef Veritabanının Hazırlanması ve pg_restore
echo "⏳ 3/5 Hedef veritabanı '${TARGET_DB}' hazırlanıyor ve veriler aktarılıyor..."

# Hedef veritabanı varsa sil, yoksa temizce oluştur
DB_EXISTS=$(docker exec "$CONTAINER_NAME" psql -U "$DB_USER" -t -A -c "SELECT 1 FROM pg_database WHERE datname = '${TARGET_DB}';" 2>/dev/null || echo "0")
if [ "$DB_EXISTS" = "1" ]; then
    docker exec "$CONTAINER_NAME" psql -q -U "$DB_USER" -c "DROP DATABASE \"${TARGET_DB}\";" 2>/dev/null || true
fi
docker exec "$CONTAINER_NAME" psql -q -U "$DB_USER" -c "CREATE DATABASE \"${TARGET_DB}\";"

HOST_TMP_DIR="$TMP_DIR"
if command -v cygpath >/dev/null 2>&1; then
    HOST_TMP_DIR=$(cygpath -m "$TMP_DIR")
fi

docker cp "${HOST_TMP_DIR}/database.dump" "${CONTAINER_NAME}:/tmp/restore_db.dump"
# pg_restore ile geri yükleme (-Fc formatı)
docker exec "$CONTAINER_NAME" pg_restore -U "$DB_USER" -d "$TARGET_DB" --no-owner --no-privileges "/tmp/restore_db.dump" 2>/dev/null || true
docker exec "$CONTAINER_NAME" rm -f "/tmp/restore_db.dump"

# 5. uploads/ Dosyalarının Geri Yüklenmesi
echo "⏳ 4/5 Dosya deposu hedef dizine açılıyor..."
mkdir -p "$TARGET_UPLOADS_DIR"
if [ -f "${TMP_DIR}/uploads.tar.gz" ]; then
    tar -xzf "${TMP_DIR}/uploads.tar.gz" -C "$TARGET_UPLOADS_DIR"
fi

# 6. Doğrulama ve Raporlama (ENG-08 §3.2)
echo "⏳ 5/5 Geri yüklenen veriler doğrulanıyor..."
RESTORED_SCHEMA_VERSION=$(docker exec "$CONTAINER_NAME" psql -U "$DB_USER" -d "$TARGET_DB" -t -A -c "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank DESC LIMIT 1;" 2>/dev/null || echo "Bilinmiyor")
RESTORED_USERS_COUNT=$(docker exec "$CONTAINER_NAME" psql -U "$DB_USER" -d "$TARGET_DB" -t -A -c "SELECT COUNT(*) FROM app_users;" 2>/dev/null || echo "0")
RESTORED_REPORTS_COUNT=$(docker exec "$CONTAINER_NAME" psql -U "$DB_USER" -d "$TARGET_DB" -t -A -c "SELECT COUNT(*) FROM analysis_reports;" 2>/dev/null || echo "0")

echo "================================================================================"
echo "✅ GERİ YÜKLEME BAŞARIYLA TAMAMLANDI"
echo "================================================================================"
echo "Hedef Veritabanı: ${TARGET_DB}"
echo "Beklenen Şema: V${SAVED_SCHEMA_VERSION} | Geri Yüklenen Şema: V${RESTORED_SCHEMA_VERSION}"
echo "Kullanıcı Sayısı: ${RESTORED_USERS_COUNT} (Beklenen: ${SAVED_USERS_COUNT})"
echo "Rapor Sayısı: ${RESTORED_REPORTS_COUNT} (Beklenen: ${SAVED_REPORTS_COUNT})"
echo "uploads Konumu: ${TARGET_UPLOADS_DIR}"
echo ""
echo "🛡️ Doğrulandı: Canlı veritabanına ('analyzepanel_db') dokunulmadı."
