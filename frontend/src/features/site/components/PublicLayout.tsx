import { useEffect, useState } from "react";
import { Link, NavLink, Outlet, useLocation } from "react-router-dom";
import { Menu, X } from "lucide-react";
import { useAuth } from "@/lib/auth-context";
import { useSiteContent } from "../site-content-context";
import { text } from "../siteContent";

/** Üst menüdeki bilgi sayfaları (S-026). */
const NAV_ITEMS = [
  { to: "/ozellikler", label: "Özellikler" },
  { to: "/hakkinda", label: "Hakkında" },
  { to: "/sss", label: "SSS" },
  { to: "/iletisim", label: "İletişim" },
];

const FOCUS_RING =
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-500 focus-visible:ring-offset-2";

const desktopLinkClass = ({ isActive }: { isActive: boolean }) =>
  `tap-44 inline-flex items-center rounded-lg text-[11px] font-black uppercase tracking-[0.2em] transition-colors ${FOCUS_RING} ${
    isActive ? "text-cyan-700" : "text-slate-500 hover:text-slate-900"
  }`;

const mobileLinkClass = ({ isActive }: { isActive: boolean }) =>
  `flex min-h-[48px] items-center rounded-xl px-3 text-sm font-bold ${FOCUS_RING} ${
    isActive ? "bg-slate-100 text-slate-900" : "text-slate-600 hover:bg-slate-50"
  }`;

/**
 * PublicLayout: Genel sitenin üst menüsü, mobil menüsü ve alt bilgisi (T-063B / S-026).
 * Oturumsuz ziyaretçiye "Giriş yap", giriş yapmış kullanıcıya "Panele dön" gösterilir.
 * Dar ekranda (375 px) taşmasın diye hesap bağlantısı mobil menüye alınır.
 */
export function PublicLayout() {
  const { isAuthenticated } = useAuth();
  const contentState = useSiteContent();
  const { pathname } = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);

  // Sayfa değişince mobil menü kapanır.
  useEffect(() => {
    setMenuOpen(false);
  }, [pathname]);

  const content = contentState.status === "ready" ? contentState.content : {};
  const institution = text(content, "site.institutionName");
  const footerNote = text(content, "footer.note");
  const homePath = isAuthenticated ? "/karsilama" : "/";
  const account = isAuthenticated
    ? { to: "/", label: "Panele dön" }
    : { to: "/giris", label: "Giriş yap" };

  return (
    <div className="flex min-h-screen flex-col bg-[#F8FAFC] text-slate-900">
      <a
        href="#site-icerik"
        className="sr-only focus:not-sr-only focus:fixed focus:left-3 focus:top-3 focus:z-[60] focus:rounded-xl focus:bg-slate-900 focus:px-4 focus:py-3 focus:text-sm focus:font-bold focus:text-white"
      >
        İçeriğe geç
      </a>

      <header className="sticky top-0 z-50 border-b border-slate-100 bg-white/85 backdrop-blur-xl">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-4 px-4 sm:h-20 sm:px-8">
          <Link to={homePath} className={`flex min-h-[44px] min-w-0 items-center rounded-xl ${FOCUS_RING}`}>
            <Wordmark institution={institution} />
          </Link>

          <nav aria-label="Site menüsü" className="hidden items-center gap-8 md:flex">
            {NAV_ITEMS.map((item) => (
              <NavLink key={item.to} to={item.to} className={desktopLinkClass}>
                {item.label}
              </NavLink>
            ))}
          </nav>

          <div className="flex shrink-0 items-center gap-2">
            <Link
              to={account.to}
              className={`hidden h-11 items-center justify-center rounded-2xl bg-slate-900 px-5 text-[11px] font-black uppercase tracking-[0.2em] text-white transition-colors hover:bg-slate-800 sm:inline-flex ${FOCUS_RING}`}
            >
              {account.label}
            </Link>
            <button
              type="button"
              onClick={() => setMenuOpen((open) => !open)}
              aria-expanded={menuOpen}
              aria-controls="site-mobil-menu"
              aria-label={menuOpen ? "Menüyü kapat" : "Menüyü aç"}
              className={`inline-flex h-11 w-11 items-center justify-center rounded-2xl border border-slate-200 bg-white text-slate-700 md:hidden ${FOCUS_RING}`}
            >
              {menuOpen ? <X className="h-5 w-5" aria-hidden="true" /> : <Menu className="h-5 w-5" aria-hidden="true" />}
            </button>
          </div>
        </div>

        {menuOpen && (
          <nav
            id="site-mobil-menu"
            aria-label="Site menüsü (mobil)"
            className="border-t border-slate-100 bg-white px-4 pb-4 pt-2 md:hidden"
          >
            <ul className="flex flex-col gap-1">
              {NAV_ITEMS.map((item) => (
                <li key={item.to}>
                  <NavLink to={item.to} className={mobileLinkClass}>
                    {item.label}
                  </NavLink>
                </li>
              ))}
              <li className="pt-2">
                <Link
                  to={account.to}
                  className={`flex min-h-[48px] items-center justify-center rounded-2xl bg-slate-900 text-[11px] font-black uppercase tracking-[0.2em] text-white ${FOCUS_RING}`}
                >
                  {account.label}
                </Link>
              </li>
            </ul>
          </nav>
        )}
      </header>

      <main id="site-icerik" className="flex-1">
        <Outlet />
      </main>

      <footer className="border-t border-slate-200 bg-white">
        <div className="mx-auto grid max-w-6xl gap-10 px-4 py-12 sm:px-8 md:grid-cols-[1.4fr_1fr]">
          <div className="min-w-0 space-y-4">
            <Wordmark institution={institution} />
            {footerNote && (
              <p className="max-w-md whitespace-pre-line break-words text-sm leading-relaxed text-slate-600">
                {footerNote}
              </p>
            )}
          </div>
          <nav aria-label="Alt bilgi menüsü">
            <ul className="grid grid-cols-2 gap-x-6 text-sm font-semibold text-slate-600">
              {[...NAV_ITEMS, { to: "/gizlilik", label: "Gizlilik ve KVKK" }, account].map((item) => (
                <li key={item.to + item.label}>
                  <Link to={item.to} className={`inline-flex min-h-[44px] items-center rounded-lg hover:text-slate-900 ${FOCUS_RING}`}>
                    {item.label}
                  </Link>
                </li>
              ))}
            </ul>
          </nav>
        </div>
        <div className="border-t border-slate-100">
          <p className="mx-auto max-w-6xl truncate px-4 py-5 text-[11px] font-bold uppercase tracking-[0.2em] text-slate-400 sm:px-8">
            © {new Date().getFullYear()} {institution || "Analiz Sistemi"}
          </p>
        </div>
      </footer>
    </div>
  );
}

/** Wordmark: Uygulamanın mevcut markası; kurum adı doldurulmuşsa altında görünür. */
function Wordmark({ institution }: { institution: string }) {
  return (
    <span className="flex min-w-0 items-center gap-3">
      <span
        aria-hidden="true"
        className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-slate-900 text-lg font-black italic text-white"
      >
        A
      </span>
      <span className="min-w-0">
        <span className="block whitespace-nowrap text-lg font-black tracking-tighter text-slate-900 sm:text-xl">
          ANALİZ <span className="italic text-cyan-500">SİSTEMİ</span>
        </span>
        {institution && (
          <span className="block truncate text-[10px] font-bold uppercase tracking-[0.2em] text-slate-500">
            {institution}
          </span>
        )}
      </span>
    </span>
  );
}
