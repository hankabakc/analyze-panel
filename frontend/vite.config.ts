/// <reference types="vitest/config" />
import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react-swc'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'

// IST-03 §3 / IST-07 §5: Paket boyutu üst sınırı — sınırı aşan JS parçası derlemeyi durdurur (T-066).
// Giriş parçası (react-dom, react-router, axios, Sentry, karşılama sayfaları) ~556 kB ve tamamı ilk ekranda
// gerekli; sınır bunun ~%8 üstünde. Panel kodu giriş parçasına sızarsa (ör. grafik kitaplığı) derleme durur.
// Doğrulama için sınır PAKET_SINIRI_KB ortam değişkeniyle geçici olarak düşürülebilir.
const PAKET_SINIRI_KB = Number(process.env.PAKET_SINIRI_KB ?? 600)

function paketButcesi(): Plugin {
  return {
    name: 'paket-butcesi',
    apply: 'build',
    generateBundle(_options, bundle) {
      for (const file of Object.values(bundle)) {
        const kb = file.type === 'chunk' ? Buffer.byteLength(file.code) / 1000 : 0
        if (kb > PAKET_SINIRI_KB) {
          this.error(`${file.fileName} ${kb.toFixed(1)} kB — paket sınırı ${PAKET_SINIRI_KB} kB (IST-03 §3)`)
        }
      }
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    paketButcesi(),
  ],
  server: {
    port: 5174,
    strictPort: true,
    // Backend'e aynı origin üzerinden gidilir. Doğrudan localhost:8081'e istek
    // atıldığında tarayıcı backend'in çerezlerini üçüncü taraf sayıp JS'den
    // gizliyor; bu yüzden axios XSRF-TOKEN çerezini okuyamıyor ve CSRF korumalı
    // bütün POST istekleri 403 dönüyordu. Proxy ile çerezler birinci taraf olur.
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: false,
      },
    },
  },
  build: {
    // Vite'ın boyut uyarısı da aynı sınıra bağlı; sınırın tek kaynağı yukarıdaki sabit.
    chunkSizeWarningLimit: PAKET_SINIRI_KB,
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  // T-063B / S-026: İstemci bileşen testleri (Vitest + Testing Library).
  // Testler gerçek ağa çıkmaz; API katmanı her testte sahteyle değiştirilir (IST-08 §1.1).
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
})
