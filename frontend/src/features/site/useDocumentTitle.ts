import { useEffect } from "react";

/** useDocumentTitle: Sayfa adını tarayıcı sekmesine yazar (T-063B). */
export function useDocumentTitle(title: string) {
  useEffect(() => {
    document.title = `${title} · Analiz Sistemi`;
  }, [title]);
}
