import { useState, useEffect, useCallback } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { analysisApi } from "@/lib/api";
import { toast } from "sonner";
import { 
  ChevronLeft, Sparkles, FileBarChart2, 
  Layers, RefreshCw, CheckCircle2, History, Info, TrendingUp,
  AlertCircle, Trophy, Zap
} from "lucide-react";
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

  if (isLoading) return <div className="flex-1 flex items-center justify-center py-32"><div className="w-12 h-12 border-4 border-indigo-500 border-t-transparent rounded-full animate-spin"></div></div>;

  return (
    <div className="w-full space-y-12 animate-in fade-in duration-700 pb-20">
      
      {/* ÜST NAVİGASYON */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <button onClick={() => navigate(-1)} className="tap-44 min-h-[44px] flex items-center gap-2 text-[10px] font-black uppercase tracking-widest text-slate-400 hover:text-slate-900 transition-colors self-start sm:self-auto"><ChevronLeft className="h-4 w-4" /> Geri Dön</button>
        <div className="text-left sm:text-right">
          <h2 className="text-2xl sm:text-3xl font-black text-slate-900 tracking-tighter uppercase italic">Gelişim <span className="text-indigo-600">Dosyası</span></h2>
          <p className="text-[10px] font-black text-slate-400 uppercase tracking-widest mt-1 italic">Tüm PDF'lerin Birleşimi</p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-12">
        
        {/* SOL: RAPOR YÖNETİMİ */}
        <div className="lg:col-span-1 space-y-10">
           
           {/* DAHİL EDİLENLER */}
           <div className="space-y-6">
              <h3 className="text-[10px] font-black uppercase tracking-[0.3em] text-slate-400 flex items-center gap-2 px-4"><CheckCircle2 className="h-4 w-4 text-emerald-500" /> Dahil Edilen PDF'ler</h3>
              <div className="space-y-3">
                 {includedReports.length === 0 ? (
                   <p className="text-[10px] font-bold text-slate-300 italic px-4">Henüz bir rapor dahil edilmedi.</p>
                 ) : includedReports.map(r => (
                   <div key={r.id} className="p-5 bg-white rounded-3xl border border-slate-100 shadow-sm flex items-center gap-4">
                      <div className="w-10 h-10 bg-emerald-50 text-emerald-600 rounded-xl flex items-center justify-center"><FileBarChart2 className="h-5 w-5" /></div>
                      <div>
                         <h5 className="font-black text-[11px] text-slate-900 uppercase leading-tight">{r.examTitle}</h5>
                         <p className="text-[8px] font-bold text-slate-400 uppercase mt-1">{new Date(r.processedAt).toLocaleDateString('tr-TR')}</p>
                      </div>
                   </div>
                 ))}
              </div>
           </div>

           {/* BEKLEMEDE OLANLAR */}
           <div className="space-y-6">
              <h3 className="text-[10px] font-black uppercase tracking-[0.3em] text-slate-400 flex items-center gap-2 px-4"><History className="h-4 w-4 text-amber-500" /> Beklemede Olanlar</h3>
              <div className="space-y-3">
                 {pendingReports.length === 0 ? (
                   <div className="p-8 text-center border-2 border-dashed border-slate-100 rounded-[2rem]">
                      <p className="text-[9px] font-bold text-slate-300 uppercase tracking-widest">Yeni rapor bekleniyor</p>
                   </div>
                 ) : pendingReports.map(r => (
                   <div key={r.id} className="p-5 bg-white rounded-3xl border-2 border-amber-100 shadow-xl shadow-amber-50 flex items-center gap-4 animate-in slide-in-from-left-2">
                      <div className="w-10 h-10 bg-amber-50 text-amber-600 rounded-xl flex items-center justify-center"><Layers className="h-5 w-5" /></div>
                      <div className="flex-1">
                         <h5 className="font-black text-[11px] text-slate-900 uppercase leading-tight">{r.examTitle}</h5>
                         <p className="text-[8px] font-bold text-slate-400 uppercase mt-1">Onaylandı - Analize Hazır</p>
                      </div>
                   </div>
                 ))}
              </div>
           </div>
        </div>

        {/* SAĞ: GELİŞİM YORUMU VE AKSİYON */}
        <div className="lg:col-span-2 space-y-10">
           
           <Card className="rounded-[4rem] border-0 shadow-2xl bg-slate-900 text-white p-12 relative overflow-hidden">
              <div className="absolute top-0 right-0 w-96 h-96 bg-indigo-500/10 rounded-full blur-[100px] -mr-20 -mt-20"></div>
              
              <div className="flex flex-col md:flex-row items-center justify-between gap-10 relative z-10">
                 <div className="flex items-center gap-8">
                    <div className="w-20 h-20 bg-indigo-600 text-white rounded-[2rem] flex items-center justify-center shadow-2xl shadow-indigo-500/20"><Sparkles className="h-10 w-10 animate-pulse" /></div>
                    <div className="space-y-2">
                       <h3 className="text-2xl font-black tracking-tight uppercase italic">Akademik Gelişim Analizi</h3>
                       <p className="text-[10px] font-black text-indigo-400 uppercase tracking-[0.3em]">Master Gelişim Motoru v2.0</p>
                    </div>
                 </div>
                 
                 {pendingReports.length > 0 && (
                   <button 
                    onClick={handleMerge}
                    disabled={isMerging}
                    className="bg-white text-slate-900 px-10 py-5 rounded-[2rem] font-black text-[11px] uppercase tracking-widest hover:bg-indigo-400 hover:text-white transition-all shadow-2xl active:scale-95 flex items-center gap-3"
                   >
                    <RefreshCw className={`h-4 w-4 ${isMerging ? 'animate-spin' : ''}`} /> Bekleyenleri Harmanla
                   </button>
                 )}
              </div>

              <div className="mt-12 p-10 bg-white/5 border border-white/5 rounded-[3rem] backdrop-blur-md relative z-10">
                 <div className="flex items-center gap-4 mb-8">
                    <TrendingUp className="h-6 w-6 text-indigo-400" />
                    <h4 className="font-black text-sm uppercase tracking-widest text-indigo-200">Global Stratejik Özet</h4>
                 </div>
                 <p className="text-slate-100 font-bold leading-relaxed text-2xl italic whitespace-pre-line">
                    "{globalSummary || "Veriler harmanlandığında burada öğrencinin genel gelişim serüveni hakkında derinlemesine bir analiz belirecektir."}"
                 </p>
              </div>

              <div className="mt-10 flex items-center gap-4 text-slate-500 italic relative z-10">
                 <Info className="h-4 w-4" />
                 <p className="text-[9px] font-black uppercase tracking-widest">Harmanla butonuna basıldığında beklemede olan {pendingReports.length} yeni veri analize dahil edilecektir.</p>
              </div>
           </Card>

           {/* TREND ANALİZİ KARTLARI */}
           {cumulativeData?.topicTrendData && (
             <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                <Card className="rounded-[3rem] border-0 shadow-xl bg-red-50 p-8 border-l-8 border-red-500">
                   <div className="flex items-center gap-4 mb-4">
                      <div className="w-10 h-10 bg-red-100 text-red-600 rounded-xl flex items-center justify-center"><AlertCircle className="h-6 w-6" /></div>
                      <h4 className="font-black text-xs uppercase tracking-widest text-red-900">Kronik Sorunlar</h4>
                   </div>
                   <div className="space-y-2">
                      {cumulativeData.topicTrendData.chronicTopics.length > 0 ? (
                        cumulativeData.topicTrendData.chronicTopics.slice(0, 3).map((t: string) => (
                          <div key={t} className="bg-white/50 p-3 rounded-xl text-[10px] font-bold text-red-700 italic flex items-center gap-2">• {t}</div>
                        ))
                      ) : <p className="text-[10px] text-slate-400 italic">Harika! Kronikleşmiş bir hata yok.</p>}
                   </div>
                </Card>

                <Card className="rounded-[3rem] border-0 shadow-xl bg-emerald-50 p-8 border-l-8 border-emerald-500">
                   <div className="flex items-center gap-4 mb-4">
                      <div className="w-10 h-10 bg-emerald-100 text-emerald-600 rounded-xl flex items-center justify-center"><Trophy className="h-6 w-6" /></div>
                      <h4 className="font-black text-xs uppercase tracking-widest text-emerald-900">Başarı Hikayeleri</h4>
                   </div>
                   <div className="space-y-2">
                      {cumulativeData.topicTrendData.improvedTopics.length > 0 ? (
                        cumulativeData.topicTrendData.improvedTopics.slice(0, 3).map((t: string) => (
                          <div key={t} className="bg-white/50 p-3 rounded-xl text-[10px] font-bold text-emerald-700 italic flex items-center gap-2">✓ {t}</div>
                        ))
                      ) : <p className="text-[10px] text-slate-400 italic">Yakında yeni başarılar belirecek.</p>}
                   </div>
                </Card>

                <Card className="rounded-[3rem] border-0 shadow-xl bg-amber-50 p-8 border-l-8 border-amber-500">
                   <div className="flex items-center gap-4 mb-4">
                      <div className="w-10 h-10 bg-amber-100 text-amber-600 rounded-xl flex items-center justify-center"><Zap className="h-6 w-6" /></div>
                      <h4 className="font-black text-xs uppercase tracking-widest text-amber-900">Dalgalı Performans</h4>
                   </div>
                   <div className="space-y-2">
                      {cumulativeData.topicTrendData.inconsistentTopics.length > 0 ? (
                        cumulativeData.topicTrendData.inconsistentTopics.slice(0, 3).map((t: string) => (
                          <div key={t} className="bg-white/50 p-3 rounded-xl text-[10px] font-bold text-amber-700 italic flex items-center gap-2">~ {t}</div>
                        ))
                      ) : <p className="text-[10px] text-slate-400 italic">İstikrar sağlanmış görünüyor.</p>}
                   </div>
                </Card>
             </div>
           )}

           {/* KONU ISI HARİTASI (HEATMAP) */}
           {cumulativeData?.topicTrendData && (
             <Card className="rounded-[4rem] border-0 shadow-2xl bg-white p-12 space-y-8">
                <div className="flex items-center justify-between">
                   <h4 className="font-black text-slate-900 text-sm uppercase tracking-widest flex items-center gap-3"><TrendingUp className="h-5 w-5 text-blue-500" /> Konu Gelişim Matrisi (Isı Haritası)</h4>
                   <p className="text-[9px] font-black text-slate-400 uppercase tracking-widest italic">* Yeşil: Doğru, Kırmızı: Yanlış, Gri: Boş</p>
                </div>
                <div className="overflow-x-auto custom-scrollbar pb-4">
                   <table className="w-full text-left">
                      <thead>
                         <tr className="border-b border-slate-50">
                            <th className="py-4 text-[10px] font-black text-slate-400 uppercase tracking-widest pr-8">Konu Adı</th>
                            <th className="py-4 text-[10px] font-black text-slate-400 uppercase tracking-widest">Gelişim Seyri (Son Sınavlar →)</th>
                         </tr>
                      </thead>
                      <tbody>
                         {cumulativeData.topicTrendData.heatmap.map((topic) => (
                           <tr key={topic.topicName} className="border-b border-slate-50/50 group hover:bg-slate-50 transition-colors">
                              <td className="py-4 pr-8">
                                 <p className="text-[11px] font-black text-slate-900 uppercase leading-tight">{topic.topicName}</p>
                                 <p className="text-[8px] font-bold text-slate-400 uppercase mt-1">{topic.lessonName}</p>
                              </td>
                              <td className="py-4">
                                 <div className="flex gap-2">
                                    {topic.statusHistory.map((status: string, idx: number) => (
                                      <div 
                                        key={idx}
                                        className={`w-8 h-8 rounded-lg flex items-center justify-center shadow-sm transition-transform hover:scale-110 ${
                                          status === 'CORRECT' ? 'bg-emerald-500 text-white' : 
                                          status === 'WRONG' ? 'bg-red-500 text-white' : 'bg-slate-200 text-slate-400'
                                        }`}
                                      >
                                         <span className="text-[9px] font-black">{status === 'CORRECT' ? 'D' : status === 'WRONG' ? 'Y' : 'B'}</span>
                                      </div>
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

           {/* IFRAME: CANLI KÜMÜLATİF GRAFİKLER */}
           {includedReports.length > 0 && (
             <div className="space-y-6">
                <h4 className="font-black text-slate-900 text-sm uppercase tracking-widest ml-4">Mevcut Gelişim Çizgisi</h4>
                <div className="h-[800px] w-full rounded-[4rem] overflow-hidden shadow-2xl border border-slate-50 bg-white">
                   <iframe src={`/analysis/${includedReports[0].id}?embedded=true&cumulative=true`} className="w-full h-full border-0" />
                </div>
             </div>
           )}

        </div>
      </div>
    </div>
  );
}
