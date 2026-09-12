import { useEffect, useState } from "react";
import type { ReactNode } from "react";
import { siteContentApi } from "@/lib/api";
import { SiteContentContext } from "./site-content-context";
import type { SiteContentState } from "./site-content-context";

/**
 * SiteContentProvider: Genel sitenin içeriğini sunucudan bir kez çeker ve sayfalara dağıtır (T-063B / S-026).
 * Sayfalar arasında gezinirken yeniden istek atılmaz; hata olursa kullanıcı yeniden deneyebilir.
 */
export function SiteContentProvider({ children }: { children: ReactNode }) {
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<SiteContentState>({ status: "loading" });

  useEffect(() => {
    let active = true;
    siteContentApi
      .get()
      .then((response) => {
        if (active) {
          setState({ status: "ready", content: response.data.data ?? {} });
        }
      })
      .catch(() => {
        if (active) {
          setState({
            status: "error",
            retry: () => {
              setState({ status: "loading" });
              setAttempt((count) => count + 1);
            },
          });
        }
      });
    return () => {
      active = false;
    };
  }, [attempt]);

  return <SiteContentContext.Provider value={state}>{children}</SiteContentContext.Provider>;
}
