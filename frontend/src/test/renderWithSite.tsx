import { render } from "@testing-library/react";
import type { ReactElement } from "react";
import { MemoryRouter } from "react-router-dom";
import { SiteContentContext } from "@/features/site/site-content-context";
import type { SiteContentState } from "@/features/site/site-content-context";
import type { SiteContent } from "@/features/site/siteContent";

/** ready: Hazır durumdaki içerik (test yardımcısı). */
export function ready(content: SiteContent): SiteContentState {
  return { status: "ready", content };
}

/**
 * renderWithSiteContent: Sayfayı yönlendirici ve verilen içerik durumuyla çizer.
 * Ağ kullanılmaz; içerik doğrudan bağlama verilir (IST-08 §1.1).
 */
export function renderWithSiteContent(ui: ReactElement, state: SiteContentState, route = "/") {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <SiteContentContext.Provider value={state}>{ui}</SiteContentContext.Provider>
    </MemoryRouter>,
  );
}
