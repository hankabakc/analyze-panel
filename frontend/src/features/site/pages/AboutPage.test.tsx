import { describe, expect, it } from "vitest";
import { screen } from "@testing-library/react";
import AboutPage from "./AboutPage";
import { ready, renderWithSiteContent } from "@/test/renderWithSite";

describe("AboutPage", () => {
  it("dolu bölümleri gösterir, boş bölümün başlığını çizmez", () => {
    renderWithSiteContent(<AboutPage />, ready({ "about.body": "Kurum hakkında metin", "about.mission": "Misyon metni" }));
    expect(screen.getByText("Kurum hakkında metin")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Misyonumuz" })).toBeInTheDocument();
    expect(screen.getByText("Misyon metni")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Vizyonumuz" })).not.toBeInTheDocument();
  });

  it("bütün alanlar boşsa içeriğin henüz eklenmediğini söyler", () => {
    renderWithSiteContent(<AboutPage />, ready({ "about.body": "   " }));
    expect(screen.getByText("Bu sayfanın içeriği henüz eklenmedi.")).toBeInTheDocument();
  });
});
