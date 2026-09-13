import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import LoginPage from "./LoginPage";
import { LOGIN_ACCOUNT_NOTICE } from "../siteContent";

const auth = vi.hoisted(() => ({
  isAuthenticated: false,
  login: vi.fn(),
}));

vi.mock("@/lib/auth-context", () => ({
  useAuth: () => auth,
}));

function renderLoginPage() {
  return render(
    <MemoryRouter initialEntries={["/giris"]}>
      <LoginPage />
    </MemoryRouter>,
  );
}

describe("LoginPage (T-085)", () => {
  it("giriş formunun altında kurumsal hesap bilgilendirme notunu gösterir", () => {
    renderLoginPage();
    expect(screen.getByText(LOGIN_ACCOUNT_NOTICE)).toBeInTheDocument();
  });

  it("sayfada adında 'Kayıt' geçen herhangi bir bağlantı veya düğme bulunmaz", () => {
    renderLoginPage();
    const links = screen.queryAllByRole("link");
    const buttons = screen.queryAllByRole("button");

    links.forEach((link) => {
      expect(link.textContent).not.toMatch(/kayıt/i);
    });

    buttons.forEach((button) => {
      expect(button.textContent).not.toMatch(/kayıt/i);
    });
  });

  it("siteye dönüş bağlantısı anasayfaya (/ rotasına) yönlendirir", () => {
    renderLoginPage();
    const returnLink = screen.getByRole("link", { name: /Siteye dön/i });
    expect(returnLink).toHaveAttribute("href", "/");
  });

  it("sekme başlığını Giriş olarak belirler", () => {
    renderLoginPage();
    expect(document.title).toBe("Giriş · Analiz Sistemi");
  });
});
