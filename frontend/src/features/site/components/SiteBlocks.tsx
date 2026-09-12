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
        <p role="status" className="text-[11px] font-black uppercase tracking-[0.3em] text-slate-400">
          İçerik yükleniyor…
        </p>
      </div>
    );
  }

  if (state.status === "error") {
    return (
      <div className={`${PAGE_WIDTH} py-20`}>
        <div role="alert" className="max-w-lg rounded-[2rem] border border-red-100 bg-white p-8">
          <p className="font-bold text-slate-900">İçerik yüklenemedi.</p>
          <p className="mt-2 text-sm text-slate-600">Bağlantınızı kontrol edip yeniden deneyin.</p>
          <button
            type="button"
            onClick={state.retry}
            className="mt-6 inline-flex h-11 items-center rounded-2xl bg-slate-900 px-5 text-[11px] font-black uppercase tracking-[0.2em] text-white hover:bg-slate-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-500 focus-visible:ring-offset-2"
          >
            Yeniden dene
          </button>
        </div>
      </div>
    );
  }

  return <>{children(state.content)}</>;
}

/** PageHeader: Alt sayfa başlığı; altındaki kısa şerit imza öğesinin küçük hâlidir. */
export function PageHeader({ title }: { title: string }) {
  return (
    <header className={`${PAGE_WIDTH} pt-14 sm:pt-20`}>
      <h1 className="text-4xl font-black tracking-tighter text-slate-900 sm:text-6xl">{title}</h1>
      <div aria-hidden="true" className="mt-6 flex h-1.5 w-28 overflow-hidden rounded-full">
        <span className="w-[60%] bg-cyan-500" />
        <span className="w-[25%] bg-slate-900" />
        <span className="w-[15%] bg-slate-200" />
      </div>
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
      <p className="max-w-lg rounded-[2rem] border border-dashed border-slate-300 bg-white p-8 text-slate-600">
        Bu sayfanın içeriği henüz eklenmedi.
      </p>
    </div>
  );
}
