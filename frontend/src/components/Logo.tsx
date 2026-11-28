/**
 * Logo: Ürünün işaretinden (deneme şeridi: doğru / yanlış / boş) türetilen yalın marka.
 * Panel ve karşılama sitesi aynı bileşeni kullanır.
 */
export function LogoMark({ className = "h-7 w-7" }: { className?: string }) {
  return (
    <svg viewBox="0 0 28 28" className={className} aria-hidden="true">
      <rect width="28" height="28" rx="7" fill="#18181b" />
      <rect x="6" y="8" width="16" height="3" rx="1.5" fill="#ffffff" />
      <rect x="6" y="12.5" width="11" height="3" rx="1.5" fill="#a3b3e0" />
      <rect x="6" y="17" width="6" height="3" rx="1.5" fill="#71717a" />
    </svg>
  );
}

export function Logo({ institution }: { institution?: string }) {
  return (
    <span className="flex min-w-0 items-center gap-2.5">
      <LogoMark />
      <span className="min-w-0 leading-tight">
        <span className="block whitespace-nowrap text-[15px] font-semibold tracking-tight text-slate-900">
          Analiz Sistemi
        </span>
        {institution && <span className="block truncate text-xs text-slate-500">{institution}</span>}
      </span>
    </span>
  );
}
