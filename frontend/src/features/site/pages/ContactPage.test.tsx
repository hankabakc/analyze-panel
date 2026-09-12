import { describe, expect, it } from "vitest";
import { screen } from "@testing-library/react";
import ContactPage from "./ContactPage";
import { ready, renderWithSiteContent } from "@/test/renderWithSite";

describe("ContactPage", () => {
  it("telefonu ve e-postayı bağlantı, adresi metin olarak gösterir; form yoktur", () => {
    const { container } = renderWithSiteContent(
      <ContactPage />,
      ready({
        "contact.address": "Örnek Mah. 1\nİstanbul",
        "contact.phone": "0 212 555 12 34",
        "contact.email": "bilgi@kurum.com.tr",
        "contact.hours": "09:00 - 18:00",
      }),
    );
    expect(screen.getByRole("link", { name: "0 212 555 12 34" })).toHaveAttribute("href", "tel:02125551234");
    expect(screen.getByRole("link", { name: "bilgi@kurum.com.tr" })).toHaveAttribute("href", "mailto:bilgi@kurum.com.tr");
    expect(screen.getByText(/Örnek Mah\. 1/)).toBeInTheDocument();
    expect(screen.getByText("09:00 - 18:00")).toBeInTheDocument();
    expect(container.querySelector("form")).toBeNull();
  });

  it("geçersiz e-postayı bağlantısız düz metin olarak gösterir", () => {
    renderWithSiteContent(<ContactPage />, ready({ "contact.email": "javascript:alert(1)" }));
    expect(screen.getByText("javascript:alert(1)")).toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });

  it("boş alanları çizmez; hepsi boşsa içeriğin henüz eklenmediğini söyler", () => {
    renderWithSiteContent(<ContactPage />, ready({}));
    expect(screen.getByText("Bu sayfanın içeriği henüz eklenmedi.")).toBeInTheDocument();
    expect(screen.queryByText("Telefon")).not.toBeInTheDocument();
  });
});
