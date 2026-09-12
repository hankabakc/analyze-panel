import { createContext, useContext } from "react";
import type { SiteContent } from "./siteContent";

/**
 * SiteContentState: Genel sitenin içerik durumu açıkça modellenir (IST-01 §2.2).
 */
export type SiteContentState =
  | { status: "loading" }
  | { status: "error"; retry: () => void }
  | { status: "ready"; content: SiteContent };

export const SiteContentContext = createContext<SiteContentState | null>(null);

/** useSiteContent: Genel sitedeki sayfaların içerik durumuna erişimi. */
export function useSiteContent(): SiteContentState {
  const state = useContext(SiteContentContext);
  if (!state) {
    throw new Error("useSiteContent, SiteContentProvider içinde kullanılmalıdır.");
  }
  return state;
}
