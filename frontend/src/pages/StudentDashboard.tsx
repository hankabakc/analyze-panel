import { useState, useEffect, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import { 
  membershipApi, 
  analysisApi, 
  studyPlansApi, 
  psychTestsApi,
  type StudyPlanDto, 
  type MyPsychTestAssignmentDto 
} from "@/lib/api";
import { StudentPsychTestModal } from "@/features/psychtest/components/StudentPsychTestModal";
import { StudentBourdonTestModal } from "@/features/psychtest/components/StudentBourdonTestModal";
import { useAuth } from "@/lib/auth-context";
import axios from "axios";
import type { AnalysisReportSummary, ReferenceSchool, StudentProfile, User } from "@/lib/types";
import { toast } from "sonner";
import { Briefcase, FileBarChart2, ChevronRight, Target, School, Search, Trophy, X, MapPin, Calendar, CheckCircle, CheckCircle2, Clock, BookOpen, AlertCircle, Loader2, ClipboardCheck } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";

export default function StudentDashboard() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [teachers, setTeachers] = useState<User[]>([]);
  const [reports, setReports] = useState<AnalysisReportSummary[]>([]);
  const [profile, setProfile] = useState<StudentProfile | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // Psikolojik Ölçek State'leri (T-052C)
  const [psychAssignments, setPsychAssignments] = useState<MyPsychTestAssignmentDto[]>([]);
  const [isLoadingPsych, setIsLoadingPsych] = useState(true);
  const [activePsychAssignment, setActivePsychAssignment] = useState<MyPsychTestAssignmentDto | null>(null);
  
  // Çalışma Planı State'leri (T-050C)
  const [studyPlans, setStudyPlans] = useState<StudyPlanDto[]>([]);
  const [isLoadingPlans, setIsLoadingPlans] = useState(true);
  const [completingItemIds, setCompletingItemIds] = useState<string[]>([]);

  // Hedef Belirleme Modal State'leri
  const [showTargetModal, setShowTargetModal] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [schools, setSchools] = useState<ReferenceSchool[]>([]);
  const [isSearching, setIsSchoolsSearching] = useState(false);

  const fetchData = useCallback(async () => {
    if (!user) return;
    try {
      const [teachersRes, reportsRes, profileRes, studyPlansRes, psychRes] = await Promise.all([
        membershipApi.getMyTeachers(),
        analysisApi.getStudentReports(user.id),
        analysisApi.getProfile(),
        studyPlansApi.getPlansForStudent(user.id),
        psychTestsApi.getMyAssignments()
      ]);
      
      if (teachersRes.data.success) setTeachers(teachersRes.data.data);
      if (reportsRes.data.success) setReports(reportsRes.data.data);
      if (profileRes.data.success) setProfile(profileRes.data.data);
      if (studyPlansRes.data.success) setStudyPlans(studyPlansRes.data.data);
      if (psychRes.data.success) setPsychAssignments(psychRes.data.data);
    } catch {
      toast.error("Verileriniz yüklenirken bir hata oluştu.");
    } finally {
      setIsLoading(false);
      setIsLoadingPlans(false);
      setIsLoadingPsych(false);
    }
  }, [user]);

  const handleCompleteItem = async (itemId: string) => {
    if (completingItemIds.includes(itemId)) return;

    setCompletingItemIds(prev => [...prev, itemId]);
    try {
      const res = await studyPlansApi.completeItem(itemId);
      if (res.data.success) {
        const updatedItem = res.data.data;
        // İstemci zaman damgası uydurmaz, sunucudan dönen completedAt kullanılır (APP-01 §2.1)
        setStudyPlans(prevPlans =>
          prevPlans.map(plan => {
            if (plan.items.some(item => item.id === itemId)) {
              return {
                ...plan,
                items: plan.items.map(item =>
                  item.id === itemId ? updatedItem : item
                )
              };
            }
            return plan;
          })
        );
        toast.success("Tebrikler! Görev tamamlandı olarak işaretlendi.");
      }
    } catch (error) {
      const msg = (axios.isAxiosError(error) && error.response?.data?.message) || "Görev işaretlenirken bir hata oluştu.";
      toast.error(msg);
    } finally {
      setCompletingItemIds(prev => prev.filter(id => id !== itemId));
    }
  };

  const handleSearchSchools = async (query: string) => {
    setSearchQuery(query);
    if (query.length < 3) {
      setSchools([]);
      return;
    }
    setIsSchoolsSearching(true);
    try {
      const res = await analysisApi.searchSchools(query);
      if (res.data.success) setSchools(res.data.data);
    } catch (error) {
      console.error(error);
    } finally {
      setIsSchoolsSearching(false);
    }
  };

  const selectSchool = async (school: ReferenceSchool) => {
    try {
      const res = await analysisApi.updateProfile(school.id, school.baseScore);
      if (res.data.success) {
        toast.success(`Hedef okul ${school.schoolName} olarak güncellendi!`);
        setShowTargetModal(false);
        fetchData();
      }
    } catch {
      toast.error("Hedef güncellenemedi.");
    }
  };

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  if (isLoading) return (
    <div className="w-full flex flex-col items-center justify-center gap-6 py-32">
      <div className="w-10 h-10 border-[5px] border-cyan-500 border-t-transparent rounded-full animate-spin"></div>
      <p className="text-[10px] font-black uppercase tracking-[0.4em] text-slate-400">Öğrenci Paneli Yükleniyor</p>
    </div>
  );

  return (
    <div className="w-full space-y-12 animate-in fade-in duration-700 pb-20">
      
      {/* ÜST KARŞILAMA VE HEDEF KARTI */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6 sm:gap-8">
        <div className="flex flex-col justify-center gap-2">
          <h2 className="text-2xl sm:text-3xl lg:text-4xl font-black text-slate-900 tracking-tighter uppercase">
            Merhaba, <span className="text-cyan-500 italic">{user?.fullName.split(' ')[0]}</span>
          </h2>
          <p className="text-[10px] font-black text-slate-400 uppercase tracking-[0.4em]">Akademik Gelişim Merkezi</p>
        </div>

        <Card className="rounded-[2rem] sm:rounded-[3rem] border-0 shadow-2xl bg-gradient-to-br from-slate-900 to-slate-800 text-white p-5 sm:p-8 relative overflow-hidden group">
           <div className="absolute top-0 right-0 w-64 h-64 bg-cyan-500/10 rounded-full blur-3xl -mr-20 -mt-20"></div>
           <div className="relative z-10 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div className="flex items-center gap-4 sm:gap-6 min-w-0">
                 <div className="w-12 h-12 sm:w-16 sm:h-16 rounded-[1.2rem] sm:rounded-[1.5rem] bg-cyan-500/20 border border-cyan-500/30 flex items-center justify-center shrink-0">
                    <Target className="h-6 w-6 sm:h-8 sm:w-8 text-cyan-400" />
                 </div>
                 <div className="min-w-0">
                    <h4 className="text-[10px] font-black uppercase tracking-[0.2em] text-cyan-400 mb-1">Akademik Hedefin</h4>
                    <p className="font-bold text-base sm:text-lg leading-tight truncate">
                       {profile?.targetSchoolName ? profile.targetSchoolName : (profile?.targetScore ? `Hedef Puan: ${profile.targetScore}` : 'Henüz hedef belirlenmedi')}
                    </p>
                 </div>
              </div>
              <button 
                onClick={() => setShowTargetModal(true)}
                className="tap-44 min-h-[44px] self-start sm:self-auto bg-white text-slate-900 px-6 py-3 rounded-2xl font-black text-[10px] uppercase tracking-widest hover:bg-cyan-400 hover:text-white transition-all shadow-xl shadow-black/20 shrink-0"
              >
                {profile?.targetSchoolId ? 'Hedefi Güncelle' : 'Hedef Belirle'}
              </button>
           </div>
        </Card>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 sm:gap-12">
        <div className="lg:col-span-2 space-y-12">
          {/* ÇALIŞMA PLANIM BÖLÜMÜ (T-050C) */}
          <div className="space-y-6">
            <div className="flex items-center justify-between">
              <h3 className="text-xl font-black text-slate-900 tracking-tight flex items-center gap-3">
                <Calendar className="h-6 w-6 text-cyan-500" /> Çalışma Planım
              </h3>
              {studyPlans.length > 0 && (
                <span className="text-xs font-bold text-slate-400">
                  Toplam {studyPlans.length} Plan
                </span>
              )}
            </div>

            {isLoadingPlans ? (
              <Card className="rounded-[2.5rem] border border-slate-100 bg-white p-12 text-center shadow-sm">
                <div className="flex flex-col items-center justify-center gap-3">
                  <Loader2 className="h-8 w-8 animate-spin text-cyan-500" />
                  <p className="text-xs font-black uppercase tracking-widest text-slate-400">Çalışma Planları Yükleniyor...</p>
                </div>
              </Card>
            ) : studyPlans.length === 0 ? (
              <Card className="rounded-[2.5rem] sm:rounded-[3rem] border-2 border-dashed border-slate-200 bg-slate-50/50 p-10 sm:p-14 text-center">
                <p className="text-slate-400 font-black text-xs uppercase tracking-widest">
                  Henüz sana atanmış bir çalışma planı bulunmuyor.
                </p>
              </Card>
            ) : (
              <div className="space-y-4">
                {studyPlans.map(plan => {
                  const completedCount = plan.items.filter(i => i.completedAt).length;
                  const totalCount = plan.items.length;
                  const isAllCompleted = totalCount > 0 && completedCount === totalCount;
                  const isPastDue = new Date(plan.dueDate + 'T23:59:59') < new Date();

                  return (
                    <Card
                      key={plan.id}
                      className="rounded-[2rem] border border-slate-200/80 bg-white shadow-sm hover:shadow-md transition-all overflow-hidden"
                    >
                      <CardContent className="p-5 sm:p-7 space-y-4">
                        {/* Plan Başlık Çubuğu */}
                        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-slate-100 pb-4">
                          <div className="flex items-center gap-2.5">
                            <Calendar className="h-4 w-4 text-cyan-600 shrink-0" />
                            <span className="font-black text-sm text-slate-900">
                              Teslim Tarihi: {new Date(plan.dueDate).toLocaleDateString('tr-TR', { day: 'numeric', month: 'long', year: 'numeric' })}
                            </span>
                          </div>

                          <div className="flex items-center gap-2 flex-wrap">
                            {isPastDue && !isAllCompleted && (
                              <span className="px-2.5 py-1 rounded-lg text-xs font-black bg-rose-50 text-rose-700 border border-rose-200/60 flex items-center gap-1">
                                <AlertCircle className="h-3.5 w-3.5" /> Süresi Geçti
                              </span>
                            )}
                            <span className={`px-2.5 py-1 rounded-lg text-xs font-bold flex items-center gap-1.5 ${
                              isAllCompleted
                                ? "bg-emerald-50 text-emerald-700 border border-emerald-200/60"
                                : "bg-cyan-50 text-cyan-700 border border-cyan-200/60"
                            }`}>
                              {isAllCompleted ? (
                                <>
                                  <CheckCircle className="h-3.5 w-3.5 text-emerald-600" /> Tamamlandı
                                </>
                              ) : (
                                <>
                                  <Clock className="h-3.5 w-3.5 text-cyan-600" /> {completedCount}/{totalCount} Tamamlandı
                                </>
                              )}
                            </span>
                          </div>
                        </div>

                        {/* Plan Kalemleri Listesi */}
                        <div className="space-y-2.5">
                          {plan.items.map(item => {
                            const isCompleted = !!item.completedAt;
                            const isCompleting = completingItemIds.includes(item.id);

                            return (
                              <div
                                key={item.id}
                                className={`p-3.5 sm:p-4 rounded-xl border flex flex-col sm:flex-row sm:items-center justify-between gap-3 transition-all ${
                                  isCompleted
                                    ? "bg-slate-50/60 border-slate-100"
                                    : "bg-white border-slate-200/70 hover:border-cyan-200 shadow-xs"
                                }`}
                              >
                                <div className="flex items-center gap-3 min-w-0">
                                  <div className={`w-8 h-8 rounded-lg flex items-center justify-center shrink-0 ${
                                    isCompleted ? "bg-emerald-100 text-emerald-700" : "bg-cyan-50 text-cyan-600"
                                  }`}>
                                    <BookOpen className="h-4 w-4" />
                                  </div>
                                  <div className="min-w-0">
                                    <h5 className={`font-bold text-sm truncate ${
                                      isCompleted ? "text-slate-500 line-through decoration-slate-300" : "text-slate-900"
                                    }`} title={item.topicName}>
                                      {item.topicName}
                                    </h5>
                                    <p className="text-[11px] font-black text-slate-500">
                                      Hedef: <span className="text-cyan-600 font-bold">{item.questionCount} Soru</span>
                                    </p>
                                  </div>
                                </div>

                                <div className="flex items-center justify-end shrink-0 pt-2 sm:pt-0 border-t sm:border-t-0 border-slate-100">
                                  {isCompleted ? (
                                    <span className="tap-44 min-h-[44px] px-3.5 py-2 rounded-xl bg-emerald-50 border border-emerald-200/60 text-emerald-700 text-xs font-black flex items-center gap-1.5">
                                      <CheckCircle className="h-4 w-4 text-emerald-600" /> Çözüldü
                                    </span>
                                  ) : (
                                    <button
                                      type="button"
                                      disabled={isCompleting}
                                      onClick={() => handleCompleteItem(item.id)}
                                      className="tap-44 min-h-[44px] px-5 py-2 bg-cyan-500 hover:bg-cyan-600 active:bg-cyan-700 text-white rounded-xl font-black text-xs uppercase tracking-wider transition-all shadow-sm flex items-center gap-2 disabled:opacity-50 focus-visible:ring-2 focus-visible:ring-cyan-500 focus-visible:outline-none"
                                    >
                                      {isCompleting ? (
                                        <>
                                          <Loader2 className="h-4 w-4 animate-spin" /> İşleniyor...
                                        </>
                                      ) : (
                                        <>
                                          <CheckCircle2 className="h-4 w-4" /> Çözdüm
                                        </>
                                      )}
                                    </button>
                                  )}
                                </div>
                              </div>
                            );
                          })}
                        </div>
                      </CardContent>
                    </Card>
                  );
                })}
              </div>
            )}
          </div>

          {/* PSİKOLOJİK ÖLÇEKLERİM BÖLÜMÜ (T-052C) */}
          <div className="space-y-6">
            <div className="flex items-center justify-between">
              <h3 className="text-xl font-black text-slate-900 tracking-tight flex items-center gap-3">
                <ClipboardCheck className="h-6 w-6 text-cyan-500" /> Psikolojik Ölçeklerim
              </h3>
              {psychAssignments.length > 0 && (
                <span className="text-xs font-bold text-slate-400">
                  Toplam {psychAssignments.length} Ölçek
                </span>
              )}
            </div>

            {isLoadingPsych ? (
              <Card className="rounded-[2.5rem] border border-slate-100 bg-white p-12 text-center shadow-sm">
                <div className="flex flex-col items-center justify-center gap-3">
                  <Loader2 className="h-8 w-8 animate-spin text-cyan-500" />
                  <p className="text-xs font-black uppercase tracking-widest text-slate-400">Ölçekler Yükleniyor...</p>
                </div>
              </Card>
            ) : psychAssignments.length === 0 ? (
              <Card className="rounded-[2.5rem] sm:rounded-[3rem] border-2 border-dashed border-slate-200 bg-slate-50/50 p-10 sm:p-14 text-center">
                <p className="text-slate-400 font-black text-xs uppercase tracking-widest">
                  Henüz sana atanmış bir psikolojik test veya ölçek bulunmuyor.
                </p>
              </Card>
            ) : (
              <div className="space-y-4" data-testid="psych-tests-section">
                {psychAssignments.map(assignment => {
                  const isCompleted = assignment.status === "COMPLETED";
                  const formattedDate = new Date(assignment.assignedAt).toLocaleDateString("tr-TR", { day: "numeric", month: "long", year: "numeric" });
                  const completedDate = assignment.completedAt ? new Date(assignment.completedAt).toLocaleDateString("tr-TR", { day: "numeric", month: "long", year: "numeric" }) : null;

                  return (
                    <Card
                      key={assignment.id}
                      data-testid={`psych-assignment-${assignment.id}`}
                      className="rounded-[2rem] border border-slate-200/80 bg-white shadow-sm hover:shadow-md transition-all overflow-hidden"
                    >
                      <CardContent className="p-5 sm:p-7 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                        <div className="flex items-start gap-4 min-w-0">
                          <div className={`w-12 h-12 rounded-2xl flex items-center justify-center shrink-0 ${
                            isCompleted ? "bg-emerald-50 text-emerald-600" : "bg-cyan-50 text-cyan-600"
                          }`}>
                            <ClipboardCheck className="h-6 w-6" />
                          </div>
                          <div className="min-w-0 space-y-1">
                            <div className="flex items-center gap-2.5 flex-wrap">
                              <h4 className="text-base sm:text-lg font-black text-slate-900 tracking-tight">
                                {assignment.testTitle || assignment.testCode}
                              </h4>
                              {isCompleted ? (
                                <span 
                                  data-testid={`assignment-completed-badge-${assignment.id}`}
                                  className="px-2.5 py-0.5 rounded-full text-xs font-bold bg-emerald-50 text-emerald-700 border border-emerald-200/60 flex items-center gap-1"
                                >
                                  <CheckCircle className="h-3.5 w-3.5 text-emerald-600" /> Tamamlandı
                                </span>
                              ) : (
                                <span 
                                  data-testid={`assignment-pending-badge-${assignment.id}`}
                                  className="px-2.5 py-0.5 rounded-full text-xs font-bold bg-amber-50 text-amber-700 border border-amber-200/60 flex items-center gap-1"
                                >
                                  <Clock className="h-3.5 w-3.5 text-amber-600" /> Bekliyor
                                </span>
                              )}
                            </div>
                            <p className="text-[10px] font-black text-slate-400 uppercase tracking-wider">
                              Atanma Tarihi: {formattedDate}
                              {completedDate && ` • Tamamlanma: ${completedDate}`}
                            </p>
                          </div>
                        </div>

                        <div className="flex items-center justify-end shrink-0 pt-3 sm:pt-0 border-t sm:border-t-0 border-slate-100">
                          {isCompleted ? (
                            <span className="tap-44 min-h-[44px] px-4 py-2 text-xs font-black text-slate-400 flex items-center gap-1.5">
                              <CheckCircle className="h-4 w-4 text-emerald-600" /> Yanıtlandı
                            </span>
                          ) : (
                            <button
                              type="button"
                              data-testid={assignment.testCode === 'BOURDON' ? `start-bourdon-btn-${assignment.id}` : `start-test-btn-${assignment.id}`}
                              onClick={() => setActivePsychAssignment(assignment)}
                              className="tap-44 min-h-[44px] px-6 py-2.5 bg-cyan-500 hover:bg-cyan-600 active:bg-cyan-700 text-white rounded-xl font-black text-xs uppercase tracking-wider transition-all shadow-sm shadow-cyan-500/20 flex items-center gap-2 focus-visible:ring-2 focus-visible:ring-cyan-500 focus-visible:outline-none cursor-pointer"
                            >
                              {assignment.testCode === 'BOURDON'
                                ? (assignment.status === 'IN_PROGRESS' ? 'Teste Devam Et' : 'Teste Başla')
                                : 'Testi Doldur'}
                            </button>
                          )}
                        </div>
                      </CardContent>
                    </Card>
                  );
                })}
              </div>
            )}
          </div>

          {/* ANALİZ RAPORLARIM BÖLÜMÜ */}
          <div className="space-y-6">
            <h3 className="text-xl font-black text-slate-900 tracking-tight flex items-center gap-3"><FileBarChart2 className="h-6 w-6 text-cyan-500" /> Analiz Raporlarım</h3>
           {reports.length === 0 ? (
             <Card className="rounded-[3rem] sm:rounded-[4rem] border-2 border-dashed border-slate-200 bg-slate-50/50 p-12 sm:p-24 text-center">
                <p className="text-slate-400 font-black text-[10px] uppercase tracking-[0.4em]">Henüz bir analiz raporun bulunmuyor.</p>
             </Card>
           ) : (
             <div className="grid grid-cols-1 gap-4">
                {reports.map(report => (
                  <Card key={report.id} onClick={() => navigate(`/analysis/${report.id}`)} className="rounded-[2rem] sm:rounded-[2.5rem] border-0 shadow-lg hover:shadow-xl transition-all bg-white group cursor-pointer overflow-hidden">
                    <CardContent className="p-5 sm:p-8 flex items-center justify-between">
                       <div className="flex items-center gap-4 sm:gap-6 min-w-0">
                          <div className="w-12 h-12 sm:w-14 sm:h-14 bg-cyan-50 text-cyan-600 rounded-2xl flex items-center justify-center shrink-0"><FileBarChart2 className="h-6 w-6" /></div>
                          <div className="min-w-0">
                             <h4 className="text-base sm:text-lg font-black text-slate-900 tracking-tight truncate">{report.examTitle}</h4>
                             <p className="text-[9px] font-black text-slate-400 uppercase tracking-widest mt-1">
                                {new Date(report.processedAt).toLocaleDateString('tr-TR')} • {report.intendedExamCount} Deneme Verisi
                             </p>
                          </div>
                       </div>
                       <ChevronRight className="h-6 w-6 text-slate-300 group-hover:text-cyan-500 transition-colors" />
                    </CardContent>
                  </Card>
                ))}
              </div>
            )}
          </div>
        </div>

        <div className="lg:col-span-1 space-y-8">
           <h3 className="text-xl font-black text-slate-900 tracking-tight flex items-center gap-3"><Briefcase className="h-6 w-6 text-cyan-500" /> Eğitmenlerim</h3>
           <div className="space-y-4">
              {teachers.map(teacher => (
                <Card key={teacher.id} className="rounded-[2.5rem] border-0 shadow-md bg-white overflow-hidden">
                   <CardContent className="p-6 flex items-center gap-4">
                      <div className="w-12 h-12 bg-slate-50 rounded-xl flex items-center justify-center text-slate-900 font-black">{teacher.fullName.charAt(0)}</div>
                      <div>
                         <h5 className="font-black text-sm text-slate-900 tracking-tight">{teacher.fullName}</h5>
                         <p className="text-[9px] font-bold text-slate-400 uppercase tracking-widest">{teacher.email}</p>
                      </div>
                   </CardContent>
                </Card>
              ))}
           </div>
        </div>
      </div>

      {/* HEDEF BELİRLEME MODALI */}
      {showTargetModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-6 backdrop-blur-xl bg-slate-900/40 animate-in fade-in duration-300">
           <div className="bg-white w-full max-w-2xl rounded-[4rem] shadow-2xl overflow-hidden animate-in zoom-in-95 duration-300">
              <div className="bg-slate-900 p-10 text-white flex justify-between items-center">
                 <div className="flex items-center gap-6">
                    <div className="w-16 h-16 bg-cyan-500 text-white rounded-3xl flex items-center justify-center"><School className="h-8 w-8" /></div>
                    <div><h3 className="text-2xl font-black tracking-tight">Hedef Okulunu Seç</h3><p className="text-[10px] font-bold text-cyan-400 uppercase tracking-widest mt-1">2025 LGS Referans Verileri</p></div>
                 </div>
                 <button onClick={() => setShowTargetModal(false)} className="p-4 hover:bg-white/10 rounded-2xl transition-all"><X className="h-6 w-6" /></button>
              </div>
              
              <div className="p-10 space-y-8">
                 <div className="relative">
                    <Search className="absolute left-6 top-1/2 -translate-y-1/2 h-5 w-5 text-slate-400" />
                    <input 
                      type="text" 
                      placeholder="Okul adı veya şehir yazın... (Örn: Ankara Fen)"
                      className="w-full bg-slate-50 border-2 border-slate-100 rounded-[2rem] py-6 pl-16 pr-8 font-bold text-sm focus:outline-none focus:border-cyan-500 focus:bg-white transition-all"
                      value={searchQuery}
                      onChange={(e) => handleSearchSchools(e.target.value)}
                    />
                 </div>

                 <div className="max-h-[400px] overflow-y-auto pr-4 space-y-4 custom-scrollbar">
                    {isSearching ? (
                      <div className="text-center py-10"><div className="w-8 h-8 border-4 border-cyan-500 border-t-transparent rounded-full animate-spin mx-auto mb-4"></div><p className="text-[10px] font-black uppercase text-slate-400">Okullar taranıyor...</p></div>
                    ) : schools.length > 0 ? (
                      schools.map(school => (
                        <div 
                          key={school.id}
                          onClick={() => selectSchool(school)}
                          className="flex items-center justify-between p-6 bg-slate-50 rounded-[2rem] hover:bg-cyan-50 hover:border-cyan-100 border-2 border-transparent transition-all cursor-pointer group"
                        >
                           <div className="flex items-center gap-4">
                              <div className="w-12 h-12 bg-white rounded-2xl flex items-center justify-center text-slate-400 group-hover:text-cyan-500 shadow-sm"><MapPin className="h-5 w-5" /></div>
                              <div>
                                 <h5 className="font-black text-sm text-slate-900 leading-tight">{school.schoolName}</h5>
                                 <p className="text-[10px] font-bold text-slate-400 uppercase tracking-widest mt-1">{school.city} • {school.schoolType}</p>
                              </div>
                           </div>
                           <div className="text-right">
                              <div className="flex items-center gap-2 justify-end text-cyan-600"><Trophy className="h-4 w-4" /><span className="text-lg font-black">{school.baseScore}</span></div>
                              <p className="text-[9px] font-black text-slate-400 uppercase mt-1">Dilim: %{school.percentile}</p>
                           </div>
                        </div>
                      ))
                    ) : searchQuery.length >= 3 ? (
                      <div className="text-center py-10 text-slate-400"><p className="text-[10px] font-black uppercase">Sonuç bulunamadı.</p></div>
                    ) : (
                      <div className="text-center py-10 text-slate-300"><p className="text-[10px] font-black uppercase">Arama yapmak için en az 3 harf girin.</p></div>
                    )}
                 </div>
              </div>
           </div>
        </div>
      )}

      {/* PSİKOLOJİK TEST DOLDURMA MODALI (T-052C / STAI) */}
      {activePsychAssignment && activePsychAssignment.testCode !== "BOURDON" && (
        <StudentPsychTestModal
          assignmentId={activePsychAssignment.id}
          testCode={activePsychAssignment.testCode}
          testTitle={activePsychAssignment.testTitle}
          isOpen={!!activePsychAssignment}
          onClose={() => setActivePsychAssignment(null)}
          onSuccess={() => {
            fetchData();
          }}
        />
      )}

      {/* BOURDON DİKKAT TESTİ MODALI (T-053B / T-053F) */}
      {activePsychAssignment && activePsychAssignment.testCode === "BOURDON" && (
        <StudentBourdonTestModal
          assignmentId={activePsychAssignment.id}
          assignmentStatus={activePsychAssignment.status}
          testCode={activePsychAssignment.testCode}
          testTitle={activePsychAssignment.testTitle}
          isOpen={!!activePsychAssignment}
          onClose={() => setActivePsychAssignment(null)}
          onSuccess={() => {
            fetchData();
          }}
        />
      )}
    </div>
  );
}
