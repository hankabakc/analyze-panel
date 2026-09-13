import { describe, expect, it, vi } from "vitest";
import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import HomePage from "./HomePage";
import { ready, renderWithSiteContent } from "@/test/renderWithSite";
import { SITE_FEATURES } from "../siteContent";

const auth = vi.hoisted(() => ({ isAuthenticated: false }));
vi.mock("@/lib/auth-context", () => ({ useAuth: () => auth }));

describe("HomePage", () => {
  it("doldurulmuş alanları gösterir", () => {
    auth.isAuthenticated = false;
    renderWithSiteContent(
      <HomePage />,
      ready({
        "site.institutionName": "Örnek Kurum",
        "home.heroTitle": "Her denemeyi konu konu okuyoruz",
        "home.heroSubtitle": "Alt başlık metni",
        "home.intro": "Tanıtım metni",
        "features.studyPlan": "Plan açıklaması",
      }),
    );
    expect(screen.getByRole("heading", { level: 1, name: "Her denemeyi konu konu okuyoruz" })).toBeInTheDocument();
    expect(screen.getByText("Örnek Kurum")).toBeInTheDocument();
    expect(screen.getByText("Alt başlık metni")).toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Tanıtım" })).toHaveTextContent("Tanıtım metni");
    expect(screen.getByText("Plan açıklaması")).toBeInTheDocument();
  });

  it("alanlar boşken marka başlığını gösterir ve boş bölüm çizmez", () => {
    renderWithSiteContent(<HomePage />, ready({}));
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("ANALİZ SİSTEMİ");
    expect(screen.queryByRole("region", { name: "Tanıtım" })).not.toBeInTheDocument();
  });

  it("uygulamadaki yedi modülün hepsini listeler", () => {
    renderWithSiteContent(<HomePage />, ready({}));
    const section = screen.getByRole("region", { name: "Uygulamada neler var" });
    SITE_FEATURES.forEach((feature) =>
      expect(within(section).getByRole("heading", { level: 3, name: feature.title })).toBeInTheDocument(),
    );
  });

  it("oturumsuz ziyaretçiye giriş bağlantısı, giriş yapmış kullanıcıya panel bağlantısı verir", () => {
    auth.isAuthenticated = false;
    const { unmount } = renderWithSiteContent(<HomePage />, ready({}));
    const loginLinks = screen.getAllByRole("link", { name: /Öğrenci \/ Öğretmen Girişi/ });
    expect(loginLinks.length).toBe(2);
    loginLinks.forEach((link) => expect(link).toHaveAttribute("href", "/giris"));
    unmount();

    auth.isAuthenticated = true;
    renderWithSiteContent(<HomePage />, ready({}));
    expect(screen.getByRole("link", { name: /Panele dön/ })).toHaveAttribute("href", "/");
    expect(screen.queryByRole("link", { name: /Öğrenci \/ Öğretmen Girişi/ })).not.toBeInTheDocument();
    auth.isAuthenticated = false;
  });

  it("yüklenirken durum bildirir, yüklenemezse yeniden denemeyi sunar", async () => {
    const retry = vi.fn();
    const { unmount } = renderWithSiteContent(<HomePage />, { status: "loading" });
    expect(screen.getByRole("status")).toHaveTextContent("İçerik yükleniyor");
    unmount();

    renderWithSiteContent(<HomePage />, { status: "error", retry });
    expect(screen.getByRole("alert")).toHaveTextContent("İçerik yüklenemedi.");
    await userEvent.click(screen.getByRole("button", { name: "Yeniden dene" }));
    expect(retry).toHaveBeenCalledTimes(1);
  });

  it("sekme başlığını yazar", () => {
    renderWithSiteContent(<HomePage />, ready({}));
    expect(document.title).toBe("Ana sayfa · Analiz Sistemi");
  });
});
