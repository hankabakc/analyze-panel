import type { SiteContentFieldDto } from "@/lib/api";
import { SITE_FEATURES } from "../siteContent";

/**
 * siteContentFields: Yönetici editöründe alanların adı ve grubu (T-063C / S-026).
 *
 * Hangi alanların var olduğu ve sınırları sunucudan gelir (`/site-content/fields`). Burada yalnızca
 * okunur ad ve gruplama durur; sunucu yeni bir alan eklerse editör onu anahtarıyla "Diğer" altında gösterir.
 */
const FIXED_LABELS: Record<string, string> = {
  "site.institutionName": "Kurum adı",
  "home.heroTitle": "Karşılama başlığı",
  "home.heroSubtitle": "Karşılama alt başlığı",
  "home.intro": "Tanıtım metni",
  "about.body": "Hakkında metni",
  "about.mission": "Misyon",
  "about.vision": "Vizyon",
  "contact.address": "Adres",
  "contact.phone": "Telefon",
  "contact.email": "E-posta",
  "contact.hours": "Çalışma saatleri",
  "contact.note": "İletişim notu",
  "privacy.body": "Gizlilik ve KVKK metni",
  "footer.note": "Alt bilgi notu",
};

const FEATURE_LABELS: Record<string, string> = Object.fromEntries(
  SITE_FEATURES.map((feature) => [feature.key, `${feature.title} — açıklama`]),
);

const GROUP_TITLES: Record<string, string> = {
  site: "Genel",
  footer: "Genel",
  home: "Ana sayfa",
  features: "Özellikler",
  about: "Hakkında",
  faq: "Sıkça sorulan sorular",
  contact: "İletişim",
  privacy: "Gizlilik ve KVKK",
};

/** fieldLabel: Alanın editörde görünen adı; tanınmayan alan anahtarıyla gösterilir. */
export function fieldLabel(key: string): string {
  if (FIXED_LABELS[key]) return FIXED_LABELS[key];
  if (FEATURE_LABELS[key]) return FEATURE_LABELS[key];
  const faq = /^faq\.(\d+)\.(question|answer)$/.exec(key);
  if (faq) return `${faq[2] === "question" ? "Soru" : "Cevap"} ${faq[1]}`;
  return key;
}

export interface FieldGroup {
  title: string;
  fields: SiteContentFieldDto[];
}

/** groupFields: Alanları sunucudaki sırayı koruyarak gruplara ayırır. */
export function groupFields(fields: SiteContentFieldDto[]): FieldGroup[] {
  const groups: FieldGroup[] = [];
  for (const field of fields) {
    const title = GROUP_TITLES[field.key.split(".")[0]] ?? "Diğer";
    let group = groups.find((existing) => existing.title === title);
    if (!group) {
      group = { title, fields: [] };
      groups.push(group);
    }
    group.fields.push(field);
  }
  return groups;
}
