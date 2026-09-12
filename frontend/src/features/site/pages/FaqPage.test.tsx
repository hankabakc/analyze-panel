import { describe, expect, it } from "vitest";
import { screen } from "@testing-library/react";
import FaqPage from "./FaqPage";
import { ready, renderWithSiteContent } from "@/test/renderWithSite";

describe("FaqPage", () => {
  it("sorusu dolu yuvaları sırasıyla açılır-kapanır öğe olarak gösterir", () => {
    renderWithSiteContent(
      <FaqPage />,
      ready({
        "faq.2.question": "İkinci soru",
        "faq.2.answer": "İkinci cevap",
        "faq.1.question": "Birinci soru",
        "faq.1.answer": "",
        "faq.3.question": "",
        "faq.3.answer": "Sahipsiz cevap",
      }),
    );
    const questions = screen.getAllByText(/soru$/).map((element) => element.textContent);
    expect(questions).toEqual(["Birinci soru", "İkinci soru"]);
    expect(screen.getByText("İkinci cevap")).toBeInTheDocument();
    expect(screen.queryByText("Sahipsiz cevap")).not.toBeInTheDocument();
    expect(document.querySelectorAll("details")).toHaveLength(2);
  });

  it("hiç soru yoksa içeriğin henüz eklenmediğini söyler", () => {
    renderWithSiteContent(<FaqPage />, ready({ "faq.1.answer": "Sorusuz cevap" }));
    expect(screen.getByText("Bu sayfanın içeriği henüz eklenmedi.")).toBeInTheDocument();
  });
});
