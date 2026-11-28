import { Link } from "react-router-dom";
import { ArrowRight } from "@/components/icons";
import { useAuth } from "@/lib/auth-context";
import { ProductPreview } from "../components/ProductPreview";
import { Prose, SiteContentGate } from "../components/SiteBlocks";
import { LOGIN_BUTTON_LABEL, SITE_FEATURES, text } from "../siteContent";
import type { SiteContent } from "../siteContent";
import { useDocumentTitle } from "../useDocumentTitle";

const FOCUS_RING =
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-500 focus-visible:ring-offset-2";

const STEPS = [
  { title: "Karneyi yükleyin", body: "Öğretmen öğrencinin deneme karnesini PDF olarak yükler. Netler, dersler ve konular okunur." },
  { title: "Kontrol edip onaylayın", body: "Okunan sonuçlar öğretmen onaylamadan öğrenciye görünmez. Hatalı okuma reddedilir." },
  { title: "Gelişimi izleyin", body: "Onaylı karneler gelişim dosyasında birleşir; puan kaybettiren konular öne çıkar." },
];

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
      <section aria-labelledby="karsilama-baslik" className="mx-auto max-w-6xl px-4 pt-16 sm:px-8 sm:pt-24">
        <div className="mx-auto max-w-3xl text-center">
          {institution && (
            <p className="inline-flex items-center rounded-full border border-slate-200 px-3 py-1 text-xs text-slate-600">
              {institution}
            </p>
          )}
          <h1
            id="karsilama-baslik"
            className="mt-6 break-words text-4xl font-semibold leading-[1.05] tracking-tight text-slate-900 sm:text-6xl"
          >
            {heroTitle || "Analiz Sistemi"}
          </h1>
          <Prose value={heroSubtitle} className="mx-auto mt-6 max-w-xl text-slate-500 sm:text-lg" />
          <div className="mt-9 flex flex-wrap justify-center gap-3">
            <Link
              to={account.to}
              className={`inline-flex h-11 items-center gap-2 rounded-md bg-slate-900 px-5 text-sm font-medium text-white transition-colors hover:bg-slate-700 ${FOCUS_RING}`}
            >
              {account.label}
              <ArrowRight className="h-4 w-4" aria-hidden="true" />
            </Link>
            <Link
              to="/ozellikler"
              className={`inline-flex h-11 items-center rounded-md border border-slate-300 bg-white px-5 text-sm font-medium text-slate-800 transition-colors hover:bg-slate-50 ${FOCUS_RING}`}
            >
              Özellikleri incele
            </Link>
          </div>
        </div>
        <div className="mx-auto mt-16 max-w-5xl">
          <ProductPreview />
        </div>
      </section>

      {intro && (
        <section aria-label="Tanıtım" className="mx-auto max-w-6xl px-4 py-20 sm:px-8 sm:py-28">
          <Prose value={intro} className="mx-auto max-w-3xl text-lg leading-relaxed text-slate-600 sm:text-2xl sm:leading-snug" />
        </section>
      )}

      <section aria-labelledby="adimlar-baslik" className="border-y border-slate-200 bg-slate-50">
        <div className="mx-auto max-w-6xl px-4 py-16 sm:px-8">
          <h2 id="adimlar-baslik" className="text-sm font-medium text-slate-500">Nasıl çalışır</h2>
          <ol className="mt-6 grid gap-8 sm:grid-cols-3">
            {STEPS.map((step, i) => (
              <li key={step.title}>
                <span className="font-mono text-sm text-slate-400">0{i + 1}</span>
                <h3 className="mt-2 text-lg font-semibold tracking-tight text-slate-900">{step.title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-slate-600">{step.body}</p>
              </li>
            ))}
          </ol>
        </div>
      </section>

      <section aria-labelledby="moduller-baslik" className="mx-auto max-w-6xl px-4 py-20 sm:px-8 sm:py-24">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <h2 id="moduller-baslik" className="text-3xl font-semibold tracking-tight text-slate-900">
            Uygulamada neler var
          </h2>
          <Link
            to="/ozellikler"
            className={`tap-44 inline-flex items-center gap-1.5 rounded-md text-sm text-slate-600 hover:text-slate-900 ${FOCUS_RING}`}
          >
            Tümünü gör
            <ArrowRight className="h-4 w-4" aria-hidden="true" />
          </Link>
        </div>
        <ul className="mt-10 grid overflow-hidden rounded-xl border border-slate-200 sm:grid-cols-2 lg:grid-cols-4">
          {SITE_FEATURES.map(({ key, title, icon: Icon }, i) => {
            const description = text(content, key);
            return (
              <li key={key} className={`-mb-px -mr-px border-b border-r border-slate-200 bg-white p-6 ${i === 0 ? "sm:col-span-2" : ""}`}>
                <Icon className="h-5 w-5 text-slate-500" aria-hidden="true" />
                <h3 className="mt-4 text-base font-semibold tracking-tight text-slate-900">{title}</h3>
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
        <section aria-labelledby="hesap-baslik" className="mx-auto max-w-6xl px-4 pb-20 sm:px-8">
          <div className="flex flex-col gap-6 rounded-xl bg-slate-900 px-8 py-12 text-white sm:flex-row sm:items-center sm:justify-between sm:px-12">
            <div>
              <h2 id="hesap-baslik" className="text-2xl font-semibold tracking-tight sm:text-3xl">
                Hesaplar kurum tarafından açılır
              </h2>
              <p className="mt-2 text-slate-400">Giriş bilgilerinizi kurumunuzun yöneticisinden alırsınız.</p>
            </div>
            <Link
              to="/giris"
              className="inline-flex h-11 shrink-0 items-center justify-center rounded-md bg-white px-5 text-sm font-medium text-slate-900 transition-colors hover:bg-slate-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white focus-visible:ring-offset-2 focus-visible:ring-offset-slate-900"
            >
              {LOGIN_BUTTON_LABEL}
            </Link>
          </div>
        </section>
      )}
    </>
  );
}
