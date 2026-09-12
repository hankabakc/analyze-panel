# ==============================================================================
# ANALYZEPANEL - GUVENLI SIFRELI YEDEKLEME POWERSHELL BETIGI (T-073 / S-027)
# ==============================================================================
# Kurallar: ENG-10 Section 1 (Sir Izolasyonu), APP-03 Section 4 (Kisisel Veri Zirhi)
# ==============================================================================

param(
    [string]$BackupDir = $(if ($env:BACKUP_DIR) { $env:BACKUP_DIR } else { "./backups" }),
    [string]$ContainerName = $(if ($env:CONTAINER_NAME) { $env:CONTAINER_NAME } else { "analyzepanel-db" }),
    [string]$DbName = $(if ($env:DB_NAME) { $env:DB_NAME } else { "analyzepanel_db" }),
    [string]$DbUser = $(if ($env:DB_USER) { $env:DB_USER } else { "postgres" }),
    [string]$UploadsDir = $(if ($env:UPLOADS_DIR) { $env:UPLOADS_DIR } else { "./backend/uploads/analysis" }),
    [int]$RetentionDays = $(if ($env:RETENTION_DAYS) { [int]$env:RETENTION_DAYS } else { 7 }),
    [int]$RetentionCount = $(if ($env:RETENTION_COUNT) { [int]$env:RETENTION_COUNT } else { 14 })
)

$ErrorActionPreference = "Stop"

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

