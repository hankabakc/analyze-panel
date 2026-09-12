import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { SiteContentEditor } from "./SiteContentEditor";
import { confirmLeave } from "@/lib/unsavedChanges";

const api = vi.hoisted(() => ({ get: vi.fn(), getFields: vi.fn(), update: vi.fn() }));
const toast = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock("@/lib/api", () => ({ siteContentApi: api }));
vi.mock("sonner", () => ({ toast }));

const FIELDS = [
  { key: "site.institutionName", maxLength: 100 },
  { key: "home.heroTitle", maxLength: 150 },
  { key: "home.intro", maxLength: 2000 },
  { key: "features.studyPlan", maxLength: 1000 },
  { key: "faq.1.question", maxLength: 300 },
];

const SAVED = {
  "site.institutionName": "Örnek Kurum",
  "home.heroTitle": "",
  "home.intro": "",
  "features.studyPlan": "",
  "faq.1.question": "",
};

beforeEach(() => {
  vi.clearAllMocks();
  api.getFields.mockResolvedValue({ data: { data: FIELDS } });
  api.get.mockResolvedValue({ data: { data: SAVED } });
});

/** Editör, sayfadan ayrılma engeli (`useBlocker`) için veri yönlendiricisi içinde çizilir; önceki adres /raporlar. */
function renderEditor() {
  const router = createMemoryRouter(
    [
      { path: "/", element: <SiteContentEditor /> },
      { path: "/raporlar", element: <p>Raporlar sayfası</p> },
    ],
    { initialEntries: ["/raporlar", "/"], initialIndex: 1 },
  );
  return { router, ...render(<RouterProvider router={router} />) };
}

