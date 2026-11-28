import { Card } from "@/components/ui/card";
import type { AnalysisData } from "@/lib/types";

/** Gelişim dosyasına özgü bölüm: genel özet, konu eğilimleri ve konuların denemelere göre durumu. */
export function TopicTrendSection({ data }: { data: AnalysisData }) {
  const trend = data.topicTrendData;
  return (
    <div className="space-y-6">
      {data.globalFeedback && (
        <Card className="rounded-xl border border-slate-200 shadow-none bg-white p-6 sm:p-8">
          <h3 className="text-lg font-semibold tracking-tight text-slate-900">Genel özet</h3>
          <p className="mt-4 text-slate-700 leading-relaxed whitespace-pre-line">{data.globalFeedback}</p>
        </Card>
      )}

      {trend && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-px rounded-xl border border-slate-200 bg-slate-200 overflow-hidden">
          {[
            { title: "Sürekli yanlış yapılan", items: trend.chronicTopics, empty: "Sürekli yanlış yapılan konu yok.", tone: "bg-red-500" },
            { title: "Gelişen konular", items: trend.improvedTopics, empty: "Henüz belirgin bir gelişme yok.", tone: "bg-emerald-500" },
            { title: "Dalgalı konular", items: trend.inconsistentTopics, empty: "Dalgalı sonuç veren konu yok.", tone: "bg-amber-500" },
          ].map((col) => (
            <div key={col.title} className="bg-white p-5">
              <h4 className="flex items-center gap-2 text-sm font-medium text-slate-900"><span className={`h-2 w-2 rounded-full ${col.tone}`} />{col.title}</h4>
              <ul className="mt-3 space-y-1.5">
                {col.items.length > 0
                  ? col.items.slice(0, 3).map((t: string) => <li key={t} className="text-sm text-slate-600">{t}</li>)
                  : <li className="text-sm text-slate-400">{col.empty}</li>}
              </ul>
            </div>
          ))}
        </div>
      )}

      {trend && trend.heatmap.length > 0 && (
        <Card className="rounded-xl border border-slate-200 shadow-none bg-white p-6 sm:p-8 space-y-5">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h4 className="font-semibold text-slate-900 text-base tracking-tight">Konuların karnelere göre durumu</h4>
            <p className="text-xs text-slate-500">D: doğru · Y: yanlış · B: boş</p>
          </div>
          <div className="overflow-x-auto pb-2">
            <table className="w-full text-left">
              <thead>
                <tr className="border-b border-slate-200">
                  <th className="py-2 text-xs font-medium text-slate-500 pr-8">Konu</th>
                  <th className="py-2 text-xs font-medium text-slate-500">Karneler (eskiden yeniye)</th>
                </tr>
              </thead>
              <tbody>
                {trend.heatmap.map((topic) => (
                  <tr key={topic.lessonName + topic.topicName} className="border-b border-slate-100">
                    <td className="py-2.5 pr-8">
                      <p className="text-sm text-slate-900 leading-tight">{topic.topicName}</p>
                      <p className="text-xs text-slate-400 mt-0.5">{topic.lessonName}</p>
                    </td>
                    <td className="py-2.5">
                      <div className="flex gap-1.5">
                        {topic.statusHistory.map((status: string, idx: number) => (
                          <span
                            key={idx}
                            className={`w-6 h-6 rounded flex items-center justify-center text-xs font-medium ${
                              status === 'CORRECT' ? 'bg-emerald-100 text-emerald-700' :
                              status === 'WRONG' ? 'bg-red-100 text-red-700' : 'bg-slate-100 text-slate-500'
                            }`}
                          >
                            {status === 'CORRECT' ? 'D' : status === 'WRONG' ? 'Y' : 'B'}
                          </span>
                        ))}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}
    </div>
  );
}
