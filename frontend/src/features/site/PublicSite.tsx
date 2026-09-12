import { SiteContentProvider } from "./SiteContentProvider";
import { PublicLayout } from "./components/PublicLayout";

/**
 * PublicSite: Genel sitenin kök düzeni — içerik sağlayıcı, üst menü ve alt bilgi (T-063B / S-026).
 * Alt sayfalar yönlendiricideki iç rotalardan gelir.
 */
export function PublicSite() {
  return (
    <SiteContentProvider>
      <PublicLayout />
    </SiteContentProvider>
  );
}
