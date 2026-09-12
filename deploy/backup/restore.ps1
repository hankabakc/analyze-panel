# ==============================================================================
# ANALYZEPANEL - GUVENLI GERI YUKLEME POWERSHELL BETIGI (T-073 / S-027)
# ==============================================================================
# Kurallar: ENG-10 Section 1 (Sir Izolasyonu), APP-03 Section 4, ENG-08 Section 3.2
# ==============================================================================

param(
    [Parameter(Position=0)]
    [string]$BackupFile = $(if ($env:BACKUP_FILE) { $env:BACKUP_FILE } else { "" }),

    [string]$TargetDb = $(if ($env:TARGET_DB) { $env:TARGET_DB } else { "analyzepanel_restore_test" }),
    [string]$ContainerName = $(if ($env:CONTAINER_NAME) { $env:CONTAINER_NAME } else { "analyzepanel-db" }),
    [string]$DbUser = $(if ($env:DB_USER) { $env:DB_USER } else { "postgres" }),
    [string]$TargetUploadsDir = $(if ($env:TARGET_UPLOADS_DIR) { $env:TARGET_UPLOADS_DIR } else { "./restore_test_uploads" }),
    [switch]$ForceOverwriteLiveDb
)

$ErrorActionPreference = "Stop"

if ($env:FORCE_OVERWRITE_LIVE_DB -eq "true") {
    $ForceOverwriteLiveDb = [switch]::Present
}

# 1. OpenSSL Yolu Tespiti
$openSslPath = "openssl"
if (-not (Get-Command "openssl" -ErrorAction SilentlyContinue)) {
    if (Test-Path "C:\Program Files\Git\usr\bin\openssl.exe") {
        $openSslPath = "C:\Program Files\Git\usr\bin\openssl.exe"
    } else {
        Write-Error "HATA: 'openssl' bulunamadi! Lutfen Git veya OpenSSL'in kurulu oldugundan emin olun."
        exit 1
    }
}

# 2. Dosya ve Sifre Kontrolleri
if (-not $BackupFile) {
    Write-Error "HATA: Geri yuklenecek yedek dosyasi belirtilmedi!`nKullanim: .\deploy\backup\restore.ps1 -BackupFile <yedek_dosyasi.tar.gz.enc> [-TargetDb <hedef_db>]"
    exit 1
}

if (-not (Test-Path $BackupFile)) {
    Write-Error "HATA: Belirtilen yedek dosyasi bulunamadi: $BackupFile"
    exit 1
}

if (-not $env:BACKUP_ENCRYPTION_KEY) {
    Write-Error "HATA: 'BACKUP_ENCRYPTION_KEY' ortam degiskeni tanimlanmamis!`nSifreli yedek anahtarsiz acilamaz (ENG-10 Section 1).`nKullanim: `$env:BACKUP_ENCRYPTION_KEY='guclu_parola'; .\deploy\backup\restore.ps1 -BackupFile <dosya>"
    exit 1
}

