import { describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { PublicLayout } from "./PublicLayout";
import { SiteContentContext } from "../site-content-context";
import type { SiteContentState } from "../site-content-context";

const auth = vi.hoisted(() => ({ isAuthenticated: false }));
vi.mock("@/lib/auth-context", () => ({ useAuth: () => auth }));

function renderLayout(state: SiteContentState, route = "/") {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <SiteContentContext.Provider value={state}>
        <Routes>
          <Route element={<PublicLayout />}>
            <Route path="/" element={<p>Ana içerik</p>} />
            <Route path="/hakkinda" element={<p>Hakkında içeriği</p>} />
          </Route>
        </Routes>
      </SiteContentContext.Provider>
    </MemoryRouter>,
  );
}

describe("PublicLayout", () => {
  it("üst menüde bilgi sayfalarına bağlantı verir, sayfa içeriğini ve içeriğe geç bağlantısını çizer", () => {
    auth.isAuthenticated = false;
    renderLayout({ status: "ready", content: {} });
    const nav = screen.getByRole("navigation", { name: "Site menüsü" });
    const hrefs = within(nav).getAllByRole("link").map((link) => link.getAttribute("href"));
    expect(hrefs).toEqual(["/ozellikler", "/hakkinda", "/sss", "/iletisim"]);
    expect(screen.getByText("Ana içerik")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "İçeriğe geç" })).toHaveAttribute("href", "#site-icerik");
  });

  it("oturumsuz ziyaretçiye Öğrenci / Öğretmen Girişi, giriş yapmış kullanıcıya Panele dön gösterir", () => {
    auth.isAuthenticated = false;
    const { unmount } = renderLayout({ status: "ready", content: {} });
    screen.getAllByRole("link", { name: "Öğrenci / Öğretmen Girişi" }).forEach((link) => expect(link).toHaveAttribute("href", "/giris"));
    unmount();

    auth.isAuthenticated = true;
    renderLayout({ status: "ready", content: {} });
    expect(screen.queryByRole("link", { name: "Öğrenci / Öğretmen Girişi" })).not.toBeInTheDocument();
    screen.getAllByRole("link", { name: "Panele dön" }).forEach((link) => expect(link).toHaveAttribute("href", "/"));
    auth.isAuthenticated = false;
  });

  it("mobil menü düğmesi menüyü açar ve sayfa değişince menü kapanır", async () => {
    auth.isAuthenticated = false;
    renderLayout({ status: "ready", content: {} });
    const toggle = screen.getByRole("button", { name: "Menüyü aç" });
    expect(toggle).toHaveAttribute("aria-expanded", "false");

    await userEvent.click(toggle);
    expect(screen.getByRole("button", { name: "Menüyü kapat" })).toHaveAttribute("aria-expanded", "true");
    const mobile = screen.getByRole("navigation", { name: "Site menüsü (mobil)" });

    await userEvent.click(within(mobile).getByRole("link", { name: "Hakkında" }));
    expect(screen.getByText("Hakkında içeriği")).toBeInTheDocument();
    expect(screen.queryByRole("navigation", { name: "Site menüsü (mobil)" })).not.toBeInTheDocument();
  });

  it("alt bilgide kurum adını, notu ve gizlilik bağlantısını gösterir", () => {
    auth.isAuthenticated = false;
    renderLayout({
      status: "ready",
      content: { "site.institutionName": "Örnek Kurum", "footer.note": "Alt bilgi notu" },
    });
    expect(screen.getAllByText("Örnek Kurum").length).toBeGreaterThan(0);
    expect(screen.getByText("Alt bilgi notu")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Gizlilik ve KVKK" })).toHaveAttribute("href", "/gizlilik");
  });
});