describe("SiteContentEditor", () => {
  it("alanları sunucudaki sınırlarla ve gruplar hâlinde gösterir", async () => {
    renderEditor();
    const institution = await screen.findByLabelText("Kurum adı");
    expect(institution).toHaveValue("Örnek Kurum");
    expect(institution).toHaveAttribute("maxLength", "100");
    expect(screen.getByText("11 / 100")).toBeInTheDocument();
    expect(screen.getByRole("group", { name: "Ana sayfa" })).toBeInTheDocument();
    expect(screen.getByLabelText("Karşılama başlığı").tagName).toBe("INPUT");
    expect(screen.getByLabelText("Tanıtım metni").tagName).toBe("TEXTAREA");
    expect(screen.getByLabelText("Çalışma planı — açıklama")).toBeInTheDocument();
    expect(screen.getByLabelText("Soru 1")).toBeInTheDocument();
  });

  it("yalnızca değişen alanı kaydeder ve sunucunun döndürdüğü değeri gösterir", async () => {
    api.update.mockResolvedValue({ data: { data: { ...SAVED, "home.heroTitle": "Yeni başlık" } } });
    renderEditor();
    await userEvent.type(await screen.findByLabelText("Karşılama başlığı"), "  Yeni başlık  ");
    expect(screen.getByRole("status")).toHaveTextContent("1 alanda kaydedilmemiş değişiklik var.");

    await userEvent.click(screen.getByRole("button", { name: "Kaydet" }));
    expect(api.update).toHaveBeenCalledWith({ "home.heroTitle": "  Yeni başlık  " });
    expect(await screen.findByText("Tüm değişiklikler kaydedildi.")).toBeInTheDocument();
    expect(screen.getByLabelText("Karşılama başlığı")).toHaveValue("Yeni başlık");
    expect(toast.success).toHaveBeenCalledWith("Site içeriği kaydedildi.");
  });

  it("kaydetme hatasında sunucunun mesajını gösterir ve değişikliği korur", async () => {
    api.update.mockRejectedValue({
      isAxiosError: true,
      response: { data: { message: "'home.heroTitle' alanı en fazla 150 karakter olabilir." } },
    });
    renderEditor();
    await userEvent.type(await screen.findByLabelText("Karşılama başlığı"), "Deneme");
    await userEvent.click(screen.getByRole("button", { name: "Kaydet" }));

    expect(toast.error).toHaveBeenCalledWith("'home.heroTitle' alanı en fazla 150 karakter olabilir.");
    expect(screen.getByLabelText("Karşılama başlığı")).toHaveValue("Deneme");
    expect(screen.getByRole("status")).toHaveTextContent("1 alanda kaydedilmemiş değişiklik var.");
  });

  it("değişiklik yokken kaydet kapalıdır; geri al taslağı kayıtlı hâline döndürür", async () => {
    renderEditor();
    const input = await screen.findByLabelText("Kurum adı");
    const saveButton = screen.getByRole("button", { name: "Kaydet" });
    expect(saveButton).toBeDisabled();

    await userEvent.type(input, " Şubesi");
    expect(saveButton).toBeEnabled();

    await userEvent.click(screen.getByRole("button", { name: "Değişiklikleri geri al" }));
    expect(input).toHaveValue("Örnek Kurum");
    expect(saveButton).toBeDisabled();
  });

  it("kaydedilmemiş değişiklik varken sayfadan ayrılmaya karşı uyarır", async () => {
    renderEditor();
    const beforeUnload = () => {
      const event = new Event("beforeunload", { cancelable: true });
      window.dispatchEvent(event);
      return event.defaultPrevented;
    };
    const input = await screen.findByLabelText("Kurum adı");
    expect(beforeUnload()).toBe(false);

    await userEvent.type(input, "!");
    expect(beforeUnload()).toBe(true);

    await userEvent.click(screen.getByRole("button", { name: "Değişiklikleri geri al" }));
    expect(beforeUnload()).toBe(false);
  });

  it("yüklenemezse hata gösterir ve yeniden denemeyle yükler", async () => {
    api.getFields.mockRejectedValueOnce(new Error("ağ hatası"));
    renderEditor();
    expect(await screen.findByRole("alert")).toHaveTextContent("Site içeriği yüklenemedi.");

    await userEvent.click(screen.getByRole("button", { name: "Yeniden dene" }));
    expect(await screen.findByLabelText("Kurum adı")).toHaveValue("Örnek Kurum");
    expect(api.getFields).toHaveBeenCalledTimes(2);
  });

  it("siteyi görüntüle bağlantısı karşılama sayfasını yeni sekmede açar", async () => {
    renderEditor();
    const link = await screen.findByRole("link", { name: /Siteyi görüntüle/ });
    expect(link).toHaveAttribute("href", "/karsilama");
    expect(link).toHaveAttribute("target", "_blank");
    expect(link).toHaveAttribute("rel", "noopener noreferrer");
  });

  it("kaydedilmemiş değişiklik varken uygulama içi gezinme onay ister; editör kapanınca koruma kalkar", async () => {
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
    const { unmount } = renderEditor();
    const input = await screen.findByLabelText("Kurum adı");
    expect(confirmLeave()).toBe(true);
    expect(confirm).not.toHaveBeenCalled();

    await userEvent.type(input, "!");
    expect(confirmLeave()).toBe(false);
    expect(confirm).toHaveBeenCalledTimes(1);

    unmount();
    expect(confirmLeave()).toBe(true);
    confirm.mockRestore();
  });

  it("kaydedilmemiş değişiklik varken geri düğmesi ve başka adrese geçiş onay ister", async () => {
    const confirm = vi.spyOn(window, "confirm").mockReturnValueOnce(false).mockReturnValueOnce(true);
    const { router } = renderEditor();
    await userEvent.type(await screen.findByLabelText("Kurum adı"), "!");

    await act(() => router.navigate(-1));
    expect(confirm).toHaveBeenCalledTimes(1);
    expect(router.state.location.pathname).toBe("/");
    expect(screen.getByLabelText("Kurum adı")).toHaveValue("Örnek Kurum!");

    await act(() => router.navigate("/raporlar"));
    expect(confirm).toHaveBeenCalledTimes(2);
    expect(await screen.findByText("Raporlar sayfası")).toBeInTheDocument();
    confirm.mockRestore();
  });

  it("değişiklik yokken gezinme onay sormaz", async () => {
    const confirm = vi.spyOn(window, "confirm");
    const { router } = renderEditor();
    await screen.findByLabelText("Kurum adı");

    await act(() => router.navigate(-1));
    expect(await screen.findByText("Raporlar sayfası")).toBeInTheDocument();
    expect(confirm).not.toHaveBeenCalled();
    confirm.mockRestore();
  });
});
