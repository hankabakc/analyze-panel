import { Link } from "react-router-dom";
import { ArrowRight } from "@/components/icons";
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
    ? { to: "/", label: "Panele dön" }
    : { to: "/", label: "Ana sayfaya dön" };

  return (
    <main role="main" className="mx-auto flex min-h-[60vh] max-w-2xl flex-col items-center justify-center px-4 py-16 text-center sm:px-8">
      <p className="font-mono text-sm text-slate-400">404</p>
      <h1 className="mt-3 text-4xl font-semibold tracking-tight text-slate-900 sm:text-5xl">Sayfa bulunamadı</h1>
      <p className="mt-4 max-w-md text-base leading-relaxed text-slate-600">
        Aradığınız sayfa taşınmış ya da hiç var olmamış olabilir.
      </p>
      <div className="mt-8 flex flex-wrap justify-center gap-3">
        <Link to={primaryAction.to} className={`inline-flex h-11 items-center gap-2 rounded-md bg-slate-900 px-5 text-sm font-medium text-white transition-colors hover:bg-slate-700 ${FOCUS_RING}`}>
          {primaryAction.label}
          <ArrowRight className="h-4 w-4" aria-hidden="true" />
        </Link>
        {!isAuthenticated && (
          <Link to="/giris" className={`inline-flex h-11 items-center rounded-md border border-slate-300 bg-white px-5 text-sm font-medium text-slate-800 transition-colors hover:bg-slate-50 ${FOCUS_RING}`}>
            {LOGIN_BUTTON_LABEL}
          </Link>
        )}
      </div>
      <nav aria-label="Yararlı bağlantılar" className="mt-10 flex gap-6 text-sm text-slate-500">
        <Link to="/ozellikler" className={`hover:text-slate-900 ${FOCUS_RING}`}>Özellikler</Link>
        <Link to="/sss" className={`hover:text-slate-900 ${FOCUS_RING}`}>SSS</Link>
        <Link to="/iletisim" className={`hover:text-slate-900 ${FOCUS_RING}`}>İletişim</Link>
      </nav>
    </main>
  );
}
