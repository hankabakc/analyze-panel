import { Link } from "react-router-dom";
import { ArrowRight } from "lucide-react";
import { useAuth } from "@/lib/auth-context";
import { NetStripe } from "../components/NetStripe";
import { Prose, SiteContentGate } from "../components/SiteBlocks";
import { LOGIN_BUTTON_LABEL, SITE_FEATURES, text } from "../siteContent";
import type { SiteContent } from "../siteContent";
import { useDocumentTitle } from "../useDocumentTitle";

const FOCUS_RING =
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-500 focus-visible:ring-offset-2";

/**
 * HomePage: Karşılama sayfası (T-063B / S-026). Bütün yazılar yöneticinin doldurduğu alanlardan gelir;
 * başlık boşsa uygulamanın markası gösterilir, diğer boş alanlar hiç çizilmez.
 */
export default function HomePage() {
  useDocumentTitle("Ana sayfa");
  return <SiteContentGate>{(content) => <HomeContent content={content} />}</SiteContentGate>;
}

function HomeContent({ content }: { content: SiteContent }) {
  const { isAuthenticated } = useAuth();
  const institution = text(content, "site.institutionName");
  const heroTitle = text(content, "home.heroTitle");
  const heroSubtitle = text(content, "home.heroSubtitle");
  const intro = text(content, "home.intro");
  const account = isAuthenticated
    ? { to: "/", label: "Panele dön" }
    : { to: "/giris", label: LOGIN_BUTTON_LABEL };

  return (
    <>
      <section
        aria-labelledby="karsilama-baslik"
        className="mx-auto grid max-w-6xl items-center gap-12 px-4 py-14 sm:px-8 sm:py-24 lg:grid-cols-[1.15fr_0.85fr]"
      >
        <div className="min-w-0">
          {institution && (
            <p className="break-words text-[11px] font-black uppercase tracking-[0.3em] text-cyan-700">{institution}</p>
          )}
          <h1
            id="karsilama-baslik"
            className="mt-4 break-words text-5xl font-black leading-[0.95] tracking-tighter text-slate-900 sm:text-7xl"
          >
            {heroTitle || (
              <>
                ANALİZ <span className="italic text-cyan-500">SİSTEMİ</span>
              </>
            )}
          </h1>
          <Prose value={heroSubtitle} className="mt-6 max-w-xl" />
          <div className="mt-10 flex flex-wrap gap-3">
            <Link
              to={account.to}
              className={`inline-flex h-14 items-center gap-3 rounded-2xl bg-slate-900 px-7 text-[11px] font-black uppercase tracking-[0.2em] text-white transition-colors hover:bg-slate-800 ${FOCUS_RING}`}
            >
              {account.label}
              <ArrowRight className="h-4 w-4" aria-hidden="true" />
            </Link>
            <Link
              to="/ozellikler"
              className={`inline-flex h-14 items-center rounded-2xl border-2 border-slate-200 bg-white px-7 text-[11px] font-black uppercase tracking-[0.2em] text-slate-700 transition-colors hover:border-slate-300 ${FOCUS_RING}`}
            >
              Özellikleri incele
            </Link>
          </div>
        </div>
        <NetStripe />
      </section>

      {intro && (
        <section aria-label="Tanıtım" className="border-y border-slate-200 bg-white">
          <div className="mx-auto max-w-6xl px-4 py-14 sm:px-8">
            <Prose value={intro} className="max-w-3xl text-slate-700 sm:text-xl" />
          </div>
        </section>
      )}

      <section aria-labelledby="moduller-baslik" className="mx-auto max-w-6xl px-4 py-14 sm:px-8 sm:py-24">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <h2 id="moduller-baslik" className="text-3xl font-black tracking-tighter text-slate-900 sm:text-4xl">
            Uygulamada neler var
          </h2>
          <Link
            to="/ozellikler"
            className={`tap-44 inline-flex items-center gap-2 rounded-lg text-[11px] font-black uppercase tracking-[0.2em] text-cyan-700 hover:text-cyan-900 ${FOCUS_RING}`}
          >
            Tümünü gör
            <ArrowRight className="h-4 w-4" aria-hidden="true" />
          </Link>
        </div>
        <ul className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {SITE_FEATURES.map(({ key, title, icon: Icon }) => {
            const description = text(content, key);
            return (
              <li key={key} className="rounded-[2rem] border border-slate-200 bg-white p-6">
                <Icon className="h-6 w-6 text-cyan-600" aria-hidden="true" />
                <h3 className="mt-4 text-lg font-black tracking-tight text-slate-900">{title}</h3>
                {description && (
                  <p className="mt-2 line-clamp-3 whitespace-pre-line break-words text-sm leading-relaxed text-slate-600">
                    {description}
                  </p>
                )}
              </li>
            );
          })}
        </ul>
      </section>

      {!isAuthenticated && (
        <section aria-labelledby="hesap-baslik" className="bg-slate-900 text-white">
          <div className="mx-auto flex max-w-6xl flex-col gap-6 px-4 py-14 sm:flex-row sm:items-center sm:justify-between sm:px-8">
            <div>
              <h2 id="hesap-baslik" className="text-2xl font-black tracking-tighter sm:text-3xl">
                Hesaplar kurum tarafından açılır
              </h2>
              <p className="mt-2 text-slate-300">Giriş bilgilerinizi kurumunuzun yöneticisinden alırsınız.</p>
            </div>
            <Link
              to="/giris"
              className="inline-flex h-14 shrink-0 items-center justify-center rounded-2xl bg-cyan-500 px-7 text-[11px] font-black uppercase tracking-[0.2em] text-slate-900 transition-colors hover:bg-cyan-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-300 focus-visible:ring-offset-2 focus-visible:ring-offset-slate-900"
            >
              {LOGIN_BUTTON_LABEL}
            </Link>
          </div>
        </section>
      )}
    </>
  );
}
