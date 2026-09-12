import { useState } from "react";
import type { PriorityLists, PriorityTopic } from "@/lib/types";

/**
 * PriorityTopics: Öğrencinin önce çalışması gereken konuları, kanıtıyla birlikte sıralar (T-023 / T-029 / T-030).
 *
 * <p>Tasarım kararı: bu liste eskiden sunucudan gelen düz metin bloğu olarak basılıyordu
 * ("Mentor Tavsiyeleri"). Oysa içeriği aritmetik — ders, konu, soru sayısı, doğru sayısı.
 * Metin hâlinde öğretmen "hangisi daha acil" sorusunu okuyarak çözmek zorundaydı.</p>
 *
 * <p>Numaralandırma burada süs değil: liste gerçek bir öncelik sırasıdır, sıra bilgi taşır.</p>
 *
 * <p>T-030: Öğretmen listeyi hem "Puana Göre" (en çok puan kazandıracak kaçırılan sorular)
 * hem de "Orana Göre" (başarı yüzdesi en düşük konular) görebilir. Her iki liste de sunucuda
 * sıralanıp ilk 5'e kesilmiş olarak gelir (APP-01 §2.1). İstemci yalnızca seçilen listeyi gösterir;
 * yeni ağ isteği veya yerel sıralama hesabı yapılmaz.</p>
 *
 * <p>Her satırın barı <b>soru sayısı kadar uzundur</b>. Yüzde tek başına yanıltıcıdır:
 * 2 soruda 1 doğru da %50'dir, 10 soruda 5 doğru da. İkincisi beş kat fazla net kaybettirir
 * ve bar bunu tek bakışta gösterir.</p>
 *
 * <p>Erişilebilirlik (IST-02 §3.2, §3.3): sıralama düğmeleri `aria-pressed` ile hangi sıralamanın
 * etkin olduğunu ekran okuyucuya bildirir; bilgi yalnızca renge bırakılmaz.
 * Doğru/yanlış/boş ayrımı da yalnızca renge bırakılmaz;
 * her segmentin sayısı ayrıca yazıyla verilir. Sıralama seçici klavyeyle kullanılabilir ve
 * görünür odak göstergesi taşır (focus-visible:ring-2 focus-visible:ring-cyan-500).</p>
 */

/** Doğrulanmış durum renkleri — kırmızı/yeşil çakışmasını önlemek için doğru cyan'dır. */
const RENK = {
  dogru: "#0891b2",
  yanlis: "#be123c",
  bos: "#94a3b8",
} as const;

export type SortMode = "points" | "rate";

interface PriorityTopicsProps {
  priorityLists?: PriorityLists;
  /** Satıra tıklandığında o konunun ders listesindeki kartına atlar. */
  onSelect?: (lessonName: string, topicName: string) => void;
  /**
   * Ortada gösterilecek analiz verisi var mı? Boş liste iki farklı şey anlatabilir:
   * "veri var, eksik konu yok" ile "hiç veri yok". İkisine aynı mesajı vermek
   * kullanıcıya yalan söyler (T-025 denetiminde çıktı: gelişim dosyasında onaylı
   * rapor yokken ekran "her konuda tam başarı var" diyordu).
   */
  hasData?: boolean;
}

