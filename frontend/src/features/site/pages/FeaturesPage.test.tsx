import { describe, expect, it } from "vitest";
import { screen } from "@testing-library/react";
import FeaturesPage from "./FeaturesPage";
import { ready, renderWithSiteContent } from "@/test/renderWithSite";
import { SITE_FEATURES } from "../siteContent";

describe("FeaturesPage", () => {
  it("yedi modülün her birini sırasıyla başlık olarak gösterir", () => {
    renderWithSiteContent(<FeaturesPage />, ready({}));
    expect(screen.getByRole("heading", { level: 1, name: "Özellikler" })).toBeInTheDocument();
    const titles = screen.getAllByRole("heading", { level: 2 }).map((heading) => heading.textContent);
    expect(titles).toEqual(SITE_FEATURES.map((feature) => feature.title));
  });

  it("yalnızca doldurulmuş açıklamayı gösterir", () => {
    renderWithSiteContent(<FeaturesPage />, ready({ "features.ranking": "Sıralama açıklaması" }));
    expect(screen.getByText("Sıralama açıklaması")).toBeInTheDocument();
    const withDescription = screen.getAllByRole("listitem").filter((item) => item.querySelector("p"));
    expect(withDescription).toHaveLength(1);
  });
});
