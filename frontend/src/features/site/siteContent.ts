import { Brain, FileText, ListChecks, Target, TrendingUp, Trophy, Users } from "lucide-react";
import type { LucideIcon } from "lucide-react";

/**
 * siteContent: Karşılama sitesinin içerik yardımcıları (T-063B / S-026).
 *
 * İçerik sunucunun herkese açık ucundan anahtar → düz metin olarak gelir. Hangi anahtarların var
 * olduğunu ve uzunluk sınırlarını sunucu belirler (T-063A); burada yalnızca gösterim eşlemesi durur.
 */
export type SiteContent = Record<string, string>;

export interface SiteFeature {
  key: string;
  title: string;
  icon: LucideIcon;
}

/** Uygulamada gerçekten bulunan yedi modül (S-026): adlar sabit, açıklamalar doldurulabilir. */
export const SITE_FEATURES: readonly SiteFeature[] = [
  { key: "features.reportAnalysis", title: "Karne analizi", icon: FileText },
  { key: "features.progressTracking", title: "Gelişim dosyası ve öncelikli konular", icon: TrendingUp },
  { key: "features.goals", title: "Hedef okul ve puan karşılaştırması", icon: Target },
  { key: "features.studyPlan", title: "Çalışma planı", icon: ListChecks },
  { key: "features.ranking", title: "Sınıf içi sıralama", icon: Trophy },
  { key: "features.psychTests", title: "Psikolojik ölçekler", icon: Brain },
  { key: "features.management", title: "Kullanıcı ve sınıf yönetimi", icon: Users },
];

/** text: Alanın kırpılmış değeri; alan yoksa veya boşsa "". */
export function text(content: SiteContent, key: string): string {
  return (content[key] ?? "").trim();
}

export interface FaqItem {
  question: string;
  answer: string;
}

/**
 * faqItems: Sorusu doldurulmuş SSS yuvaları, yuva numarası sırasıyla. Cevap boş olabilir.
 * Yuva sayısı sunucudaki anahtarlardan okunur; burada sabit tutulmaz.
 */
export function faqItems(content: SiteContent): FaqItem[] {
  return Object.keys(content)
    .map((key) => /^faq\.(\d+)\.question$/.exec(key))
    .filter((match): match is RegExpExecArray => match !== null)
    .map((match) => Number(match[1]))
    .sort((a, b) => a - b)
    .map((slot) => ({
      question: text(content, `faq.${slot}.question`),
      answer: text(content, `faq.${slot}.answer`),
    }))
    .filter((item) => item.question !== "");
}

/** telHref: Telefon metnini arama bağlantısına çevirir; numara gibi görünmüyorsa null (düz metin gösterilir). */
export function telHref(phone: string): string | null {
  const dialable = phone.replace(/[^\d+]/g, "");
  return /^\+?\d{7,15}$/.test(dialable) ? `tel:${dialable}` : null;
}

/** mailtoHref: Geçerli görünen e-posta için posta bağlantısı; değilse null (düz metin gösterilir). */
export function mailtoHref(email: string): string | null {
  const address = email.trim();
  return /^[^\s@<>"'()]+@[^\s@<>"'()]+\.[^\s@<>"'()]+$/.test(address) ? `mailto:${address}` : null;
}

/** Oturumsuz ziyaretçiyi /giris sayfasına yönlendiren buton ve bağlantıların ortak adı (T-085). */
export const LOGIN_BUTTON_LABEL = "Öğrenci / Öğretmen Girişi";

/** Giriş formunun altındaki sabit kurumsal hesap bilgilendirme notu (T-085). */
export const LOGIN_ACCOUNT_NOTICE =
  "Hesabınız yok mu? Hesaplar kurum tarafından verilir; bu sayfadan hesap açılamaz.";

