import { EmptyPage, PageHeader, Prose, SiteContentGate } from "../components/SiteBlocks";
import { text } from "../siteContent";
import { useDocumentTitle } from "../useDocumentTitle";

const SECTIONS = [
  { key: "about.body", title: null },
  { key: "about.mission", title: "Misyonumuz" },
  { key: "about.vision", title: "Vizyonumuz" },
] as const;

/**
 * AboutPage: Kurum hakkında sayfası (T-063B / S-026). Boş bölüm başlığıyla birlikte gizlenir;
 * bütün bölümler boşsa sayfa içerik eklenmediğini söyler.
 */
export default function AboutPage() {
  useDocumentTitle("Hakkında");
  return (
    <>
      <PageHeader title="Hakkında" />
      <SiteContentGate>
        {(content) => {
          const filled = SECTIONS.map((section) => ({ ...section, value: text(content, section.key) })).filter(
            (section) => section.value !== "",
          );
          if (filled.length === 0) {
            return <EmptyPage />;
          }
          return (
            <div className="mx-auto max-w-6xl space-y-14 px-4 py-12 sm:px-8">
              {filled.map((section) => (
                <section key={section.key}>
                  {section.title && (
                    <h2 className="text-[11px] font-black uppercase tracking-[0.3em] text-cyan-700">{section.title}</h2>
                  )}
                  <Prose value={section.value} className="mt-4 max-w-3xl text-slate-700 sm:text-xl" />
                </section>
              ))}
            </div>
          );
        }}
      </SiteContentGate>
    </>
  );
}
