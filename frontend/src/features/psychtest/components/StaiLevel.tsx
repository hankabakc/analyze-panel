import { Info } from "lucide-react";

/**
 * StaiLevelBadge: Sunucudan gelen kaygı seviyesine göre renkli rozet çizer (T-061 / S-024 / T-061B / T-062).
 * APP-01 §2.1: İstemcide puan karşılaştırması yapılmaz, yalnızca sunucunun seviye alanı kullanılır.
 * ENG-01 (DRY): Ortak seviye rozeti bileşeni.
 */
export function StaiLevelBadge({
  level,
  testIdPrefix = "stai-level",
}: {
  level?: "LOW" | "MEDIUM" | "HIGH" | null;
  testIdPrefix?: string;
}) {
  if (!level) return null;
  switch (level) {
    case "LOW":
      return (
        <span
          data-testid={`${testIdPrefix}-low`}
          className="px-2 py-0.5 rounded-md bg-emerald-50 text-emerald-700 border border-emerald-200 text-xs font-bold"
        >
          Düşük
        </span>
      );
    case "MEDIUM":
      return (
        <span
          data-testid={`${testIdPrefix}-medium`}
          className="px-2 py-0.5 rounded-md bg-amber-50 text-amber-700 border border-amber-200 text-xs font-bold"
        >
          Orta
        </span>
      );
    case "HIGH":
      return (
        <span
          data-testid={`${testIdPrefix}-high`}
          className="px-2 py-0.5 rounded-md bg-rose-50 text-rose-700 border border-rose-200 text-xs font-bold"
        >
          Yüksek
        </span>
      );
    default:
      return null;
  }
}

interface StaiExplanationNoteProps {
  variant?: "blue" | "purple" | "neutral";
  className?: string;
}

/**
 * StaiExplanationContent: Şartnamedeki standart sabit STAI açıklama metni ve seviye açıklamaları içeriği.
 * ENG-01 (DRY): Açıklama metni tek bir yerde tanımlanır; varyantlar yalnızca dış çerçeveyi değiştirir.
 */
function StaiExplanationContent({
  dividerClass = "border-slate-100",
  textColor = "text-slate-600",
}: {
  dividerClass?: string;
  textColor?: string;
}) {
  return (
    <div className={`space-y-1.5 leading-relaxed ${textColor}`}>
      <p>
        <strong className="text-slate-700">Durumluk:</strong> Öğrencinin testi doldurduğu andaki kaygısı; o güne göre değişebilir.{" "}
        <strong className="text-slate-700">Sürekli:</strong> Öğrencinin genel kaygı eğilimi; daha kalıcıdır. Puan aralığı 20–80.
      </p>
      <div className={`flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] pt-1.5 border-t ${dividerClass} text-slate-500`}>
        <span>
          <strong className="text-emerald-700">Düşük:</strong> Kaygı düzeyi düşük.
        </span>
        <span>•</span>
        <span>
          <strong className="text-amber-700">Orta:</strong> Kaygı düzeyi orta; sınav döneminde takip edilebilir.
        </span>
        <span>•</span>
        <span>
          <strong className="text-rose-700">Yüksek:</strong> Kaygı düzeyi yüksek; rehber öğretmenle görüşülmesi önerilir.
        </span>
      </div>
    </div>
  );
}

/**
 * StaiExplanationNote: Şartnamedeki standart sabit STAI açıklama kartı (T-061 / T-061B / T-062).
 * ENG-01 (DRY): Öğretmen ve yönetici ekranlarında kullanılan ortak bilgilendirme kartı.
 */
export function StaiExplanationNote({
  variant = "blue",
  className = "",
}: StaiExplanationNoteProps) {
  if (variant === "purple") {
    return (
      <div
        data-testid="stai-explanation-note"
        className={`mt-6 pt-6 border-t border-slate-100 flex items-start gap-3 text-slate-600 text-xs ${className}`}
      >
        <Info className="h-4 w-4 text-purple-600 shrink-0 mt-0.5" />
        <StaiExplanationContent dividerClass="border-slate-100" />
      </div>
    );
  }

  // Varsayılan / blue varyantı (Öğretmen paneli stili)
  return (
    <div
      data-testid="stai-explanation-note"
      className={`p-3.5 rounded-xl bg-blue-50/50 border border-blue-100 text-xs text-blue-900 flex items-start gap-2.5 ${className}`}
    >
      <Info className="h-4 w-4 text-blue-600 shrink-0 mt-0.5" />
      <StaiExplanationContent dividerClass="border-blue-100" />
    </div>
  );
}
