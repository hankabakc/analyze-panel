import { useState, useEffect, useRef, useMemo, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import axios from "axios";
import { membershipApi, analysisApi, studyPlansApi, psychTestsApi } from "@/lib/api";
import type { AnalysisReportSummary, User } from "@/lib/types";
import type { StudyPlanDto, PsychTestResultDto, BourdonResultDto } from "@/lib/api";
import { toast } from "sonner";
import { 
  Users, ChevronRight, Upload, FileBarChart2, Info, Sparkles, Layers,
  FileUp, AlertCircle, CheckCircle2, Trash2, Loader2, ArrowRight,
  Calendar, Plus, ListTodo, CheckCircle, Clock, X, BookOpen, RefreshCw,
  Brain
} from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";

export const BULK_POLLING_TIMEOUT_MS = 120_000; // 2 dakika üst sınır (T-051)

import { StaiLevelBadge, StaiExplanationNote } from "@/features/psychtest/components/StaiLevel";

interface BulkUploadItem {
  id: string;
  file: File;
  studentId: string;
  status: "PENDING" | "UPLOADING" | "PROCESSING" | "PENDING_APPROVAL" | "SUCCESS" | "FAILED" | "TIMEOUT";
  errorMessage?: string;
  reportId?: string;
  processingStartedAt?: number;
}

/**
 * TeacherDashboard: Otonom analiz, tekil rapor ve toplu karne yükleme merkezi.
 */
export default function TeacherDashboard() {
  const navigate = useNavigate();
  const [students, setStudents] = useState<User[]>([]);
  const [reports, setReports] = useState<AnalysisReportSummary[]>([]); 
  const [selectedStudentId, setSelectedStudentId] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // Çalışma Planı State'leri (T-050B)
  const [studyPlans, setStudyPlans] = useState<StudyPlanDto[]>([]);
  const [availableTopics, setAvailableTopics] = useState<{ lessonName: string; topicName: string }[]>([]);
  const [isLoadingPlans, setIsLoadingPlans] = useState(false);
  const [isPlanModalOpen, setIsPlanModalOpen] = useState(false);
  const [planDueDate, setPlanDueDate] = useState("");
  const [planItems, setPlanItems] = useState<{
    id: string;
    lessonName?: string;
    topicSearch?: string;
    topicName: string;
    questionCount: number | "";
  }[]>([
    { id: "1", lessonName: "", topicSearch: "", topicName: "", questionCount: 20 }
  ]);
  const [isSubmittingPlan, setIsSubmittingPlan] = useState(false);

  // T-060 / APP-01 §2.7: Konu seçimi için benzersiz ders listesi
  const availableLessons = useMemo(() => {
    return Array.from(new Set(availableTopics.map(t => t.lessonName))).sort();
  }, [availableTopics]);

  // activeMode: "SINGLE" (öğrenci odaklı) veya "BULK" (toplu karne yükleme)
  const [activeMode, setActiveMode] = useState<"SINGLE" | "BULK">("SINGLE");

  // T-050D: Öğretmenin öğrencilerine ait okunmamış tamamlanan görev sayıları {studentId: count}
  const [unseenCounts, setUnseenCounts] = useState<Record<string, number>>({});

  // T-062: Öğretmenin öğrencilerine ait görülmemiş psikolojik test sayıları {studentId: count}
  const [unseenPsychCounts, setUnseenPsychCounts] = useState<Record<string, number>>({});

  // T-052D: Psikolojik Ölçek Sonuçları State'leri
  const [psychResults, setPsychResults] = useState<PsychTestResultDto[]>([]);
  const [isLoadingPsychResults, setIsLoadingPsychResults] = useState(false);
  const [psychResultsForbidden, setPsychResultsForbidden] = useState(false);

  // Toplu yükleme state'leri (T-049)
  const [bulkItems, setBulkItems] = useState<BulkUploadItem[]>([]);
  const [isBulkUploading, setIsBulkUploading] = useState(false);
  
  // Polling (Otomatik Tazeleme) için referans
  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null);

  /**
   * T-050D: Öğretmenin eşleşmiş öğrencileri için okunmamış görev sayılarını sunucudan tek çağrıyla çeker.
   */
  const fetchUnseenCounts = useCallback(async () => {
    try {
      const res = await studyPlansApi.getUnseenCounts();
      if (res.data.success && res.data.data) {
        setUnseenCounts(res.data.data);
      }
    } catch {
      // Sessizce geç
    }
  }, []);

  /**
   * T-062: Öğretmenin eşleşmiş öğrencileri için görülmemiş psikolojik test sayılarını sunucudan tek çağrıyla çeker.
   */
  const fetchUnseenPsychCounts = useCallback(async () => {
    try {
      const res = await psychTestsApi.getUnseenCounts();
      if (res.data?.success && res.data.data) {
        setUnseenPsychCounts(res.data.data.studentCounts || {});
      }
    } catch {
      // Sessizce geç
    }
  }, []);

  const fetchInitialData = useCallback(async () => {
    try {
      const response = await membershipApi.getMyStudents();
      if (response.data.success) {
        setStudents(response.data.data);
      }
      await Promise.all([fetchUnseenCounts(), fetchUnseenPsychCounts()]);
    } catch {
      toast.error("Öğrenci listeniz yüklenirken bir hata oluştu.");
    } finally {
      setIsLoading(false);
    }
  }, [fetchUnseenCounts, fetchUnseenPsychCounts]);

  /**
   * fetchReports: Raporları getirir ve eğer "Analiz Ediliyor..." varsa polling başlatır.
   */
  const fetchReports = async (studentId: string, isAutoRefresh = false) => {
    if (!isAutoRefresh) setSelectedStudentId(studentId);
    
    try {
      const response = await analysisApi.getStudentReports(studentId);
      if (response.data.success) {
        const fetchedReports = response.data.data;
        setReports(fetchedReports);

        // Eğer listede hala "Analiz Ediliyor..." olan bir rapor varsa polling'i başlat/devam ettir
        const isStillProcessing = fetchedReports.some((r) => r.examTitle === "Analiz Ediliyor...");
        
        if (isStillProcessing) {
          if (!pollingRef.current) {
            console.log(">> [POLLING] Analiz devam ediyor, otomatik takip başlatıldı.");
            pollingRef.current = setInterval(() => fetchReports(studentId, true), 3000);
          }
        } else {
          stopPolling();
        }
      }
    } catch {
      if (!isAutoRefresh) toast.error("Raporlar yüklenemedi.");
      stopPolling();
    }
  };

  const stopPolling = useCallback(() => {
    if (pollingRef.current) {
      clearInterval(pollingRef.current);
      pollingRef.current = null;
      console.log(">> [POLLING] Tüm analizler tamamlandı, takip durduruldu.");
    }
  }, []);

  /**
   * T-050B & T-050D: Öğrencinin mevcut çalışma planlarını getirir.
   * Görülmemiş tamamlanan kalemler varsa görüldü damgası basar ve rozeti sıfırlar.
   */
  const fetchStudyPlans = async (studentId: string) => {
    setIsLoadingPlans(true);
    try {
      const res = await studyPlansApi.getPlansForStudent(studentId);
      if (res.data.success) {
        const plans = res.data.data || [];
        setStudyPlans(plans);

        // T-050D: Tamamlanmış ancak henüz görülmemiş (teacherSeenAt null) kalemleri olan planları görüldü olarak işaretle
        const plansToMark = plans.filter(p => p.items?.some(i => i.completedAt && !i.teacherSeenAt));
        if (plansToMark.length > 0) {
          for (const plan of plansToMark) {
            try {
              await studyPlansApi.markPlanAsSeen(plan.id);
            } catch (e) {
              console.error("Görüldü damgası basılamadı:", e);
            }
          }
          // Rozeti yerel olarak sıfırla
          setUnseenCounts(prev => ({ ...prev, [studentId]: 0 }));
        }
      }
    } catch {
      setStudyPlans([]);
    } finally {
      setIsLoadingPlans(false);
    }
  };

  /**
   * T-050B: Öğrencinin kümülatif analizinden ayrıştırılmış konuları derler (Yeni uç açılmaz, APP-01 §2.1).
   */
  const fetchAvailableTopics = async (studentId: string) => {
    try {
      const res = await analysisApi.getCumulativeProfile(studentId);
      if (res.data.success && res.data.data?.consolidatedResult?.lessons) {
        const list: { lessonName: string; topicName: string }[] = [];
        for (const lesson of res.data.data.consolidatedResult.lessons) {
          if (lesson.topics) {
            for (const t of lesson.topics) {
              if (t.topicName && !list.some(x => x.topicName === t.topicName)) {
                list.push({ lessonName: lesson.lessonName, topicName: t.topicName });
              }
            }
          }
        }
        setAvailableTopics(list);
      } else {
        setAvailableTopics([]);
      }
    } catch {
      setAvailableTopics([]);
    }
  };

  /**
   * T-052D: Öğrencinin psikolojik ölçek sonuçlarını sunucudan getirir.
   * Yalnızca öğretmenin aktif eşleştiği öğrenciler için 200 döner, eşleşmemişse 403 döner (ENG-11 §3.1).
   */
  const [bourdonDetails, setBourdonDetails] = useState<Record<string, BourdonResultDto>>({});

  const fetchPsychResults = async (studentId: string) => {
    setIsLoadingPsychResults(true);
    setPsychResultsForbidden(false);
    try {
      const res = await psychTestsApi.getStudentResults(studentId);
      if (res.data.success) {
        const results = res.data.data || [];
        setPsychResults(results);

        // T-062: Öğrencinin psikolojik test sonuçları açıldığında görüldü olarak damgalanır ve rozet sıfırlanır
        const hasCompleted = results.some(r => r.status === 'COMPLETED');
        if (hasCompleted) {
          psychTestsApi.markResultsSeen(studentId).catch(() => {});
          setUnseenPsychCounts(prev => ({ ...prev, [studentId]: 0 }));
        }

        // T-053C: Tamamlanmış Bourdon testlerinin blok dökümünü paralel çek
        const completedBourdon = results.filter(r => r.testCode === 'BOURDON' && r.status === 'COMPLETED');
        if (completedBourdon.length > 0) {
          const detailsMap: Record<string, BourdonResultDto> = {};
          await Promise.all(
            completedBourdon.map(async (b) => {
              try {
                const bRes = await psychTestsApi.getBourdonResult(b.id);
                if (bRes.data?.success && bRes.data.data) {
                  detailsMap[b.id] = bRes.data.data;
                }
              } catch (e) {
                console.error("Bourdon detay hatası:", e);
              }
            })
          );
          setBourdonDetails(prev => ({ ...prev, ...detailsMap }));
        }
      }
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 403) {
        setPsychResultsForbidden(true);
      }
      setPsychResults([]);
    } finally {
      setIsLoadingPsychResults(false);
    }
  };

  const fetchStudentDetails = (studentId: string) => {
    setSelectedStudentId(studentId);
    stopPolling();
    fetchReports(studentId);
    fetchStudyPlans(studentId);
    fetchAvailableTopics(studentId);
    fetchPsychResults(studentId);
    setIsPlanModalOpen(false);
  };

  const openCreatePlanModal = () => {
    const defaultDate = new Date(Date.now() + 7 * 86400000).toISOString().split("T")[0];
    setPlanDueDate(defaultDate);
    const initialLesson = availableTopics[0]?.lessonName || "";
    setPlanItems([
      {
        id: "1",
        lessonName: initialLesson,
        topicSearch: "",
        topicName: availableTopics[0]?.topicName || "",
        questionCount: 20,
      },
    ]);
    setIsPlanModalOpen(true);
  };

  const addPlanRow = () => {
    setPlanItems(prev => [
      ...prev,
      { id: String(Date.now() + Math.random()), lessonName: "", topicSearch: "", topicName: "", questionCount: 20 }
    ]);
  };

  const removePlanRow = (id: string) => {
    if (planItems.length <= 1) {
      toast.error("Çalışma planında en az bir konu kalemi bulunmalıdır.");
      return;
    }
    setPlanItems(prev => prev.filter(item => item.id !== id));
  };

  const handlePlanItemChange = (
    id: string,
    field: "lessonName" | "topicSearch" | "topicName" | "questionCount",
    value: string | number
  ) => {
    setPlanItems(prev => prev.map(item => {
      if (item.id === id) {
        if (field === "lessonName") {
          return { ...item, lessonName: String(value), topicSearch: "", topicName: "" };
        }
        return { ...item, [field]: value };
      }
      return item;
    }));
  };

  const submitPlan = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!selectedStudentId) return;

    if (!planDueDate) {
      toast.error("Lütfen teslim tarihi seçiniz.");
      return;
    }

    const todayStr = new Date().toISOString().split("T")[0];
    if (planDueDate < todayStr) {
      toast.error("Teslim tarihi bugünden önceki bir tarih olamaz.");
      return;
    }

    if (planItems.length === 0) {
      toast.error("En az bir konu kalemi eklemelisiniz.");
      return;
    }

    for (let i = 0; i < planItems.length; i++) {
      const item = planItems[i];
      if (!item.topicName || item.topicName.trim() === "") {
        toast.error(`${i + 1}. satırda lütfen bir konu seçiniz.`);
        return;
      }
      const count = Number(item.questionCount);
      if (!count || count <= 0) {
        toast.error(`${i + 1}. satır için soru sayısı en az 1 olmalıdır.`);
        return;
      }
    }

    setIsSubmittingPlan(true);
    try {
      const response = await studyPlansApi.createPlan({
        studentId: selectedStudentId,
        dueDate: planDueDate,
        items: planItems.map(item => ({
          topicName: item.topicName.trim(),
          questionCount: Number(item.questionCount)
        }))
      });

      if (response.data.success) {
        toast.success("Çalışma planı başarıyla kaydedildi!");
        setIsPlanModalOpen(false);
        fetchStudyPlans(selectedStudentId);
      }
    } catch (error) {
      const errMsg = (axios.isAxiosError(error) && error.response?.data?.message) || "Çalışma planı kaydedilirken bir hata oluştu.";
      toast.error(errMsg);
    } finally {
      setIsSubmittingPlan(false);
    }
  };

  const handleFileUpload = async (studentId: string, event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    try {
      toast.loading("Otonom Süper Analiz başlatıldı...");
      const response = await analysisApi.uploadPdf(studentId, 0, 'AUTO', file);
      if (response.data.success) {
        toast.dismiss();
        toast.success("Dosya alındı! Karne ayrıştırılıyor.");
        // Hemen ilk sorguyu yap, bu zaten polling'i tetikleyecek
        fetchReports(studentId);
      }
    } catch (error) {
      toast.dismiss();
      // Sunucunun kendi mesajını göster; genel bir "başarısız" metni hatayı gizliyordu.
      // 413 ayrıca ele alınır: bu durum sunucuya Spring'e ulaşmadan takıldığında
      // yanıt gövdesi teknik bir metin oluyor, kullanıcıya onu göstermenin anlamı yok.
      const errorResponse = axios.isAxiosError(error) ? error.response : undefined;
      const mesaj = errorResponse?.status === 413
        ? "Dosya çok büyük. Daha küçük bir PDF seçiniz."
        : errorResponse?.data?.message || "Yükleme başarısız oldu.";
      toast.error(mesaj);
    } finally {
      event.target.value = '';
    }
  };

  // Toplu yükleme işlemleri (T-049)
  const handleBulkFilesSelected = (event: React.ChangeEvent<HTMLInputElement>) => {
    const files = event.target.files;
    if (!files || files.length === 0) return;

    const newItems: BulkUploadItem[] = Array.from(files).map((file) => ({
      id: crypto.randomUUID(),
      file,
      studentId: "",
      status: "PENDING",
    }));

    setBulkItems((prev) => [...prev, ...newItems]);
    event.target.value = "";
  };

  const updateItemStudent = (itemId: string, studentId: string) => {
    setBulkItems((prev) =>
      prev.map((item) => (item.id === itemId ? { ...item, studentId } : item))
    );
  };

  const removeBulkItem = (itemId: string) => {
    setBulkItems((prev) => prev.filter((item) => item.id !== itemId));
  };

  const handleClearBulkItems = () => {
    setBulkItems((prev) => prev.filter((item) => item.status === "UPLOADING"));
  };

  const startBulkUpload = async () => {
    const pendingItems = bulkItems.filter(
      (item) => item.status === "PENDING" || item.status === "FAILED"
    );

    const unassigned = pendingItems.find((item) => !item.studentId);
    if (unassigned) {
      toast.error("Lütfen her dosya için bir öğrenci seçiniz.");
      return;
    }

    if (pendingItems.length === 0) {
      toast.info("Yüklenecek bekleyen dosya bulunmuyor.");
      return;
    }

    setIsBulkUploading(true);
    toast.loading("Toplu karne yüklemesi başlatıldı...", { id: "bulk-upload-toast" });

    let successCount = 0;
    let failCount = 0;

    // Sıralı çağrı döngüsü (APP-01 §2.7, ENG-11 §3.1)
    for (const item of pendingItems) {
      setBulkItems((prev) =>
        prev.map((it) =>
          it.id === item.id ? { ...it, status: "UPLOADING", errorMessage: undefined } : it
        )
      );

      try {
        const response = await analysisApi.uploadPdf(item.studentId, 0, "AUTO", item.file);
        if (response.data.success) {
          successCount++;
          setBulkItems((prev) =>
            prev.map((it) =>
              it.id === item.id
                ? {
                    ...it,
                    status: "PROCESSING",
                    reportId: response.data.data,
                    processingStartedAt: Date.now(),
                  }
                : it
            )
          );
        } else {
          failCount++;
          setBulkItems((prev) =>
            prev.map((it) =>
              it.id === item.id
                ? {
                    ...it,
                    status: "FAILED",
                    errorMessage: response.data.message || "Yükleme başarısız.",
                  }
                : it
            )
          );
        }
      } catch (error) {
        failCount++;
        const errorResponse = axios.isAxiosError(error) ? error.response : undefined;
        const mesaj =
          errorResponse?.status === 413
            ? "Dosya çok büyük. Daha küçük bir PDF seçiniz."
            : errorResponse?.data?.message || "Yükleme başarısız oldu.";
        setBulkItems((prev) =>
          prev.map((it) =>
            it.id === item.id
              ? {
                  ...it,
                  status: "FAILED",
                  errorMessage: mesaj,
                }
              : it
          )
        );
      }
    }

    setIsBulkUploading(false);
    toast.dismiss("bulk-upload-toast");

    if (failCount === 0) {
      toast.success(`Tüm dosyalar (${successCount}) sisteme iletildi! Ayrıştırma tamamlanıyor.`);
    } else {
      toast.warning(`${successCount} dosya iletildi, ${failCount} dosyada hata oluştu.`);
    }
  };

  // Toplu yüklemede PROCESSING olan raporları periyodik takip et (T-049, T-051)
  const bulkItemsRef = useRef(bulkItems);
  bulkItemsRef.current = bulkItems;

  const hasProcessingItems = bulkItems.some(
    (item) => item.status === "PROCESSING" && item.reportId && item.studentId
  );

  useEffect(() => {
    if (!hasProcessingItems) return;

    const interval = setInterval(async () => {
      const currentItems = bulkItemsRef.current;
      const now = Date.now();

      // 1. Süresi 2 dakikayı (120 saniye) aşan kalemleri TIMEOUT durumuna al (T-051, IST-03)
      const timedOutItemIds = new Set<string>();
      for (const item of currentItems) {
        if (item.status === "PROCESSING" && item.reportId && item.studentId) {
          const startTime = item.processingStartedAt || now;
          if (now - startTime >= BULK_POLLING_TIMEOUT_MS) {
            timedOutItemIds.add(item.id);
          }
        }
      }

      if (timedOutItemIds.size > 0) {
        setBulkItems((prev) =>
          prev.map((item) => {
            if (timedOutItemIds.has(item.id)) {
              return {
                ...item,
                status: "TIMEOUT",
                errorMessage: "Durum alınamadı — zaman aşımı",
              };
            }
            return item;
          })
        );
      }

      // 2. Hâlâ PROCESSING durumunda olan aktif kalemleri filtrele
      const activeItems = currentItems.filter(
        (item) =>
          item.status === "PROCESSING" &&
          item.reportId &&
          item.studentId &&
          !timedOutItemIds.has(item.id)
      );

      if (activeItems.length === 0) return;

      const studentIds = [...new Set(activeItems.map((i) => i.studentId))];
      for (const stId of studentIds) {
        try {
          const res = await analysisApi.getStudentReports(stId);
          if (res.data.success) {
            const studentReports = res.data.data;
            setBulkItems((prev) =>
              prev.map((item) => {
                if (item.studentId === stId && item.status === "PROCESSING" && item.reportId) {
                  const rep = studentReports.find((r) => r.id === item.reportId);
                  if (rep) {
                    if (rep.status === "FAILED") {
                      return {
                        ...item,
                        status: "FAILED",
                        errorMessage: rep.validationErrors || "Ayrıştırma başarısız.",
                      };
                    } else if (rep.status === "PENDING_APPROVAL" || rep.status === "APPROVED") {
                      return {
                        ...item,
                        status: "PENDING_APPROVAL",
                      };
                    }
                  }
                }
                return item;
              })
            );
          }
        } catch {
          // Polling hatası sessizce geçilir
        }
      }
    }, 2000);

    return () => clearInterval(interval);
  }, [hasProcessingItems]);

  const handleRetryBulkItem = async (itemId: string) => {
    const item = bulkItems.find((i) => i.id === itemId);
    if (!item || !item.studentId || !item.reportId) return;

    // Durumu yeniden PROCESSING'e al ve zaman aşımı sayacını sıfırla
    setBulkItems((prev) =>
      prev.map((i) =>
        i.id === itemId
          ? {
              ...i,
              status: "PROCESSING",
              errorMessage: undefined,
              processingStartedAt: Date.now(),
            }
          : i
      )
    );

    // İlk denemeyi hemen gönder (IST-02 §4.2)
    try {
      const res = await analysisApi.getStudentReports(item.studentId);
      if (res.data.success) {
        const studentReports = res.data.data;
        const rep = studentReports.find((r) => r.id === item.reportId);
        if (rep) {
          if (rep.status === "FAILED") {
            setBulkItems((prev) =>
              prev.map((i) =>
                i.id === itemId
                  ? {
                      ...i,
                      status: "FAILED",
                      errorMessage: rep.validationErrors || "Ayrıştırma başarısız.",
                    }
                  : i
              )
            );
          } else if (rep.status === "PENDING_APPROVAL" || rep.status === "APPROVED") {
            setBulkItems((prev) =>
              prev.map((i) =>
                i.id === itemId
                  ? {
                      ...i,
                      status: "PENDING_APPROVAL",
                    }
                  : i
              )
            );
          }
        }
      }
    } catch {
      // Hata durumunda periyodik yoklama aralığı devam eder
    }
  };

  const canStartBulkUpload =
    bulkItems.length > 0 &&
    bulkItems.some((item) => item.status === "PENDING" || item.status === "FAILED" || item.status === "TIMEOUT") &&
    bulkItems
      .filter((item) => item.status === "PENDING" || item.status === "FAILED" || item.status === "TIMEOUT")
      .every((item) => item.studentId !== "");

  const unuploadedCount = bulkItems.filter(
    (item) => item.status === "PENDING" || item.status === "FAILED" || item.status === "TIMEOUT"
  ).length;

  // Sayfa kapanırken veya öğrenci değişirken polling'i temizle
  useEffect(() => {
    fetchInitialData();
    return () => stopPolling();
  }, [fetchInitialData, stopPolling]);

  if (isLoading) return (
    <div className="w-full flex flex-col items-center justify-center gap-6 py-32">
      <div className="w-10 h-10 border-[5px] border-blue-500 border-t-transparent rounded-full animate-spin"></div>
      <p className="text-[10px] font-black uppercase tracking-[0.4em] text-slate-400">Analiz Merkezi Yükleniyor</p>
    </div>
  );

  return (
    <div className="w-full space-y-10 sm:space-y-12 animate-in fade-in duration-700">
      {/* Üst Başlık ve Mod Değiştirici Sekmeler */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-100 pb-6">
        <div className="flex flex-col gap-2">
          <h2 className="text-2xl sm:text-4xl font-black text-slate-900 tracking-tighter">
            Eğitmen <span className="text-blue-500 italic">Paneli</span>
          </h2>
          <p className="text-[10px] font-black text-slate-400 uppercase tracking-[0.4em]">Otonom Analiz & Öğrenci Takibi</p>
        </div>

        <div className="flex items-center gap-2 p-1.5 bg-slate-100/80 rounded-2xl self-start sm:self-auto">
          <button
            type="button"
            onClick={() => setActiveMode("SINGLE")}
            className={`tap-44 min-h-[44px] px-4 sm:px-6 rounded-xl font-black text-xs transition-all flex items-center gap-2 focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:outline-none ${
              activeMode === "SINGLE"
                ? "bg-white text-slate-900 shadow-sm"
                : "text-slate-500 hover:text-slate-900"
            }`}
          >
            <Users className="h-4 w-4 text-blue-500" />
            Öğrenci Raporları
          </button>
          <button
            type="button"
            onClick={() => setActiveMode("BULK")}
            className={`tap-44 min-h-[44px] px-4 sm:px-6 rounded-xl font-black text-xs transition-all flex items-center gap-2 focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:outline-none ${
              activeMode === "BULK"
                ? "bg-blue-600 text-white shadow-sm shadow-blue-200"
                : "text-slate-500 hover:text-slate-900"
            }`}
          >
            <FileUp className="h-4 w-4" />
            Toplu Karne Yükleme
            {bulkItems.length > 0 && (
              <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${activeMode === "BULK" ? "bg-white text-blue-600" : "bg-blue-100 text-blue-700"}`}>
                {bulkItems.length}
              </span>
            )}
          </button>
        </div>
      </div>

      {activeMode === "BULK" ? (
        /* Toplu Karne Yükleme Görünümü (T-049) */
        <div className="space-y-8 animate-in fade-in duration-500">
          <div className="bg-white p-6 sm:p-10 rounded-[2.5rem] sm:rounded-[3.5rem] shadow-xl border border-slate-100 flex flex-col items-center gap-6 text-center relative overflow-hidden">
            <div className="w-16 h-16 sm:w-20 sm:h-20 bg-blue-50 text-blue-600 rounded-[1.5rem] sm:rounded-[2rem] flex items-center justify-center shadow-lg shadow-blue-100">
              <FileUp className="h-8 w-8 sm:h-10 sm:w-10" />
            </div>
            <div className="space-y-2 max-w-xl">
              <h3 className="text-xl sm:text-2xl font-black text-slate-900 tracking-tight">Toplu Karne Yükleme Merkezi</h3>
              <p className="text-xs font-bold text-slate-500">
                Birden çok PDF karne seçin, her birini ilgili öğrenciyle eşleştirin ve tek akışta yükleyin. Karneden öğrenci bilgisi okunmaz, eşleştirme kontrolü öğretmendedir.
              </p>
            </div>

            <label
              htmlFor="bulk-pdf-input"
              className="cursor-pointer bg-slate-900 text-white px-6 sm:px-10 py-3.5 sm:py-4 rounded-2xl font-black text-xs uppercase tracking-wider flex items-center justify-center gap-3 hover:bg-blue-600 transition-all shadow-xl shadow-slate-200 tap-44 min-h-[44px] focus-within:ring-2 focus-within:ring-blue-500"
            >
              <Upload className="h-4 w-4 shrink-0" />
              {bulkItems.length === 0 ? "PDF Karneleri Seçin" : "Daha Fazla PDF Ekle"}
              <input
                id="bulk-pdf-input"
                type="file"
                multiple
                accept=".pdf"
                disabled={isBulkUploading}
                onChange={handleBulkFilesSelected}
                className="sr-only"
              />
            </label>
          </div>

          {bulkItems.length === 0 ? (
            <div className="text-center py-16 text-slate-400 text-xs font-black uppercase tracking-widest italic border-2 border-dashed border-slate-200 rounded-[2.5rem] p-8">
              Henüz dosya seçilmedi. Yukarıdaki butona tıklayarak birden çok PDF karne seçebilirsiniz.
            </div>
          ) : (
            <div className="space-y-4">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 px-2">
                <div className="flex items-center gap-3">
                  <h4 className="font-black text-base text-slate-900">
                    Seçilen Dosyalar ({bulkItems.length})
                  </h4>
                  <span className="text-[11px] font-bold text-slate-500">
                    ({bulkItems.filter(i => i.studentId).length}/{bulkItems.length} öğrenci eşleştirildi)
                  </span>
                </div>
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    disabled={isBulkUploading}
                    onClick={handleClearBulkItems}
                    className="tap-44 min-h-[44px] px-4 py-2 text-xs font-bold text-slate-500 hover:text-red-600 transition-colors disabled:opacity-50 focus-visible:ring-2 focus-visible:ring-red-500 focus-visible:outline-none rounded-xl"
                  >
                    Listeyi Temizle
                  </button>
                  <button
                    type="button"
                    disabled={isBulkUploading || !canStartBulkUpload}
                    onClick={startBulkUpload}
                    className="tap-44 min-h-[44px] px-6 py-2.5 bg-blue-600 text-white rounded-xl font-black text-xs uppercase tracking-wider hover:bg-blue-700 transition-all shadow-lg shadow-blue-200 flex items-center gap-2 disabled:opacity-50 disabled:pointer-events-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:outline-none"
                  >
                    {isBulkUploading ? (
                      <>
                        <Loader2 className="h-4 w-4 animate-spin" /> Yükleniyor...
                      </>
                    ) : (
                      <>
                        <Upload className="h-4 w-4" /> Tümünü Yükle ({unuploadedCount})
                      </>
                    )}
                  </button>
                </div>
              </div>

              <div className="space-y-3">
                {bulkItems.map((item, index) => (
                  <div
                    key={item.id}
                    className="p-4 sm:p-5 rounded-2xl border border-slate-200 bg-white shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-4 transition-all"
                  >
                    <div className="flex items-center gap-3 min-w-0 flex-1">
                      <div className="w-10 h-10 rounded-xl bg-slate-100 flex items-center justify-center shrink-0 font-bold text-slate-700 text-xs">
                        {index + 1}
                      </div>
                      <div className="min-w-0 flex-1">
                        <p className="font-bold text-sm text-slate-900 truncate" title={item.file.name}>
                          {item.file.name}
                        </p>
                        <p className="text-[10px] text-slate-400 font-semibold mt-0.5">
                          {(item.file.size / 1024).toFixed(1)} KB
                        </p>
                      </div>
                    </div>

                    <div className="w-full sm:w-72 sm:shrink-0">
                      <label htmlFor={`student-select-${item.id}`} className="sr-only">
                        Öğrenci Seçin
                      </label>
                      <select
                        id={`student-select-${item.id}`}
                        value={item.studentId}
                        disabled={isBulkUploading || item.status === "SUCCESS"}
                        onChange={(e) => updateItemStudent(item.id, e.target.value)}
                        className="w-full min-h-[44px] tap-44 px-3 py-2 text-xs font-bold text-slate-800 bg-slate-50 border border-slate-200 rounded-xl focus:border-blue-500 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 transition-all disabled:opacity-60"
                      >
                        <option value="">-- Öğrenci Seçiniz --</option>
                        {students.map((s) => (
                          <option key={s.id} value={s.id}>
                            {s.fullName} ({s.email})
                          </option>
                        ))}
                      </select>
                    </div>

                    <div className="flex items-center justify-between sm:justify-end gap-3 shrink-0 flex-wrap">
                      {item.status === "PENDING" && (
                        <span className="text-[10px] font-bold px-3 py-1.5 rounded-lg bg-slate-100 text-slate-600">
                          Bekliyor
                        </span>
                      )}
                      {item.status === "UPLOADING" && (
                        <span className="text-[10px] font-bold px-3 py-1.5 rounded-lg bg-blue-50 text-blue-600 flex items-center gap-1.5 animate-pulse">
                          <Loader2 className="h-3.5 w-3.5 animate-spin" /> Yükleniyor
                        </span>
                      )}
                      {item.status === "PROCESSING" && (
                        <span className="text-[10px] font-bold px-3 py-1.5 rounded-lg bg-indigo-50 text-indigo-600 flex items-center gap-1.5 animate-pulse">
                          <Loader2 className="h-3.5 w-3.5 animate-spin" /> Ayrıştırılıyor
                        </span>
                      )}
                      {(item.status === "PENDING_APPROVAL" || item.status === "SUCCESS") && (
                        <span className="text-[10px] font-bold px-3 py-1.5 rounded-lg bg-emerald-50 text-emerald-700 flex items-center gap-1.5">
                          <CheckCircle2 className="h-3.5 w-3.5" /> Onay Bekliyor
                        </span>
                      )}
                      {item.status === "FAILED" && (
                        <span
                          className="text-[10px] font-bold px-3 py-1.5 rounded-lg bg-red-50 text-red-700 flex items-center gap-1.5 max-w-[180px] sm:max-w-[240px] truncate"
                          title={item.errorMessage}
                        >
                          <AlertCircle className="h-3.5 w-3.5 shrink-0" />
                          <span className="truncate">{item.errorMessage || "Başarısız"}</span>
                        </span>
                      )}
                      {item.status === "TIMEOUT" && (
                        <div className="flex items-center gap-2">
                          <span
                            className="text-[10px] font-bold px-3 py-1.5 rounded-lg bg-amber-50 text-amber-800 flex items-center gap-1.5 border border-amber-200/60"
                            title="Ayrıştırma durumu 2 dakika içinde doğrulanamadı"
                          >
                            <AlertCircle className="h-3.5 w-3.5 text-amber-600 shrink-0" />
                            <span>Durum alınamadı — yenile</span>
                          </span>
                          <button
                            type="button"
                            onClick={() => handleRetryBulkItem(item.id)}
                            className="tap-44 min-h-[44px] min-w-[44px] px-3.5 py-2 bg-amber-500 hover:bg-amber-600 active:bg-amber-700 text-white rounded-xl text-xs font-black uppercase tracking-wider flex items-center gap-1.5 transition-all shadow-sm focus-visible:ring-2 focus-visible:ring-amber-500 focus-visible:outline-none"
                            title="Durumu yeniden sorgula"
                          >
                            <RefreshCw className="h-3.5 w-3.5" /> Yenile
                          </button>
                        </div>
                      )}

                      <button
                        type="button"
                        disabled={isBulkUploading}
                        onClick={() => removeBulkItem(item.id)}
                        className="tap-44 min-w-[44px] min-h-[44px] flex items-center justify-center text-slate-400 hover:text-red-600 hover:bg-red-50 rounded-xl transition-colors disabled:opacity-40 focus-visible:ring-2 focus-visible:ring-red-500 focus-visible:outline-none"
                        title="Dosyayı kaldır"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      ) : (
        /* SINGLE: Bireysel Öğrenci Analizi (Mevcut Görünüm) */
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 lg:gap-12">
          {/* SOL: Öğrenci Listesi */}
          <div className="lg:col-span-1 space-y-6">
             <h3 className="text-xl font-black text-slate-900 tracking-tight flex items-center gap-3">
                <Users className="h-6 w-6 text-blue-500" /> Öğrencilerim
             </h3>
             <div className="space-y-3">
                {students.map(student => {
                  const count = unseenCounts[student.id] || 0;
                  return (
                    <div 
                      key={student.id}
                      role="button"
                      data-testid={`student-item-${student.id}`}
                      tabIndex={0}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' || e.key === ' ') {
                          e.preventDefault();
                          fetchStudentDetails(student.id);
                        }
                      }}
                      onClick={() => fetchStudentDetails(student.id)}
                      className={`p-4 sm:p-6 rounded-[2rem] sm:rounded-[2.5rem] border-2 cursor-pointer transition-all tap-44 min-h-[44px] ${selectedStudentId === student.id ? 'border-blue-500 bg-blue-50/30 shadow-lg' : 'border-slate-50 bg-white hover:border-slate-200'}`}
                    >
                       <div className="flex items-center justify-between gap-2">
                         <h5 className="font-black text-sm text-slate-900 tracking-tight truncate">{student.fullName}</h5>
                         <div className="flex items-center gap-1.5 shrink-0">
                           {count > 0 && (
                             <span 
                               data-testid={`unseen-badge-${student.id}`}
                               className="inline-flex items-center justify-center px-2 py-0.5 rounded-full text-[10px] font-black bg-blue-600 text-white shadow-sm animate-pulse"
                               title={`${count} yeni tamamlanan görev`}
                               aria-label={`${count} yeni tamamlanan çalışma görevi`}
                             >
                               {count}
                             </span>
                           )}
                           {(unseenPsychCounts[student.id] || 0) > 0 && (
                             <span 
                               data-testid={`unseen-psych-badge-${student.id}`}
                               className="inline-flex items-center justify-center px-2 py-0.5 rounded-full text-[10px] font-black bg-purple-600 text-white shadow-sm animate-pulse"
                               title={`${unseenPsychCounts[student.id]} yeni psikolojik test sonucu`}
                               aria-label={`${unseenPsychCounts[student.id]} yeni psikolojik test sonucu`}
                             >
                               {unseenPsychCounts[student.id]}
                             </span>
                           )}
                         </div>
                       </div>
                       <p className="text-[9px] font-black text-slate-400 uppercase tracking-widest mt-1 truncate">{student.email}</p>
                    </div>
                  );
                })}
             </div>
          </div>

          {/* SAĞ: Otonom Analiz Merkezi */}
          <div className="lg:col-span-2 space-y-8">
             {!selectedStudentId ? (
               <Card className="rounded-[2.5rem] sm:rounded-[4rem] border-2 border-dashed border-slate-200 bg-slate-50/50 p-12 sm:p-32 text-center">
                  <Info className="h-12 w-12 text-slate-200 mx-auto mb-6" />
                  <p className="text-slate-400 font-black text-[10px] uppercase tracking-[0.4em]">Soldan bir öğrenci seçerek otonom analizi başlatın.</p>
               </Card>
             ) : (
               <div className="space-y-8 animate-in slide-in-from-bottom-5">
                  
                  {/* Otonom Yükleme Alanı */}
                  <div className="bg-white p-6 sm:p-10 rounded-[2.5rem] sm:rounded-[4rem] shadow-2xl border border-slate-50 flex flex-col items-center gap-8 relative overflow-hidden">
                     <div className="absolute top-0 right-0 w-32 h-32 bg-blue-500/5 rounded-full blur-3xl"></div>
                     <div className="flex flex-col items-center text-center gap-4">
                        <div className="w-16 h-16 sm:w-20 sm:h-20 bg-blue-600 text-white rounded-[1.5rem] sm:rounded-[2rem] flex items-center justify-center shadow-2xl shadow-blue-200">
                           <Sparkles className="h-8 w-8 sm:h-10 sm:w-10 animate-pulse" />
                        </div>
                        <div className="space-y-1">
                           <h3 className="font-black text-slate-900 text-lg sm:text-xl tracking-tight uppercase">Karne Analizi</h3>
                           <p className="text-[10px] font-bold text-slate-400 uppercase tracking-[0.2em]">PDF Karneyi Yükleyin, Netler ve Konular Otomatik Okunsun</p>
                        </div>
                     </div>

                     <div className="flex flex-col items-center gap-3 w-full max-w-md">
                       <label className="w-full cursor-pointer bg-slate-900 text-white px-6 sm:px-12 py-4 sm:py-6 rounded-[2rem] sm:rounded-[2.5rem] font-black text-[11px] uppercase tracking-[0.3em] flex items-center justify-center gap-4 hover:bg-blue-600 transition-all shadow-2xl shadow-blue-100 group tap-44 min-h-[44px]">
                          <Upload className="h-5 w-5 group-hover:-translate-y-1 transition-transform shrink-0" /> 
                          PDF Karne Analiz Et
                          <input type="file" className="hidden" accept=".pdf" onChange={(e) => handleFileUpload(selectedStudentId, e)} />
                       </label>
                       
                       <button
                         type="button"
                         onClick={() => setActiveMode("BULK")}
                         className="tap-44 min-h-[44px] text-xs font-bold text-blue-600 hover:text-blue-700 flex items-center gap-1.5 focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:outline-none rounded-xl"
                       >
                         Birden fazla karne mi var? Toplu yüklemeye geçin <ArrowRight className="h-3.5 w-3.5" />
                       </button>
                      </div>
                   </div>

                  {/* T-050B: Çalışma Planları Bölümü */}
                  <div className="bg-white p-6 sm:p-10 rounded-[2.5rem] sm:rounded-[3.5rem] shadow-xl border border-slate-100 space-y-6">
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-100 pb-4">
                      <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center shrink-0">
                          <ListTodo className="h-5 w-5" />
                        </div>
                        <div>
                          <h3 className="text-lg font-black text-slate-900 tracking-tight">Çalışma Planları</h3>
                          <p className="text-[10px] font-bold text-slate-400 uppercase tracking-widest">Konu Hedefleri & Teslim Takvimi</p>
                        </div>
                      </div>

                      {!isPlanModalOpen && (
                        <button
                          type="button"
                          id="btn-create-plan"
                          data-testid="btn-create-plan"
                          onClick={openCreatePlanModal}
                          className="tap-44 min-h-[44px] px-5 py-2.5 bg-blue-600 text-white rounded-xl font-black text-xs uppercase tracking-wider hover:bg-blue-700 transition-all shadow-md shadow-blue-200 flex items-center gap-2 self-start sm:self-auto focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:outline-none"
                        >
                          <Plus className="h-4 w-4" /> Yeni Plan Ver
                        </button>
                      )}
                    </div>

                    {/* Yeni Plan Verme Formu */}
                    {isPlanModalOpen && (
                      <form onSubmit={submitPlan} className="p-5 sm:p-7 rounded-2xl border-2 border-blue-100 bg-blue-50/20 space-y-5 animate-in fade-in duration-300">
                        <div className="flex items-center justify-between gap-2 border-b border-blue-100 pb-3">
                          <h4 className="font-black text-sm text-slate-900 flex items-center gap-2">
                            <Plus className="h-4 w-4 text-blue-600" /> Öğrenciye Yeni Çalışma Planı Ata
                          </h4>
                          <button
                            type="button"
                            onClick={() => setIsPlanModalOpen(false)}
                            className="tap-44 min-w-[44px] min-h-[44px] flex items-center justify-center text-slate-400 hover:text-slate-600 rounded-xl"
                            title="Kapat"
                          >
                            <X className="h-4 w-4" />
                          </button>
                        </div>

                        {availableTopics.length === 0 ? (
                          <div className="p-4 rounded-xl bg-amber-50 border border-amber-200 text-amber-800 text-xs font-semibold flex items-center gap-3">
                            <AlertCircle className="h-5 w-5 text-amber-600 shrink-0" />
                            <span>Bu öğrencinin henüz onaylanmış bir karne analizi veya ayrıştırılmış konusu bulunmamaktadır. Çalışma planı oluşturabilmek için önce bir karne yükleyip onaylayınız.</span>
                          </div>
                        ) : (
                          <>
                            {/* Teslim Tarihi */}
                            <div className="space-y-1.5">
                              <label htmlFor="plan-due-date" className="block text-xs font-black text-slate-700 uppercase tracking-wider">
                                Teslim Tarihi <span className="text-red-500">*</span>
                              </label>
                              <div className="relative max-w-xs">
                                <input
                                  type="date"
                                  id="plan-due-date"
                                  min={new Date().toISOString().split("T")[0]}
                                  value={planDueDate}
                                  onChange={(e) => setPlanDueDate(e.target.value)}
                                  className="tap-44 min-h-[44px] w-full px-4 py-2.5 bg-white border border-slate-200 rounded-xl font-bold text-xs text-slate-800 focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:outline-none shadow-sm"
                                />
                              </div>
                            </div>

                            {/* Konu Kalemleri */}
                            <div className="space-y-3">
                              <label className="block text-xs font-black text-slate-700 uppercase tracking-wider">
                                Konu ve Soru Hedefleri <span className="text-red-500">*</span>
                              </label>

                                <div className="space-y-2.5">
                                  {planItems.map((item, index) => {
                                    const lessonTopics = item.lessonName
                                      ? availableTopics.filter(t => t.lessonName === item.lessonName)
                                      : [];
                                    const filteredLessonTopics = item.topicSearch?.trim()
                                      ? lessonTopics.filter(t => t.topicName.toLowerCase().includes(item.topicSearch!.trim().toLowerCase()))
                                      : lessonTopics;

                                    return (
                                      <div
                                        key={item.id}
                                        className="p-3 sm:p-4 rounded-xl border border-slate-200 bg-white shadow-sm flex flex-col lg:flex-row items-stretch lg:items-center gap-2.5 sm:gap-3"
                                      >
                                        <span className="w-6 h-6 rounded-lg bg-slate-100 flex items-center justify-center font-bold text-slate-600 text-xs shrink-0 self-start lg:self-auto">
                                          {index + 1}
                                        </span>

                                        {/* T-060: 1. Ders Seçici */}
                                        <div className="w-full lg:w-44 shrink-0">
                                          <select
                                            id={`plan-lesson-select-${index}`}
                                            data-testid={`plan-lesson-select-${index}`}
                                            value={item.lessonName || ""}
                                            onChange={(e) => handlePlanItemChange(item.id, "lessonName", e.target.value)}
                                            className="tap-44 min-h-[44px] w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl font-bold text-xs text-slate-900 focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:outline-none"
                                          >
                                            <option value="">-- Ders Seçiniz --</option>
                                            {availableLessons.map((lesson: string) => (
                                              <option key={lesson} value={lesson}>
                                                {lesson}
                                              </option>
                                            ))}
                                          </select>
                                        </div>

                                        {/* T-060: 2. Konu Arama / Filtreleme Kutusu */}
                                        <div className="w-full lg:w-36 shrink-0">
                                          <input
                                            type="text"
                                            id={`plan-topic-search-${index}`}
                                            data-testid={`plan-topic-search-${index}`}
                                            placeholder="Konu ara..."
                                            value={item.topicSearch || ""}
                                            onChange={(e) => handlePlanItemChange(item.id, "topicSearch", e.target.value)}
                                            disabled={!item.lessonName}
                                            className="tap-44 min-h-[44px] w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl font-bold text-xs text-slate-900 focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:outline-none disabled:opacity-50 disabled:bg-slate-100"
                                          />
                                        </div>

                                        {/* T-060: 3. Konu Seçici (Filtrelenmiş ayrıştırılmış konular) */}
                                        <div className="flex-1 min-w-0">
                                          <select
                                            id={`plan-topic-select-${index}`}
                                            data-testid={`plan-topic-select-${index}`}
                                            value={item.topicName}
                                            onChange={(e) => handlePlanItemChange(item.id, "topicName", e.target.value)}
                                            disabled={!item.lessonName}
                                            className="tap-44 min-h-[44px] w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl font-bold text-xs text-slate-900 focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:outline-none disabled:opacity-50 disabled:bg-slate-100"
                                          >
                                            <option value="">
                                              {item.lessonName ? `-- Konu Seçiniz (${filteredLessonTopics.length}) --` : "-- Önce Ders Seçiniz --"}
                                            </option>
                                            {filteredLessonTopics.map(t => (
                                              <option key={`${t.lessonName}-${t.topicName}`} value={t.topicName}>
                                                {t.topicName}
                                              </option>
                                            ))}
                                          </select>
                                        </div>

                                        {/* Soru Sayısı */}
                                        <div className="w-full lg:w-32 shrink-0 flex items-center gap-2">
                                          <input
                                            type="number"
                                            id={`plan-question-count-${index}`}
                                            data-testid={`plan-question-count-${index}`}
                                            min="1"
                                            placeholder="Soru"
                                            value={item.questionCount}
                                            onChange={(e) => handlePlanItemChange(item.id, "questionCount", e.target.value)}
                                            className="tap-44 min-h-[44px] w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl font-bold text-xs text-slate-900 focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:outline-none text-center"
                                          />
                                          <span className="text-xs font-bold text-slate-400 shrink-0">Soru</span>
                                        </div>

                                        {/* Sil Butonu */}
                                        <button
                                          type="button"
                                          data-testid={`plan-remove-row-${index}`}
                                          onClick={() => removePlanRow(item.id)}
                                          className="tap-44 min-w-[44px] min-h-[44px] flex items-center justify-center text-slate-400 hover:text-red-600 hover:bg-red-50 rounded-xl transition-colors shrink-0 focus-visible:ring-2 focus-visible:ring-red-500 focus-visible:outline-none self-end lg:self-auto"
                                          title="Konuyu kaldır"
                                        >
                                          <Trash2 className="h-4 w-4" />
                                        </button>
                                      </div>
                                    );
                                  })}
                                </div>

                              <button
                                type="button"
                                id="btn-add-plan-topic"
                                data-testid="btn-add-plan-topic"
                                onClick={addPlanRow}
                                className="tap-44 min-h-[44px] px-4 py-2 text-xs font-bold text-blue-600 bg-white border border-blue-200 hover:bg-blue-50 rounded-xl transition-colors flex items-center gap-2 focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:outline-none"
                              >
                                <Plus className="h-4 w-4" /> Yeni Konu Ekle
                              </button>
                            </div>

                            {/* Form Aksiyonları */}
                            <div className="flex items-center justify-end gap-3 pt-3 border-t border-blue-100">
                              <button
                                type="button"
                                disabled={isSubmittingPlan}
                                onClick={() => setIsPlanModalOpen(false)}
                                className="tap-44 min-h-[44px] px-5 py-2.5 text-xs font-bold text-slate-500 hover:text-slate-800 rounded-xl transition-colors"
                              >
                                Vazgeç
                              </button>
                              <button
                                type="submit"
                                id="btn-save-plan"
                                data-testid="btn-save-plan"
                                disabled={isSubmittingPlan}
                                className="tap-44 min-h-[44px] px-6 py-2.5 bg-blue-600 text-white rounded-xl font-black text-xs uppercase tracking-wider hover:bg-blue-700 transition-all shadow-md shadow-blue-200 flex items-center gap-2 disabled:opacity-50 focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:outline-none"
                              >
                                {isSubmittingPlan ? (
                                  <>
                                    <Loader2 className="h-4 w-4 animate-spin" /> Kaydediliyor...
                                  </>
                                ) : (
                                  <>
                                    <CheckCircle2 className="h-4 w-4" /> Planı Kaydet
                                  </>
                                )}
                              </button>
                            </div>
                          </>
                        )}
                      </form>
                    )}

                    {/* Mevcut Planlar Listesi */}
                    <div className="space-y-4">
                      {isLoadingPlans ? (
                        <div className="flex justify-center py-8">
                          <Loader2 className="h-6 w-6 animate-spin text-blue-500" />
                        </div>
                      ) : studyPlans.length === 0 ? (
                        <p className="text-center py-8 text-slate-400 text-xs font-black uppercase tracking-widest italic border border-dashed border-slate-200 rounded-2xl p-6">
                          Henüz bu öğrenciye atanmış bir çalışma planı bulunmuyor.
                        </p>
                      ) : (
                        <div className="space-y-4">
                          {studyPlans.map(plan => {
                            const completedCount = plan.items.filter(i => i.completedAt).length;
                            const totalCount = plan.items.length;
                            const isAllCompleted = totalCount > 0 && completedCount === totalCount;

                            return (
                              <div
                                key={plan.id}
                                className="p-5 sm:p-6 rounded-2xl border border-slate-200 bg-white shadow-sm space-y-4 transition-all"
                              >
                                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-slate-100 pb-3">
                                  <div className="flex items-center gap-3">
                                    <Calendar className="h-4 w-4 text-blue-500 shrink-0" />
                                    <span className="font-black text-sm text-slate-900">
                                      Teslim: {new Date(plan.dueDate).toLocaleDateString('tr-TR', { day: 'numeric', month: 'long', year: 'numeric' })}
                                    </span>
                                  </div>
                                  <div className="flex items-center gap-2">
                                    <span className={`px-2.5 py-1 rounded-lg text-xs font-bold flex items-center gap-1.5 ${
                                      isAllCompleted
                                        ? "bg-emerald-50 text-emerald-700"
                                        : "bg-blue-50 text-blue-700"
                                    }`}>
                                      {isAllCompleted ? (
                                        <>
                                          <CheckCircle className="h-3.5 w-3.5" /> Tamamlandı
                                        </>
                                      ) : (
                                        <>
                                          <Clock className="h-3.5 w-3.5" /> {completedCount}/{totalCount} Tamamlandı
                                        </>
                                      )}
                                    </span>
                                  </div>
                                </div>

                                <div className="space-y-2">
                                  {plan.items.map(item => (
                                    <div
                                      key={item.id}
                                      className="p-3 rounded-xl bg-slate-50 flex items-center justify-between gap-3 text-xs"
                                    >
                                      <div className="flex items-center gap-2.5 min-w-0">
                                        <BookOpen className="h-4 w-4 text-slate-400 shrink-0" />
                                        <span className="font-bold text-slate-800 truncate" title={item.topicName}>
                                          {item.topicName}
                                        </span>
                                      </div>

                                      <div className="flex items-center gap-3 shrink-0">
                                        <span className="font-black text-slate-600">
                                          {item.questionCount} Soru
                                        </span>
                                        {item.completedAt ? (
                                          <span className="px-2 py-0.5 rounded-md bg-emerald-100 text-emerald-800 text-[10px] font-bold flex items-center gap-1">
                                            <CheckCircle className="h-3 w-3" /> Çözüldü
                                          </span>
                                        ) : (
                                          <span className="px-2 py-0.5 rounded-md bg-amber-100 text-amber-800 text-[10px] font-bold flex items-center gap-1">
                                            <Clock className="h-3 w-3" /> Bekliyor
                                          </span>
                                        )}
                                      </div>
                                    </div>
                                  ))}
                                </div>
                              </div>
                            );
                          })}
                        </div>
                      )}
                    </div>
                  </div>

                  {/* Psikolojik Ölçek Sonuçları (T-052D) */}
                  <div
                    data-testid="psych-results-section"
                    className="p-6 sm:p-8 rounded-[2.5rem] sm:rounded-[3rem] border border-slate-100 bg-white/60 backdrop-blur-xl shadow-xl space-y-6"
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-3">
                        <div className="p-3 bg-purple-50 text-purple-600 rounded-2xl">
                          <Brain className="h-6 w-6" />
                        </div>
                        <div>
                          <h3 className="text-lg font-black text-slate-900 tracking-tight">Psikolojik Ölçek Sonuçları</h3>
                          <p className="text-xs font-bold text-slate-400">STAI Durumluk ve Sürekli Kaygı Envanteri</p>
                        </div>
                      </div>
                    </div>

                    {isLoadingPsychResults ? (
                      <div className="flex justify-center py-8">
                        <Loader2 className="h-6 w-6 animate-spin text-purple-500" />
                      </div>
                    ) : psychResultsForbidden ? (
                      <div
                        data-testid="psych-results-forbidden"
                        className="p-5 rounded-2xl bg-amber-50 border border-amber-200 text-amber-900 flex items-start gap-3"
                      >
                        <AlertCircle className="h-5 w-5 text-amber-600 shrink-0 mt-0.5" />
                        <div>
                          <h4 className="font-bold text-sm text-amber-900">Erişim Yetkisi Yok</h4>
                          <p className="text-xs text-amber-700 mt-1">
                            Bu öğrencinin psikolojik ölçek sonuçlarını görüntüleme yetkiniz bulunmamaktadır.
                          </p>
                        </div>
                      </div>
                    ) : psychResults.length === 0 ? (
                      <p
                        data-testid="psych-results-empty"
                        className="text-center py-8 text-slate-400 text-xs font-black uppercase tracking-widest italic border border-dashed border-slate-200 rounded-2xl p-6"
                      >
                        Bu öğrenciye ait henüz bir psikolojik ölçek kaydı bulunmuyor.
                      </p>
                    ) : (
                      <div className="space-y-4">
                        {psychResults.map((result) => {
                          const isCompleted = result.status === 'COMPLETED';
                          const isBourdon = result.testCode === 'BOURDON';
                          const bDetail = isBourdon ? bourdonDetails[result.id] : null;

                          return (
                            <div
                              key={result.id}
                              data-testid={`psych-result-card-${result.id}`}
                              className="p-5 sm:p-6 rounded-2xl border border-slate-200 bg-white shadow-sm space-y-4"
                            >
                              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-slate-100 pb-3">
                                <div className="flex items-center gap-3">
                                  <span className="font-black text-sm text-slate-900">
                                    {isBourdon
                                      ? 'Bourdon Dikkat Testi'
                                      : result.testCode === 'STAI'
                                      ? 'STAI (Durumluk–Sürekli Kaygı Envanteri)'
                                      : result.testCode}
                                  </span>
                                </div>
                                <div className="flex items-center gap-2">
                                  {isCompleted ? (
                                    <span
                                      data-testid="psych-result-completed"
                                      className="px-2.5 py-1 rounded-lg text-xs font-bold bg-emerald-50 text-emerald-700 flex items-center gap-1.5"
                                    >
                                      <CheckCircle className="h-3.5 w-3.5" /> Tamamlandı
                                    </span>
                                  ) : (
                                    <span
                                      data-testid="psych-result-pending"
                                      className="px-2.5 py-1 rounded-lg text-xs font-bold bg-amber-50 text-amber-700 flex items-center gap-1.5"
                                    >
                                      <Clock className="h-3.5 w-3.5" /> Bekliyor
                                    </span>
                                  )}
                                </div>
                              </div>

                              <div className="text-xs text-slate-500 space-y-1">
                                <div>
                                  <span className="font-medium text-slate-400">Atanma Tarihi: </span>
                                  <span className="font-bold text-slate-700">
                                    {new Date(result.assignedAt).toLocaleDateString('tr-TR', {
                                      day: 'numeric',
                                      month: 'long',
                                      year: 'numeric',
                                      hour: '2-digit',
                                      minute: '2-digit'
                                    })}
                                  </span>
                                </div>
                                {result.completedAt && (
                                  <div>
                                    <span className="font-medium text-slate-400">Tamamlanma Tarihi: </span>
                                    <span className="font-bold text-slate-700">
                                      {new Date(result.completedAt).toLocaleDateString('tr-TR', {
                                        day: 'numeric',
                                        month: 'long',
                                        year: 'numeric',
                                        hour: '2-digit',
                                        minute: '2-digit'
                                      })}
                                    </span>
                                  </div>
                                )}
                              </div>

                              {isCompleted ? (
                                isBourdon ? (
                                  /* BOURDON SONUÇ GÖRÜNÜMÜ (T-053C) */
                                  <div className="space-y-4 pt-2">
                                    {/* SÜRE AŞIMI UYARISI */}
                                    {result.bourdonTimedOut && (
                                      <div
                                        data-testid={`bourdon-timed-out-alert-${result.id}`}
                                        className="p-3.5 rounded-xl bg-amber-50 border border-amber-200 text-xs text-amber-900 flex items-start gap-2.5"
                                      >
                                        <AlertCircle className="h-4 w-4 text-amber-600 shrink-0 mt-0.5" />
                                        <div>
                                          <p className="font-bold">Süre aşımıyla teslim edildi</p>
                                          <p className="mt-0.5 text-amber-800">
                                            Test 180 saniyelik standart süre aşılarak tamamlanmıştır — gerçek süre: <strong>{result.bourdonDurationSeconds ?? 0} sn</strong>. Sayılar değerlendirilirken süre dikkate alınmalıdır.
                                          </p>
                                        </div>
                                      </div>
                                    )}

                                    {/* TOPLAM SAYILAR */}
                                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                                      <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-100 flex flex-col justify-center">
                                        <span className="text-[11px] font-medium text-slate-500">Toplam Doğru</span>
                                        <span data-testid="bourdon-total-correct" className="text-xl font-black text-emerald-600 mt-1">
                                          {result.bourdonTotalCorrect ?? 0}
                                        </span>
                                      </div>
                                      <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-100 flex flex-col justify-center">
                                        <span className="text-[11px] font-medium text-slate-500">Toplam Atlanan</span>
                                        <span data-testid="bourdon-total-omitted" className="text-xl font-black text-amber-600 mt-1">
                                          {result.bourdonTotalOmitted ?? 0}
                                        </span>
                                      </div>
                                      <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-100 flex flex-col justify-center">
                                        <span className="text-[11px] font-medium text-slate-500">Toplam Yanlış</span>
                                        <span data-testid="bourdon-total-incorrect" className="text-xl font-black text-rose-600 mt-1">
                                          {result.bourdonTotalIncorrect ?? 0}
                                        </span>
                                      </div>
                                      <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-100 flex flex-col justify-center">
                                        <span className="text-[11px] font-medium text-slate-500">Harcanan Süre</span>
                                        <span data-testid="bourdon-duration" className="text-xl font-black text-slate-900 mt-1">
                                          {result.bourdonDurationSeconds ?? 0} sn
                                        </span>
                                      </div>
                                    </div>

                                    {/* BLOK 1/2/3 TABLOSU */}
                                    {bDetail ? (
                                      <div className="space-y-3 pt-1">
                                        <h5 className="text-xs font-bold text-slate-700 uppercase tracking-wider">Bölüm (Blok) Analizi</h5>
                                        <div className="overflow-x-auto rounded-xl border border-slate-100">
                                          <table className="w-full text-xs text-left" data-testid="bourdon-blocks-table">
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
                                              {[bDetail.block1, bDetail.block2, bDetail.block3].map((block) => (
                                                <tr key={block.blockNumber} data-testid={`bourdon-block-row-${block.blockNumber}`}>
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

                                        {/* NÖTR GÖZLEM NOTU (APP-03 §5) */}
                                        {bDetail.observationNote && (
                                          <div data-testid="bourdon-observation-note" className="p-3.5 rounded-xl bg-cyan-50/60 border border-cyan-100 text-xs text-cyan-900 flex items-start gap-2.5">
                                            <Info className="h-4 w-4 text-cyan-600 shrink-0 mt-0.5" />
                                            <p className="leading-relaxed font-medium">{bDetail.observationNote}</p>
                                          </div>
                                        )}
                                      </div>
                                    ) : null}

                                    {/* APP-03 §5 YASAL BİLGİLENDİRME */}
                                    <div className="p-3 rounded-xl bg-slate-50 border border-slate-100 text-[11px] text-slate-500 flex items-start gap-2">
                                      <Info className="h-4 w-4 text-slate-400 shrink-0 mt-0.5" />
                                      <p className="leading-relaxed">
                                        Bourdon Dikkat Testi (a, b, d, g harfleri, 30 satır). Puanlar tanı veya kategori içermez; sonuçlar bireysel değerlendirilir.
                                      </p>
                                    </div>
                                  </div>
                                ) : (
                                  /* STAI SONUÇ GÖRÜNÜMÜ */
                                  <div className="space-y-3 pt-2">
                                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                                      <div className="p-4 rounded-xl bg-slate-50 border border-slate-100 flex flex-col justify-center">
                                        <span className="text-xs font-medium text-slate-500">Durumluk Kaygı Puanı</span>
                                        <div className="flex items-center gap-2.5 mt-1">
                                          <span data-testid="state-score-value" className="text-2xl font-black text-slate-900">
                                            {result.stateScore ?? '-'}
                                          </span>
                                          <StaiLevelBadge level={result.stateLevel} testIdPrefix={`teacher-state-level-${result.id}`} />
                                        </div>
                                      </div>
                                      <div className="p-4 rounded-xl bg-slate-50 border border-slate-100 flex flex-col justify-center">
                                        <span className="text-xs font-medium text-slate-500">Sürekli Kaygı Puanı</span>
                                        <div className="flex items-center gap-2.5 mt-1">
                                          <span data-testid="trait-score-value" className="text-2xl font-black text-slate-900">
                                            {result.traitScore ?? '-'}
                                          </span>
                                          <StaiLevelBadge level={result.traitLevel} testIdPrefix={`teacher-trait-level-${result.id}`} />
                                        </div>
                                      </div>
                                    </div>

                                    {/* GİZLİLİK VE STAI AÇIKLAMA NOTU (APP-03 §4, S-024, T-061B) */}
                                    <StaiExplanationNote variant="blue" />
                                  </div>
                                )
                              ) : (
                                <div data-testid={isBourdon ? `bourdon-pending-${result.id}` : `psych-pending-${result.id}`} className="p-3.5 rounded-xl bg-slate-50 text-xs text-slate-500 border border-dashed border-slate-200">
                                  {isBourdon
                                    ? 'Öğrenci henüz testi tamamlamadı. Tamamlandığında dikkat ve işaretleme sonuçları burada görüntülenecektir.'
                                    : 'Öğrenci henüz testi tamamlamadı. Tamamlandığında puanlar burada görüntülenecektir.'}
                                </div>
                              )}
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </div>

                  {/* Rapor Listesi */}
                  <div className="space-y-4">
                     <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 px-2 sm:px-4 mb-6">
                        <h3 className="text-lg font-black text-slate-900 tracking-tight">Analiz Geçmişi</h3>
                        <button 
                          onClick={() => navigate(`/analysis/cumulative/${selectedStudentId}`)}
                          className="tap-44 min-h-[44px] self-start sm:self-auto bg-indigo-600 text-white px-6 py-3 rounded-2xl font-black text-[10px] uppercase tracking-widest hover:bg-indigo-700 transition-all shadow-xl shadow-indigo-100 flex items-center gap-2 focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:outline-none"
                        >
                          <Layers className="h-4 w-4" /> Tüm PDF'lerin Analizi
                        </button>
                     </div>
                     {reports.length === 0 ? (
                       <p className="text-center py-16 sm:py-20 text-slate-400 text-[10px] font-black uppercase tracking-widest italic border-2 border-dashed border-slate-100 rounded-[2.5rem] sm:rounded-[3rem] px-4">Henüz bir analiz raporu bulunmuyor.</p>
                     ) : (
                       reports.map(report => (
                          <Card 
                           key={report.id} 
                           onClick={() => navigate(`/analysis/${report.id}`)}
                           className={`rounded-[2.5rem] border-0 shadow-lg hover:shadow-xl transition-all bg-white group cursor-pointer overflow-hidden ${report.examTitle === "Analiz Ediliyor..." || report.status === "PROCESSING" ? "opacity-60 pointer-events-none" : ""}`}
                          >
                             <CardContent className="p-5 sm:p-8 flex items-center justify-between">
                                <div className="flex items-center gap-4 sm:gap-6 min-w-0">
                                   <div className={`w-12 h-12 sm:w-14 sm:h-14 rounded-2xl flex items-center justify-center shrink-0 ${report.examTitle === "Analiz Ediliyor..." || report.status === "PROCESSING" ? "bg-blue-50 text-blue-500 animate-spin" : "bg-slate-50 text-slate-900"}`}>
                                     <FileBarChart2 className="h-5 w-5 sm:h-6 sm:w-6" />
                                  </div>
                                  <div className="min-w-0">
                                     <div className="flex items-center gap-2 sm:gap-3 flex-wrap">
                                        <h4 className="text-base sm:text-lg font-black text-slate-900 tracking-tight truncate">
                                          {report.examTitle}
                                          {report.examTitle === "Analiz Ediliyor..." && <span className="ml-2 inline-block w-1.5 h-1.5 bg-blue-500 rounded-full animate-ping"></span>}
                                        </h4>
                                        <span className={`text-[8px] font-black uppercase tracking-widest px-2 py-1 rounded-md shrink-0 ${
                                          report.status === 'APPROVED' ? 'bg-emerald-50 text-emerald-600' :
                                          report.status === 'PROCESSING' ? 'bg-blue-50 text-blue-600' :
                                          report.status === 'FAILED' ? 'bg-red-50 text-red-600' :
                                          'bg-amber-50 text-amber-600'
                                        }`}>
                                           {report.status === 'APPROVED' ? 'Yayımlandı' :
                                            report.status === 'PROCESSING' ? 'Hazırlanıyor' :
                                            report.status === 'FAILED' ? 'Başarısız' : 'Onay Bekliyor'}
                                        </span>
                                     </div>
                                     <p className="text-[9px] font-black text-slate-400 uppercase tracking-widest mt-1 truncate">
                                        {new Date(report.processedAt).toLocaleDateString('tr-TR')} • {(report.intendedExamCount ?? 0) > 0 ? `${report.intendedExamCount} Deneme Verisi` : "Tür Tespit Ediliyor..."}
                                     </p>
                                  </div>
                               </div>
                               <ChevronRight className="h-6 w-6 text-slate-300 group-hover:text-blue-500 transition-colors shrink-0" />
                            </CardContent>
                         </Card>
                       ))
                     )}
                  </div>
               </div>
             )}
          </div>
        </div>
      )}
    </div>
  );
}
