import { useState, useEffect, useCallback } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { analysisApi } from "@/lib/api";
import { toast } from "sonner";
import { 
  ChevronLeft, ChevronRight, FileBarChart2,
  Layers, RefreshCw, CheckCircle2, History,
} from "@/components/icons";
import { Card } from "@/components/ui/card";
import type { AnalysisData, AnalysisReportSummary } from "@/lib/types";

export default function CumulativeAnalysis() {
  const { studentId } = useParams();
  const navigate = useNavigate();
  const [reports, setReports] = useState<AnalysisReportSummary[]>([]);
  const [cumulativeData, setCumulativeData] = useState<AnalysisData | null>(null);
  const [globalSummary, setGlobalSummary] = useState<string>("");
  const [isLoading, setIsLoading] = useState(true);
  const [isMerging, setIsMerging] = useState(false);

  const fetchData = useCallback(async () => {
    if (!studentId) return;
    try {
      const [reportsRes, summaryRes, profileRes] = await Promise.all([
        analysisApi.getStudentReports(studentId),
        analysisApi.getGlobalSummary(studentId),
        analysisApi.getCumulativeProfile(studentId)
      ]);
      
      if (reportsRes.data.success) setReports(reportsRes.data.data);
      if (summaryRes.data.success) setGlobalSummary(summaryRes.data.data);
      if (profileRes.data.success) setCumulativeData(profileRes.data.data);
    } catch {
      toast.error("Veriler yüklenirken bir hata oluştu.");
    } finally {
      setIsLoading(false);
    }
  }, [studentId]);

  const handleMerge = async () => {
    if (!studentId) return;
    setIsMerging(true);
    toast.loading("Yeni veriler kümülatif dosyaya harmanlanıyor...");
    try {
      const res = await analysisApi.mergeCumulative(studentId);
      if (res.data.success) {
        setGlobalSummary(res.data.data);
        toast.dismiss();
        toast.success("Gelişim Dosyası Güncellendi!");
        fetchData(); // Listeleri yenile
      }
    } catch {
      toast.dismiss();
      toast.error("Harmanlama işlemi başarısız.");
    } finally {
      setIsMerging(false);
    }
  };

  useEffect(() => { fetchData(); }, [fetchData]);

  const includedReports = reports.filter(r => r.cumulativeStatus === 'INCLUDED' && r.status === 'APPROVED');
  const pendingReports = reports.filter(r => r.cumulativeStatus === 'NOT_INCLUDED' && r.status === 'APPROVED');

  if (isLoading) return <div className="flex-1 flex items-center justify-center py-32"><div className="w-12 h-12 border-2 border-slate-300 border-t-slate-900 rounded-full animate-spin"></div></div>;

  return (
    <div className="w-full space-y-8 pb-20">
      
      {/* ÜST NAVİGASYON */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <button onClick={() => navigate(-1)} className="tap-44 min-h-[44px] flex items-center gap-2 text-sm text-slate-500 hover:text-slate-900 transition-colors self-start sm:self-auto"><ChevronLeft className="h-4 w-4" /> Geri</button>
        <div className="text-left sm:text-right">
          <h2 className="text-2xl sm:text-3xl font-semibold text-slate-900 tracking-tight">Gelişim dosyası</h2>
          <p className="text-sm text-slate-500 mt-1">Onaylı karnelerin birleşimi</p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        
        {/* SOL: RAPOR YÖNETİMİ */}
        <div className="lg:col-span-1 space-y-8">
           
           {/* DAHİL EDİLENLER */}
           <div className="space-y-6">
              <h3 className="text-sm text-slate-500 flex items-center gap-2 px-1"><CheckCircle2 className="h-4 w-4 text-emerald-500" /> Dahil edilen karneler</h3>
              <div className="space-y-3">
                 {includedReports.length === 0 ? (
                   <p className="text-sm text-slate-400 px-1">Henüz karne eklenmedi.</p>
                 ) : includedReports.map(r => (
                   <div key={r.id} className="p-4 bg-white rounded-lg border border-slate-200 flex items-center gap-3">
                      <FileBarChart2 className="h-4 w-4 text-slate-400 shrink-0" />
                      <div>
                         <h5 className="font-semibold text-xs text-slate-900 leading-tight">{r.examTitle}</h5>
                         <p className="text-xs font-medium text-slate-400 mt-1">{new Date(r.processedAt).toLocaleDateString('tr-TR')}</p>
                      </div>
                   </div>
                 ))}
              </div>
           </div>

           {/* BEKLEMEDE OLANLAR */}
           <div className="space-y-6">
              <h3 className="text-sm text-slate-500 flex items-center gap-2 px-1"><History className="h-4 w-4 text-amber-500" /> Eklenmeyi bekleyenler</h3>
              <div className="space-y-3">
                 {pendingReports.length === 0 ? (
                   <div className="p-6 text-center border border-dashed border-slate-200 rounded-lg">
                      <p className="text-sm text-slate-400">Bekleyen karne yok</p>
                   </div>
                 ) : pendingReports.map(r => (
                   <div key={r.id} className="p-4 bg-white rounded-lg border border-amber-200 flex items-center gap-3">
                      <Layers className="h-4 w-4 text-amber-600 shrink-0" />
                      <div className="flex-1">
                         <h5 className="font-semibold text-xs text-slate-900 leading-tight">{r.examTitle}</h5>
                         <p className="text-xs font-medium text-slate-400 mt-1">Onaylandı, eklenmeye hazır</p>
                      </div>
                   </div>
                 ))}
              </div>
           </div>
        </div>

        {/* SAĞ: GELİŞİM ÖZETİ */}
        <div className="lg:col-span-2 space-y-6">

           <Card className="rounded-xl border border-slate-200 shadow-none bg-white p-6 sm:p-8">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                 <div>
                    <h3 className="text-lg font-semibold tracking-tight text-slate-900">Genel özet</h3>
                    <p className="text-sm text-slate-500 mt-0.5">Dahil edilen karnelerin sayılarından çıkarılır</p>
                 </div>
                 {pendingReports.length > 0 && (
                   <button
                    onClick={handleMerge}
                    disabled={isMerging}
                    className="tap-44 h-9 px-4 rounded-md bg-slate-900 text-white text-sm flex items-center gap-2 hover:bg-slate-700 transition-colors disabled:opacity-60"
                   >
                    <RefreshCw className={`h-4 w-4 ${isMerging ? 'animate-spin' : ''}`} /> Bekleyenleri ekle ({pendingReports.length})
                   </button>
                 )}
              </div>
              <div className="mt-6 border-t border-slate-200 pt-6">
                 <p className="text-slate-700 leading-relaxed whitespace-pre-line">
                    {globalSummary || "Bekleyen karneler eklendiğinde öğrencinin genel durumu burada özetlenir."}
                 </p>
              </div>
           </Card>

           {cumulativeData?.topicTrendData && (
             <div className="grid grid-cols-1 md:grid-cols-3 gap-px rounded-xl border border-slate-200 bg-slate-200 overflow-hidden">
                {[
                  { title: "Sürekli yanlış yapılan", items: cumulativeData.topicTrendData.chronicTopics, empty: "Sürekli yanlış yapılan konu yok.", tone: "bg-red-500" },
                  { title: "Gelişen konular", items: cumulativeData.topicTrendData.improvedTopics, empty: "Henüz belirgin bir gelişme yok.", tone: "bg-emerald-500" },
                  { title: "Dalgalı konular", items: cumulativeData.topicTrendData.inconsistentTopics, empty: "Dalgalı sonuç veren konu yok.", tone: "bg-amber-500" },
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

           {cumulativeData?.topicTrendData && (
             <Card className="rounded-xl border border-slate-200 shadow-none bg-white p-6 sm:p-8 space-y-5">
                <div className="flex flex-wrap items-center justify-between gap-3">
                   <h4 className="font-semibold text-slate-900 text-base tracking-tight">Konuların denemelere göre durumu</h4>
                   <p className="text-xs text-slate-500">D: doğru · Y: yanlış · B: boş</p>
                </div>
                <div className="overflow-x-auto pb-2">
                   <table className="w-full text-left">
                      <thead>
                         <tr className="border-b border-slate-200">
                            <th className="py-2 text-xs font-medium text-slate-500 pr-8">Konu</th>
                            <th className="py-2 text-xs font-medium text-slate-500">Denemeler (eskiden yeniye)</th>
                         </tr>
                      </thead>
                      <tbody>
                         {cumulativeData.topicTrendData.heatmap.map((topic) => (
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

           {includedReports.length > 0 && (
             <button
               onClick={() => navigate(`/analysis/${includedReports[0].id}`)}
               className="tap-44 inline-flex items-center gap-2 text-sm text-slate-600 hover:text-slate-900"
             >
                Grafikleri ve deneme tarihçesini analiz ekranında aç <ChevronRight className="h-4 w-4" />
             </button>
           )}

        </div>
      </div>
    </div>
  );
}
