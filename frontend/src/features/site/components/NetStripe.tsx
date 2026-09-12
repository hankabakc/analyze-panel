/**
 * NetStripe: Karnenin dili — doğru / yanlış / boş payları (T-063B imza öğesi).
 *
 * Dekoratiftir, veri taşımaz: oranlar sabittir, hiçbir öğrenciye veya sınava ait değildir ve
 * ekran okuyuculardan gizlenir. Açılış hareketi hareket azaltma tercihinde çalışmaz (index.css).
 */
const ROWS: ReadonlyArray<readonly [number, number, number]> = [
  [68, 20, 12],
  [54, 31, 15],
  [63, 25, 12],
  [79, 11, 10],
  [72, 16, 12],
  [58, 22, 20],
];

const LEGEND = [
  { label: "Doğru", swatch: "bg-cyan-500" },
  { label: "Yanlış", swatch: "bg-slate-900" },
  { label: "Boş", swatch: "bg-slate-200" },
];

export function NetStripe() {
  return (
    <figure
      aria-hidden="true"
      className="rounded-[2rem] border border-slate-200 bg-white p-6 shadow-xl shadow-slate-200/60 sm:p-8"
    >
      <div className="space-y-3">
        {ROWS.map(([correct, wrong, blank], row) => (
          <div key={row} className="flex h-3.5 overflow-hidden rounded-full bg-slate-100">
            <span className="net-segment bg-cyan-500" style={{ width: `${correct}%`, animationDelay: `${row * 90}ms` }} />
            <span className="net-segment bg-slate-900" style={{ width: `${wrong}%`, animationDelay: `${row * 90 + 60}ms` }} />
            <span className="net-segment bg-slate-200" style={{ width: `${blank}%`, animationDelay: `${row * 90 + 120}ms` }} />
          </div>
        ))}
      </div>
      <figcaption className="mt-6 flex flex-wrap gap-x-5 gap-y-2 text-[10px] font-black uppercase tracking-[0.25em] text-slate-500">
        {LEGEND.map((item) => (
          <span key={item.label} className="inline-flex items-center gap-2">
            <span className={`h-2.5 w-2.5 rounded-full ${item.swatch}`} />
            {item.label}
          </span>
        ))}
      </figcaption>
    </figure>
  );
}
