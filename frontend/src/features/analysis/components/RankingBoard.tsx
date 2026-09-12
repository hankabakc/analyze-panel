import { useEffect, useState } from "react";
import { analysisApi } from "@/lib/api";
import type { ClassRankingGroup } from "@/lib/types";
import { Card } from "@/components/ui/card";
import { useAuth } from "@/lib/auth-context";

/**
 * RankingBoard: Denemelere göre sınıf bazlı sıralama tablosu (T-038 / T-038A / T-038B).
 *
 * <p>Bu bileşen <b>maskeleme veya gruplama yapmaz.</b> Gruplar ve gösterilecek adlar sunucuda
 * kararlaştırılır ve buraya hazır gelir (ENG-11 §3.3 / APP-02 §1):
 * - Öğrenci yalnızca kendi sınıf grubunu alır; başka sınıfın verisi ağ yanıtında hiç yer almaz.
 * - Öğretmen ve yönetici tüm sınıfları ayrı bölümler halinde görür (T-038A/B).
 * - Sınıfsız öğrenciler "Sınıfsız" grubu altında gösterilir.</p>
 */
export function RankingBoard() {
  const { user } = useAuth();
  const [groups, setGroups] = useState<ClassRankingGroup[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [hata, setHata] = useState(false);

  useEffect(() => {
    let iptal = false;
    analysisApi
      .getRanking()
      .then((res) => { if (!iptal) setGroups(res.data.data || []); })
      .catch(() => { if (!iptal) setHata(true); })
      .finally(() => { if (!iptal) setIsLoading(false); });
    return () => { iptal = true; };
  }, []);

  if (isLoading) {
    return (
      <Card className="rounded-[2rem] border-0 shadow-lg bg-white p-10 text-center">
        <p className="text-[11px] font-black uppercase tracking-widest text-slate-400">Sıralama yükleniyor…</p>
      </Card>
    );
  }

  if (hata) {
    return (
      <Card className="rounded-[2rem] border-0 shadow-lg bg-white p-10 text-center">
        <p className="text-sm font-black text-slate-700">Sıralama getirilemedi.</p>
      </Card>
    );
  }

  const toplamKatilimci = groups.reduce((acc, g) => acc + g.entries.length, 0);

  if (groups.length === 0 || toplamKatilimci === 0) {
    return (
      <Card className="rounded-[2rem] border-0 shadow-lg bg-white p-10 text-center">
        <p className="text-sm font-black text-slate-700">Henüz sıralama oluşmadı.</p>
        <p className="text-[11px] font-bold text-slate-400 mt-2">
          Sıralama yalnızca onaylanmış denemelerden hesaplanır.
        </p>
      </Card>
    );
  }

  const isStudent = user?.role === "STUDENT";

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-baseline justify-between gap-1 sm:gap-4">
        <h3 className="text-xl font-black tracking-tight text-slate-900">Deneme Sıralaması</h3>
        <span className="text-[9px] font-black uppercase tracking-widest text-slate-400">
          {toplamKatilimci} katılımcı · onaylı denemelerin net ortalaması
        </span>
      </div>

      {groups.map((group) => (
        <div key={group.classId ?? "unassigned"} className="space-y-3">
          <div className="flex items-center justify-between px-2">
            <h4 className="text-sm font-black tracking-tight text-slate-800 flex items-center gap-2">
              <span className="inline-block w-2.5 h-2.5 rounded-full bg-cyan-500" />
              {group.className}
            </h4>
            <span className="text-[10px] font-bold text-slate-400">
              {group.entries.length} öğrenci
            </span>
          </div>

          <Card className="rounded-[2rem] border-0 shadow-lg bg-white overflow-hidden">
            <ol className="divide-y divide-slate-50">
              {group.entries.map((r) => (
                <li
                  key={`${group.classId ?? "unassigned"}-${r.rank}`}
                  className={`flex items-center justify-between gap-3 sm:gap-4 px-4 sm:px-8 py-4 ${
                    r.isSelf ? "bg-cyan-50" : ""
                  }`}
                >
                  <div className="flex items-center gap-4 min-w-0">
                    <span
                      className={`w-9 h-9 shrink-0 rounded-xl flex items-center justify-center text-xs font-black ${
                        r.isSelf ? "bg-cyan-500 text-white" : "bg-slate-100 text-slate-500"
                      }`}
                    >
                      {r.rank}
                    </span>
                    <span className={`text-sm truncate ${r.isSelf ? "font-black text-cyan-900" : "font-bold text-slate-700"}`}>
                      {r.displayName}
                      {r.isSelf && (
                        <span className="ml-2 text-[9px] font-black uppercase tracking-widest text-cyan-600">Sen</span>
                      )}
                    </span>
                  </div>
                  <div className="shrink-0 text-right">
                    <span className="block text-sm font-black tabular-nums text-slate-900">
                      {r.averageNet.toFixed(2)} net
                    </span>
                    <span className="block text-[10px] font-bold text-slate-400">{r.examCount} deneme</span>
                  </div>
                </li>
              ))}
            </ol>
          </Card>
        </div>
      ))}

      {isStudent && (
        <p className="text-[10px] font-bold text-slate-400 leading-relaxed">
          Diğer öğrenciler sıra numarasıyla gösterilir. Etiket kişiye değil sıraya aittir; aynı etiket
          farklı zamanlarda farklı kişiye denk gelebilir.
        </p>
      )}
    </div>
  );
}
