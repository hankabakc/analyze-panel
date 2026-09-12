import { useState, useEffect, useMemo } from "react";
import type { User } from "@/lib/types";
import { psychTestsApi, type PsychTestResultDto, type BourdonResultDto } from "@/lib/api";
import { toast } from "sonner";
import { 
  Brain, Clock, CheckCircle2, AlertCircle, Search, 
  Send, Loader2, CheckSquare, Square, Users, ShieldAlert, Info, X
} from "lucide-react";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { StaiLevelBadge, StaiExplanationNote } from "./StaiLevel";

interface PsychTestAssignmentTabProps {
  students: User[];
  unseenCount?: number;
  onSeen?: () => void;
}

type StudentPsychStatus = "NOT_ASSIGNED" | "PENDING" | "COMPLETED";

/**
 * PsychTestAssignmentTab: Yöneticinin öğrencilere STAI ve Bourdon testleri atamasını
 * ve sonuçlarını incelemesini sağlayan arayüz bileşeni (T-052B / T-052D / T-053C / T-062).
 */
export default function PsychTestAssignmentTab({
  students,
  unseenCount = 0,
  onSeen,
}: PsychTestAssignmentTabProps) {
  // T-052D: Sekme Seçimi ("ASSIGN": Öğrencilere Test Ata, "RESULTS": Ölçek Sonuçları ve Puanlar)
  const [activeSubTab, setActiveSubTab] = useState<"ASSIGN" | "RESULTS">("ASSIGN");
  const [searchQuery, setSearchQuery] = useState("");
  const [resultsSearchQuery, setResultsSearchQuery] = useState("");
  const [selectedStudentIds, setSelectedStudentIds] = useState<Set<string>>(new Set());
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isLoadingResults, setIsLoadingResults] = useState(true);
  const [testResults, setTestResults] = useState<PsychTestResultDto[]>([]);
  const [skippedStudentNames, setSkippedStudentNames] = useState<string[]>([]);

  // T-062B: Ölçek sonuçları sekmesine geçiş
  const handleOpenResultsTab = () => {
    setActiveSubTab("RESULTS");
  };

  // T-062B: Sonuç sekmesi açıkken ekranda listelenen tamamlanmış testleri görüldü işaretler (çift çağrı engellenir)
  useEffect(() => {
    if (activeSubTab === "RESULTS" && unseenCount > 0 && !isLoadingResults) {
      const completedIds = testResults
        .filter((r) => r.status === "COMPLETED")
        .map((r) => r.id);
      if (completedIds.length > 0) {
        psychTestsApi.markResultsSeen(undefined, completedIds).catch(() => {});
        onSeen?.();
      }
    }
  }, [activeSubTab, unseenCount, isLoadingResults, testResults, onSeen]);


  // T-053C: Yönetici Bourdon Detay Modalı State'leri
  const [selectedBourdonDetail, setSelectedBourdonDetail] = useState<BourdonResultDto | null>(null);
  const [isLoadingBourdonDetail, setIsLoadingBourdonDetail] = useState(false);

  const handleOpenBourdonDetail = async (result: PsychTestResultDto) => {
    setIsLoadingBourdonDetail(true);
    try {
      const res = await psychTestsApi.getBourdonResult(result.id);
      if (res.data?.success && res.data.data) {
        setSelectedBourdonDetail(res.data.data);
      } else {
        toast.error("Bourdon sonucu alınamadı.");
      }
    } catch {
      toast.error("Sonuç yüklenirken hata oluştu.");
    } finally {
      setIsLoadingBourdonDetail(false);
    }
  };

  /**
   * loadResults: Yöneticinin görebildiği tüm test atama ve sonuç verilerini çeker.
   */
  const loadResults = async () => {
    try {
      setIsLoadingResults(true);
      const res = await psychTestsApi.getAllResults();
      if (res.data && res.data.data) {
        setTestResults(res.data.data);
      }
    } catch {
      toast.error("Test durumları yüklenirken bir hata oluştu.");
    } finally {
      setIsLoadingResults(false);
    }
  };

  useEffect(() => {
    loadResults();
  }, []);

  /**
   * Öğrenci bazlı en güncel STAI durum haritasını oluşturur.
   */
  const studentStatusMap = useMemo(() => {
    const map = new Map<string, StudentPsychStatus>();
    for (const student of students) {
      const studentTests = testResults.filter(
        (r) => r.studentId === student.id && r.testCode === "STAI"
      );
      if (studentTests.some((t) => t.status === "PENDING")) {
        map.set(student.id, "PENDING");
      } else if (studentTests.some((t) => t.status === "COMPLETED")) {
        map.set(student.id, "COMPLETED");
      } else {
        map.set(student.id, "NOT_ASSIGNED");
      }
    }
    return map;
  }, [students, testResults]);

  /**
   * Arama filtresine göre öğrencileri listeler.
   */
  const filteredStudents = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return students;
    return students.filter(
      (s) =>
        s.fullName.toLowerCase().includes(q) ||
        (s.email && s.email.toLowerCase().includes(q))
    );
  }, [students, searchQuery]);

  /**
   * T-052D: Arama filtresine göre ölçek sonuçlarını listeler.
   */
  const filteredResults = useMemo(() => {
    const q = resultsSearchQuery.trim().toLowerCase();
    if (!q) return testResults;
    return testResults.filter(
      (r) =>
        (r.studentName && r.studentName.toLowerCase().includes(q)) ||
        (r.testCode && r.testCode.toLowerCase().includes(q))
    );
  }, [testResults, resultsSearchQuery]);

  /**
   * "Hepsini Seç" durumu: Filtrelenen tüm öğrenciler seçili mi?
   */
  const isAllFilteredSelected = useMemo(() => {
    if (filteredStudents.length === 0) return false;
    return filteredStudents.every((s) => selectedStudentIds.has(s.id));
  }, [filteredStudents, selectedStudentIds]);

  /**
   * "Hepsini Seç" tetikleyicisi
   */
  const handleToggleSelectAll = () => {
    if (isAllFilteredSelected) {
      // Filtrelenenleri seçimden çıkar
      const next = new Set(selectedStudentIds);
      for (const s of filteredStudents) {
        next.delete(s.id);
      }
      setSelectedStudentIds(next);
    } else {
      // Filtrelenenleri seçime ekle
      const next = new Set(selectedStudentIds);
      for (const s of filteredStudents) {
        next.add(s.id);
      }
      setSelectedStudentIds(next);
    }
  };

  /**
   * Tekil öğrenci seçimini değiştirir
   */
  const handleToggleStudent = (studentId: string) => {
    const next = new Set(selectedStudentIds);
    if (next.has(studentId)) {
      next.delete(studentId);
    } else {
      next.add(studentId);
    }
    setSelectedStudentIds(next);
  };

  /**
   * Seçili öğrencilere STAI testi atar
   */
  const handleAssignStai = async () => {
    if (selectedStudentIds.size === 0 || isSubmitting) return;

    setIsSubmitting(true);
    setSkippedStudentNames([]);

    try {
      const studentIdsArray = Array.from(selectedStudentIds);
      const res = await psychTestsApi.assignTest({
        testCode: "STAI",
        studentIds: studentIdsArray,
      });

      if (res.data && res.data.data) {
        const { assignedStudentIds, skippedStudentIds, message } = res.data.data;

        // Atlanan öğrencilerin adlarını belirle (IST-02 §4.2)
        if (skippedStudentIds && skippedStudentIds.length > 0) {
          const names = skippedStudentIds.map((id) => {
            const found = students.find((s) => s.id === id);
            return found ? found.fullName : id;
          });
          setSkippedStudentNames(names);
        }

        if (assignedStudentIds && assignedStudentIds.length > 0) {
          toast.success(message || `${assignedStudentIds.length} öğrenciye STAI testi atandı.`);
        } else {
          toast.info("Seçili tüm öğrencilerde açık test bulunduğu için yeni atama yapılmadı.");
        }

        // Seçimi temizle ve güncel verileri çek
        setSelectedStudentIds(new Set());
        await loadResults();
      }
    } catch (err: unknown) {
      const errorObj = err as { response?: { data?: { message?: string } } };
      toast.error(errorObj.response?.data?.message || "Test atama işlemi sırasında bir hata oluştu.");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="space-y-6 sm:space-y-8 animate-in slide-in-from-right-10 duration-500 max-w-full overflow-hidden">
      {/* SEKMELER (T-052D) */}
      <div className="flex flex-wrap items-center gap-3">
        <button
          type="button"
          data-testid="psych-subtab-assign"
          onClick={() => setActiveSubTab("ASSIGN")}
          className={`tap-44 min-h-[44px] px-6 py-3 rounded-2xl font-black text-xs uppercase tracking-wider transition-all flex items-center gap-2.5 ${
            activeSubTab === "ASSIGN"
              ? "bg-cyan-600 text-white shadow-lg shadow-cyan-200 ring-2 ring-cyan-600/20"
              : "bg-white text-slate-600 hover:bg-slate-50 border border-slate-200 shadow-sm"
          }`}
        >
          <Send className="h-4 w-4" />
          <span>Öğrencilere Test Ata</span>
        </button>

        <button
          type="button"
          data-testid="psych-subtab-results"
          onClick={handleOpenResultsTab}
          className={`tap-44 min-h-[44px] px-6 py-3 rounded-2xl font-black text-xs uppercase tracking-wider transition-all flex items-center gap-2.5 ${
            activeSubTab === "RESULTS"
              ? "bg-purple-600 text-white shadow-lg shadow-purple-200 ring-2 ring-purple-600/20"
              : "bg-white text-slate-600 hover:bg-slate-50 border border-slate-200 shadow-sm"
          }`}
        >
          <Brain className="h-4 w-4" />
          <span>Ölçek Sonuçları ve Puanlar</span>
          {unseenCount > 0 && (
            <span
              data-testid="psych-unseen-badge"
              className="px-2 py-0.5 rounded-full text-[10px] font-black bg-purple-600 text-white animate-pulse"
              aria-label={`${unseenCount} yeni sonuç`}
            >
              {unseenCount}
            </span>
          )}
          {testResults.length > 0 && (
            <span
              data-testid="psych-results-count-badge"
              className={`px-2 py-0.5 rounded-full text-[10px] font-black ${
                activeSubTab === "RESULTS" ? "bg-purple-700 text-white" : "bg-slate-100 text-slate-600"
              }`}
            >
              {testResults.length}
            </span>
          )}
        </button>
      </div>

      {/* 1. SEKME: ÖĞRENCİLERE TEST ATA (T-052B - PUANSIZ) */}
      {activeSubTab === "ASSIGN" && (
        <div className="space-y-6 sm:space-y-8">
          {/* BAŞLIK VE AÇIKLAMA KARTI */}
          <Card className="rounded-[2.5rem] sm:rounded-[3rem] p-6 sm:p-10 bg-white border border-slate-100 shadow-xl shadow-slate-100/50 relative overflow-hidden">
            <div className="absolute top-0 right-0 w-64 h-64 bg-cyan-500/5 rounded-full blur-3xl pointer-events-none"></div>
            <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-6 relative z-10">
              <div className="space-y-2">
                <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-cyan-50 border border-cyan-200/50 text-cyan-700 text-[10px] font-black tracking-widest uppercase">
                  <Brain className="h-3.5 w-3.5" />
                  <span>Ölçek Altyapısı</span>
                </div>
                <h2 className="text-2xl sm:text-3xl font-black text-slate-900 tracking-tight">
                  Psikolojik Test Yönetimi
                </h2>
                <p className="text-xs sm:text-sm text-slate-500 font-medium max-w-2xl leading-relaxed">
                  Öğrencilere STAI (Durumluk–Sürekli Kaygı Envanteri) testi atayabilir ve doldurma durumlarını takip edebilirsiniz.
                </p>
              </div>

              <div className="flex items-center gap-3 w-full sm:w-auto">
                <Button
                  type="button"
                  data-testid="send-stai-btn"
                  onClick={handleAssignStai}
                  disabled={isSubmitting || selectedStudentIds.size === 0}
                  className="w-full sm:w-auto tap-44 min-h-[44px] px-6 rounded-2xl bg-cyan-600 hover:bg-cyan-700 text-white font-black text-xs uppercase tracking-widest shadow-lg shadow-cyan-200 transition-all disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
                >
                  {isSubmitting ? (
                    <>
                      <Loader2 className="h-4 w-4 animate-spin" />
                      <span>Gönderiliyor...</span>
                    </>
                  ) : (
                    <>
                      <Send className="h-4 w-4" />
                      <span>STAI Gönder ({selectedStudentIds.size})</span>
                    </>
                  )}
                </Button>
              </div>
            </div>

            {/* GİZLİLİK VE KURAL NOTU (APP-03 §4, §5) */}
            <div className="mt-6 pt-6 border-t border-slate-100 flex items-start gap-3 text-slate-500 text-xs">
              <ShieldAlert className="h-4 w-4 text-amber-500 shrink-0 mt-0.5" />
              <p className="leading-relaxed">
                <strong className="text-slate-700">Özel Nitelikli Veri Koruması (APP-03 §4, §5):</strong> Bu ekranda puanlar veya kaygı etiketleri gösterilmez. Yalnızca testin doldurulma durumu (*Atanmadı / Bekliyor / Tamamlandı*) takip edilir.
              </p>
            </div>
          </Card>

          {/* ATLANAN ÖĞRENCİLER UYARI KUTUSU (IST-02 §4.2) */}
          {skippedStudentNames.length > 0 && (
            <div 
              data-testid="skipped-warning"
              className="p-5 rounded-2xl bg-amber-50/80 border border-amber-200 text-amber-900 animate-in fade-in duration-300 space-y-2"
            >
              <div className="flex items-center gap-2 font-black text-xs sm:text-sm uppercase tracking-wider text-amber-800">
                <AlertCircle className="h-4 w-4 text-amber-600 shrink-0" />
                <span>Açık Testi Bulunan Öğrenciler Atlandı ({skippedStudentNames.length})</span>
              </div>
              <p className="text-xs text-amber-700 leading-relaxed">
                Aşağıdaki öğrencilerde halihazırda bekleyen/tamamlanmamış açık bir STAI testi bulunduğu için mükerrer atama yapılmadı:
              </p>
              <div className="flex flex-wrap gap-2 pt-1">
                {skippedStudentNames.map((name, idx) => (
                  <span 
                    key={idx} 
                    className="inline-flex items-center px-2.5 py-1 rounded-lg bg-amber-100/80 border border-amber-300 text-amber-900 text-xs font-bold"
                  >
                    {name}
                  </span>
                ))}
              </div>
            </div>
          )}

          {/* AKSİYON VE ARAMA ÇUBUĞU */}
          <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-4">
            {/* Arama Kutusu */}
            <div className="relative flex-1 max-w-md">
              <Search className="absolute left-4 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
              <Input
                type="text"
                placeholder="Öğrenci adı veya e-posta ile ara..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="pl-11 h-12 rounded-2xl bg-white border-slate-200 text-sm font-medium focus-visible:ring-2 focus-visible:ring-cyan-500"
              />
            </div>

            {/* Hepsini Seç & Sayaç */}
            <div className="flex items-center justify-between sm:justify-end gap-4">
              <button
                type="button"
                data-testid="select-all-checkbox"
                onClick={handleToggleSelectAll}
                className="tap-44 min-h-[44px] inline-flex items-center gap-2 px-4 py-2.5 rounded-2xl bg-white hover:bg-slate-50 border border-slate-200 text-slate-700 text-xs font-black uppercase tracking-wider shadow-sm transition-all active:scale-95"
              >
                {isAllFilteredSelected ? (
                  <CheckSquare className="h-4 w-4 text-cyan-600" />
                ) : (
                  <Square className="h-4 w-4 text-slate-400" />
                )}
                <span>{isAllFilteredSelected ? "Seçimi Kaldır" : "Hepsini Seç"}</span>
              </button>

              <div className="inline-flex items-center gap-2 px-3.5 py-2 rounded-2xl bg-slate-100 text-slate-600 text-xs font-bold">
                <Users className="h-4 w-4 text-slate-400" />
                <span>Toplam: {filteredStudents.length}</span>
                {selectedStudentIds.size > 0 && (
                  <span className="text-cyan-700 font-black">({selectedStudentIds.size} seçili)</span>
                )}
              </div>
            </div>
          </div>

          {/* ÖĞRENCİ LİSTESİ */}
          {isLoadingResults ? (
            <div className="py-20 flex flex-col items-center justify-center gap-4">
              <Loader2 className="h-8 w-8 text-cyan-600 animate-spin" />
              <p className="text-xs font-black uppercase tracking-widest text-slate-400">Veriler Yükleniyor...</p>
            </div>
          ) : filteredStudents.length === 0 ? (
            <Card className="rounded-[2.5rem] p-12 text-center bg-white border border-slate-100 shadow-sm">
              <Users className="h-12 w-12 text-slate-300 mx-auto mb-4" />
              <h3 className="text-base font-black text-slate-800 tracking-tight">Kayıtlı Öğrenci Bulunamadı</h3>
              <p className="text-xs text-slate-400 mt-1 max-w-sm mx-auto">
                {searchQuery ? "Arama kriterlerinize uygun öğrenci bulunamadı." : "Henüz sisteme eklenmiş bir öğrenci hesabı yok."}
              </p>
            </Card>
          ) : (
            <Card className="rounded-[2.5rem] bg-white border border-slate-100 shadow-xl shadow-slate-100/50 overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse min-w-[600px] sm:min-w-full">
                  <thead>
                    <tr className="border-b border-slate-100 bg-slate-50/50 text-[10px] font-black uppercase tracking-[0.2em] text-slate-400">
                      <th className="py-4 px-6 w-14">Seç</th>
                      <th className="py-4 px-6">Öğrenci Adı</th>
                      <th className="py-4 px-6">Sınıf</th>
                      <th className="py-4 px-6">E-posta</th>
                      <th className="py-4 px-6 text-right">STAI Durumu</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 text-sm font-medium text-slate-700">
                    {filteredStudents.map((student) => {
                      const isSelected = selectedStudentIds.has(student.id);
                      const status = studentStatusMap.get(student.id) || "NOT_ASSIGNED";

                      return (
                        <tr 
                          key={student.id}
                          className={`hover:bg-slate-50/60 transition-colors ${
                            isSelected ? "bg-cyan-50/40" : ""
                          }`}
                        >
                          {/* Seçim Onay Kutusu */}
                          <td className="py-3 px-6">
                            <label 
                              className="tap-44 min-h-[44px] min-w-[44px] -ml-2.5 inline-flex items-center justify-center cursor-pointer"
                              htmlFor={`checkbox-${student.id}`}
                            >
                              <input
                                type="checkbox"
                                id={`checkbox-${student.id}`}
                                data-testid={`student-checkbox-${student.id}`}
                                checked={isSelected}
                                onChange={() => handleToggleStudent(student.id)}
                                className="absolute inset-0 opacity-0 cursor-pointer w-full h-full"
                              />
                              <div 
                                className={`w-5 h-5 rounded-lg border flex items-center justify-center transition-all ${
                                  isSelected 
                                    ? "bg-cyan-600 border-cyan-600 text-white shadow-sm" 
                                    : "border-slate-300 bg-white hover:border-slate-400"
                                }`}
                              >
                                {isSelected && <CheckSquare className="h-3.5 w-3.5" />}
                              </div>
                            </label>
                          </td>

                          {/* Öğrenci Adı */}
                          <td className="py-3 px-6">
                            <span className="font-bold text-slate-900">{student.fullName}</span>
                          </td>

                          {/* Sınıf */}
                          <td className="py-3 px-6">
                            <span className="inline-flex px-2.5 py-1 rounded-lg bg-slate-100 text-slate-600 text-xs font-bold">
                              {student.grade ? `${student.grade}. Sınıf` : "Sınıfsız"}
                            </span>
                          </td>

                          {/* E-posta */}
                          <td className="py-3 px-6 text-slate-500 font-mono text-xs">
                            {student.email}
                          </td>

                          {/* STAI Durum Rozeti (Puan Kesinlikle Gösterilmez) */}
                          <td className="py-3 px-6 text-right">
                            <div className="inline-flex justify-end">
                              {status === "PENDING" && (
                                <span 
                                  data-testid={`status-badge-${student.id}`}
                                  className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-amber-50 text-amber-700 border border-amber-200 text-xs font-black uppercase tracking-wider"
                                >
                                  <Clock className="h-3.5 w-3.5 text-amber-600" />
                                  <span>Bekliyor</span>
                                </span>
                              )}

                              {status === "COMPLETED" && (
                                <span 
                                  data-testid={`status-badge-${student.id}`}
                                  className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 text-xs font-black uppercase tracking-wider"
                                >
                                  <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" />
                                  <span>Tamamlandı</span>
                                </span>
                              )}

                              {status === "NOT_ASSIGNED" && (
                                <span 
                                  data-testid={`status-badge-${student.id}`}
                                  className="inline-flex items-center px-3 py-1 rounded-full bg-slate-100 text-slate-500 border border-slate-200 text-xs font-black uppercase tracking-wider"
                                >
                                  <span>Atanmadı</span>
                                </span>
                              )}
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </Card>
          )}
        </div>
      )}

      {/* 2. SEKME: ÖLÇEK SONUÇLARI VE PUANLAR (T-052D) */}
      {activeSubTab === "RESULTS" && (
        <div className="space-y-6 sm:space-y-8">
          {/* BAŞLIK VE AÇIKLAMA KARTI */}
          <Card className="rounded-[2.5rem] sm:rounded-[3rem] p-6 sm:p-10 bg-white border border-slate-100 shadow-xl shadow-slate-100/50 relative overflow-hidden">
            <div className="absolute top-0 right-0 w-64 h-64 bg-purple-500/5 rounded-full blur-3xl pointer-events-none"></div>
            <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-6 relative z-10">
              <div className="space-y-2">
                <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-purple-50 border border-purple-200/50 text-purple-700 text-[10px] font-black tracking-widest uppercase">
                  <Brain className="h-3.5 w-3.5" />
                  <span>Ölçek Puanları</span>
                </div>
                <h2 className="text-2xl sm:text-3xl font-black text-slate-900 tracking-tight">
                  STAI Ölçek Sonuçları ve Puanlar
                </h2>
                <p className="text-xs sm:text-sm text-slate-500 font-medium max-w-2xl leading-relaxed">
                  Öğrencilerin tamamladığı veya bekleyen STAI durumluk ve sürekli kaygı envanteri puanlarını inceleyebilirsiniz.
                </p>
              </div>

              <div className="flex items-center gap-3 w-full sm:w-auto">
                <div className="inline-flex items-center gap-2 px-4 py-2.5 rounded-2xl bg-purple-50 border border-purple-200/50 text-purple-800 text-xs font-bold">
                  <Users className="h-4 w-4 text-purple-600" />
                  <span>Toplam: {testResults.length} Kayıt</span>
                </div>
              </div>
            </div>

            {/* GİZLİLİK VE STAI AÇIKLAMA NOTU (APP-03 §4, S-024, T-061B) */}
            <StaiExplanationNote variant="purple" />
          </Card>

          {/* ARAMA VE FİLTRELEME ÇUBUĞU */}
          <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-4">
            <div className="relative flex-1 max-w-md">
              <Search className="absolute left-4 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
              <Input
                type="text"
                data-testid="psych-results-search-input"
                placeholder="Öğrenci adı ile ara..."
                value={resultsSearchQuery}
                onChange={(e) => setResultsSearchQuery(e.target.value)}
                className="pl-11 h-12 rounded-2xl bg-white border-slate-200 text-sm font-medium focus-visible:ring-2 focus-visible:ring-purple-500"
              />
            </div>

            <div className="inline-flex items-center gap-2 px-3.5 py-2 rounded-2xl bg-slate-100 text-slate-600 text-xs font-bold self-start sm:self-auto">
              <Users className="h-4 w-4 text-slate-400" />
              <span>Gösterilen: {filteredResults.length}</span>
            </div>
          </div>

          {/* SONUÇLAR TABLOSU */}
          {isLoadingResults ? (
            <div className="py-20 flex flex-col items-center justify-center gap-4">
              <Loader2 className="h-8 w-8 text-purple-600 animate-spin" />
              <p className="text-xs font-black uppercase tracking-widest text-slate-400">Sonuçlar Yükleniyor...</p>
            </div>
          ) : filteredResults.length === 0 ? (
            <Card
              data-testid="manager-results-empty"
              className="rounded-[2.5rem] p-12 text-center bg-white border border-slate-100 shadow-sm"
            >
              <Brain className="h-12 w-12 text-slate-300 mx-auto mb-4" />
              <h3 className="text-base font-black text-slate-800 tracking-tight">Ölçek Sonucu Bulunamadı</h3>
              <p className="text-xs text-slate-400 mt-1 max-w-sm mx-auto">
                {resultsSearchQuery ? "Arama kriterlerinize uygun ölçek sonucu bulunamadı." : "Henüz sisteme kaydedilmiş bir psikolojik ölçek sonucu bulunmuyor."}
              </p>
            </Card>
          ) : (
            <Card className="rounded-[2.5rem] bg-white border border-slate-100 shadow-xl shadow-slate-100/50 overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse min-w-[700px] sm:min-w-full">
                  <thead>
                    <tr className="border-b border-slate-100 bg-slate-50/50 text-[10px] font-black uppercase tracking-[0.2em] text-slate-400">
                      <th className="py-4 px-6">Öğrenci Adı</th>
                      <th className="py-4 px-6">Ölçek</th>
                      <th className="py-4 px-6">Atanma / Tamamlanma</th>
                      <th className="py-4 px-6">Durum</th>
                      <th className="py-4 px-6 text-right">Puanlar (Durumluk / Sürekli)</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 text-sm font-medium text-slate-700">
                    {filteredResults.map((result) => {
                      const isCompleted = result.status === "COMPLETED";

                      return (
                        <tr key={result.id} className="hover:bg-slate-50/60 transition-colors">
                          {/* Öğrenci Adı */}
                          <td className="py-4 px-6">
                            <span className="font-bold text-slate-900">{result.studentName}</span>
                          </td>

                          {/* Ölçek */}
                          <td className="py-4 px-6">
                            {result.testCode === "BOURDON" ? (
                              <span className="inline-flex items-center px-2.5 py-1 rounded-lg bg-cyan-50 text-cyan-700 border border-cyan-200/60 text-xs font-bold">
                                Bourdon Dikkat Testi
                              </span>
                            ) : (
                              <span className="font-semibold text-slate-700">
                                {result.testCode === "STAI" ? "STAI (Durumluk–Sürekli Kaygı)" : result.testCode}
                              </span>
                            )}
                          </td>

                          {/* Tarihler */}
                          <td className="py-4 px-6 text-xs text-slate-500">
                            <div>
                              <span className="text-slate-400 font-medium">Atanma: </span>
                              <span className="font-bold text-slate-700">
                                {new Date(result.assignedAt).toLocaleDateString("tr-TR", {
                                  day: "numeric",
                                  month: "short",
                                  year: "numeric",
                                  hour: "2-digit",
                                  minute: "2-digit",
                                })}
                              </span>
                            </div>
                            {result.completedAt && (
                              <div className="mt-0.5">
                                <span className="text-slate-400 font-medium">Bitiş: </span>
                                <span className="font-bold text-slate-700">
                                  {new Date(result.completedAt).toLocaleDateString("tr-TR", {
                                    day: "numeric",
                                    month: "short",
                                    year: "numeric",
                                    hour: "2-digit",
                                    minute: "2-digit",
                                  })}
                                </span>
                              </div>
                            )}
                          </td>

                          {/* Durum */}
                          <td className="py-4 px-6">
                            {isCompleted ? (
                              <span
                                data-testid={`manager-status-${result.id}`}
                                className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 text-xs font-black uppercase tracking-wider"
                              >
                                <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" />
                                <span>Tamamlandı</span>
                              </span>
                            ) : (
                              <span
                                data-testid={`manager-status-${result.id}`}
                                className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-amber-50 text-amber-700 border border-amber-200 text-xs font-black uppercase tracking-wider"
                              >
                                <Clock className="h-3.5 w-3.5 text-amber-600" />
                                <span>Bekliyor</span>
                              </span>
                            )}
                          </td>

                          {/* Puanlar */}
                          <td className="py-4 px-6 text-right">
                            {isCompleted ? (
                              result.testCode === "BOURDON" ? (
                                <div
                                  data-testid={`manager-bourdon-scores-${result.id}`}
                                  className="inline-flex flex-col sm:flex-row items-end sm:items-center gap-2"
                                >
                                  {result.bourdonTimedOut && (
                                    <span
                                      data-testid={`manager-bourdon-timeout-${result.id}`}
                                      className="px-2 py-0.5 rounded-md bg-amber-50 text-amber-700 border border-amber-200 text-[10px] font-black uppercase tracking-wider"
                                    >
                                      Süre Aşımı ({result.bourdonDurationSeconds} sn)
                                    </span>
                                  )}
                                  <span className="px-2.5 py-1 rounded-lg bg-emerald-50 text-emerald-800 border border-emerald-100 text-xs font-bold font-mono">
                                    D: {result.bourdonTotalCorrect ?? 0} | A: {result.bourdonTotalOmitted ?? 0} | Y: {result.bourdonTotalIncorrect ?? 0}
                                  </span>
                                  <span className="px-2 py-1 rounded-lg bg-slate-100 text-slate-700 text-xs font-bold font-mono">
                                    {result.bourdonDurationSeconds ?? 0} sn
                                  </span>
                                  <button
                                    type="button"
                                    data-testid={`manager-bourdon-detail-btn-${result.id}`}
                                    onClick={() => handleOpenBourdonDetail(result)}
                                    disabled={isLoadingBourdonDetail}
                                    className="tap-44 min-h-[36px] px-3 py-1 bg-cyan-50 hover:bg-cyan-100 active:bg-cyan-200 text-cyan-800 border border-cyan-200 rounded-xl text-xs font-bold transition-all cursor-pointer flex items-center gap-1 focus-visible:ring-2 focus-visible:ring-cyan-500 focus-visible:outline-none"
                                  >
                                    Detay
                                  </button>
                                </div>
                              ) : (
                                <div
                                  data-testid={`manager-scores-${result.id}`}
                                  className="inline-flex flex-wrap items-center gap-2 font-mono font-bold text-slate-900"
                                >
                                  <span className="px-2.5 py-1 rounded-lg bg-slate-100 text-slate-800 text-xs inline-flex items-center gap-1.5">
                                    Durumluk: <strong data-testid="manager-state-score" className="text-slate-900">{result.stateScore ?? "-"}</strong>
                                    <StaiLevelBadge level={result.stateLevel} testIdPrefix={`manager-state-level-${result.id}`} />
                                  </span>
                                  <span className="px-2.5 py-1 rounded-lg bg-slate-100 text-slate-800 text-xs inline-flex items-center gap-1.5">
                                    Sürekli: <strong data-testid="manager-trait-score" className="text-slate-900">{result.traitScore ?? "-"}</strong>
                                    <StaiLevelBadge level={result.traitLevel} testIdPrefix={`manager-trait-level-${result.id}`} />
                                  </span>
                                </div>
                              )
                            ) : (
                              <span
                                data-testid={`manager-pending-${result.id}`}
                                className="text-xs text-slate-400 italic"
                              >
                                Bekliyor (Puan Yok)
                              </span>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </Card>
          )}

          {/* T-053C: YÖNETİCİ BOURDON DETAY MODALI */}
          {selectedBourdonDetail && (
            <div
              data-testid="manager-bourdon-detail-modal"
              className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-6 backdrop-blur-xl bg-slate-900/60 animate-in fade-in duration-300"
              role="dialog"
              aria-modal="true"
            >
              <div className="bg-white w-full max-w-2xl rounded-[2rem] sm:rounded-[2.5rem] shadow-2xl overflow-hidden flex flex-col border border-slate-100 max-h-[90vh]">
                {/* Üst Bar */}
                <div className="bg-slate-900 p-5 sm:p-6 text-white flex justify-between items-center shrink-0">
                  <div className="flex items-center gap-3">
                    <div className="p-2.5 rounded-xl bg-cyan-500/20 text-cyan-400 border border-cyan-500/30">
                      <Brain className="h-5 w-5" />
                    </div>
                    <div>
                      <h3 className="text-base font-black tracking-tight">{selectedBourdonDetail.studentName}</h3>
                      <p className="text-xs text-slate-400">Bourdon Dikkat Testi Sonuç Raporu</p>
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => setSelectedBourdonDetail(null)}
                    className="tap-44 min-w-[44px] min-h-[44px] p-2 text-slate-400 hover:text-white rounded-xl hover:bg-slate-800 transition-colors flex items-center justify-center cursor-pointer"
                    aria-label="Kapat"
                  >
                    <X className="h-5 w-5" />
                  </button>
                </div>

                {/* Gövde */}
                <div className="p-5 sm:p-7 overflow-y-auto space-y-5">
                  {/* Süre Aşımı Uyarısı */}
                  {selectedBourdonDetail.timedOut && (
                    <div
                      data-testid="manager-bourdon-modal-timeout"
                      className="p-3.5 rounded-2xl bg-amber-50 border border-amber-200 text-xs text-amber-900 flex items-start gap-2.5"
                    >
                      <AlertCircle className="h-4 w-4 text-amber-600 shrink-0 mt-0.5" />
                      <div>
                        <p className="font-bold">Süre aşımıyla teslim edildi</p>
                        <p className="mt-0.5 text-amber-800">
                          Test 180 saniyelik standart süre aşılarak tamamlanmıştır — gerçek süre: <strong>{selectedBourdonDetail.durationSeconds} sn</strong>.
                        </p>
                      </div>
                    </div>
                  )}

                  {/* 4'lü Özet İstatistik Kartları */}
                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                    <div className="p-3.5 rounded-2xl bg-slate-50 border border-slate-100 flex flex-col justify-center">
                      <span className="text-[11px] font-medium text-slate-500">Toplam Doğru</span>
                      <span className="text-xl font-black text-emerald-600 mt-1">{selectedBourdonDetail.totalCorrect}</span>
                    </div>
                    <div className="p-3.5 rounded-2xl bg-slate-50 border border-slate-100 flex flex-col justify-center">
                      <span className="text-[11px] font-medium text-slate-500">Toplam Atlanan</span>
                      <span className="text-xl font-black text-amber-600 mt-1">{selectedBourdonDetail.totalOmitted}</span>
                    </div>
                    <div className="p-3.5 rounded-2xl bg-slate-50 border border-slate-100 flex flex-col justify-center">
                      <span className="text-[11px] font-medium text-slate-500">Toplam Yanlış</span>
                      <span className="text-xl font-black text-rose-600 mt-1">{selectedBourdonDetail.totalIncorrect}</span>
                    </div>
                    <div className="p-3.5 rounded-2xl bg-slate-50 border border-slate-100 flex flex-col justify-center">
                      <span className="text-[11px] font-medium text-slate-500">Harcanan Süre</span>
                      <span className="text-xl font-black text-slate-900 mt-1">{selectedBourdonDetail.durationSeconds} sn</span>
                    </div>
                  </div>

                  {/* Blok 1, 2, 3 Tablosu */}
                  <div className="space-y-2">
                    <h4 className="text-xs font-bold text-slate-700 uppercase tracking-wider">Bölüm (Blok) Analizi</h4>
                    <div className="overflow-x-auto rounded-2xl border border-slate-100">
                      <table className="w-full text-xs text-left" data-testid="manager-bourdon-blocks-table">
                        <thead className="bg-slate-50 text-slate-400 font-bold uppercase text-[10px]">
                          <tr>
                            <th className="py-2.5 px-3">Bölüm</th>
                            <th className="py-2.5 px-3">Satırlar</th>
                            <th className="py-2.5 px-3 text-emerald-700">Doğru</th>
                            <th className="py-2.5 px-3 text-amber-700">Atlanan</th>
                            <th className="py-2.5 px-3 text-rose-700">Yanlış</th>
                            <th className="py-2.5 px-3 text-slate-500">Toplam Hedef</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-100 text-slate-700 font-medium">
                          {[selectedBourdonDetail.block1, selectedBourdonDetail.block2, selectedBourdonDetail.block3].map((block) => (
                            <tr key={block.blockNumber}>
                              <td className="py-2 px-3 font-bold text-slate-900">Blok {block.blockNumber}</td>
                              <td className="py-2 px-3 text-slate-400">{(block.blockNumber - 1) * 10 + 1}–{block.blockNumber * 10}</td>
                              <td className="py-2 px-3 font-bold text-emerald-600">{block.correct}</td>
                              <td className="py-2 px-3 font-bold text-amber-600">{block.omitted}</td>
                              <td className="py-2 px-3 font-bold text-rose-600">{block.incorrect}</td>
                              <td className="py-2 px-3 text-slate-500">{block.targetCount}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>

                  {/* Nötr Gözlem Notu */}
                  {selectedBourdonDetail.observationNote && (
                    <div data-testid="manager-bourdon-observation-note" className="p-3.5 rounded-2xl bg-cyan-50/60 border border-cyan-100 text-xs text-cyan-900 flex items-start gap-2.5">
                      <Info className="h-4 w-4 text-cyan-600 shrink-0 mt-0.5" />
                      <p className="leading-relaxed font-medium">{selectedBourdonDetail.observationNote}</p>
                    </div>
                  )}

                  {/* Yasal Uyarı */}
                  <div className="p-3 rounded-2xl bg-slate-50 border border-slate-100 text-[11px] text-slate-500 flex items-start gap-2">
                    <Info className="h-4 w-4 text-slate-400 shrink-0 mt-0.5" />
                    <p className="leading-relaxed">
                      Bourdon Dikkat Testi (a, b, d, g harfleri, 30 satır). Puanlar tanı veya kategori içermez; sonuçlar bireysel değerlendirilir.
                    </p>
                  </div>
                </div>

                {/* Alt Bar */}
                <div className="bg-slate-50 p-4 px-6 border-t border-slate-100 flex justify-end">
                  <Button
                    type="button"
                    onClick={() => setSelectedBourdonDetail(null)}
                    className="tap-44 min-h-[44px] px-6 rounded-xl bg-slate-800 hover:bg-slate-900 text-white text-xs font-bold cursor-pointer"
                  >
                    Kapat
                  </Button>
                </div>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
