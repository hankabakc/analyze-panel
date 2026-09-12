import { describe, expect, it } from "vitest";
import { fieldLabel, groupFields } from "./siteContentFields";

describe("siteContentFields", () => {
  it("fieldLabel: sabit, modül ve SSS alanlarına okunur ad verir; tanınmayan alanı anahtarıyla gösterir", () => {
    expect(fieldLabel("site.institutionName")).toBe("Kurum adı");
    expect(fieldLabel("features.studyPlan")).toBe("Çalışma planı — açıklama");
    expect(fieldLabel("faq.3.question")).toBe("Soru 3");
    expect(fieldLabel("faq.3.answer")).toBe("Cevap 3");
    expect(fieldLabel("yeni.alan")).toBe("yeni.alan");
  });

  it("groupFields: sunucudaki sırayı koruyarak gruplar; alt bilgi Genel'e, tanınmayan Diğer'e düşer", () => {
    const groups = groupFields([
      { key: "site.institutionName", maxLength: 100 },
      { key: "home.heroTitle", maxLength: 150 },
      { key: "footer.note", maxLength: 300 },
      { key: "yeni.alan", maxLength: 50 },
    ]);
    expect(groups.map((group) => group.title)).toEqual(["Genel", "Ana sayfa", "Diğer"]);
    expect(groups[0].fields.map((field) => field.key)).toEqual(["site.institutionName", "footer.note"]);
  });
});
