import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { AuthProvider } from "./lib/auth-provider";
import { AppRoutes } from "./App";
import type { User } from "./lib/types";

const mocks = vi.hoisted(() => ({ me: vi.fn(), getContent: vi.fn() }));

vi.mock("./lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("./lib/api")>();
  return {
    ...actual,
    authApi: { ...actual.authApi, me: mocks.me },
    siteContentApi: { ...actual.siteContentApi, get: mocks.getContent },
  };
});
vi.mock("./pages/ManagerDashboard", () => ({ default: () => <p>Yönetici paneli</p> }));
vi.mock("./pages/TeacherDashboard", () => ({ default: () => <p>Öğretmen paneli</p> }));
vi.mock("./pages/StudentDashboard", () => ({ default: () => <p>Öğrenci paneli</p> }));
vi.mock("./pages/ReportsPage", () => ({ default: () => <p>Raporlar sayfası</p> }));
vi.mock("./pages/LeaderboardPage", () => ({ default: () => <p>Liderlik sayfası</p> }));
vi.mock("./pages/AnalysisDetail", () => ({ default: () => <p>Analiz sayfası</p> }));
vi.mock("./pages/CumulativeAnalysis", () => ({ default: () => <p>Gelişim sayfası</p> }));
vi.mock("./features/auth/components/MustChangePasswordScreen", () => ({
  MustChangePasswordScreen: () => <p>Zorunlu şifre değiştirme ekranı</p>,
}));

const student: User = {
  id: "ogrenci-1",
  email: "ogrenci@test.local",
  fullName: "Test Öğrenci",
  role: "STUDENT",
  status: "ACTIVE",
  mustChangePassword: false,
};

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </MemoryRouter>,
  );
}

const signedOut = () => mocks.me.mockRejectedValue(new Error("401"));
const signedIn = (user: User) => mocks.me.mockResolvedValue({ data: { success: true, data: user } });

beforeEach(() => {
  mocks.me.mockReset();
  mocks.getContent.mockReset();
  mocks.getContent.mockResolvedValue({ data: { success: true, data: {} } });
});

describe("Yönlendirme (S-026)", () => {
  it("oturumsuz ziyaretçi / adresinde karşılama sayfasını görür", async () => {
    signedOut();
    renderAt("/");
    expect(await screen.findByRole("heading", { level: 1, name: "ANALİZ SİSTEMİ" })).toBeInTheDocument();
  });

  it("oturumsuz ziyaretçi /giris adresinde giriş formunu görür", async () => {
    signedOut();
    renderAt("/giris");
    expect(await screen.findByRole("heading", { name: "Giriş Yap" })).toBeInTheDocument();
  });

  it("oturumsuz ziyaretçi panel adresine girerse giriş ekranına yönlenir", async () => {
    signedOut();
    renderAt("/raporlar");
    expect(await screen.findByRole("heading", { name: "Giriş Yap" })).toBeInTheDocument();
    expect(screen.queryByText("Raporlar sayfası")).not.toBeInTheDocument();
  });

  it("oturumsuz ziyaretçi bilinmeyen adreste özel 404 sayfasını görür", async () => {
    signedOut();
    renderAt("/olmayan-sayfa");
    expect(await screen.findByRole("heading", { level: 1, name: /Sayfa Bulunamadı/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Ana Sayfaya Dön/i })).toBeInTheDocument();
  });

  it("giriş yapmış kullanıcı bilinmeyen adreste özel 404 sayfasını ve panele dönüş bağlantısını görür", async () => {
    signedIn(student);
    renderAt("/bilinmeyen-rota");
    expect(await screen.findByRole("heading", { level: 1, name: /Sayfa Bulunamadı/i })).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: /Panele Dön/i }).length).toBeGreaterThanOrEqual(1);
  });

  it("giriş yapmış kullanıcı / adresinde kendi panelini görür", async () => {
    signedIn(student);
    renderAt("/");
    expect(await screen.findByText("Öğrenci paneli")).toBeInTheDocument();
  });

  it("giriş yapmış kullanıcı /giris adresine girerse paneline döner", async () => {
    signedIn(student);
    renderAt("/giris");
    expect(await screen.findByText("Öğrenci paneli")).toBeInTheDocument();
  });

  it("bilgi sayfaları giriş yapmış kullanıcıya da açıktır ve panele dönüş bağlantısı verir", async () => {
    signedIn(student);
    renderAt("/hakkinda");
    expect(await screen.findByRole("heading", { level: 1, name: "Hakkında" })).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "Panele dön" }).length).toBeGreaterThan(0);
  });

  it("şifresini değiştirmesi gereken kullanıcı panel yerine şifre ekranını görür", async () => {
    signedIn({ ...student, mustChangePassword: true });
    renderAt("/");
    expect(await screen.findByText("Zorunlu şifre değiştirme ekranı")).toBeInTheDocument();
    expect(screen.queryByText("Öğrenci paneli")).not.toBeInTheDocument();
  });
});
