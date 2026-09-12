import { describe, expect, it } from "vitest";
import { faqItems, mailtoHref, SITE_FEATURES, telHref, text } from "./siteContent";

describe("siteContent yardımcıları", () => {
  it("text: kırpılmış değeri, alan yoksa boş metni döner", () => {
    expect(text({ a: "  merhaba \n" }, "a")).toBe("merhaba");
    expect(text({}, "yok")).toBe("");
  });

  it("faqItems: yalnızca sorusu dolu yuvaları sayısal yuva sırasıyla döner; cevap boş olabilir", () => {
    const items = faqItems({
      "faq.2.question": "İkinci?",
      "faq.2.answer": "Cevap 2",
      "faq.1.question": "Birinci?",
      "faq.1.answer": "",
      "faq.3.question": "   ",
      "faq.3.answer": "Sahipsiz cevap",
      "faq.10.question": "Onuncu?",
      "faq.10.answer": "Cevap 10",
    });
    expect(items).toEqual([
      { question: "Birinci?", answer: "" },
      { question: "İkinci?", answer: "Cevap 2" },
      { question: "Onuncu?", answer: "Cevap 10" },
    ]);
  });

  it("telHref: numarayı arama bağlantısına çevirir, numara değilse null döner", () => {
    expect(telHref("0 (212) 555 12 34")).toBe("tel:02125551234");
    expect(telHref("+90 212 555 1234")).toBe("tel:+902125551234");
    expect(telHref("Hafta içi arayın")).toBeNull();
  });

  it("mailtoHref: geçerli adres için bağlantı, değilse null döner", () => {
    expect(mailtoHref("bilgi@kurum.com.tr")).toBe("mailto:bilgi@kurum.com.tr");
    expect(mailtoHref("javascript:alert(1)")).toBeNull();
    expect(mailtoHref("adres yok")).toBeNull();
  });

  it("SITE_FEATURES: uygulamadaki yedi modülü benzersiz anahtarlarla listeler", () => {
    expect(SITE_FEATURES).toHaveLength(7);
    expect(new Set(SITE_FEATURES.map((feature) => feature.key)).size).toBe(7);
    SITE_FEATURES.forEach((feature) => expect(feature.key).toMatch(/^features\./));
  });
});
