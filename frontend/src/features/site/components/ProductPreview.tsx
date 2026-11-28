/**
 * ProductPreview: Karşılama sayfasında panelin küçük bir kesiti (öğretmenin analiz ekranı).
 *
 * Dekoratiftir: sayılar örnektir, hiçbir öğrenciye ait değildir ve ekran okuyuculardan gizlenir.
 * Gerçek ekran görüntüsü yerine HTML ile çizilir; böylece tema değişince kendiliğinden uyar.
 */
const NETS = [62.3, 66.0, 64.7, 70.3, 71.0, 74.7, 73.3, 78.0];

const TOPICS = [
  { lesson: "Fen Bilimleri", topic: "Basınç", rate: 67, lost: 8.9 },
  { lesson: "Matematik", topic: "Üslü ifadeler", rate: 75, lost: 8.9 },
  { lesson: "Türkçe", topic: "Paragrafta anlatım", rate: 80, lost: 4.4 },
  { lesson: "İnkılap Tarihi", topic: "Mondros Ateşkesi", rate: 80, lost: 4.4 },
];

function NetChart() {
  const w = 520;
  const h = 150;
  const min = 55;
  const max = 85;
  const x = (i: number) => (i / (NETS.length - 1)) * w;
  const y = (v: number) => h - ((v - min) / (max - min)) * h;
  const line = NETS.map((v, i) => `${i === 0 ? "M" : "L"}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(" ");
  return (
    <svg viewBox={`0 0 ${w} ${h + 4}`} className="h-36 w-full" preserveAspectRatio="none">
      {[0, 1, 2].map((g) => (
        <line key={g} x1="0" x2={w} y1={(h / 2) * g + 1} y2={(h / 2) * g + 1} stroke="#e7e7ea" strokeWidth="1" />
      ))}
      <path d={`${line} L${w},${h} L0,${h} Z`} fill="#3e5aa8" opacity="0.07" />
      <path d={line} fill="none" stroke="#3e5aa8" strokeWidth="2" vectorEffect="non-scaling-stroke" />
      {NETS.map((v, i) => (
        <circle key={i} cx={x(i)} cy={y(v)} r="2.5" fill="#ffffff" stroke="#3e5aa8" strokeWidth="1.5" vectorEffect="non-scaling-stroke" />
      ))}
    </svg>
  );
}

export function ProductPreview() {
  return (
    <div aria-hidden="true" className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-[0_24px_60px_-30px_rgba(24,24,27,0.35)]">
      <div className="flex items-center gap-2 border-b border-slate-200 bg-slate-50 px-4 py-2.5">
        <span className="h-2.5 w-2.5 rounded-full bg-slate-300" />
        <span className="h-2.5 w-2.5 rounded-full bg-slate-300" />
        <span className="h-2.5 w-2.5 rounded-full bg-slate-300" />
        <span className="ml-3 text-xs text-slate-500">Gelişim dosyası · 8-A</span>
      </div>

      <div className="grid grid-cols-2 border-b border-slate-200 sm:grid-cols-4">
        {[
          ["Net ortalaması", "71,2", "+4,6"],
          ["Tahmini puan", "≈416", "+20"],
          ["Deneme", "8", ""],
          ["Hedefe kalan", "≈9 net", ""],
        ].map(([label, value, delta], i) => (
          <div key={label} className={`px-5 py-4 ${i > 0 ? "sm:border-l" : ""} ${i % 2 === 1 ? "border-l" : ""} border-slate-200`}>
            <p className="text-xs text-slate-500">{label}</p>
            <p className="mt-1 text-xl font-semibold text-slate-900">
              {value}
              {delta && <span className="ml-2 text-xs font-medium text-emerald-600">{delta}</span>}
            </p>
          </div>
        ))}
      </div>

      <div className="grid lg:grid-cols-[1.4fr_1fr]">
        <div className="p-5">
          <p className="text-xs font-medium text-slate-500">Toplam net, deneme sırasıyla</p>
          <div className="mt-3">
            <NetChart />
          </div>
        </div>
        <div className="border-t border-slate-200 p-5 lg:border-l lg:border-t-0">
          <div className="flex items-center justify-between text-xs text-slate-500">
            <span className="font-medium">Önce şu konular</span>
            <span>Kaçan puan</span>
          </div>
          <ul className="mt-3 space-y-2.5">
            {TOPICS.map((t) => (
              <li key={t.topic} className="relative overflow-hidden rounded-md">
                <span className="absolute inset-y-0 left-0 bg-accent-50" style={{ width: `${(t.lost / 8.9) * 100}%` }} />
                <span className="relative flex items-center justify-between gap-3 px-2.5 py-1.5 text-sm">
                  <span className="min-w-0 truncate">
                    <span className="text-slate-900">{t.topic}</span>
                    <span className="ml-2 text-xs text-slate-400">{t.lesson}</span>
                  </span>
                  <span className="shrink-0 font-mono text-xs text-slate-700">≈{t.lost.toFixed(1)}</span>
                </span>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  );
}