# 3. Canli Veritabani Koruma Kilidi
if ($TargetDb -eq "analyzepanel_db" -and -not $ForceOverwriteLiveDb) {
    Write-Error "KRITIK GUVENLIK ENGELI: Canli veritabanina ('analyzepanel_db') dogrudan geri yukleme kilitlidir!`nMevcut verilerin uzerine yazilmasini onlemek icin ayri bir hedef veritabani seciniz (Orn: -TargetDb analyzepanel_restore_test).`nCanli veritabaninin uzerine kesinlikle yazmak istiyorsaniz -ForceOverwriteLiveDb parametresini vermelisiniz."
    exit 1
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$tmpDir = Join-Path $env:TEMP "analyzepanel_restore_$timestamp"
New-Item -ItemType Directory -Path $tmpDir -Force | Out-Null

try {
    Write-Host "================================================================================" -ForegroundColor Cyan
    Write-Host "[ANALYZEPANEL] GUVENLI GERI YUKLEME BASLATILIYOR" -ForegroundColor Cyan
    Write-Host "================================================================================" -ForegroundColor Cyan
    Write-Host "Kaynak Yedek: $BackupFile"
    Write-Host "Hedef Veritabani: $TargetDb"
    Write-Host "Hedef Dosya Deposu: $TargetUploadsDir"
    Write-Host "Konteyner: $ContainerName"

    # 4. Sifre Cozme (OpenSSL AES-256-CBC)
    Write-Host "1/5 Yedek paketi cozuyor (OpenSSL AES-256-CBC)..." -ForegroundColor Yellow
    $rawTar = Join-Path $tmpDir "bundle.tar.gz"
    
    $proc = Start-Process -FilePath $openSslPath -ArgumentList "enc", "-d", "-aes-256-cbc", "-pbkdf2", "-salt", "-pass", "env:BACKUP_ENCRYPTION_KEY", "-in", "`"$BackupFile`"", "-out", "`"$rawTar`"" -NoNewWindow -PassThru -Wait
    if ($proc.ExitCode -ne 0 -or -not (Test-Path $rawTar)) {
        throw "HATA: Sifre cozme BASARISIZ! Yanlis sifreleme anahtari veya bozuk dosya."
    }

    tar -xzf $rawTar -C $tmpDir
    Remove-Item $rawTar -Force

    $dbDump = Join-Path $tmpDir "database.dump"
    $metaJson = Join-Path $tmpDir "metadata.json"

    if (-not (Test-Path $dbDump) -or -not (Test-Path $metaJson)) {
        throw "HATA: Cozulen yedek paketi eksik dosya iceriyor!"
    }

    # 5. Butunluk ve Checksum Kontrolu
    Write-Host "2/5 SHA-256 butunluk kontrolu yapiliyor..." -ForegroundColor Yellow
    $meta = Get-Content $metaJson -Raw -Encoding Ascii | ConvertFrom-Json
    $calcDbHash = (Get-FileHash -Algorithm SHA256 $dbDump).Hash.ToLower()
    $expectedDbHash = $meta.checksums.database_dump_sha256.ToLower()

    if ($calcDbHash -ne $expectedDbHash) {
        throw "KRITIK HATA: Veritabani dump SHA-256 ozeti uyusmuyor! Dosya degistirilmis veya bozulmus."
    }

    $savedSchema = $meta.schema_version
    $savedUsers = $meta.record_counts.app_users
    $savedReports = $meta.record_counts.analysis_reports

    Write-Host "Butunluk dogrulandi. Yedek Sema Surumu: V$savedSchema" -ForegroundColor Green

    # 6. Hedef Veritabaninin Hazirlanmasi ve pg_restore
    Write-Host "3/5 Hedef veritabani '$TargetDb' hazirlaniyor ve veriler aktariliyor..." -ForegroundColor Yellow
    $dbExists = (docker exec $ContainerName psql -U $DbUser -t -A -c "SELECT 1 FROM pg_database WHERE datname = '$TargetDb';" 2>$null) -eq '1'
    if ($dbExists) {
        docker exec $ContainerName psql -q -U $DbUser -c "DROP DATABASE `"$TargetDb`";" 2>$null | Out-Null
    }
    docker exec $ContainerName psql -q -U $DbUser -c "CREATE DATABASE `"$TargetDb`";" 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Hedef veritabani '$TargetDb' olusturulamadi!"
    }

    docker cp $dbDump "${ContainerName}:/tmp/restore_db.dump"
    docker exec $ContainerName pg_restore -U $DbUser -d $TargetDb --no-owner --no-privileges "/tmp/restore_db.dump" 2>$null | Out-Null
    docker exec $ContainerName rm -f "/tmp/restore_db.dump"

    # 7. uploads/ Dosyalarinin Geri Yuklenmesi
    Write-Host "4/5 Dosya deposu hedef dizine aciliyor..." -ForegroundColor Yellow
    New-Item -ItemType Directory -Path $TargetUploadsDir -Force | Out-Null
    $uploadsTar = Join-Path $tmpDir "uploads.tar.gz"
    if (Test-Path $uploadsTar) {
        tar -xzf $uploadsTar -C $TargetUploadsDir
    }

    # 8. Dogrulama ve Raporlama (ENG-08 Section 3.2)
    Write-Host "5/5 Geri yuklenen veriler dogrulaniyor..." -ForegroundColor Yellow
    $restoredSchema = docker exec $ContainerName psql -U $DbUser -d $TargetDb -t -A -c "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank DESC LIMIT 1;" 2>$null
    $restoredUsers = docker exec $ContainerName psql -U $DbUser -d $TargetDb -t -A -c "SELECT COUNT(*) FROM app_users;" 2>$null
    $restoredReports = docker exec $ContainerName psql -U $DbUser -d $TargetDb -t -A -c "SELECT COUNT(*) FROM analysis_reports;" 2>$null

    Write-Host "================================================================================" -ForegroundColor Green
    Write-Host "GERI YUKLEME BASARIYLA TAMAMLANDI" -ForegroundColor Green
    Write-Host "================================================================================" -ForegroundColor Green
    Write-Host "Hedef Veritabani: $TargetDb"
    Write-Host "Beklenen Sema: V$savedSchema | Geri Yuklenen Sema: V$restoredSchema"
    Write-Host "Kullanici Sayisi: $restoredUsers (Beklenen: $savedUsers)"
    Write-Host "Rapor Sayisi: $restoredReports (Beklenen: $savedReports)"
    Write-Host "uploads Konumu: $TargetUploadsDir"
    Write-Host ""
    Write-Host "Dogrulandi: Canli veritabanina ('analyzepanel_db') dokunulmadi." -ForegroundColor Cyan

} finally {
    if (Test-Path $tmpDir) {
        Remove-Item -Path $tmpDir -Recurse -Force
    }
}
