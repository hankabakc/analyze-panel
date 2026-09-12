import { describe, expect, it } from "vitest";
import { screen } from "@testing-library/react";
import PrivacyPage from "./PrivacyPage";
import { ready, renderWithSiteContent } from "@/test/renderWithSite";

describe("PrivacyPage", () => {
  it("metni satır sonlarını koruyarak ve HTML'i çalıştırmadan düz metin olarak gösterir", () => {
    const body = "Birinci paragraf.\nİkinci paragraf.\n<script>alert(1)</script>";
    renderWithSiteContent(<PrivacyPage />, ready({ "privacy.body": body }));
    const paragraph = screen.getByText(/Birinci paragraf\./);
    expect(paragraph.textContent).toBe(body);
    expect(paragraph).toHaveClass("whitespace-pre-line");
    expect(document.querySelector("script")).toBeNull();
  });

  it("metin boşsa içeriğin henüz eklenmediğini söyler", () => {
    renderWithSiteContent(<PrivacyPage />, ready({}));
    expect(screen.getByRole("heading", { level: 1, name: "Gizlilik ve KVKK" })).toBeInTheDocument();
    expect(screen.getByText("Bu sayfanın içeriği henüz eklenmedi.")).toBeInTheDocument();
  });
});