# 2. Sifreleme Anahtari Zorunlulugu (ENG-10 Section 1)
if (-not $env:BACKUP_ENCRYPTION_KEY) {
    Write-Error "HATA: 'BACKUP_ENCRYPTION_KEY' ortam degiskeni tanimlanmamis!`nKisisel veriler sifrelenmeden yedeklenemez (APP-03 Section 4).`nKullanim: `$env:BACKUP_ENCRYPTION_KEY='guclu_parola'; .\deploy\backup\backup.ps1"
    exit 1
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$tmpDir = Join-Path $env:TEMP "analyzepanel_backup_$timestamp"
New-Item -ItemType Directory -Path $tmpDir -Force | Out-Null
New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null

try {
    Write-Host "================================================================================" -ForegroundColor Cyan
    Write-Host "[ANALYZEPANEL] SIFRELI YEDEKLEME BASLATILIYOR: $timestamp" -ForegroundColor Cyan
    Write-Host "================================================================================" -ForegroundColor Cyan
    Write-Host "Veritabani: $DbName (Konteyner: $ContainerName)"
    Write-Host "Dosya Deposu: $UploadsDir"
    Write-Host "Hedef Dizin: $BackupDir"

    # 3. Veritabani Yedegi (pg_dump -Fc formati)
    Write-Host "1/4 Veritabani yedegi aliniyor (pg_dump -Fc)..." -ForegroundColor Yellow
    docker exec $ContainerName pg_dump -U $DbUser -Fc -d $DbName -f "/tmp/db_$timestamp.dump"
    if ($LASTEXITCODE -ne 0) { throw "pg_dump komutu basarisiz oldu!" }

    $dumpTarget = Join-Path $tmpDir "database.dump"
    docker cp "${ContainerName}:/tmp/db_$timestamp.dump" $dumpTarget
    docker exec $ContainerName rm -f "/tmp/db_$timestamp.dump"

    # 4. uploads/ Arsivi
    Write-Host "2/4 Dosya deposu arsivleniyor..." -ForegroundColor Yellow
    $uploadsTar = Join-Path $tmpDir "uploads.tar.gz"
    $fileList = @()
    if (Test-Path $UploadsDir) {
        $fileList = @(Get-ChildItem -Path $UploadsDir -Recurse -File -ErrorAction SilentlyContinue)
    }

    if ($fileList.Count -gt 0) {
        tar -czf $uploadsTar -C $UploadsDir .
        Write-Host "  [OK] $($fileList.Count) adet dosya arsivlendi." -ForegroundColor Green
    } else {
        Write-Host "  [UYARI] '$UploadsDir' dizini bulunamadi veya ici bos! Bos arsiv olusturuluyor." -ForegroundColor Red
        $emptyDir = Join-Path $tmpDir "empty_uploads"
        New-Item -ItemType Directory -Path $emptyDir -Force | Out-Null
        tar -czf $uploadsTar -C $emptyDir .
    }

    # 5. Meta Veri ve Butunluk Imzalari
    Write-Host "3/4 Meta veriler ve SHA-256 ozetleri olusturuluyor..." -ForegroundColor Yellow
    $schemaVersion = docker exec $ContainerName psql -U $DbUser -d $DbName -t -A -c "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank DESC LIMIT 1;" 2>$null
    if (-not $schemaVersion) { $schemaVersion = "Bilinmiyor" }

    $userCount = docker exec $ContainerName psql -U $DbUser -d $DbName -t -A -c "SELECT COUNT(*) FROM app_users;" 2>$null
    $reportCount = docker exec $ContainerName psql -U $DbUser -d $DbName -t -A -c "SELECT COUNT(*) FROM analysis_reports;" 2>$null

    $dbHash = (Get-FileHash -Algorithm SHA256 $dumpTarget).Hash.ToLower()
    $uploadsHash = (Get-FileHash -Algorithm SHA256 $uploadsTar).Hash.ToLower()

    $metaObj = [ordered]@{
        timestamp = $timestamp
        database = $DbName
        schema_version = "$schemaVersion"
        record_counts = [ordered]@{
            app_users = [int]$userCount
            analysis_reports = [int]$reportCount
        }
        checksums = [ordered]@{
            database_dump_sha256 = "$dbHash"
            uploads_tar_sha256 = "$uploadsHash"
        }
    }
    $metaJsonPath = Join-Path $tmpDir "metadata.json"
    $metaObj | ConvertTo-Json -Depth 5 | Set-Content -Path $metaJsonPath -Encoding Ascii

    # 6. Paketleme ve OpenSSL AES-256-CBC ile Sifreleme
    Write-Host "4/4 Paket AES-256-CBC ile sifreleniyor..." -ForegroundColor Yellow
    $rawTar = Join-Path $tmpDir "bundle.tar.gz"
    tar -czf $rawTar -C $tmpDir database.dump uploads.tar.gz metadata.json

    $finalBackupFile = Join-Path $BackupDir "backup_$timestamp.tar.gz.enc"
    
    $proc = Start-Process -FilePath $openSslPath -ArgumentList "enc", "-aes-256-cbc", "-pbkdf2", "-salt", "-pass", "env:BACKUP_ENCRYPTION_KEY", "-in", "`"$rawTar`"", "-out", "`"$finalBackupFile`"" -NoNewWindow -PassThru -Wait
    if ($proc.ExitCode -ne 0 -or -not (Test-Path $finalBackupFile)) {
        throw "OpenSSL sifreleme islemi basarisiz oldu!"
    }

    Remove-Item $rawTar -Force

    $finalHash = (Get-FileHash -Algorithm SHA256 $finalBackupFile).Hash.ToLower()
    $fileSize = (Get-Item $finalBackupFile).Length
    $sizeKb = [math]::Round($fileSize / 1024, 2)

    Write-Host "================================================================================" -ForegroundColor Green
    Write-Host "SIFRELI YEDEK BASARIYLA OLUSTURULDU" -ForegroundColor Green
    Write-Host "================================================================================" -ForegroundColor Green
    Write-Host "Dosya: $finalBackupFile"
    Write-Host "Boyut: $sizeKb KB ($fileSize bytes)"
    Write-Host "Sema Surumu: V$schemaVersion"
    Write-Host "Kullanici Sayisi: $userCount | Rapor Sayisi: $reportCount"
    Write-Host "SHA-256: $finalHash"

    # 7. Saklama Politikasi (Retention Policy)
    Write-Host ""
    Write-Host "Saklama politikasi uygulaniyor (Son $RetentionDays gun, en az $RetentionCount adet)..." -ForegroundColor Cyan
    $allBackups = Get-ChildItem -Path $BackupDir -Filter "backup_*.tar.gz.enc" | Sort-Object CreationTime -Descending
    if ($allBackups.Count -gt $RetentionCount) {
        $thresholdDate = (Get-Date).AddDays(-$RetentionDays)
        $candidates = $allBackups | Where-Object { $_.CreationTime -lt $thresholdDate }
        $currentCount = $allBackups.Count
        foreach ($candidate in $candidates) {
            if ($currentCount -gt $RetentionCount) {
                Write-Host "Eski yedek siliniyor: $($candidate.FullName)"
                Remove-Item $candidate.FullName -Force
                $currentCount--
            }
        }
    }
    $remainingCount = (Get-ChildItem -Path $BackupDir -Filter "backup_*.tar.gz.enc").Count
    Write-Host "Saklama politikasi tamamlandi. Mevcut yedek adedi: $remainingCount" -ForegroundColor Green

} finally {
    if (Test-Path $tmpDir) {
        Remove-Item -Path $tmpDir -Recurse -Force
    }
}
