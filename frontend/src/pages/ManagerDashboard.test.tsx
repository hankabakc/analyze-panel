import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import ManagerDashboard from "./ManagerDashboard";

const api = vi.hoisted(() => {
  const emptyList = () => vi.fn().mockResolvedValue({ data: { success: true, data: [] } });
  return {
    membershipApi: {
      getTeachers: emptyList(),
      getStudents: emptyList(),
      getUnpairedStudents: emptyList(),
      getPairings: emptyList(),
      getActivity: emptyList(),
      getClasses: emptyList(),
      getManagerCount: vi.fn().mockResolvedValue({ data: { success: true, data: 1 } }),
    },
    psychTestsApi: {
      getUnseenCounts: vi.fn().mockResolvedValue({ data: { success: true, data: { totalUnseen: 0, studentCounts: {} } } }),
    },
    siteContentApi: {
      getFields: vi.fn().mockResolvedValue({ data: { success: true, data: [{ key: "site.institutionName", maxLength: 100 }] } }),
      get: vi.fn().mockResolvedValue({ data: { success: true, data: { "site.institutionName": "" } } }),
      update: vi.fn(),
    },
  };
});
vi.mock("@/lib/api", () => api);

describe("ManagerDashboard — Tek Yönetici Uyarısı (T-075)", () => {
  it("sistemde tek yönetici varsa Genel Bakış ekranında güvenlik tavsiye uyarısı görünür", async () => {
    api.membershipApi.getManagerCount.mockResolvedValueOnce({ data: { success: true, data: 1 } });
    render(<RouterProvider router={createMemoryRouter([{ path: "/", element: <ManagerDashboard /> }])} />);

    expect(await screen.findByTestId("single-admin-warning")).toBeInTheDocument();
    expect(screen.getByText(/Tek Yönetici Hesabı Tespit Edildi/i)).toBeInTheDocument();
  });

  it("sistemde birden fazla yönetici varsa Genel Bakış ekranında uyarı görünmez", async () => {
    api.membershipApi.getManagerCount.mockResolvedValueOnce({ data: { success: true, data: 2 } });
    render(<RouterProvider router={createMemoryRouter([{ path: "/", element: <ManagerDashboard /> }])} />);

    // Genel bakış yüklenene kadar bekle
    await screen.findByText(/Sistem/i);
    expect(screen.queryByTestId("single-admin-warning")).not.toBeInTheDocument();
  });
});

describe("ManagerDashboard — Site İçeriği sekmesi (T-063C)", () => {
  it("sekmeyi açar; başka sekmeye geçip dönünce taslak kaybolmaz ve içerik yeniden istenmez", async () => {
    // Site içeriği editörü sayfadan ayrılma engeli (`useBlocker`) için veri yönlendiricisi ister.
    render(<RouterProvider router={createMemoryRouter([{ path: "/", element: <ManagerDashboard /> }])} />);

    await userEvent.click(await screen.findByTestId("sidebar-tab-site_content"));
    await userEvent.type(await screen.findByLabelText("Kurum adı"), "Örnek Kurum");

    await userEvent.click(screen.getByTestId("sidebar-tab-overview"));
    await userEvent.click(screen.getByTestId("sidebar-tab-site_content"));

    expect(screen.getByLabelText("Kurum adı")).toHaveValue("Örnek Kurum");
    expect(api.siteContentApi.getFields).toHaveBeenCalledTimes(1);
  });
});
