/**
 * unsavedChanges: Kaydedilmemiş değişiklik koruması (T-063C / IST-02 §4.3).
 *
 * Adres değiştiren her gezinme (menü, logo, tarayıcının geri/ileri düğmesi) formun `useBlocker` engeliyle
 * onaya düşer; sekme kapatma ve yenileme için form `beforeunload` uyarısı kurar. Çıkış adres değiştirmeden
 * paneli kapattığı için bu iki yolun dışında kalır: çıkış düğmesi `confirmLeave()` ile sorar.
 *
 * ponytail: tek bayrak — aynı anda yalnızca bir form (site içeriği editörü) korunuyor; ikinci bir form
 * eklenirse bayrak form başına sayaca çevrilmeli.
 */
export const LEAVE_MESSAGE =
  "Kaydedilmemiş değişiklikleriniz var. Sayfadan ayrılırsanız kaybolacak. Devam etmek istiyor musunuz?";

let hasUnsavedChanges = false;

export function setUnsavedChanges(value: boolean) {
  hasUnsavedChanges = value;
}

/** confirmLeave: Kaydedilmemiş değişiklik yoksa true; varsa kullanıcıya sorar ve cevabını döner. */
export function confirmLeave(): boolean {
  return !hasUnsavedChanges || window.confirm(LEAVE_MESSAGE);
}
