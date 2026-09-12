import { Plus } from "lucide-react";
import { EmptyPage, PageHeader, Prose, SiteContentGate } from "../components/SiteBlocks";
import { faqItems } from "../siteContent";
import { useDocumentTitle } from "../useDocumentTitle";

/**
 * FaqPage: Sıkça sorulan sorular (T-063B / S-026). Yalnızca sorusu doldurulmuş yuvalar gösterilir;
 * açılır-kapanır yapı tarayıcının kendi details/summary öğesidir (klavye ve ekran okuyucu desteği hazır).
 */
export default function FaqPage() {
  useDocumentTitle("Sıkça sorulan sorular");
  return (
    <>
      <PageHeader title="Sıkça sorulan sorular" />
      <SiteContentGate>
        {(content) => {
          const items = faqItems(content);
          if (items.length === 0) {
            return <EmptyPage />;
          }
          return (
            <div className="mx-auto max-w-4xl px-4 py-12 sm:px-8">
              <ul className="divide-y divide-slate-200 rounded-[2rem] border border-slate-200 bg-white">
                {items.map((item) => (
                  <li key={item.question}>
                    <details className="group">
                      <summary className="flex min-h-[56px] cursor-pointer list-none items-center justify-between gap-4 rounded-[2rem] px-6 py-4 text-lg font-bold text-slate-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-cyan-500 [&::-webkit-details-marker]:hidden">
                        <span className="min-w-0 break-words">{item.question}</span>
                        <Plus
                          className="h-5 w-5 shrink-0 text-cyan-600 transition-transform group-open:rotate-45"
                          aria-hidden="true"
                        />
                      </summary>
                      {item.answer && <Prose value={item.answer} className="px-6 pb-6" />}
                    </details>
                  </li>
                ))}
              </ul>
            </div>
          );
        }}
      </SiteContentGate>
    </>
  );
}
