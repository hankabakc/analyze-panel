import { Link } from "react-router-dom";
import { Home, HelpCircle, Mail, Sparkles, ArrowRight, ShieldAlert } from "lucide-react";
import { useAuth } from "@/lib/auth-context";
import { LOGIN_BUTTON_LABEL } from "../siteContent";
import { useDocumentTitle } from "../useDocumentTitle";

const FOCUS_RING =
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-500 focus-visible:ring-offset-2";

/**
 * NotFoundPage: Özel 404 Hata Sayfası (T-074 / S-027).
 *
 * Bilinmeyen bir adrese erişildiğinde sessizce ana sayfaya yönlendirmek yerine
 * kullanıcıya anlaşılır bilgi verir ve yararlı sayfa bağlantıları sunar (IST-02 §1.1).
 */
export default function NotFoundPage() {
  useDocumentTitle("404 - Sayfa Bulunamadı");
  const { isAuthenticated } = useAuth();

  const primaryAction = isAuthenticated
    ? { to: "/", label: "Panele Dön" }
    : { to: "/", label: "Ana Sayfaya Dön" };

  return (
    <main
      role="main"
      className="mx-auto flex min-h-[70vh] max-w-4xl flex-col items-center justify-center px-4 py-16 text-center sm:px-8 sm:py-24"
    >
      {/* 404 Durum Rozeti */}
      <div className="inline-flex items-center gap-2 rounded-full border border-cyan-100 bg-cyan-50/80 px-4 py-1.5 text-xs font-black uppercase tracking-[0.2em] text-cyan-700">
        <ShieldAlert className="h-4 w-4 text-cyan-600" aria-hidden="true" />
        Hata Kodu: 404
      </div>

      {/* Ana Başlık ve Açıklama */}
      <h1 className="mt-6 text-5xl font-black tracking-tighter text-slate-900 sm:text-7xl">
        Sayfa <span className="italic text-cyan-500">Bulunamadı</span>
      </h1>
      <p className="mt-4 max-w-lg text-base leading-relaxed text-slate-600 sm:text-lg">
        Aradığınız sayfa taşınmış, silinmiş ya da hiç var olmamış olabilir. Aşağıdaki bağlantıları kullanarak istediğiniz bölüme ulaşabilirsiniz.
      </p>

      {/* Birincil Eylem Butonu (44px dokunma hedefi - IST-02 §3.1) */}
      <div className="mt-8 flex flex-wrap justify-center gap-4">
        <Link
          to={primaryAction.to}
          className={`inline-flex h-12 items-center gap-3 rounded-2xl bg-slate-900 px-6 text-[11px] font-black uppercase tracking-[0.2em] text-white transition-colors hover:bg-slate-800 ${FOCUS_RING}`}
        >
          <Home className="h-4 w-4" aria-hidden="true" />
          {primaryAction.label}
          <ArrowRight className="h-4 w-4" aria-hidden="true" />
        </Link>
        {!isAuthenticated && (
          <Link
            to="/giris"
            className={`inline-flex h-12 items-center gap-3 rounded-2xl border border-slate-200 bg-white px-6 text-[11px] font-black uppercase tracking-[0.2em] text-slate-900 transition-colors hover:bg-slate-50 ${FOCUS_RING}`}
          >
            {LOGIN_BUTTON_LABEL}
          </Link>
        )}
      </div>

      {/* Yardımcı Bilgi Bağlantıları */}
      <nav
        aria-label="Yararlı bağlantılar"
        className="mt-14 w-full max-w-xl rounded-3xl border border-slate-100 bg-white p-6 shadow-sm sm:p-8"
      >
        <p className="text-left text-xs font-black uppercase tracking-[0.2em] text-slate-400">
          İlginizi çekebilecek sayfalar:
        </p>
        <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-3">
          <Link
            to="/ozellikler"
            className={`flex items-center gap-3 rounded-xl border border-slate-100 p-3 text-left transition-colors hover:border-cyan-200 hover:bg-cyan-50/50 ${FOCUS_RING}`}
          >
            <Sparkles className="h-5 w-5 shrink-0 text-cyan-600" aria-hidden="true" />
            <div>
              <p className="text-xs font-bold text-slate-900">Özellikler</p>
              <p className="text-[10px] text-slate-500">Platform modülleri</p>
            </div>
          </Link>

          <Link
            to="/sss"
            className={`flex items-center gap-3 rounded-xl border border-slate-100 p-3 text-left transition-colors hover:border-cyan-200 hover:bg-cyan-50/50 ${FOCUS_RING}`}
          >
            <HelpCircle className="h-5 w-5 shrink-0 text-cyan-600" aria-hidden="true" />
            <div>
              <p className="text-xs font-bold text-slate-900">S.S.S.</p>
              <p className="text-[10px] text-slate-500">Sıkça sorulanlar</p>
            </div>
          </Link>

          <Link
            to="/iletisim"
            className={`flex items-center gap-3 rounded-xl border border-slate-100 p-3 text-left transition-colors hover:border-cyan-200 hover:bg-cyan-50/50 ${FOCUS_RING}`}
          >
            <Mail className="h-5 w-5 shrink-0 text-cyan-600" aria-hidden="true" />
            <div>
              <p className="text-xs font-bold text-slate-900">İletişim</p>
              <p className="text-[10px] text-slate-500">Bize ulaşın</p>
            </div>
          </Link>
        </div>
      </nav>
    </main>
  );
}
