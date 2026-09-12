import { afterEach, describe, expect, it, vi } from "vitest";
import { confirmLeave, setUnsavedChanges } from "./unsavedChanges";

afterEach(() => {
  setUnsavedChanges(false);
  vi.restoreAllMocks();
});

describe("unsavedChanges", () => {
  it("değişiklik yokken onay sormadan geçişe izin verir", () => {
    const confirm = vi.spyOn(window, "confirm");
    expect(confirmLeave()).toBe(true);
    expect(confirm).not.toHaveBeenCalled();
  });

  it("değişiklik varken onay sorar ve kullanıcının cevabını döner", () => {
    const confirm = vi.spyOn(window, "confirm").mockReturnValueOnce(false).mockReturnValueOnce(true);
    setUnsavedChanges(true);
    expect(confirmLeave()).toBe(false);
    expect(confirmLeave()).toBe(true);
    expect(confirm).toHaveBeenCalledTimes(2);
  });
});
