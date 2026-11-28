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
  ChevronLeft, 
  CheckCircle2, AlertCircle, Send, Trash2, Milestone, 
  Lightbulb, Compass, Zap, Calendar, BarChart3, TrendingUp, HelpCircle, Layers, Filter,
  ChevronDown, School, ArrowUpRight, Clock
} from "@/components/icons";
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
  const [performanceView, setPerformanceView] = useState<'DATE' | 'NET'>('DATE');
  
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

  if (isLoading) return <div className="flex-1 flex flex-col items-center justify-center gap-6 py-32"><div className="w-12 h-12 border-[6px] border-cyan-500 border-t-transparent rounded-full animate-spin"></div><p className="text-xs font-semibold tracking-normal text-slate-400">Yükleniyor...</p></div>;
  if (!data) return null;

  return (
    <div className="w-full space-y-8 pb-20">
      
      {/* ÜST BAŞLIK */}
      <div className="space-y-4">
        <div className="flex items-center gap-2">
          <button onClick={() => navigate(-1)} className="tap-44 min-h-[44px] flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-900 transition-colors"><ChevronLeft className="h-4 w-4" /> Geri</button>
          {(user?.role === 'TEACHER' || user?.role === 'MANAGER') && <button onClick={handleDelete} aria-label="Raporu Sil" title="Raporu sil" className="tap-44 min-w-[44px] min-h-[44px] flex items-center justify-center text-slate-400 hover:text-red-600 rounded-md transition-colors"><Trash2 className="h-4 w-4" /></button>}
        </div>
        <div className="flex flex-col lg:flex-row lg:items-end justify-between gap-5">
          <div className="min-w-0">
            <h2 className="text-2xl sm:text-3xl font-semibold text-slate-900 tracking-tight truncate">{isCumulative ? "Gelişim dosyası" : data.examTitle}</h2>
            <p className="text-sm text-slate-500 mt-1 truncate">{isCumulative ? "Onaylı tüm karnelerin birleşimi" : (data.reportType === 'SUMMARY' ? "Birden çok denemenin özeti" : 'Tek deneme analizi')}</p>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <div className="flex bg-slate-100 p-1 rounded-lg shrink-0">
              <button onClick={() => setIsCumulative(false)} className={`tap-44 px-3 py-1.5 rounded-md text-sm transition-colors ${!isCumulative ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-900'}`}>Sınav karnesi</button>
              <button onClick={() => setIsCumulative(true)} className={`tap-44 px-3 py-1.5 rounded-md text-sm transition-colors ${isCumulative ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-900'}`}>Gelişim dosyası</button>
            </div>
            {user?.role === 'TEACHER' && data.status === 'PENDING_APPROVAL' && (
              <>
                <button onClick={handleReject} className="tap-44 h-9 px-4 rounded-md border border-slate-300 bg-white text-sm text-slate-700 hover:bg-slate-50 transition-colors">Reddet</button>
                <button onClick={handleApprove} className="tap-44 h-9 px-4 rounded-md bg-slate-900 text-white text-sm flex items-center gap-2 hover:bg-slate-700 transition-colors"><Send className="h-4 w-4" /> Onayla ve yayınla</button>
              </>
            )}
            {data.status === 'PROCESSING' && (
              <span className="inline-flex items-center gap-1.5 text-sm text-slate-600"><Clock className="h-4 w-4" /> Okunuyor…</span>
            )}
            {data.status === 'FAILED' && (
              <span className="inline-flex items-center gap-1.5 text-sm text-red-600"><AlertCircle className="h-4 w-4" /> Analiz başarısız</span>
            )}
          </div>
        </div>
      </div>

      {/* ÖZET ŞERİDİ: karnede yazan sayılar; yalnızca toplama ve son değer, iş kuralı yok */}
      {(() => {
        const exams = getExamHistoryData();
        const lessons = getFilteredLessons();
        const sum = (k: 'correct' | 'wrong' | 'empty') => lessons.reduce((t, l) => t + (l[k] ?? 0), 0);
        const last = exams.length ? Number(exams[exams.length - 1].totalScore) : null;
        const avg = exams.length ? exams.reduce((t, e) => t + Number(e.totalScore), 0) / exams.length : null;
        const fmt = (v: number | null) => (v === null ? "–" : v.toLocaleString('tr-TR', { minimumFractionDigits: 1, maximumFractionDigits: 1 }));
        const stats: { label: string; value: string; tone?: string }[] = [
          { label: "Deneme", value: String(exams.length) },
          { label: "Net ortalaması", value: fmt(avg) },
          { label: "Son deneme neti", value: fmt(last) },
          { label: "Doğru", value: String(sum('correct')), tone: "text-emerald-700" },
          { label: "Yanlış", value: String(sum('wrong')), tone: "text-red-600" },
          { label: "Boş", value: String(sum('empty')), tone: "text-slate-500" },
        ];
        return (
          <div className="grid grid-cols-3 lg:grid-cols-6 gap-px rounded-xl border border-slate-200 bg-slate-200 overflow-hidden">
            {stats.map((st) => (
              <div key={st.label} className="bg-white px-5 py-4">
                <p className="text-xs text-slate-500">{st.label}</p>
                <p className={`mt-1 text-2xl font-semibold tracking-tight ${st.tone ?? 'text-slate-900'}`}>{st.value}</p>
              </div>
            ))}
          </div>
        );
      })()}

      <div className="flex items-center gap-6 border-b border-slate-200 overflow-x-auto [scrollbar-width:none]">
        <button onClick={() => setActiveTab('PERFORMANCE')} className={`tap-44 -mb-px h-11 px-1 border-b-2 text-sm flex items-center gap-2 transition-colors shrink-0 ${activeTab === 'PERFORMANCE' ? 'border-slate-900 text-slate-900 font-medium' : 'border-transparent text-slate-500 hover:text-slate-900'}`}>Performans</button>
        <button onClick={() => setActiveTab('MENTOR')} className={`tap-44 -mb-px h-11 px-1 border-b-2 text-sm flex items-center gap-2 transition-colors shrink-0 ${activeTab === 'MENTOR' ? 'border-slate-900 text-slate-900 font-medium' : 'border-transparent text-slate-500 hover:text-slate-900'}`}>Öncelikler</button>
        <button onClick={() => setActiveTab('TARGET')} className={`tap-44 -mb-px h-11 px-1 border-b-2 text-sm flex items-center gap-2 transition-colors shrink-0 ${activeTab === 'TARGET' ? 'border-slate-900 text-slate-900 font-medium' : 'border-transparent text-slate-500 hover:text-slate-900'}`}>Hedef ve tahmin</button>
      </div>

      <div className="animate-in fade-in slide-in-from-bottom-5 duration-500">
        {activeTab === 'PERFORMANCE' && (
          <div className="space-y-12">
             <Card className="rounded-xl border border-slate-200 shadow-none bg-white p-5 sm:p-10">
                <div className="flex flex-col md:flex-row items-center justify-between mb-8 sm:mb-10 gap-4 sm:gap-6">
                   <h4 className="text-lg sm:text-xl font-semibold text-slate-900 tracking-tight flex items-center gap-2 sm:gap-3"><BarChart3 className="h-5 w-5 sm:h-6 sm:w-6 text-blue-500" /> {performanceView === 'NET' ? 'Derslere göre doğru ve yanlış' : 'Toplam net, deneme sırasıyla'}</h4>
                   <div className="flex bg-slate-100 p-1 rounded-lg">
                      <button onClick={() => setPerformanceView('DATE')} className={`tap-44 px-3 py-1.5 rounded-md text-sm flex items-center gap-2 transition-colors ${performanceView === 'DATE' ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-900'}`}><Calendar className="h-3.5 w-3.5" /> Tarihe göre</button>
                      <button onClick={() => setPerformanceView('NET')} className={`tap-44 px-3 py-1.5 rounded-md text-sm flex items-center gap-2 transition-colors ${performanceView === 'NET' ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-900'}`}><TrendingUp className="h-3.5 w-3.5" /> Derslere göre</button>
                   </div>
                </div>
                <div className="h-[350px] sm:h-[400px] w-full">
                   <ResponsiveContainer width="100%" height="100%">
                      {performanceView === 'NET' ? (
                        <BarChart data={getFilteredLessons()}>
                           <CartesianGrid vertical={false} stroke="#e7e7ea" /><XAxis dataKey="lessonName" axisLine={false} tickLine={false} tick={{fontSize: 10, fontWeight: 500}} /><YAxis axisLine={false} tickLine={false} tick={{fontSize: 10, fontWeight: 500}} /><Tooltip contentStyle={{ borderRadius: '8px', border: '1px solid #e7e7ea', boxShadow: 'none', fontSize: 13 }} /><Bar dataKey="correct" name="Doğru" fill="#3e5aa8" radius={[3, 3, 0, 0]} barSize={22} /><Bar dataKey="wrong" name="Yanlış" fill="#e5484d" radius={[3, 3, 0, 0]} barSize={22} />
                        </BarChart>
                      ) : (
                        <AreaChart data={getExamHistoryData()}>
                           <defs><linearGradient id="colorNet" x1="0" y1="0" x2="0" y2="1"><stop offset="5%" stopColor="#3e5aa8" stopOpacity={0.12}/><stop offset="95%" stopColor="#3e5aa8" stopOpacity={0}/></linearGradient></defs>
                           <CartesianGrid vertical={false} stroke="#e7e7ea" /><XAxis dataKey="examDate" axisLine={false} tickLine={false} tick={{fontSize: 10, fontWeight: 500}} /><YAxis axisLine={false} tickLine={false} tick={{fontSize: 10, fontWeight: 500}} /><Tooltip contentStyle={{ borderRadius: '8px', border: '1px solid #e7e7ea', boxShadow: 'none', fontSize: 13 }} /><Area type="linear" dataKey="totalScore" name="Toplam Net" stroke="#3e5aa8" strokeWidth={2} dot={{ r: 3, fill: "#fff", stroke: "#3e5aa8", strokeWidth: 1.5 }} fillOpacity={1} fill="url(#colorNet)" />
                        </AreaChart>
                      )}
                   </ResponsiveContainer>
                </div>
             </Card>
             <div className="space-y-6">
                <h3 className="text-lg sm:text-xl font-semibold text-slate-900 tracking-tight ml-2 sm:ml-4 flex items-center gap-2 sm:gap-3"><Milestone className="h-5 w-5 sm:h-6 sm:w-6 text-blue-500" /> Sınav Tarihçesi</h3>
                <Card className="rounded-xl border border-slate-200 shadow-none bg-white overflow-hidden">
                   <div className="overflow-x-auto">
                      <table className="w-full min-w-[540px] text-left border-collapse">
                         <thead>
                            <tr className="bg-slate-50 border-b border-slate-100">
                               <th className="px-5 py-3 text-xs font-semibold tracking-normal text-slate-500">Sınav Adı</th>
                               <th className="px-5 py-3 text-xs font-semibold tracking-normal text-slate-500">Tarih</th>
                               <th className="px-5 py-3 text-xs font-semibold tracking-normal text-slate-500 text-center">Net</th>
                               <th className="px-5 py-3 text-xs font-semibold tracking-normal text-slate-500 text-right">Durum</th>
                            </tr>
                         </thead>
                         <tbody>
                            {getExamHistoryData().map((exam, i) => (
                               <tr key={i} className="border-b border-slate-50 hover:bg-slate-50/50 transition-colors">
                                  <td className="px-5 py-3 font-semibold text-sm text-slate-900">{exam.examName}</td>
                                  <td className="px-5 py-3 text-xs font-medium text-slate-500">{exam.examDate}</td>
                                  <td className="px-5 py-3 text-center"><span className="text-lg font-semibold text-blue-600">{Number(exam.totalScore).toFixed(1)}</span></td>
                                  <td className="px-5 py-3 text-right"><span className="bg-emerald-50 text-emerald-600 text-xs font-semibold tracking-normal px-3 py-1.5 rounded-lg whitespace-nowrap">Analiz Edildi</span></td>
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
               <button onClick={() => setShowOnlyProblems(!showOnlyProblems)} className={`tap-44 h-9 px-4 rounded-md text-sm flex items-center gap-2 transition-colors border ${showOnlyProblems ? 'bg-slate-900 border-slate-900 text-white' : 'bg-white border-slate-300 text-slate-700 hover:bg-slate-50'}`}><Filter className="h-4 w-4" />{showOnlyProblems ? 'Tüm konuları göster' : 'Yalnızca sorunlu konular'}</button>
             </div>

             {/* T-068: Stratejik öncelik ve aksiyon planı kartları küçültüldü; ekranın yarısını kaplayan
                 gradyan pano, sayfanın asıl içeriği olan konu listesinin önüne geçiyordu. */}
             {data.strategicPriority && (
               <Card className="rounded-xl border border-blue-100 shadow-sm bg-white p-5 sm:p-6">
                  <div className="flex items-start gap-4">
                     <div className="w-10 h-10 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center shrink-0"><Lightbulb className="h-5 w-5" /></div>
                     <div className="min-w-0 space-y-1">
                        <h3 className="text-xs text-slate-500">Öncelikli ders</h3>
                        <h2 className="text-base sm:text-lg font-semibold tracking-tight leading-snug text-slate-900">{data.strategicPriority}</h2>
                     </div>
                  </div>
               </Card>
             )}

             {/* ÖĞRETMEN ÖZEL AKSİYON PLANI (SADECE ÖĞRETMEN/MANAGER GÖRÜR) */}
             {(user?.role === 'TEACHER' || user?.role === 'MANAGER') && data.teacherActionPlan && (
               <Card className="rounded-xl border border-slate-200 shadow-none bg-white p-5 sm:p-6 space-y-4">
                  <div className="flex items-center gap-3">
                     <div className="w-9 h-9 bg-slate-100 text-slate-600 rounded-md flex items-center justify-center shrink-0"><Zap className="h-4 w-4" /></div>
                     <div className="min-w-0">
                        <h4 className="font-semibold text-sm text-slate-900">Öğretmen için not</h4>
                        <p className="text-xs text-slate-500 mt-0.5">Sadece sizin görebileceğiniz strateji notu</p>
                     </div>
                  </div>
                  <p className="text-sm font-medium leading-relaxed text-slate-700 whitespace-pre-line">{data.teacherActionPlan}</p>
                  <p className="text-xs text-slate-400">* Bu bilgiler karne verilerinden öğretmenin stratejik planlaması için üretilmiştir.</p>
               </Card>
             )}

             <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 items-start">
                <Card className="rounded-xl border border-slate-200 shadow-none bg-white p-6 sm:p-10 space-y-7 lg:sticky lg:top-8">
                  <div className="space-y-1">
                    <h4 className="font-semibold text-sm tracking-normal text-slate-900">Önce Şu Konular</h4>
                    <p className="text-xs font-medium text-slate-400 leading-relaxed">
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
                      return (<Card key={lesson.id} className="rounded-xl border-0 shadow-sm bg-white overflow-hidden"><div onClick={() => toggleLesson(lesson.id)} className="bg-slate-50 px-6 sm:px-8 py-5 sm:py-6 flex justify-between items-center cursor-pointer hover:bg-slate-100 transition-colors border-b border-slate-100 group"><div className="flex items-center gap-3 sm:gap-4"><div className={`p-2 rounded-xl transition-all ${isExpanded ? 'bg-blue-600 text-white rotate-180' : 'bg-white text-slate-400 group-hover:bg-blue-50'}`}><ChevronDown className="h-5 w-5" /></div><span className="font-semibold text-xs tracking-normal text-slate-700">{lesson.lessonName}</span></div><span className="text-xs font-semibold text-blue-600 bg-blue-50 px-3 py-1.5 rounded-lg">%{lesson.successRate} Başarı</span></div>{isExpanded && (<CardContent className="p-5 sm:p-8 space-y-6 animate-in slide-in-from-top-2 duration-300">{Object.keys(groups).map(mainTitle => (<div key={mainTitle} className="space-y-3"><div className="flex items-center gap-3 px-2"><Layers className="h-4 w-4 text-slate-400" /><span className="font-semibold text-xs tracking-normal text-slate-900">{mainTitle}</span></div><div className="space-y-2 border-l-2 border-slate-100 ml-4 pl-4">{groups[mainTitle].map(topic => { const ui = getStatusUI(topic); const isHigh = highlightedTopicId === topic.id; return (<div key={topic.id} ref={el => { scrollRefs.current[topic.id] = el; }} className={`flex items-center justify-between group p-3 rounded-xl transition-all duration-700 ${isHigh ? 'bg-yellow-400/40 scale-105 shadow-[0_0_30px_rgba(250,204,21,0.6)] border-2 border-yellow-400 z-20' : 'hover:bg-slate-50'}`}><div className="flex items-center gap-3"><div className={`${ui.color} ${ui.bg} p-1.5 rounded-lg shadow-sm`}>{ui.icon}</div><span className={`font-medium text-xs ${isHigh ? 'text-slate-900' : 'text-slate-600'}`}>{topic.topicName.split(" - ").pop()}</span></div><div className="text-xs font-semibold text-slate-400">SS: {topic.totalQuestions || '--'} | D: {topic.correctCount || 0}</div></div>); })}</div></div>))}{standalone.length > 0 && (<div className="space-y-2 pt-4 border-t border-slate-50">{standalone.map(topic => { const ui = getStatusUI(topic); const isHigh = highlightedTopicId === topic.id; return (<div key={topic.id} ref={el => { scrollRefs.current[topic.id] = el; }} className={`flex items-center justify-between group p-3 rounded-xl transition-all duration-700 ${isHigh ? 'bg-yellow-400/40 scale-105 shadow-[0_0_30px_rgba(250,204,21,0.6)] border-2 border-yellow-400 z-20' : 'hover:bg-slate-50'}`}><div className="flex items-center gap-3"><div className={`${ui.color} ${ui.bg} p-1.5 rounded-lg shadow-sm`}>{ui.icon}</div><span className={`font-medium text-xs ${isHigh ? 'text-slate-900' : 'text-slate-600'}`}>{topic.topicName}</span></div><div className="text-xs font-semibold text-slate-400">SS: {topic.totalQuestions || '--'} | D: {topic.correctCount || 0}</div></div>); })}</div>)}</CardContent>)}</Card>);
                   })}
                </div>
             </div>
          </div>
        )}

        {activeTab === 'TARGET' && (
          <div className="space-y-12">
             {/* OKUL SEÇME MODALI */}
             {isSearchModalOpen && (
               <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 sm:p-6 bg-slate-900/60 animate-in fade-in duration-300">
                  <Card className="w-full max-w-2xl rounded-xl sm:rounded-xl border-0 shadow-sm bg-white overflow-hidden">
                     <div className="p-6 sm:p-10 space-y-6 sm:space-y-8">
                        <div className="flex items-center justify-between">
                           <div className="flex items-center gap-4">
                              <div className="w-10 h-10 sm:w-12 sm:h-12 bg-cyan-50 text-cyan-600 rounded-lg flex items-center justify-center shadow-sm shadow-cyan-100"><School className="h-5 w-5 sm:h-6 sm:w-6" /></div>
                              <h4 className="font-semibold text-lg sm:text-xl text-slate-900 tracking-tight">Hedef Okul Seç</h4>
                           </div>
                           <button onClick={() => setIsSearchModalOpen(false)} aria-label="Kapat" className="tap-44 min-w-[44px] min-h-[44px] flex items-center justify-center p-2.5 text-slate-400 hover:text-slate-900 transition-colors"><Trash2 className="h-5 w-5" /></button>
                        </div>
                        <div className="relative">
                           <input type="text" placeholder="Okul adı veya şehir ara..." className="w-full bg-slate-50 border-2 border-slate-100 rounded-xl px-5 sm:px-8 py-4 sm:py-5 font-medium text-slate-700 focus:outline-none focus:border-cyan-500 transition-all" value={searchQuery} onChange={(e) => handleSchoolSearch(e.target.value)} autoFocus />
                           {searchResults.length > 0 && (
                             <div className="absolute top-full left-0 right-0 mt-4 bg-white border border-slate-100 rounded-xl shadow-sm overflow-hidden z-20 max-h-[300px] overflow-y-auto">
                                {searchResults.map((school) => (
                                  <button key={school.id} onClick={() => selectTargetSchool(school.id)} className="w-full px-5 sm:px-8 py-4 sm:py-5 text-left hover:bg-slate-50 border-b border-slate-50 last:border-0 transition-colors group flex items-center justify-between">
                                     <div><p className="font-semibold text-slate-900 group-hover:text-cyan-600 transition-colors">{school.schoolName}</p><p className="text-xs text-slate-500">{school.city} • {school.schoolType}</p></div>
                                     <div className="text-right"><p className="font-semibold text-cyan-600">Puan: {school.baseScore}</p><p className="text-xs font-medium text-slate-300">Yüzdelik: %{school.percentile}</p></div>
                                  </button>
                                ))}
                             </div>
                           )}
                        </div>
                        <p className="text-xs font-semibold text-slate-400 tracking-normal text-center">* 2025 LGS TABAN PUANLARI REFERANS ALINMAKTADIR</p>
                     </div>
                  </Card>
               </div>
             )}

             <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
                {/* HEDEF OKUL KARTI */}
                <Card
                  onClick={() => user?.role === 'STUDENT' && setIsSearchModalOpen(true)}
                  className={`lg:col-span-2 rounded-xl border border-slate-200 shadow-none bg-white p-6 sm:p-8 transition-colors ${user?.role === 'STUDENT' ? 'cursor-pointer hover:border-slate-300' : 'cursor-default'}`}
                >
                  <div className="flex items-start justify-between gap-4">
                    <div className="flex items-center gap-3 min-w-0">
                      <School className="h-5 w-5 text-slate-400 shrink-0" />
                      <h3 className="text-sm text-slate-500">Hedef okul</h3>
                    </div>
                    {user?.role === 'STUDENT' && <span className="tap-44 inline-flex items-center text-sm text-slate-600 border border-slate-300 rounded-md px-3 h-8">Hedefi değiştir</span>}
                  </div>
                  <h2 className="mt-3 text-2xl sm:text-3xl font-semibold tracking-tight text-slate-900 truncate">{data.targetComparison?.schoolName || data.targetSchoolName || "Hedef belirlenmedi"}</h2>
                  {data.targetComparison && (
                    <dl className="mt-6 grid grid-cols-3 gap-px bg-slate-200 rounded-lg overflow-hidden border border-slate-200">
                      <div className="bg-white px-4 py-3"><dt className="text-xs text-slate-500">Taban puan</dt><dd className="mt-1 text-lg font-semibold text-slate-900">{Number(data.targetComparison.targetScore).toFixed(1)}</dd></div>
                      <div className="bg-white px-4 py-3"><dt className="text-xs text-slate-500">Net ortalaması</dt><dd className="mt-1 text-lg font-semibold text-slate-900">{data.targetComparison.averageNet.toFixed(2)}</dd></div>
                      <div className="bg-white px-4 py-3"><dt className="text-xs text-slate-500">Tahmini LGS puanı</dt><dd className="mt-1 text-lg font-semibold text-cyan-700">≈{data.targetComparison.predictedLgsScore.toFixed(1)}</dd></div>
                    </dl>
                  )}
                </Card>

                {/* UYUM SKORU KARTI */}
                <Card className="rounded-xl border border-slate-200 shadow-none bg-white p-6 sm:p-12 flex flex-col items-center justify-center text-center space-y-6 relative overflow-hidden">
                   <div className={`w-28 h-28 sm:w-32 sm:h-32 rounded-full border-[6px] flex items-center justify-center transition-all duration-1000 ${data.targetComparison ? (data.targetComparison.compatibilityPercent > 80 ? 'border-emerald-500 text-emerald-600' : data.targetComparison.compatibilityPercent > 50 ? 'border-amber-500 text-amber-600' : 'border-red-500 text-red-600') : 'border-slate-200 text-slate-400'}`}>
                      <span className="text-2xl sm:text-3xl font-semibold">%{data.targetComparison ? data.targetComparison.compatibilityPercent : 0}</span>
                   </div>
                   <div><h4 className="font-semibold text-slate-900 text-sm tracking-normal">Hedef uyumu</h4><p className="text-xs text-slate-500 mt-1">Tahmini puanın taban puana oranı</p></div>
                </Card>
             </div>

             <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 sm:gap-12">
                {/* Projeksiyon yalnızca hesaplanmışsa gösterilir */}
                {data.futureProjection && (
                <Card className="rounded-xl border border-slate-200 shadow-none bg-white p-6 sm:p-12 space-y-6 sm:space-y-8 relative overflow-hidden">
                   <div className="relative z-10 flex items-center gap-4 sm:gap-6"><Compass className="h-8 w-8 sm:h-10 sm:w-10 text-emerald-400" /><h4 className="font-semibold text-sm tracking-normal">Gelecek Projeksiyonu</h4></div>
                   <p className="relative z-10 text-slate-700 leading-relaxed text-lg">"{data.futureProjection}"</p>
                </Card>
                )}
                <div className="space-y-8">
                   <Card className="rounded-xl border border-slate-200 shadow-none bg-white p-6 sm:p-12 space-y-6 sm:space-y-8">
                      <div className="flex items-center gap-4"><div className="w-10 h-10 sm:w-12 sm:h-12 bg-red-50 text-red-500 rounded-lg flex items-center justify-center"><ArrowUpRight className="h-5 w-5 sm:h-6 sm:w-6" /></div><h4 className="font-semibold text-sm tracking-normal text-slate-900">Hedefe kalan</h4></div>
                      <div className="space-y-6">
                         {data.targetComparison ? (
                           data.targetComparison.scoreGap > 0 ? (
                             <div className="p-6 sm:p-8 bg-red-50 rounded-xl sm:rounded-xl border-2 border-red-100"><p className="text-red-900 font-medium text-base sm:text-lg leading-snug">Hedeflediğin okula ulaşmak için projeksiyonunun <span className="text-xl sm:text-2xl font-semibold">+{data.targetComparison.scoreGap.toFixed(1)}</span> puan (≈{data.targetComparison.netGap.toFixed(1)} net) daha üzerine çıkmalısın.</p></div>
                           ) : (
                             <div className="p-6 sm:p-8 bg-emerald-50 rounded-xl sm:rounded-xl border-2 border-emerald-100"><p className="text-emerald-900 font-medium text-base sm:text-lg leading-snug">Tebrikler! Mevcut performansın hedeflediğin okulun <span className="text-xl sm:text-2xl font-semibold">{Math.abs(data.targetComparison.scoreGap).toFixed(1)}</span> puan üzerinde.</p></div>
                           )
                         ) : (<p className="text-slate-400 font-medium">Hedef analizi için lütfen profilinden bir okul seç.</p>)}
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
