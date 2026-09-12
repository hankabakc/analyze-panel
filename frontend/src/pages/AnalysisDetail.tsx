import { useState, useEffect, useRef, useCallback } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { analysisApi } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import { PriorityTopics } from "@/features/analysis/components/PriorityTopics"; 
import { toast } from "sonner";
import { 
  BarChart, Bar, XAxis, YAxis, CartesianGrid, 
  Tooltip, ResponsiveContainer, AreaChart, Area
} from "recharts";
import { 
  ChevronLeft, Sparkles, Target, 
  CheckCircle2, AlertCircle, Send, Trash2, Milestone, 
  Lightbulb, Compass, Zap, Calendar, BarChart3, TrendingUp, HelpCircle, Layers, Filter,
  ChevronDown, School, Trophy, ArrowUpRight, Clock
} from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import type { AnalysisData, ReferenceSchool, TopicDetail } from "@/lib/types";

export default function AnalysisDetail() {
  const { reportId } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth(); 
  const [data, setData] = useState<AnalysisData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  
  // GÖRÜNÜM MODU STATE (TEKİL VS KÜMÜLATİF)
  const [isCumulative, setIsCumulative] = useState(false);

  const [activeTab, setActiveTab] = useState<'PERFORMANCE' | 'MENTOR' | 'TARGET'>('PERFORMANCE');
  const [performanceView, setPerformanceView] = useState<'DATE' | 'NET'>('NET');
  
  const [showOnlyProblems, setShowOnlyProblems] = useState(false);
  const [expandedLessons, setExpandedLessons] = useState<string[]>([]);
  const [highlightedTopicId, setHighlightedTopicId] = useState<string | null>(null);
  const scrollRefs = useRef<{ [key: string]: HTMLDivElement | null }>({});

  // OKUL ARAMA VE HEDEF GÜNCELLEME STATE'LERİ
  const [isSearchModalOpen, setIsSearchModalOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [searchResults, setSearchResults] = useState<ReferenceSchool[]>([]);

  const fetchDetail = useCallback(async () => {
    if (!reportId) return;
    try {
      const response = await analysisApi.getAnalysisDetail(reportId, isCumulative);
      if (response.data.success) {
        const reportData = response.data.data;
        setData(reportData);
      }
    } catch {
      toast.error("Analiz detayları yüklenirken bir hata oluştu.");
    } finally {
      setIsLoading(false);
    }
  }, [reportId, isCumulative]);

  const handleSchoolSearch = async (query: string) => {
    setSearchQuery(query);
    if (query.length < 3) {
      setSearchResults([]);
      return;
    }
    try {
      const res = await analysisApi.searchSchools(query);
      if (res.data.success) {
        setSearchResults(res.data.data);
      }
    } catch (e) {
      console.error("Okul arama hatası", e);
    }
  };

  const selectTargetSchool = async (schoolId: string) => {
    try {
      const res = await analysisApi.updateProfile(schoolId);
      if (res.data.success) {
        toast.success("Hedef okul başarıyla güncellendi!");
        setIsSearchModalOpen(false);
        fetchDetail();
      }
    } catch {
      toast.error("Hedef güncellenirken bir hata oluştu.");
    }
  };

  const toggleLesson = (lessonId: string) => {
    setExpandedLessons(prev => prev.includes(lessonId) ? prev.filter(id => id !== lessonId) : [...prev, lessonId]);
  };

  const navigateToTopic = (lessonId: string, topicId: string) => {
    if (!expandedLessons.includes(lessonId)) setExpandedLessons(prev => [...prev, lessonId]);
    setTimeout(() => {
      const element = scrollRefs.current[topicId];
      if (element) {
        element.scrollIntoView({ behavior: 'smooth', block: 'center' });
        setHighlightedTopicId(topicId);
        setTimeout(() => setHighlightedTopicId(null), 3000);
      }
    }, 150);
  };

  /**
   * navigateToTopicByName: Öncelik listesindeki bir satırdan ders/konu adıyla o konunun
   * kartına atlar. Eskiden bu iş, düz metin bloğunda konu adlarını regex ile arayarak
   * yapılıyordu; veri yapısallaşınca ad eşleşmesi tek yerde ve okunur hâle geldi (T-023).
   */
  const navigateToTopicByName = (lessonName: string, topicName: string) => {
    if (!data) return;
    const lesson = data.consolidatedResult.lessons.find(l => l.lessonName === lessonName);
    const topic = lesson?.topics.find(t => t.topicName === topicName);
    if (lesson && topic) navigateToTopic(lesson.id, topic.id);
  };

  const getStatusUI = (topic: TopicDetail) => {
    if (topic.performanceBand === "STRONG") {
      return { color: "text-emerald-500", bg: "bg-emerald-50", icon: <CheckCircle2 className="h-4 w-4" /> };
    }
    if (topic.performanceBand === "MEDIUM") {
      return { color: "text-amber-500", bg: "bg-amber-50", icon: <HelpCircle className="h-4 w-4" /> };
    }
    return { color: "text-red-500", bg: "bg-red-50", icon: <AlertCircle className="h-4 w-4" /> };
  };

  const getGroupedTopics = (topics: TopicDetail[]) => {
    const displayTopics = showOnlyProblems ? topics.filter(t => t.performanceBand !== "STRONG") : topics;
    const groups: { [key: string]: TopicDetail[] } = {};
    const allMainTitles = new Set<string>();
    displayTopics.forEach(t => { if (t.topicName.includes(" - ")) allMainTitles.add(t.topicName.split(" - ")[0]); });
    const standalone: TopicDetail[] = [];
    displayTopics.forEach(topic => {
      if (topic.topicName.includes(" - ")) {
        const mainTitle = topic.topicName.split(" - ")[0];
        if (!groups[mainTitle]) groups[mainTitle] = [];
        groups[mainTitle].push(topic);
      } else if (!allMainTitles.has(topic.topicName)) { standalone.push(topic); }
    });
    return { groups, standalone };
  };

  /**
   * T-030B (S-016 → A): Ekran yalnızca gerçekten var olan veriyi gösterir.
   *
   * <p>Burada eskiden bir "kapsam çarpanı" vardı: seçilen kapsam raporun deneme sayısından
   * farklıysa ders doğru/yanlış/boş sayıları `×2` veya `×0.5` ile yeniden yazılıyordu.
   * Bu uydurma veriydi (APP-01 §2.1, §2.7 · IST-02 §2) ve arayüzde onu tetikleyecek bir
   * kapsam seçici hiç olmadığı için sessizce yanlış yerde ateşleniyordu: raporun deneme
   * sayısı 5 veya 10 dışında herhangi bir değer olduğunda (örn. kümülatif görünümde 3 veya
   * 7 onaylı deneme) bütün ders sayıları yarıya iniyordu. Çarpan kaldırıldı; sunucudan gelen
   * sayılar olduğu gibi gösteriliyor.</p>
   */
  const getFilteredLessons = () => {
    if (!data) return [];
    return data.consolidatedResult.lessons;
  };

  const getExamHistoryData = () => {
    if (!data || !data.examList) return [];
    return data.examList;
  };

  const handleApprove = async () => { if (!reportId) return; try { const res = await analysisApi.approveReport(reportId); if (res.data.success) { toast.success("Onaylandı."); if (data) setData({ ...data, status: 'APPROVED' }); } } catch { toast.error("Hata"); } };
  const handleReject = async () => { if (!reportId) return; try { const res = await analysisApi.rejectReport(reportId); if (res.data.success) { toast.info("Reddedildi."); navigate(-1); } } catch { toast.error("Hata"); } };
  const handleDelete = async () => { if (!reportId || !window.confirm("Emin misiniz?")) return; try { const res = await analysisApi.deleteReport(reportId); if (res.data.success) { toast.success("Silindi."); navigate(-1); } } catch { toast.error("Hata"); } };

  useEffect(() => { fetchDetail(); }, [fetchDetail]);

  if (isLoading) return <div className="flex-1 flex flex-col items-center justify-center gap-6 py-32"><div className="w-12 h-12 border-[6px] border-cyan-500 border-t-transparent rounded-full animate-spin"></div><p className="text-[10px] font-black uppercase tracking-[0.4em] text-slate-400">Yükleniyor...</p></div>;
  if (!data) return null;

  return (
    <div className="w-full space-y-12 animate-in fade-in duration-700 pb-20">
      
      {/* ÜST NAVİGASYON */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-6">
        <div className="flex items-center justify-between gap-4">
          <div className="flex items-center gap-4">
            <button onClick={() => navigate(-1)} className="tap-44 min-h-[44px] flex items-center gap-2 text-[10px] font-black uppercase tracking-widest text-slate-400 hover:text-slate-900 transition-colors"><ChevronLeft className="h-4 w-4" /> Geri Dön</button>
            {(user?.role === 'TEACHER' || user?.role === 'MANAGER') && <button onClick={handleDelete} aria-label="Raporu Sil" className="tap-44 min-w-[44px] min-h-[44px] flex items-center justify-center p-2.5 text-red-300 hover:text-red-600 hover:bg-red-50 rounded-xl transition-all active:scale-90"><Trash2 className="h-5 w-5" /></button>}
          </div>

          {/* GÖRÜNÜM MODU ANAHTARI (MOBİLDE) */}
          <div className="flex bg-slate-100 p-1 rounded-2xl border border-slate-200 shadow-inner sm:hidden">
            <button onClick={() => setIsCumulative(false)} className={`tap-44 px-3 py-2 rounded-xl text-[8px] font-black uppercase tracking-wider transition-all ${!isCumulative ? 'bg-white text-slate-900 shadow-md scale-105' : 'text-slate-400 hover:text-slate-600'}`}>Karne</button>
            <button onClick={() => setIsCumulative(true)} className={`tap-44 px-3 py-2 rounded-xl text-[8px] font-black uppercase tracking-wider transition-all flex items-center gap-1 ${isCumulative ? 'bg-cyan-500 text-white shadow-lg shadow-cyan-500/30 scale-105' : 'text-slate-400 hover:text-slate-600'}`}><Layers className="h-3 w-3" /> Gelişim</button>
          </div>
        </div>

        <div className="flex flex-col sm:flex-row sm:items-center justify-between lg:justify-end gap-4 sm:gap-6 min-w-0">
           {/* GÖRÜNÜM MODU ANAHTARI (MASAÜSTÜ / TABLET) */}
           <div className="hidden sm:flex bg-slate-100 p-1.5 rounded-2xl border border-slate-200 shadow-inner shrink-0">
              <button onClick={() => setIsCumulative(false)} className={`tap-44 px-5 sm:px-6 py-2.5 rounded-xl text-[9px] font-black uppercase tracking-widest transition-all ${!isCumulative ? 'bg-white text-slate-900 shadow-md scale-105' : 'text-slate-400 hover:text-slate-600'}`}>Sınav Karnesi</button>
              <button onClick={() => setIsCumulative(true)} className={`tap-44 px-5 sm:px-6 py-2.5 rounded-xl text-[9px] font-black uppercase tracking-widest transition-all flex items-center gap-2 ${isCumulative ? 'bg-cyan-500 text-white shadow-lg shadow-cyan-500/30 scale-105' : 'text-slate-400 hover:text-slate-600'}`}><Layers className="h-3.5 w-3.5" /> Gelişim Dosyası</button>
           </div>

           {user?.role === 'TEACHER' && data.status === 'PENDING_APPROVAL' && (
             <div className="flex items-center gap-3 shrink-0">
                <button onClick={handleReject} className="tap-44 min-h-[44px] bg-red-50 text-red-500 px-5 sm:px-8 py-3 sm:py-4 rounded-2xl font-black text-[10px] uppercase tracking-widest hover:bg-red-500 hover:text-white transition-all shadow-xl shadow-red-100">Reddet</button>
                <button onClick={handleApprove} className="tap-44 min-h-[44px] bg-emerald-500 text-white px-5 sm:px-8 py-3 sm:py-4 rounded-2xl font-black text-[10px] uppercase tracking-widest flex items-center gap-2 sm:gap-3 hover:bg-emerald-600 transition-all shadow-xl shadow-emerald-100"><Send className="h-4 w-4" /> Onayla ve Yayınla</button>
             </div>
           )}
           {data.status === 'PROCESSING' && (
             <div className="bg-blue-50 text-blue-600 px-5 py-2.5 rounded-2xl font-black text-[10px] uppercase tracking-widest flex items-center gap-2 border border-blue-100 animate-pulse shrink-0">
                <Clock className="h-4 w-4" /> Ayrıştırılıyor...
             </div>
           )}
           {data.status === 'FAILED' && (
             <div className="bg-red-50 text-red-600 px-5 py-2.5 rounded-2xl font-black text-[10px] uppercase tracking-widest flex items-center gap-2 border border-red-100 shrink-0">
                <AlertCircle className="h-4 w-4" /> Analiz Başarısız
             </div>
           )}
           <div className="text-left sm:text-right min-w-0">
             <h2 className="text-2xl sm:text-3xl font-black text-slate-900 tracking-tighter truncate">{isCumulative ? "GELİŞİM DOSYASI" : data.examTitle}</h2>
             <p className="text-[10px] font-black text-slate-400 uppercase tracking-widest mt-1 truncate">{isCumulative ? "Tüm Zamanların Birleşimi" : (data.reportType === 'SUMMARY' ? "5'li Gelişim Özeti" : 'Tekil Deneme Analizi')}</p>
           </div>
        </div>
      </div>

      <div className="w-full flex items-center justify-center overflow-x-auto py-2">
         <div className="bg-white p-1.5 sm:p-2 rounded-2xl sm:rounded-[2.5rem] shadow-2xl border border-slate-50 flex items-center gap-1 sm:gap-2 max-w-full">
            <button onClick={() => setActiveTab('PERFORMANCE')} className={`tap-44 min-h-[44px] px-4 sm:px-10 py-2.5 sm:py-4 rounded-xl sm:rounded-[1.8rem] font-black text-[9px] sm:text-[10px] uppercase tracking-wider sm:tracking-widest flex items-center gap-1.5 sm:gap-3 transition-all shrink-0 ${activeTab === 'PERFORMANCE' ? 'bg-slate-900 text-white shadow-lg' : 'text-slate-400 hover:bg-slate-50'}`}><Zap className="h-3.5 w-3.5 sm:h-4 sm:w-4 shrink-0" /> Performans</button>
            <button onClick={() => setActiveTab('MENTOR')} className={`tap-44 min-h-[44px] px-4 sm:px-10 py-2.5 sm:py-4 rounded-xl sm:rounded-[1.8rem] font-black text-[9px] sm:text-[10px] uppercase tracking-wider sm:tracking-widest flex items-center gap-1.5 sm:gap-3 transition-all shrink-0 ${activeTab === 'MENTOR' ? 'bg-blue-600 text-white shadow-lg' : 'text-slate-400 hover:bg-slate-50'}`}><Sparkles className="h-3.5 w-3.5 sm:h-4 sm:w-4 shrink-0" /> Analiz Uzmanı</button>
            <button onClick={() => setActiveTab('TARGET')} className={`tap-44 min-h-[44px] px-4 sm:px-10 py-2.5 sm:py-4 rounded-xl sm:rounded-[1.8rem] font-black text-[9px] sm:text-[10px] uppercase tracking-wider sm:tracking-widest flex items-center gap-1.5 sm:gap-3 transition-all shrink-0 ${activeTab === 'TARGET' ? 'bg-emerald-600 text-white shadow-lg' : 'text-slate-400 hover:bg-slate-50'}`}><Target className="h-3.5 w-3.5 sm:h-4 sm:w-4 shrink-0" /> Hedef & Tahmin</button>
         </div>
      </div>

      <div className="animate-in fade-in slide-in-from-bottom-5 duration-500">
        {activeTab === 'PERFORMANCE' && (
          <div className="space-y-12">
             <Card className="rounded-[2.5rem] sm:rounded-[3rem] border-0 shadow-xl bg-white p-5 sm:p-10">
                <div className="flex flex-col md:flex-row items-center justify-between mb-8 sm:mb-10 gap-4 sm:gap-6">
                   <h4 className="text-lg sm:text-xl font-black text-slate-900 tracking-tight flex items-center gap-2 sm:gap-3"><BarChart3 className="h-5 w-5 sm:h-6 sm:w-6 text-blue-500" /> {performanceView === 'NET' ? 'Ders Başarı Analizi' : 'Sınav Net Gelişimi'}</h4>
                   <div className="flex bg-slate-50 p-1.5 rounded-2xl border border-slate-100">
                      <button onClick={() => setPerformanceView('DATE')} className={`tap-44 min-h-[44px] px-4 sm:px-6 py-2 sm:py-2.5 rounded-xl text-[9px] font-black uppercase flex items-center gap-2 transition-all ${performanceView === 'DATE' ? 'bg-white text-blue-600 shadow-md' : 'text-slate-400 hover:text-slate-600'}`}><Calendar className="h-3.5 w-3.5" /> Tarihe Göre</button>
                      <button onClick={() => setPerformanceView('NET')} className={`tap-44 min-h-[44px] px-4 sm:px-6 py-2 sm:py-2.5 rounded-xl text-[9px] font-black uppercase flex items-center gap-2 transition-all ${performanceView === 'NET' ? 'bg-white text-blue-600 shadow-md' : 'text-slate-400 hover:text-slate-600'}`}><TrendingUp className="h-3.5 w-3.5" /> Nete Göre</button>
                   </div>
                </div>
                <div className="h-[350px] sm:h-[400px] w-full">
                   <ResponsiveContainer width="100%" height="100%">
                      {performanceView === 'NET' ? (
                        <BarChart data={getFilteredLessons()}>
                           <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f1f5f9" /><XAxis dataKey="lessonName" axisLine={false} tickLine={false} tick={{fontSize: 10, fontWeight: 'bold'}} /><YAxis axisLine={false} tickLine={false} tick={{fontSize: 10, fontWeight: 'bold'}} /><Tooltip contentStyle={{ borderRadius: '20px', border: 'none', boxShadow: '0 20px 50px rgba(0,0,0,0.1)' }} /><Bar dataKey="correct" name="Doğru" fill="#2563eb" radius={[6, 6, 0, 0]} barSize={30} /><Bar dataKey="wrong" name="Yanlış" fill="#ef4444" radius={[6, 6, 0, 0]} barSize={30} />
                        </BarChart>
                      ) : (
                        <AreaChart data={getExamHistoryData()}>
                           <defs><linearGradient id="colorNet" x1="0" y1="0" x2="0" y2="1"><stop offset="5%" stopColor="#3b82f6" stopOpacity={0.3}/><stop offset="95%" stopColor="#3b82f6" stopOpacity={0}/></linearGradient></defs>
                           <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f1f5f9" /><XAxis dataKey="examDate" axisLine={false} tickLine={false} tick={{fontSize: 10, fontWeight: 'bold'}} /><YAxis axisLine={false} tickLine={false} tick={{fontSize: 10, fontWeight: 'bold'}} /><Tooltip /><Area type="monotone" dataKey="totalScore" name="Tahmini Puan" stroke="#2563eb" strokeWidth={4} fillOpacity={1} fill="url(#colorNet)" />
                        </AreaChart>
                      )}
                   </ResponsiveContainer>
                </div>
             </Card>
             <div className="space-y-6">
                <h3 className="text-lg sm:text-xl font-black text-slate-900 tracking-tight ml-2 sm:ml-4 flex items-center gap-2 sm:gap-3"><Milestone className="h-5 w-5 sm:h-6 sm:w-6 text-blue-500" /> Sınav Tarihçesi</h3>
                <Card className="rounded-[2.5rem] sm:rounded-[3rem] border-0 shadow-xl bg-white overflow-hidden">
                   <div className="overflow-x-auto">
                      <table className="w-full min-w-[540px] text-left border-collapse">
                         <thead>
                            <tr className="bg-slate-50 border-b border-slate-100">
                               <th className="px-6 sm:px-10 py-4 sm:py-6 text-[10px] font-black uppercase tracking-widest text-slate-500">Sınav Adı</th>
                               <th className="px-6 sm:px-10 py-4 sm:py-6 text-[10px] font-black uppercase tracking-widest text-slate-500">Tarih</th>
                               <th className="px-6 sm:px-10 py-4 sm:py-6 text-[10px] font-black uppercase tracking-widest text-slate-500 text-center">Puan</th>
                               <th className="px-6 sm:px-10 py-4 sm:py-6 text-[10px] font-black uppercase tracking-widest text-slate-500 text-right">Durum</th>
                            </tr>
                         </thead>
                         <tbody>
                            {getExamHistoryData().map((exam, i) => (
                               <tr key={i} className="border-b border-slate-50 hover:bg-slate-50/50 transition-colors">
                                  <td className="px-6 sm:px-10 py-4 sm:py-6 font-black text-sm text-slate-900">{exam.examName}</td>
                                  <td className="px-6 sm:px-10 py-4 sm:py-6 text-xs font-bold text-slate-500">{exam.examDate}</td>
                                  <td className="px-6 sm:px-10 py-4 sm:py-6 text-center"><span className="text-lg font-black text-blue-600">{Number(exam.totalScore).toFixed(1)}</span></td>
                                  <td className="px-6 sm:px-10 py-4 sm:py-6 text-right"><span className="bg-emerald-50 text-emerald-600 text-[8px] font-black uppercase tracking-widest px-3 py-1.5 rounded-lg whitespace-nowrap">Analiz Edildi</span></td>
                               </tr>
                            ))}
                         </tbody>
                      </table>
                   </div>
                </Card>
             </div>
          </div>
        )}

        {activeTab === 'MENTOR' && (
          <div className="space-y-8">
             {/* T-039A: Filtre düğmesi panelden ayrıldı. Önce Stratejik Öncelik kartının içindeydi;
                 panel türetilemeyip gizlendiğinde öğretmen filtreyi de kaybediyordu. Artık panelin
                 görünürlüğünden bağımsız, her durumda kullanılabilir. */}
             <div className="flex justify-end">
               <button onClick={() => setShowOnlyProblems(!showOnlyProblems)} className={`tap-44 min-h-[44px] px-6 sm:px-8 py-3 sm:py-4 rounded-2xl font-black text-[10px] uppercase tracking-widest flex items-center gap-3 transition-all shadow-2xl ${showOnlyProblems ? 'bg-amber-500 text-white scale-105' : 'bg-slate-900 text-white hover:bg-slate-800'}`}><Filter className="h-4 w-4" />{showOnlyProblems ? 'Tüm Konuları Göster' : 'Sadece Sorunlu Konular'}</button>
             </div>

             {/* T-068: Stratejik öncelik ve aksiyon planı kartları küçültüldü; ekranın yarısını kaplayan
                 gradyan pano, sayfanın asıl içeriği olan konu listesinin önüne geçiyordu. */}
             {data.strategicPriority && (
               <Card className="rounded-3xl border border-blue-100 shadow-sm bg-white p-5 sm:p-6">
                  <div className="flex items-start gap-4">
                     <div className="w-10 h-10 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center shrink-0"><Lightbulb className="h-5 w-5" /></div>
                     <div className="min-w-0 space-y-1">
                        <h3 className="text-[10px] font-black uppercase tracking-[0.3em] text-slate-400">Stratejik Öncelik</h3>
                        <h2 className="text-base sm:text-lg font-black tracking-tight leading-snug text-slate-900">{data.strategicPriority}</h2>
                     </div>
                  </div>
               </Card>
             )}

             {/* ÖĞRETMEN ÖZEL AKSİYON PLANI (SADECE ÖĞRETMEN/MANAGER GÖRÜR) */}
             {(user?.role === 'TEACHER' || user?.role === 'MANAGER') && data.teacherActionPlan && (
               <Card className="rounded-3xl border-0 shadow-lg bg-slate-900 text-white p-5 sm:p-6 space-y-4">
                  <div className="flex items-center gap-3">
                     <div className="w-9 h-9 bg-emerald-500/15 text-emerald-400 rounded-xl flex items-center justify-center border border-emerald-500/25 shrink-0"><Zap className="h-4 w-4" /></div>
                     <div className="min-w-0">
                        <h4 className="font-black text-[11px] uppercase tracking-[0.25em] text-emerald-400">Öğretmen Aksiyon Planı</h4>
                        <p className="text-[9px] font-bold text-slate-400 uppercase tracking-widest mt-0.5">Sadece sizin görebileceğiniz strateji notu</p>
                     </div>
                  </div>
                  <p className="text-sm font-bold leading-relaxed text-slate-200 whitespace-pre-line">{data.teacherActionPlan}</p>
                  <p className="text-[9px] font-bold text-slate-500 uppercase tracking-widest">* Bu bilgiler karne verilerinden öğretmenin stratejik planlaması için üretilmiştir.</p>
               </Card>
             )}

             <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 items-start">
                <Card className="rounded-[2.5rem] sm:rounded-[3rem] border-0 shadow-xl bg-white p-6 sm:p-10 space-y-7 lg:sticky lg:top-8">
                  <div className="space-y-1">
                    <h4 className="font-black text-sm uppercase tracking-widest text-slate-900">Önce Şu Konular</h4>
                    <p className="text-[11px] font-bold text-slate-400 leading-relaxed">
                      Karnedeki sayılardan çıkarıldı. Üstteki konu en çok net kaybettiriyor.
                    </p>
                  </div>
                  <PriorityTopics priorityLists={data.priorityLists} onSelect={navigateToTopicByName} hasData={data.consolidatedResult.lessons.length > 0} />
                </Card>
                <div className="space-y-6">
                   {data.consolidatedResult.lessons.map(lesson => {
                      const { groups, standalone } = getGroupedTopics(lesson.topics);
                      if (showOnlyProblems && Object.keys(groups).length === 0 && standalone.length === 0) return null;
                      const isExpanded = expandedLessons.includes(lesson.id);
                      return (<Card key={lesson.id} className="rounded-[2.5rem] border-0 shadow-lg bg-white overflow-hidden"><div onClick={() => toggleLesson(lesson.id)} className="bg-slate-50 px-6 sm:px-8 py-5 sm:py-6 flex justify-between items-center cursor-pointer hover:bg-slate-100 transition-colors border-b border-slate-100 group"><div className="flex items-center gap-3 sm:gap-4"><div className={`p-2 rounded-xl transition-all ${isExpanded ? 'bg-blue-600 text-white rotate-180' : 'bg-white text-slate-400 group-hover:bg-blue-50'}`}><ChevronDown className="h-5 w-5" /></div><span className="font-black text-xs uppercase tracking-widest text-slate-700">{lesson.lessonName}</span></div><span className="text-[10px] font-black text-blue-600 bg-blue-50 px-3 py-1.5 rounded-lg">%{lesson.successRate} Başarı</span></div>{isExpanded && (<CardContent className="p-5 sm:p-8 space-y-6 animate-in slide-in-from-top-2 duration-300">{Object.keys(groups).map(mainTitle => (<div key={mainTitle} className="space-y-3"><div className="flex items-center gap-3 px-2"><Layers className="h-4 w-4 text-slate-400" /><span className="font-black text-[11px] uppercase tracking-widest text-slate-900">{mainTitle}</span></div><div className="space-y-2 border-l-2 border-slate-100 ml-4 pl-4">{groups[mainTitle].map(topic => { const ui = getStatusUI(topic); const isHigh = highlightedTopicId === topic.id; return (<div key={topic.id} ref={el => { scrollRefs.current[topic.id] = el; }} className={`flex items-center justify-between group p-3 rounded-xl transition-all duration-700 ${isHigh ? 'bg-yellow-400/40 scale-105 shadow-[0_0_30px_rgba(250,204,21,0.6)] border-2 border-yellow-400 z-20 animate-pulse' : 'hover:bg-slate-50'}`}><div className="flex items-center gap-3"><div className={`${ui.color} ${ui.bg} p-1.5 rounded-lg shadow-sm`}>{ui.icon}</div><span className={`font-bold text-xs ${isHigh ? 'text-slate-900' : 'text-slate-600'} italic`}>{topic.topicName.split(" - ").pop()}</span></div><div className="text-[9px] font-black text-slate-400 uppercase">SS: {topic.totalQuestions || '--'} | D: {topic.correctCount || 0}</div></div>); })}</div></div>))}{standalone.length > 0 && (<div className="space-y-2 pt-4 border-t border-slate-50">{standalone.map(topic => { const ui = getStatusUI(topic); const isHigh = highlightedTopicId === topic.id; return (<div key={topic.id} ref={el => { scrollRefs.current[topic.id] = el; }} className={`flex items-center justify-between group p-3 rounded-xl transition-all duration-700 ${isHigh ? 'bg-yellow-400/40 scale-105 shadow-[0_0_30px_rgba(250,204,21,0.6)] border-2 border-yellow-400 z-20 animate-pulse' : 'hover:bg-slate-50'}`}><div className="flex items-center gap-3"><div className={`${ui.color} ${ui.bg} p-1.5 rounded-lg shadow-sm`}>{ui.icon}</div><span className={`font-bold text-xs ${isHigh ? 'text-slate-900' : 'text-slate-600'} italic`}>{topic.topicName}</span></div><div className="text-[9px] font-black text-slate-400 uppercase">SS: {topic.totalQuestions || '--'} | D: {topic.correctCount || 0}</div></div>); })}</div>)}</CardContent>)}</Card>);
                   })}
                </div>
             </div>
          </div>
        )}

        {activeTab === 'TARGET' && (
          <div className="space-y-12">
             {/* OKUL SEÇME MODALI */}
             {isSearchModalOpen && (
               <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 sm:p-6 bg-slate-900/60 backdrop-blur-xl animate-in fade-in duration-300">
                  <Card className="w-full max-w-2xl rounded-[2.5rem] sm:rounded-[3rem] border-0 shadow-2xl bg-white overflow-hidden">
                     <div className="p-6 sm:p-10 space-y-6 sm:space-y-8">
                        <div className="flex items-center justify-between">
                           <div className="flex items-center gap-4">
                              <div className="w-10 h-10 sm:w-12 sm:h-12 bg-cyan-50 text-cyan-600 rounded-2xl flex items-center justify-center shadow-lg shadow-cyan-100"><School className="h-5 w-5 sm:h-6 sm:w-6" /></div>
                              <h4 className="font-black text-lg sm:text-xl text-slate-900 tracking-tight">Hedef Okul Seç</h4>
                           </div>
                           <button onClick={() => setIsSearchModalOpen(false)} aria-label="Kapat" className="tap-44 min-w-[44px] min-h-[44px] flex items-center justify-center p-2.5 text-slate-400 hover:text-slate-900 transition-colors"><Trash2 className="h-5 w-5" /></button>
                        </div>
                        <div className="relative">
                           <input type="text" placeholder="Okul adı veya şehir ara..." className="w-full bg-slate-50 border-2 border-slate-100 rounded-[1.5rem] px-5 sm:px-8 py-4 sm:py-5 font-bold text-slate-700 focus:outline-none focus:border-cyan-500 transition-all" value={searchQuery} onChange={(e) => handleSchoolSearch(e.target.value)} autoFocus />
                           {searchResults.length > 0 && (
                             <div className="absolute top-full left-0 right-0 mt-4 bg-white border border-slate-100 rounded-[2rem] shadow-2xl overflow-hidden z-20 max-h-[300px] overflow-y-auto">
                                {searchResults.map((school) => (
                                  <button key={school.id} onClick={() => selectTargetSchool(school.id)} className="w-full px-5 sm:px-8 py-4 sm:py-5 text-left hover:bg-slate-50 border-b border-slate-50 last:border-0 transition-colors group flex items-center justify-between">
                                     <div><p className="font-black text-slate-900 group-hover:text-cyan-600 transition-colors">{school.schoolName}</p><p className="text-[10px] font-bold text-slate-400 uppercase tracking-widest">{school.city} • {school.schoolType}</p></div>
                                     <div className="text-right"><p className="font-black text-cyan-600">Puan: {school.baseScore}</p><p className="text-[9px] font-bold text-slate-300">Yüzdelik: %{school.percentile}</p></div>
                                  </button>
                                ))}
                             </div>
                           )}
                        </div>
                        <p className="text-[10px] font-black text-slate-400 uppercase tracking-[0.2em] text-center italic">* 2025 LGS TABAN PUANLARI REFERANS ALINMAKTADIR</p>
                     </div>
                  </Card>
               </div>
             )}

             <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
                {/* HEDEF OKUL KARTI */}
                <Card 
                  onClick={() => user?.role === 'STUDENT' && setIsSearchModalOpen(true)}
                  className={`lg:col-span-2 rounded-[2.5rem] sm:rounded-[4rem] border-0 shadow-2xl bg-slate-900 text-white p-6 sm:p-12 relative overflow-hidden transition-all ${user?.role === 'STUDENT' ? 'cursor-pointer group hover:scale-[1.02]' : 'cursor-default'}`}
                >
                   <div className="absolute top-0 right-0 w-96 h-96 bg-cyan-500/10 rounded-full blur-[100px] -mr-20 -mt-20 group-hover:bg-cyan-500/20 transition-all"></div>
                   <div className="relative z-10 flex flex-col md:flex-row items-center gap-6 sm:gap-10">
                      <div className="w-20 h-20 sm:w-24 sm:h-24 rounded-[2rem] sm:rounded-[2.5rem] bg-cyan-500/20 border border-cyan-500/30 flex items-center justify-center shrink-0 shadow-2xl shadow-cyan-500/20 group-hover:scale-110 transition-transform"><School className="h-10 w-10 sm:h-12 sm:w-12 text-cyan-400" /></div>
                      <div className="space-y-4 flex-1 min-w-0">
                         <div className="flex items-center justify-between">
                            <h3 className="text-[10px] font-black uppercase tracking-[0.4em] text-cyan-400">Hedeflenen Kurum</h3>
                            {user?.role === 'STUDENT' && <span className="tap-44 min-h-[44px] inline-flex items-center text-[9px] font-black bg-white/10 px-4 py-2 rounded-xl text-white/60 uppercase tracking-widest group-hover:bg-cyan-500 group-hover:text-white transition-all">Hedefi Değiştir</span>}
                         </div>
                         <h2 className="text-2xl sm:text-4xl font-black tracking-tight leading-tight uppercase truncate">{data.targetComparison?.schoolName || data.targetSchoolName || "Hedef Belirlenmedi"}</h2>
                         {data.targetComparison && (
                           <div className="flex flex-wrap items-center gap-2 sm:gap-4 mt-4">
                              <div className="flex items-center gap-2 px-3 sm:px-4 py-2 bg-white/10 rounded-xl border border-white/10">
                                <Trophy className="h-4 w-4 text-yellow-400 shrink-0" />
                                <span className="text-xs sm:text-sm font-bold text-white">
                                  <span className="hidden md:inline">Taban Puan:</span>
                                  <span className="md:hidden">Taban:</span> {data.targetComparison.targetScore}
                                </span>
                              </div>
                              <div className="flex items-center gap-2 px-3 sm:px-4 py-2 bg-white/10 rounded-xl border border-white/10">
                                <TrendingUp className="h-4 w-4 text-cyan-400 shrink-0" />
                                <span className="text-xs sm:text-sm font-bold text-white">
                                  <span className="hidden md:inline">Net Ortalaman:</span>
                                  <span className="md:hidden">Net:</span> {data.targetComparison.averageNet.toFixed(2)}
                                </span>
                              </div>
                              <div className="flex items-center gap-2 px-3 sm:px-4 py-2 bg-cyan-500/20 rounded-xl border border-cyan-500/30">
                                <span className="text-xs sm:text-sm font-bold text-cyan-300">
                                  <span className="hidden md:inline">Tahmini LGS Puanın:</span>
                                  <span className="md:hidden">Tahmini LGS:</span> ≈{data.targetComparison.predictedLgsScore.toFixed(1)}
                                </span>
                              </div>
                           </div>
                         )}
                      </div>
                   </div>
                </Card>

                {/* UYUM SKORU KARTI */}
                <Card className="rounded-[2.5rem] sm:rounded-[4rem] border-0 shadow-2xl bg-white p-6 sm:p-12 flex flex-col items-center justify-center text-center space-y-6 relative overflow-hidden">
                   <div className={`w-28 h-28 sm:w-32 sm:h-32 rounded-full border-[8px] sm:border-[10px] flex items-center justify-center transition-all duration-1000 ${data.targetComparison ? (data.targetComparison.compatibilityPercent > 80 ? 'border-emerald-500 text-emerald-600' : data.targetComparison.compatibilityPercent > 50 ? 'border-amber-500 text-amber-600' : 'border-red-500 text-red-600') : 'border-slate-200 text-slate-400'}`}>
                      <span className="text-2xl sm:text-3xl font-black">%{data.targetComparison ? data.targetComparison.compatibilityPercent : 0}</span>
                   </div>
                   <div><h4 className="font-black text-slate-900 text-sm uppercase tracking-widest">Hedef Uyumu</h4><p className="text-[9px] font-bold text-slate-400 uppercase tracking-widest mt-1">LGS Puan Eşleşme Skoru</p></div>
                </Card>
             </div>

             <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 sm:gap-12">
                {/* Projeksiyon yalnızca hesaplanmışsa gösterilir */}
                {data.futureProjection && (
                <Card className="rounded-[2.5rem] sm:rounded-[3rem] border-0 shadow-xl bg-emerald-900 text-white p-6 sm:p-12 space-y-6 sm:space-y-8 relative overflow-hidden">
                   <div className="absolute bottom-0 left-0 w-64 h-64 bg-emerald-500/20 rounded-full blur-[80px] -ml-20 -mb-20"></div>
                   <div className="relative z-10 flex items-center gap-4 sm:gap-6"><Compass className="h-8 w-8 sm:h-10 sm:w-10 text-emerald-400" /><h4 className="font-black text-sm uppercase tracking-widest">Gelecek Projeksiyonu</h4></div>
                   <p className="relative z-10 text-emerald-50 font-bold leading-relaxed text-lg sm:text-2xl italic">"{data.futureProjection}"</p>
                </Card>
                )}
                <div className="space-y-8">
                   <Card className="rounded-[2.5rem] sm:rounded-[3rem] border-0 shadow-xl bg-white p-6 sm:p-12 space-y-6 sm:space-y-8">
                      <div className="flex items-center gap-4"><div className="w-10 h-10 sm:w-12 sm:h-12 bg-red-50 text-red-500 rounded-2xl flex items-center justify-center"><ArrowUpRight className="h-5 w-5 sm:h-6 sm:w-6" /></div><h4 className="font-black text-sm uppercase tracking-widest text-slate-900">Hedef Mesafesi (Puan)</h4></div>
                      <div className="space-y-6">
                         {data.targetComparison ? (
                           data.targetComparison.scoreGap > 0 ? (
                             <div className="p-6 sm:p-8 bg-red-50 rounded-[1.8rem] sm:rounded-[2rem] border-2 border-red-100"><p className="text-red-900 font-bold text-base sm:text-lg leading-snug">Hedeflediğin okula ulaşmak için projeksiyonunun <span className="text-xl sm:text-2xl font-black">+{data.targetComparison.scoreGap.toFixed(1)}</span> puan (≈{data.targetComparison.netGap.toFixed(1)} net) daha üzerine çıkmalısın.</p></div>
                           ) : (
                             <div className="p-6 sm:p-8 bg-emerald-50 rounded-[1.8rem] sm:rounded-[2rem] border-2 border-emerald-100"><p className="text-emerald-900 font-bold text-base sm:text-lg leading-snug">Tebrikler! Mevcut performansın hedeflediğin okulun <span className="text-xl sm:text-2xl font-black">{Math.abs(data.targetComparison.scoreGap).toFixed(1)}</span> puan üzerinde.</p></div>
                           )
                         ) : (<p className="text-slate-400 font-bold italic">Hedef analizi için lütfen profilinden bir okul seç.</p>)}
                      </div>
                   </Card>
                </div>
             </div>
          </div>
        )}
      </div>
    </div>
  );
}
