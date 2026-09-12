# ==========================================
# ANALYZEPANEL Frontend Dockerfile (T-071)
# Çok Aşamalı (Multi-Stage) Üretim İmajı
# ==========================================

# ------------------------------------------
# Aşama 1: Derleme (Builder)
# ------------------------------------------
FROM node:22-alpine AS builder
WORKDIR /app

# Bağımlılık dosyalarını kopyala ve kur
COPY package.json package-lock.json ./
RUN npm ci

# Kaynak kodları kopyala ve üretim paketini derle
COPY . .
RUN npm run build

# ------------------------------------------
# Aşama 2: Çalışma Zamanı (Caddy Sunucusu)
# ------------------------------------------
FROM caddy:2-alpine AS runner

# Derlenen statik dosyaları Caddy kök dizinine kopyala
COPY --from=builder /app/dist /usr/share/caddy

# Özel Caddyfile yapılandırmasını kopyala
COPY Caddyfile /etc/caddy/Caddyfile

EXPOSE 80