export function PriorityTopics({ priorityLists, onSelect, hasData = true }: PriorityTopicsProps) {
  const [sortMode, setSortMode] = useState<SortMode>("points");

  // T-030: Sunucudan hazır sıralanmış ve 5 elemanla kesilmiş gelen liste doğrudan seçilir (APP-01 §2.1)
  const currentTopics: PriorityTopic[] =
    (sortMode === "points" ? priorityLists?.byPoints : priorityLists?.byRate) || [];

  const hasAnyTopics =
    (priorityLists?.byPoints && priorityLists.byPoints.length > 0) ||
    (priorityLists?.byRate && priorityLists.byRate.length > 0);

  if (!hasAnyTopics) {
    return (
      <div className="rounded-[2rem] bg-slate-50 border border-slate-100 p-10 text-center">
        <p className="text-sm font-black text-slate-700">
          {hasData ? "Eksik konu çıkmadı." : "Gösterilecek analiz yok."}
        </p>
        <p className="text-[11px] font-bold text-slate-400 mt-2">
          {hasData
            ? "Bu karnede her konuda tam başarı var. Bir sonraki denemeye geçebilirsiniz."
            : "Onaylanmış bir karne olduğunda öncelikli konular burada sıralanır."}
        </p>
      </div>
    );
  }

  // Bar ölçeği İKİ listenin birden en geniş konusuna göre alınır (T-030D / B-70). Yalnızca
  // görüntülenen listeye bakılsaydı aynı konu iki listede farklı genişlikte çizilirdi ve
  // sıralamayı değiştirmek barları karşılaştırılamaz hale getirirdi.
  const tumKonular = [...(priorityLists?.byPoints ?? []), ...(priorityLists?.byRate ?? [])];
  const enFazlaSoru = tumKonular.length > 0 ? Math.max(...tumKonular.map((t) => t.totalQuestions)) : 1;

  return (
    <div className="space-y-4">
      {/* Sıralama Seçici (Puana Göre / Orana Göre) */}
      <div className="flex items-center justify-between gap-3 pb-1">
        <span className="text-[10px] font-black uppercase tracking-wider text-slate-400">
          {sortMode === "points" ? "Öncelik: Puan Kaybı" : "Öncelik: Başarı Oranı"}
        </span>
        <div
          className="flex bg-slate-100 p-1 rounded-xl border border-slate-200"
          role="group"
          aria-label="Öncelik sıralama ölçütü"
        >
          <button
            type="button"
            onClick={() => setSortMode("points")}
            aria-pressed={sortMode === "points"}
            className={`tap-44 px-3 py-1.5 rounded-lg text-[10px] font-black uppercase tracking-wider transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-500 focus-visible:ring-offset-1 ${
              sortMode === "points"
                ? "bg-white text-slate-900 shadow-sm"
                : "text-slate-500 hover:text-slate-800"
            }`}
          >
            Puana Göre
          </button>
          <button
            type="button"
            onClick={() => setSortMode("rate")}
            aria-pressed={sortMode === "rate"}
            className={`tap-44 px-3 py-1.5 rounded-lg text-[10px] font-black uppercase tracking-wider transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-500 focus-visible:ring-offset-1 ${
              sortMode === "rate"
                ? "bg-white text-slate-900 shadow-sm"
                : "text-slate-500 hover:text-slate-800"
            }`}
          >
            Orana Göre
          </button>
        </div>
      </div>

      <ol className="space-y-3">
        {currentTopics.map((t, i) => {
          const oran = Math.round(t.successRate);
          const genislik = (t.totalQuestions / enFazlaSoru) * 100;
          return (
            <li key={`${t.lessonName}-${t.topicName}`}>
             <button
              type="button"
              onClick={() => onSelect?.(t.lessonName, t.topicName)}
              disabled={!onSelect}
              className="w-full text-left rounded-[1.75rem] bg-white border border-slate-100 enabled:hover:border-cyan-500 enabled:cursor-pointer transition-colors p-5 flex items-start gap-5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-500 focus-visible:ring-offset-2"
             >
              {/* Sıra numarası: liste gerçek bir öncelik sırası olduğu için bilgi taşır */}
              <span
                aria-hidden="true"
                className="shrink-0 w-9 h-9 rounded-xl bg-slate-900 text-white flex items-center justify-center text-xs font-black tabular-nums"
              >
                {i + 1}
              </span>

              <div className="min-w-0 flex-1 space-y-2.5">
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0 space-y-1">
                    <p className="text-[9px] font-black uppercase tracking-[0.2em] text-slate-400 truncate">
                      {t.lessonName}
                    </p>
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="font-black text-sm text-slate-900 leading-snug">{t.topicName}</p>
                      {t.chronic && (
                        <span className="inline-flex items-center px-2 py-0.5 rounded-md text-[10px] font-black uppercase tracking-wider bg-rose-50 text-rose-700 border border-rose-200">
                          Son 2 denemede de yanlış
                        </span>
                      )}
                    </div>
                  </div>
                  <div className="shrink-0 text-right space-y-0.5">
                    <span className="block text-sm font-black tabular-nums text-slate-900">%{oran}</span>
                    {/* B-60: İstemcideki gömülü sabit hesap yedeği kaldırıldı; puan sunucudan geliyorsa gösterilir (IST-02 §2) */}
                    {t.lostPoints != null && (
                      <span className="block text-[11px] font-black tabular-nums text-rose-600">
                        ≈{t.lostPoints.toFixed(1)} puan
                      </span>
                    )}
                  </div>
                </div>

                {/* Kanıt barı: uzunluk = soru sayısı, segmentler = doğru / yanlış / boş */}
                <div className="flex items-center gap-3">
                  <div className="flex-1 min-w-0">
                    <div
                      className="flex h-2.5 rounded-full overflow-hidden bg-slate-50"
                      style={{ width: `${genislik}%` }}
                      role="img"
                      aria-label={`${t.totalQuestions} soru: ${t.correctCount} doğru, ${t.wrongCount} yanlış, ${t.emptyCount} boş`}
                    >
                      {t.correctCount > 0 && (
                        <span style={{ flex: t.correctCount, background: RENK.dogru }} />
                      )}
                      {t.wrongCount > 0 && (
                        <span
                          style={{ flex: t.wrongCount, background: RENK.yanlis, marginLeft: 2 }}
                        />
                      )}
                      {t.emptyCount > 0 && (
                        <span style={{ flex: t.emptyCount, background: RENK.bos, marginLeft: 2 }} />
                      )}
                    </div>
                  </div>
                  <p className="shrink-0 text-[10px] font-bold text-slate-500 tabular-nums">
                    {t.totalQuestions} soruda {t.correctCount} doğru
                  </p>
                </div>
              </div>
             </button>
            </li>
          );
        })}
      </ol>

      {/* Renk tek taşıyıcı değil: her segmentin sayısı satır içinde ayrıca yazılı */}
      <div className="flex flex-wrap items-center gap-x-5 gap-y-2 pt-1 pl-1">
        {[
          { ad: "Doğru", renk: RENK.dogru },
          { ad: "Yanlış", renk: RENK.yanlis },
          { ad: "Boş", renk: RENK.bos },
        ].map((s) => (
          <span key={s.ad} className="flex items-center gap-2">
            <span className="w-3 h-2.5 rounded-sm" style={{ background: s.renk }} aria-hidden="true" />
            <span className="text-[10px] font-black uppercase tracking-widest text-slate-500">{s.ad}</span>
          </span>
        ))}
        <span className="text-[10px] font-bold text-slate-400">
          Bar uzunluğu konudaki soru sayısını gösterir.
        </span>
      </div>
    </div>
  );
}
