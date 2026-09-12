import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SiteContentProvider } from "./SiteContentProvider";
import { useSiteContent } from "./site-content-context";

const api = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock("@/lib/api", () => ({ siteContentApi: { get: api.get } }));

function Probe() {
  const state = useSiteContent();
  if (state.status === "loading") return <p>durum: yükleniyor</p>;
  if (state.status === "error") return <button onClick={state.retry}>tekrar dene</button>;
  return <p>başlık: {state.content["home.heroTitle"]}</p>;
}

describe("SiteContentProvider", () => {
  it("içeriği çeker; yüklenemezse yeniden denemeyle tekrar ister", async () => {
    api.get
      .mockRejectedValueOnce(new Error("ağ hatası"))
      .mockResolvedValueOnce({ data: { data: { "home.heroTitle": "Merhaba" } } });

    render(
      <SiteContentProvider>
        <Probe />
      </SiteContentProvider>,
    );

    expect(screen.getByText("durum: yükleniyor")).toBeInTheDocument();
    await userEvent.click(await screen.findByRole("button", { name: "tekrar dene" }));
    expect(await screen.findByText("başlık: Merhaba")).toBeInTheDocument();
    expect(api.get).toHaveBeenCalledTimes(2);
  });
});
