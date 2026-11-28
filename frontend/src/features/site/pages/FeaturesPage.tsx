import { PageHeader, Prose, SiteContentGate } from "../components/SiteBlocks";
import { SITE_FEATURES, text } from "../siteContent";
import { useDocumentTitle } from "../useDocumentTitle";

/**
 * FeaturesPage: Uygulamada bulunan yedi modül (T-063B / S-026). Modül adları sabittir;
 * açıklamalar yöneticinin doldurduğu alanlardan gelir ve boşsa çizilmez.
 */
export default function FeaturesPage() {
  useDocumentTitle("Özellikler");
  return (
    <>
      <PageHeader title="Özellikler" />
      <SiteContentGate>
        {(content) => (
          <ul className="mx-auto max-w-6xl divide-y divide-slate-200 px-4 py-10 sm:px-8">
            {SITE_FEATURES.map(({ key, title }, i) => {
              const description = text(content, key);
              return (
                <li key={key} className="grid gap-4 py-8 sm:grid-cols-[3rem_1fr] sm:gap-8">
                  <span aria-hidden="true" className="pt-1 font-mono text-sm text-slate-400">{String(i + 1).padStart(2, "0")}</span>
                  <div className="min-w-0">
                    <h2 className="text-2xl font-semibold tracking-tight text-slate-900">{title}</h2>
                    <Prose value={description} className="mt-3 max-w-3xl" />
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </SiteContentGate>
    </>
  );
}
