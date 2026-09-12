import { useState, useEffect, useRef, useCallback } from "react";
import { 
  psychTestsApi, 
  type BourdonStartResponseDto, 
  type BourdonMarkedCellDto 
} from "@/lib/api";
import { toast } from "sonner";
import { 
  CheckCircle2, X, AlertCircle, Loader2, Brain, 
  Clock, Award, ChevronRight
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

interface StudentBourdonTestModalProps {
  assignmentId: string;
  assignmentStatus?: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED';
  testCode?: string;
  testTitle?: string;
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

type ModalPhase = "INTRO" | "ACTIVE" | "COMPLETED";

/**
 * StudentBourdonTestModal: Öğrencinin kendisine atanan Bourdon Dikkat Testi'ni
 * 3 dakikalık süre kısıtlaması altında harf ızgarası üzerinde işaretlemesini sağlayan modal bileşeni (T-053B / T-053F).
 * 
 * Kurallar ve Güvenlik:
 * - APP-01 §2.1: İş kuralı ve 185 saniyelik süre sınırı sunucuda denetlenir; yarıda kalan testte saat sıfırlanmaz.
 * - APP-03 §4: Öğrenciye teslim sonrasında KESİNLİKLE hiçbir puan veya doğru/yanlış sayısı gösterilmez.
 * - IST-02 §1.1 / §3.1: 375×812 px mobil ekranda 0 px yatay taşma; harf hücreleri gerçek en az 44×44 px boyuttadır.
 * - IST-02 §4.2: Sayaç, durum rozetleri ve bildirimler net Türkçe sunulur.
 */
export function StudentBourdonTestModal({
  assignmentId,
  assignmentStatus,
  testTitle = "Bourdon Dikkat Testi",
  isOpen,
  onClose,
  onSuccess,
}: StudentBourdonTestModalProps) {
  const [phase, setPhase] = useState<ModalPhase>("INTRO");
  const [startData, setStartData] = useState<BourdonStartResponseDto | null>(null);
  const [markedKeys, setMarkedKeys] = useState<Set<string>>(new Set());
  const [timeLeft, setTimeLeft] = useState<number>(180);
  const [isStarting, setIsStarting] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);

  const isContinuing = assignmentStatus === "IN_PROGRESS";

  // Dokunarak kaydırma ile harf işaretlemeyi ayıran kancalar (IST-02 §3.1)
  const touchStartPos = useRef<{ x: number; y: number } | null>(null);
  const isDraggingRef = useRef(false);

  const handleTouchStart = (e: React.TouchEvent) => {
    if (e.touches.length > 0) {
      touchStartPos.current = { x: e.touches[0].clientX, y: e.touches[0].clientY };
    }
    isDraggingRef.current = false;
  };

  const handleTouchMove = (e: React.TouchEvent) => {
    if (!touchStartPos.current || e.touches.length === 0) return;
    const dx = Math.abs(e.touches[0].clientX - touchStartPos.current.x);
    const dy = Math.abs(e.touches[0].clientY - touchStartPos.current.y);
    if (dx > 8 || dy > 8) {
      isDraggingRef.current = true;
    }
  };

  // Otomatik teslimatın yalnızca bir kez çalışmasını güvenceye alan kilit
  const hasSubmittedRef = useRef(false);

  // Modal her açıldığında durumu sıfırla
  useEffect(() => {
    if (!isOpen) return;
    setPhase("INTRO");
    setStartData(null);
    setMarkedKeys(new Set());
    setTimeLeft(180);
    setIsStarting(false);
    setIsSubmitting(false);
    setServerError(null);
    hasSubmittedRef.current = false;
    isDraggingRef.current = false;
  }, [isOpen]);

  // Testi başlatma veya devam ettirme ucu çağrısı (T-053F / APP-01 §2.1)
  const handleStartTest = async () => {
    setIsStarting(true);
    setServerError(null);
    try {
      const res = await psychTestsApi.startBourdonTest(assignmentId);
      if (res.data?.success && res.data?.data) {
        const data = res.data.data;
        setStartData(data);

        // T-060 / APP-01 §2.1: Kalan süre sunucuda hesaplanan remainingSeconds değerinden alınır.
        // Cihaz saatine (Date.now()) kesinlikle güvenilmez; telefon saati ileride olsa bile süre doğru başlar.
        let remaining: number;
        if (typeof data.remainingSeconds === "number") {
          remaining = data.remainingSeconds;
        } else {
          const startedTime = new Date(data.startedAt).getTime();
          const elapsedSeconds = Math.max(0, Math.floor((Date.now() - startedTime) / 1000));
          remaining = Math.max(0, (data.durationSeconds || 180) - elapsedSeconds);
        }

        setTimeLeft(remaining);
        setPhase("ACTIVE");

        if (remaining <= 0) {
          toast.warning("Test süreniz dolmuştur. Yanıtlarınız teslim ediliyor...");
          // Hemen teslim et
          setTimeout(() => {
            submitTest();
          }, 100);
        }
      } else {
        const msg = res.data?.message || "Test başlatılamadı.";
        setServerError(msg);
        toast.error(msg);
      }
    } catch (err: unknown) {
      const errorResponse = err as { response?: { data?: { message?: string } } };
      const msg = errorResponse.response?.data?.message || "Test başlatılırken bir bağlantı hatası oluştu.";
      setServerError(msg);
      toast.error(msg);
    } finally {
      setIsStarting(false);
    }
  };

  // Harf hücresini toggle etme (işaretle / kaldır)
  const handleToggleCell = (row: number, col: number) => {
    if (phase !== "ACTIVE" || isSubmitting) return;

    const key = `${row}-${col}`;
    setMarkedKeys((prev) => {
      const next = new Set(prev);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  };

  // Hücre tıklama olayı (kaydırma hareketiyle tıklamayı ayırt eder - IST-02 §3.1)
  const handleCellClick = (row: number, col: number) => {
    if (isDraggingRef.current) {
      isDraggingRef.current = false;
      return;
    }
    handleToggleCell(row, col);
  };

  // Teslim etme işlemi (manuel veya otomatik)
  const submitTest = useCallback(async () => {
    if (hasSubmittedRef.current) return;
    hasSubmittedRef.current = true;
    setIsSubmitting(true);
    setServerError(null);

    // İşaretlenen hücreleri DTO formatına dönüştür ({ row, col })
    const markedCells: BourdonMarkedCellDto[] = [];
    markedKeys.forEach((key) => {
      const [rStr, cStr] = key.split("-");
      markedCells.push({
        row: parseInt(rStr, 10),
        col: parseInt(cStr, 10),
      });
    });

    try {
      const res = await psychTestsApi.submitBourdonTest(assignmentId, {
        markedCells,
      });

      if (res.data?.success) {
        setPhase("COMPLETED");
        toast.success("Bourdon Dikkat Testi başarıyla tamamlandı!");
      } else {
        const msg = res.data?.message || "Test teslim edilirken bir hata oluştu.";
        setServerError(msg);
        toast.error(msg);
        hasSubmittedRef.current = false;
      }
    } catch (err: unknown) {
      const errorResponse = err as { response?: { data?: { message?: string } } };
      const msg = errorResponse.response?.data?.message || "Test teslim edilirken sunucu hatası oluştu.";
      setServerError(msg);
      toast.error(msg);
      hasSubmittedRef.current = false;
    } finally {
      setIsSubmitting(false);
    }
  }, [assignmentId, markedKeys]);

  // Sayaç mekanizması
  useEffect(() => {
    if (phase !== "ACTIVE" || isSubmitting) return;

    const timer = setInterval(() => {
      setTimeLeft((prev) => {
        if (prev <= 1) {
          clearInterval(timer);
          // Süre bittiğinde otomatik teslim et
          submitTest();
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(timer);
  }, [phase, isSubmitting, submitTest]);

  if (!isOpen) return null;

  // Sayaç formatı (MM:SS)
  const minutes = Math.floor(timeLeft / 60);
  const seconds = timeLeft % 60;
  const formattedTime = `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
  const isTimeCritical = timeLeft <= 30;

  return (
    <div
      data-testid="student-bourdon-test-modal"
      className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-6 backdrop-blur-xl bg-slate-900/60 animate-in fade-in duration-300"
      role="dialog"
      aria-modal="true"
      aria-labelledby="bourdon-modal-title"
    >
      <div className="bg-white w-full max-w-4xl max-h-[92vh] rounded-[2rem] sm:rounded-[3rem] shadow-2xl overflow-hidden flex flex-col animate-in zoom-in-95 duration-300 border border-slate-100">
        
        {/* ÜST BAŞLIK BARI */}
        <div className="bg-slate-900 p-5 sm:p-7 text-white flex justify-between items-center shrink-0">
          <div className="flex items-center gap-3 sm:gap-4 min-w-0">
            <div className="w-10 h-10 sm:w-12 sm:h-12 bg-cyan-500/20 border border-cyan-500/40 text-cyan-400 rounded-2xl flex items-center justify-center shrink-0">
              <Brain className="h-5 w-5 sm:h-6 sm:w-6" />
            </div>
            <div className="min-w-0">
              <h3
                id="bourdon-modal-title"
                className="text-base sm:text-xl font-black tracking-tight truncate"
              >
                {testTitle}
              </h3>
              <p className="text-[9px] sm:text-[10px] font-bold text-cyan-400 uppercase tracking-widest mt-0.5">
                Dikkat ve Konsantrasyon Değerlendirmesi
              </p>
            </div>
          </div>

          {/* Kapat butonu (Sadece INTRO veya COMPLETED fazında aktiftir, aktif test sırasında kazara çıkışı önler) */}
          {phase !== "ACTIVE" && (
            <button
              type="button"
              onClick={onClose}
              className="p-2.5 sm:p-3 hover:bg-white/10 rounded-xl transition-all text-slate-300 hover:text-white"
              aria-label="Kapat"
            >
              <X className="h-5 w-5" />
            </button>
          )}
        </div>

        {/* MODAL İÇERİK GÖVDESİ */}
        <div className="p-4 sm:p-8 overflow-y-auto flex-1 space-y-6 custom-scrollbar">
          {serverError && (
            <div className="p-4 bg-rose-50 border border-rose-200 rounded-2xl flex items-center gap-3 text-rose-700 text-xs font-bold">
              <AlertCircle className="h-5 w-5 shrink-0" />
              <span>{serverError}</span>
            </div>
          )}

          {/* 1. FAZ: GİRİŞ VE YÖNERGE EKRANI */}
          {phase === "INTRO" && (
            <div className="space-y-6 py-2">
              <div className="p-5 sm:p-6 bg-cyan-50/60 border border-cyan-200/70 rounded-3xl space-y-3">
                <div className="flex items-center gap-2 text-cyan-900 font-black text-sm sm:text-base">
                  <Clock className="h-5 w-5 text-cyan-600" />
                  <span>Test Yönergesi ve Kurallar</span>
                </div>
                <p className="text-xs sm:text-sm text-slate-700 leading-relaxed font-medium">
                  Önünüzdeki sayfada bulunan bütün <strong>a, b, d, g</strong> harflerinin altını çizin / üzerine dokunarak işaretleyin.
                </p>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 pt-2">
                  <div className="p-3 bg-white rounded-2xl border border-cyan-100 flex items-center gap-3">
                    <span className="text-xl font-black text-cyan-600">3 dk</span>
                    <span className="text-[11px] font-bold text-slate-600">Toplam Süre (180 sn)</span>
                  </div>
                  <div className="p-3 bg-white rounded-2xl border border-cyan-100 flex items-center gap-3">
                    <span className="text-xl font-black text-cyan-600">4</span>
                    <span className="text-[11px] font-bold text-slate-600">Hedef Harf (a, b, d, g)</span>
                  </div>
                  <div className="p-3 bg-white rounded-2xl border border-cyan-100 flex items-center gap-3">
                    <span className="text-xl font-black text-cyan-600">30</span>
                    <span className="text-[11px] font-bold text-slate-600">Satır Harf Izgarası</span>
                  </div>
                </div>
              </div>

              {isContinuing && (
                <div className="p-4 bg-amber-50 border border-amber-200 rounded-2xl flex items-start gap-3 text-amber-800 text-xs font-medium">
                  <AlertCircle className="h-5 w-5 text-amber-600 shrink-0 mt-0.5" />
                  <div>
                    <p className="font-bold text-amber-900">Yarıda Kalan Teste Devam Ediliyor</p>
                    <p className="mt-0.5 text-amber-800/90">
                      Bu test daha önce başlatılmıştır. Süreniz ilk başladığınız andan itibaren işlemeye devam etmektedir. Kapatmadan önceki işaretlerin kaybolacağını unutmayınız.
                    </p>
                  </div>
                </div>
              )}

              <div className="p-4 sm:p-5 bg-slate-50 border border-slate-200/80 rounded-2xl text-xs text-slate-600 space-y-2">
                <p className="font-bold text-slate-800">📌 Nasıl Çözülür?</p>
                <ul className="list-disc list-inside space-y-1 text-[11px] sm:text-xs">
                  <li>Harflere dokunarak işaretleyebilir, vazgeçtiğinizde tekrar dokunarak işareti kaldırabilirsiniz.</li>
                  <li>Süreniz dolduğunda test otomatik olarak teslim edilecektir.</li>
                  <li>İstediğiniz an "Testi Tamamla" butonuyla da teslim edebilirsiniz.</li>
                  <li>Mümkün olduğunca hızlı ve dikkatli biçimde hedef harfleri bulmaya çalışın.</li>
                </ul>
              </div>

              <div className="pt-4 flex justify-end">
                <Button
                  type="button"
                  data-testid="bourdon-start-confirm-btn"
                  onClick={handleStartTest}
                  disabled={isStarting}
                  className="w-full sm:w-auto tap-44 min-h-[48px] px-8 bg-cyan-600 hover:bg-cyan-700 active:bg-cyan-800 text-white rounded-2xl font-black text-sm tracking-wide shadow-lg shadow-cyan-600/25 flex items-center justify-center gap-2 cursor-pointer"
                >
                  {isStarting ? (
                    <>
                      <Loader2 className="h-4 w-4 animate-spin" />
                      <span>{isContinuing ? "Test Yükleniyor..." : "Test Başlatılıyor..."}</span>
                    </>
                  ) : (
                    <>
                      <span>{isContinuing ? "Teste Devam Et" : "Testi Başlat"}</span>
                      <ChevronRight className="h-4 w-4" />
                    </>
                  )}
                </Button>
              </div>
            </div>
          )}

          {/* 2. FAZ: AKTİF TEST EKRANI (IZGARA VE SAYAÇ) */}
          {phase === "ACTIVE" && startData && (
            <div className="space-y-4">
              
              {/* SAYAÇ VE KONTROL BARI (Sticky: Izgara kaydırılırken her zaman ekranda görünür) */}
              <div className="sticky top-0 z-20 flex flex-wrap items-center justify-between gap-3 p-3.5 sm:p-4 bg-slate-900 text-white rounded-2xl sm:rounded-3xl shadow-lg">
                
                {/* Sol: Geri Sayım Sayacı */}
                <div className="flex items-center gap-3">
                  <div
                    data-testid="bourdon-timer"
                    className={`flex items-center gap-2 px-3.5 py-1.5 rounded-xl border transition-all ${
                      isTimeCritical 
                        ? "bg-rose-500/20 border-rose-500/50 text-rose-400 animate-pulse font-black" 
                        : "bg-white/10 border-white/20 text-cyan-300 font-black"
                    }`}
                  >
                    <Clock className="h-4 w-4" />
                    <span className="text-base sm:text-lg tracking-wider font-mono">
                      {formattedTime}
                    </span>
                  </div>
                  <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400 hidden sm:inline">
                    Kalan Süre
                  </span>
                </div>

                {/* Orta: Hedef Harfler Hatırlatıcı */}
                <div 
                  data-testid="target-letters-reminder"
                  className="flex items-center gap-1.5 text-xs font-black bg-cyan-950/80 border border-cyan-800/80 px-3 py-1.5 rounded-xl text-cyan-300"
                >
                  <span className="text-[10px] uppercase text-cyan-400/80 tracking-widest mr-1">Hedefler:</span>
                  <span className="bg-cyan-500/30 px-1.5 py-0.5 rounded text-white font-mono">a</span>
                  <span className="bg-cyan-500/30 px-1.5 py-0.5 rounded text-white font-mono">b</span>
                  <span className="bg-cyan-500/30 px-1.5 py-0.5 rounded text-white font-mono">d</span>
                  <span className="bg-cyan-500/30 px-1.5 py-0.5 rounded text-white font-mono">g</span>
                </div>

                {/* Sağ: İşaret Sayısı ve Teslim Butonu */}
                <div className="flex items-center gap-2">
                  <span 
                    data-testid="bourdon-marked-count"
                    className="text-xs font-bold text-slate-300 bg-white/10 px-2.5 py-1.5 rounded-xl"
                  >
                    {markedKeys.size} Seçildi
                  </span>

                  <Button
                    type="button"
                    data-testid="submit-bourdon-btn"
                    onClick={submitTest}
                    disabled={isSubmitting}
                    className="tap-44 min-h-[40px] px-4 sm:px-6 bg-cyan-500 hover:bg-cyan-600 active:bg-cyan-700 text-white rounded-xl font-black text-xs uppercase tracking-wider transition-all flex items-center gap-1.5 shadow-sm cursor-pointer"
                  >
                    {isSubmitting ? (
                      <>
                        <Loader2 className="h-3.5 w-3.5 animate-spin" />
                        <span>Gönderiliyor...</span>
                      </>
                    ) : (
                      <>
                        <span>Testi Tamamla</span>
                      </>
                    )}
                  </Button>
                </div>
              </div>

              {/* HARF IZGARASI (30 SATIR × 22 SÜTUN) (IST-02 §3.1: En az 44×44 px hücreler) */}
              <div className="p-2 sm:p-4 bg-slate-50 border border-slate-200/80 rounded-2xl sm:rounded-3xl shadow-inner">
                <div className="overflow-x-auto overflow-y-auto max-h-[55vh] sm:max-h-[60vh] custom-scrollbar touch-pan-x pr-1">
                  <div className="inline-block min-w-full space-y-2">
                    {startData.grid.map((rowStr, rowIdx) => (
                      <div
                        key={rowIdx}
                        data-testid={`bourdon-row-${rowIdx}`}
                        className="flex items-center gap-1.5"
                      >
                        {/* Satır Numarası */}
                        <div className="w-8 min-w-[32px] shrink-0 text-center text-xs font-black text-slate-400 select-none">
                          {rowIdx + 1}
                        </div>

                        {/* Harf Butonları (IST-02 §3.1: 44×44 px gerçek dokunma boyutu) */}
                        <div className="flex items-center gap-1.5">
                          {rowStr.split("").map((letter, colIdx) => {
                            const key = `${rowIdx}-${colIdx}`;
                            const isMarked = markedKeys.has(key);

                            return (
                              <button
                                key={colIdx}
                                type="button"
                                data-testid={`cell-${rowIdx}-${colIdx}`}
                                data-selected={isMarked ? "true" : "false"}
                                onClick={() => handleCellClick(rowIdx, colIdx)}
                                onTouchStart={handleTouchStart}
                                onTouchMove={handleTouchMove}
                                className={`w-[44px] h-[44px] min-w-[44px] min-h-[44px] shrink-0 flex items-center justify-center rounded-xl text-base font-black transition-all select-none focus:outline-none cursor-pointer ${
                                  isMarked
                                    ? "bg-cyan-600 text-white shadow-md ring-2 ring-cyan-400 ring-offset-1 scale-105 z-10"
                                    : "bg-white hover:bg-slate-200/80 text-slate-700 border border-slate-200/90 hover:border-slate-300 shadow-2xs"
                                }`}
                                aria-label={`Satır ${rowIdx + 1}, Sütun ${colIdx + 1}: ${letter}`}
                              >
                                {letter}
                              </button>
                            );
                          })}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              </div>

              <p className="text-[10px] text-slate-400 font-bold text-center">
                İpucu: Harfe dokunarak işaretleyebilir, tekrar dokunarak işareti kaldırabilirsiniz. Izgara yatay ve dikey kaydırılabilir.
              </p>
            </div>
          )}

          {/* 3. FAZ: BAŞARIYLA TAMAMLANDI EKRANI (APP-03 §4: PUAN YOK) */}
          {phase === "COMPLETED" && (
            <Card
              data-testid="bourdon-completed-card"
              className="p-6 sm:p-10 text-center space-y-6 rounded-[2rem] sm:rounded-[2.5rem] border-0 shadow-lg bg-gradient-to-b from-white to-slate-50"
            >
              <div className="w-16 h-16 sm:w-20 sm:h-20 bg-emerald-50 text-emerald-600 rounded-full flex items-center justify-center mx-auto shadow-inner border border-emerald-100">
                <CheckCircle2 className="h-8 w-8 sm:h-10 sm:w-10" />
              </div>

              <div className="space-y-2 max-w-md mx-auto">
                <h4 className="text-xl sm:text-2xl font-black text-slate-900 tracking-tight">
                  Bourdon Dikkat Testi Tamamlandı
                </h4>
                <p className="text-xs sm:text-sm text-slate-600 leading-relaxed font-medium">
                  Test yanıtlarınız başarıyla kaydedilmiştir. Sonuçlar rehberlik servisine iletilmiş olup değerlendirme öğretmeniniz tarafından yapılacaktır.
                </p>
              </div>

              <div className="p-4 bg-cyan-50/60 border border-cyan-200/60 rounded-2xl max-w-md mx-auto text-left text-xs text-cyan-950 flex items-start gap-3">
                <Award className="h-5 w-5 text-cyan-600 shrink-0 mt-0.5" />
                <p className="leading-normal">
                  Düzenli dikkat ve konsantrasyon testleri, çalışma alışkanlıklarınızı ve deneme performansınızı en üst seviyeye çıkarmaya yardımcı olur.
                </p>
              </div>

              <div className="pt-2">
                <Button
                  type="button"
                  data-testid="bourdon-modal-close-btn"
                  onClick={() => {
                    onSuccess();
                    onClose();
                  }}
                  className="tap-44 min-h-[48px] px-8 bg-slate-900 hover:bg-slate-800 text-white rounded-2xl font-black text-xs uppercase tracking-wider transition-all cursor-pointer"
                >
                  Pencereyi Kapat
                </Button>
              </div>
            </Card>
          )}

        </div>
      </div>
    </div>
  );
}

export default StudentBourdonTestModal;
