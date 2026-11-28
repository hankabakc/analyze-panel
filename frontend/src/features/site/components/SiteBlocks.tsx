import type { ReactNode } from "react";
import { useSiteContent } from "../site-content-context";
import type { SiteContent } from "../siteContent";

const PAGE_WIDTH = "mx-auto max-w-6xl px-4 sm:px-8";

/**
 * SiteContentGate: İçerik yüklenirken, yüklenemediğinde ve hazır olduğunda ne gösterileceğini
 * tek yerde belirler (IST-01 §2.2). Hazır içerik alt bileşene verilir.
 */
export function SiteContentGate({ children }: { children: (content: SiteContent) => ReactNode }) {
  const state = useSiteContent();

  if (state.status === "loading") {
    return (
      <div className={`${PAGE_WIDTH} py-20`}>
        <p role="status" className="text-sm text-slate-500">
          İçerik yükleniyor…
        </p>
      </div>
    );
  }

  if (state.status === "error") {
    return (
      <div className={`${PAGE_WIDTH} py-20`}>
        <div role="alert" className="max-w-lg rounded-xl border border-red-100 bg-white p-8">
          <p className="font-medium text-slate-900">İçerik yüklenemedi.</p>
          <p className="mt-2 text-sm text-slate-600">Bağlantınızı kontrol edip yeniden deneyin.</p>
          <button
            type="button"
            onClick={state.retry}
            className="mt-6 inline-flex h-11 items-center rounded-lg bg-slate-900 px-5 text-xs font-semibold tracking-normal text-white hover:bg-slate-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-500 focus-visible:ring-offset-2"
          >
            Yeniden dene
          </button>
        </div>
      </div>
    );
  }

  return <>{children(state.content)}</>;
}

/** PageHeader: Alt sayfa başlığı. */
export function PageHeader({ title }: { title: string }) {
  return (
    <header className={`${PAGE_WIDTH} pt-14 sm:pt-20`}>
      <h1 className="border-b border-slate-200 pb-8 text-3xl font-semibold tracking-tight text-slate-900 sm:text-5xl">{title}</h1>
    </header>
  );
}

/**
 * Prose: Düz metni satır sonlarını koruyarak gösterir; boşsa hiçbir şey çizmez.
 * Metin HTML olarak yorumlanmaz, kaçışı React yapar (ENG-12 §2.2).
 */
export function Prose({ value, className = "" }: { value: string; className?: string }) {
  if (!value) {
    return null;
  }
  return (
    <p className={`whitespace-pre-line break-words text-base leading-relaxed text-slate-600 sm:text-lg ${className}`}>
      {value}
    </p>
  );
}

/** EmptyPage: Sayfanın bütün alanları boşken gösterilir. */
export function EmptyPage() {
  return (
    <div className={`${PAGE_WIDTH} py-14`}>
      <p className="max-w-lg rounded-xl border border-dashed border-slate-300 bg-white p-8 text-slate-600">
        Bu sayfanın içeriği henüz eklenmedi.
      </p>
    </div>
  );
}
