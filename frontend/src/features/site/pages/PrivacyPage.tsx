import { EmptyPage, PageHeader, Prose, SiteContentGate } from "../components/SiteBlocks";
import { text } from "../siteContent";
import { useDocumentTitle } from "../useDocumentTitle";

/**
 * PrivacyPage: Gizlilik ve KVKK aydınlatma metni (T-063B / S-026). Metin kurum tarafından doldurulur.
 */
export default function PrivacyPage() {
  useDocumentTitle("Gizlilik ve KVKK");
  return (
    <>
      <PageHeader title="Gizlilik ve KVKK" />
      <SiteContentGate>
        {(content) => {
          const body = text(content, "privacy.body");
          if (!body) {
            return <EmptyPage />;
          }
          return (
            <div className="mx-auto max-w-4xl px-4 py-12 sm:px-8">
              <Prose value={body} />
            </div>
          );
        }}
      </SiteContentGate>
    </>
  );
}
