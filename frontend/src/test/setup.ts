import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// Her testten sonra DOM temizlenir; testler birbirinin çıktısını görmez (IST-08 §1.1).
afterEach(() => {
  cleanup();
});

// Veri yönlendiricisi her gezinmede bir Request oluşturur. jsdom'un AbortSignal'ı Node'un Request'ine (undici)
// yabancı olduğundan kurucu hata verir; testlerde sinyal düşürülür.
// ponytail: yükleyici (loader) kullanılmadığı için iptal sinyali gerekmiyor; loader eklenirse sinyal taşınmalı.
const NodeRequest = globalThis.Request;
globalThis.Request = class extends NodeRequest {
  constructor(input: RequestInfo | URL, init?: RequestInit) {
    super(input, init && { ...init, signal: null });
  }
} as typeof Request;
