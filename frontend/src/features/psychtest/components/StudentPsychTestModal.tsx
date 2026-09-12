import { useState, useEffect, useMemo } from "react";
import { psychTestsApi, type PsychTestScaleInfoDto } from "@/lib/api";
import { toast } from "sonner";
import { 
  CheckCircle2, X, AlertCircle, Loader2, Brain, 
  ShieldCheck, ArrowRight
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

interface StudentPsychTestModalProps {
  assignmentId: string;
  testCode?: string;
  testTitle?: string;
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

/**
 * StudentPsychTestModal: Öğrencinin kendisine atanan psikolojik ölçeği (STAI)
 * doldurmasını ve teslim etmesini sağlayan modal bileşeni (T-052C).
 * 
 * Güvenlik ve Kurallar:
 * - APP-03 §4 / §5: Puan ve kaygı etiketi öğrenciye ASLA gösterilmez; yalnızca tamamlandı bilgisi sunulur.
 * - APP-01 §2.1: Seçenek metinleri istemcide uydurulmaz, sunucudan geldiği gibi kullanılır.
 * - IST-02 §1.1 / §3.1: 375×812 ekranda 0 px taşma ve minimum 44×44 px dokunma alanı standartları.
 * - IST-02 §4.1 / §4.2: Eksik maddeler numaralarıyla bildirilir, ilerleme canlı takip edilir.
 */
export function StudentPsychTestModal({
  assignmentId,
  testCode = "STAI",
  testTitle = "STAI (Durumluk–Sürekli Kaygı Envanteri)",
  isOpen,
  onClose,
  onSuccess,
}: StudentPsychTestModalProps) {
  const [scaleInfo, setScaleInfo] = useState<PsychTestScaleInfoDto | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [answers, setAnswers] = useState<Map<number, number>>(new Map());
  const [missingItemNos, setMissingItemNos] = useState<number[]>([]);
  const [isCompleted, setIsCompleted] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);

  // Ölçek maddelerini sunucudan çek
  useEffect(() => {
    if (!isOpen) return;

    let isMounted = true;
    setIsLoading(true);
    setServerError(null);
    setIsCompleted(false);
    setAnswers(new Map());
    setMissingItemNos([]);

    psychTestsApi
      .getScaleItems(testCode)
      .then((res) => {
        if (isMounted && res.data?.success && res.data?.data) {
          setScaleInfo(res.data.data);
        }
      })
      .catch((err) => {
        if (isMounted) {
          const msg = err.response?.data?.message || "Ölçek soruları yüklenirken bir hata oluştu.";
          setServerError(msg);
          toast.error(msg);
        }
      })
      .finally(() => {
        if (isMounted) setIsLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, [isOpen, testCode]);

  // Cevaplanan soru sayısı ve ilerleme yüzdesi
  const answeredCount = answers.size;
  const totalCount = scaleInfo?.items?.length || 40;
  const progressPct = Math.round((answeredCount / totalCount) * 100);

  // Maddeleri bölümlere ayır (Durumluk 1-20, Sürekli 21-40)
  const stateItems = useMemo(
    () => scaleInfo?.items?.filter((i) => i.section === "STATE" || i.itemNo <= 20) || [],
    [scaleInfo]
  );
  const traitItems = useMemo(
    () => scaleInfo?.items?.filter((i) => i.section === "TRAIT" || i.itemNo > 20) || [],
    [scaleInfo]
  );

  // Seçenek işaretleme
  const handleSelectOption = (itemNo: number, value: number) => {
    const next = new Map(answers);
    next.set(itemNo, value);
    setAnswers(next);

    // Eğer bu madde eksikler arasındaysa listeden çıkar
    if (missingItemNos.includes(itemNo)) {
      setMissingItemNos((prev) => prev.filter((n) => n !== itemNo));
    }
  };

  // Testi tamamlama ve teslim etme
  const handleSubmit = async () => {
    if (isSubmitting || !scaleInfo) return;

    // Eksik maddeleri tespit et
    const missing: number[] = [];
    for (let i = 1; i <= totalCount; i++) {
      if (!answers.has(i)) {
        missing.push(i);
      }
    }

    if (missing.length > 0) {
      setMissingItemNos(missing);
      toast.warning(`Lütfen tüm maddeleri cevaplayınız. ${missing.length} eksik madde var.`);
      return;
    }

    setIsSubmitting(true);
    setServerError(null);

    const payload = {
      answers: Array.from(answers.entries()).map(([itemNo, answer]) => ({
        itemNo,
        answer,
      })),
    };

    try {
      const res = await psychTestsApi.submitTest(assignmentId, payload);
      if (res.data?.success) {
        setIsCompleted(true);
        toast.success("Test başarıyla teslim edildi!");
        onSuccess();
      }
    } catch (err: unknown) {
      const errorObj = err as { response?: { data?: { message?: string } } };
      const msg = errorObj.response?.data?.message || "Test teslim edilirken bir hata oluştu.";
      setServerError(msg);
      toast.error(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div 
      data-testid="student-psych-test-modal"
      className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-2 sm:p-4 overflow-y-auto"
      role="dialog"
      aria-modal="true"
      aria-labelledby="psych-test-modal-title"
    >
      <div className="bg-white rounded-[2rem] sm:rounded-[2.5rem] max-w-3xl w-full max-h-[92vh] flex flex-col shadow-2xl border border-slate-100 overflow-hidden animate-in zoom-in-95 duration-200">
        {/* ÜST ÇUBUK VE İLERLEME GÖSTERGESİ (STICKY) */}
        <div className="p-5 sm:p-6 border-b border-slate-100 bg-white/95 backdrop-blur-xs sticky top-0 z-20 space-y-3">
          <div className="flex items-center justify-between gap-4">
            <div className="flex items-center gap-2.5 min-w-0">
              <div className="w-10 h-10 rounded-2xl bg-cyan-50 text-cyan-600 flex items-center justify-center shrink-0 border border-cyan-100">
                <Brain className="h-5 w-5" />
              </div>
              <div className="min-w-0">
                <h3 
                  id="psych-test-modal-title"
                  className="font-black text-base sm:text-lg text-slate-900 truncate"
                  title={testTitle}
                >
                  {testTitle}
                </h3>
                <p className="text-[11px] font-bold text-slate-400">
                  {isCompleted ? "Test Tamamlandı" : "Lütfen tüm maddeleri dikkatlice cevaplayınız"}
                </p>
              </div>
            </div>

            <button
              type="button"
              data-testid="close-modal-btn"
              onClick={onClose}
              className="tap-44 min-h-[44px] min-w-[44px] p-2.5 rounded-2xl bg-slate-100 hover:bg-slate-200 text-slate-600 transition-all flex items-center justify-center shrink-0"
              aria-label="Kapat"
            >
              <X className="h-5 w-5" />
            </button>
          </div>

          {/* İlerleme Çubuğu (Test devam ederken) */}
          {!isCompleted && !isLoading && (
            <div className="space-y-1.5 pt-1">
              <div className="flex items-center justify-between text-xs font-black">
                <span data-testid="test-progress-text" className="text-cyan-700">
                  {answeredCount} / {totalCount} Tamamlandı
                </span>
                <span className="text-slate-400 font-bold">%{progressPct}</span>
              </div>
              <div 
                data-testid="test-progress-bar"
                className="w-full h-2.5 rounded-full bg-slate-100 overflow-hidden"
              >
                <div 
                  className="h-full bg-gradient-to-r from-cyan-500 to-teal-500 rounded-full transition-all duration-300"
                  style={{ width: `${progressPct}%` }}
                />
              </div>
            </div>
          )}
        </div>

        {/* İÇERİK ALANI (SCROLLABLE) */}
        <div className="flex-1 overflow-y-auto p-5 sm:p-8 space-y-8">
          {isLoading ? (
            <div className="py-24 flex flex-col items-center justify-center gap-4 text-center">
              <Loader2 className="h-10 w-10 text-cyan-600 animate-spin" />
              <p className="text-xs font-black uppercase tracking-widest text-slate-400">
                Ölçek Maddeleri Yükleniyor...
              </p>
            </div>
          ) : isCompleted ? (
            /* BAŞARI VE TAMAMLANMA EKRANI (PUAN KESİNLİKLE GÖSTERİLMEZ - APP-03 §4, §5) */
            <div 
              data-testid="test-success-card"
              className="py-12 px-4 text-center max-w-md mx-auto space-y-6 animate-in fade-in duration-300"
            >
              <div className="w-20 h-20 rounded-full bg-emerald-50 text-emerald-600 flex items-center justify-center mx-auto border-2 border-emerald-200 shadow-lg shadow-emerald-100">
                <CheckCircle2 className="h-10 w-10" />
              </div>

              <div className="space-y-2">
                <h4 className="text-2xl font-black text-slate-900 tracking-tight">
                  Test Başarıyla Tamamlandı!
                </h4>
                <p className="text-xs sm:text-sm text-slate-600 leading-relaxed font-medium">
                  Cevaplarınız başarıyla sisteme iletildi. Katılımınız ve samimi yanıtlarınız için teşekkür ederiz.
                </p>
              </div>

              {/* GİZLİLİK VE REHBERLİK BİLGİLENDİRMESİ */}
              <div className="p-4 rounded-2xl bg-slate-50 border border-slate-200/80 text-left flex items-start gap-3">
                <ShieldCheck className="h-5 w-5 text-teal-600 shrink-0 mt-0.5" />
                <p className="text-xs text-slate-500 leading-relaxed">
                  <strong className="text-slate-700">Veri Koruma ve Gizlilik Politikası:</strong> Özel nitelikli kişisel veri koruma kuralları uyarınca psikolojik ölçek puanları öğrencilere gösterilmemekte olup, sonuçlar eğitmeniniz ve kurum rehberliği tarafından akademik gelişiminizi desteklemek amacıyla değerlendirilecektir.
                </p>
              </div>

              <Button
                type="button"
                data-testid="back-to-dashboard-btn"
                onClick={() => {
                  onSuccess();
                  onClose();
                }}
                className="w-full tap-44 min-h-[44px] rounded-2xl bg-slate-900 hover:bg-slate-800 text-white font-black text-xs uppercase tracking-widest shadow-lg transition-all"
              >
                <span>Panele Dön</span>
              </Button>
            </div>
          ) : (
            /* TEST FORMU (MADDELER VE SEÇENEKLER) */
            <div className="space-y-8">
              {/* SUNUCU HATA MESAJI */}
              {serverError && (
                <div className="p-4 rounded-2xl bg-rose-50 border border-rose-200 text-rose-800 text-xs flex items-center gap-3">
                  <AlertCircle className="h-4 w-4 text-rose-600 shrink-0" />
                  <span>{serverError}</span>
                </div>
              )}

              {/* 1. BÖLÜM: DURUMLUK KAYGI (1 - 20) */}
              {stateItems.length > 0 && (
                <div className="space-y-4">
                  <div className="p-4 sm:p-5 rounded-2xl bg-cyan-50/70 border border-cyan-200/70 space-y-1.5">
                    <div className="flex items-center gap-2">
                      <span className="px-2.5 py-0.5 rounded-md bg-cyan-600 text-white text-[10px] font-black uppercase tracking-wider">
                        1. Kısım
                      </span>
                      <h4 className="font-black text-sm sm:text-base text-cyan-950">
                        Durumluk Kaygı Ölçeği (Madde 1 – 20)
                      </h4>
                    </div>
                    <p className="text-xs text-cyan-800 font-medium leading-relaxed">
                      <strong>Yönerge:</strong> Aşağıda kişilerin kendilerine ait duygularını anlatmada kullandıkları birtakım ifadeler verilmiştir. Her ifadeyi okuyun, sonra da <u>o anda, şu anda kendinizi nasıl hissettiğinizi</u> en iyi belirten seçeneği işaretleyiniz.
                    </p>
                  </div>

                  <div className="space-y-3">
                    {stateItems.map((item) => {
                      const isAnswered = answers.has(item.itemNo);
                      const selectedVal = answers.get(item.itemNo);
                      const isMissing = missingItemNos.includes(item.itemNo);

                      return (
                        <Card 
                          key={item.itemNo}
                          data-testid={`test-item-${item.itemNo}`}
                          className={`p-4 sm:p-5 rounded-2xl border transition-all ${
                            isMissing 
                              ? "border-rose-300 bg-rose-50/30 ring-1 ring-rose-400" 
                              : isAnswered 
                              ? "border-cyan-200/80 bg-cyan-50/20" 
                              : "border-slate-100 bg-white"
                          }`}
                        >
                          <div className="space-y-3">
                            <div className="flex items-start gap-2.5">
                              <span className={`px-2 py-0.5 rounded-lg text-xs font-black shrink-0 ${
                                isAnswered 
                                  ? "bg-cyan-600 text-white" 
                                  : "bg-slate-100 text-slate-600"
                              }`}>
                                {item.itemNo}
                              </span>
                              <p className="font-bold text-sm text-slate-800 leading-snug">
                                {item.text}
                              </p>
                            </div>

                            {/* Seçenek Butonları (4 basamaklı Likert) */}
                            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 pt-1">
                              {item.options.map((opt) => {
                                const isSelected = selectedVal === opt.value;
                                return (
                                  <button
                                    key={opt.value}
                                    type="button"
                                    data-testid={`option-${item.itemNo}-${opt.value}`}
                                    onClick={() => handleSelectOption(item.itemNo, opt.value)}
                                    className={`tap-44 min-h-[44px] px-3 py-2 rounded-xl text-xs font-bold transition-all flex items-center justify-center text-center border focus-visible:ring-2 focus-visible:ring-cyan-500 focus-visible:outline-none ${
                                      isSelected
                                        ? "bg-cyan-600 border-cyan-600 text-white shadow-md shadow-cyan-200 font-black"
                                        : "bg-white border-slate-200 hover:border-cyan-300 hover:bg-cyan-50/40 text-slate-700"
                                    }`}
                                  >
                                    {opt.label}
                                  </button>
                                );
                              })}
                            </div>
                          </div>
                        </Card>
                      );
                    })}
                  </div>
                </div>
              )}

              {/* 2. BÖLÜM: SÜREKLİ KAYGI (21 - 40) */}
              {traitItems.length > 0 && (
                <div className="space-y-4 pt-4 border-t border-slate-100">
                  <div className="p-4 sm:p-5 rounded-2xl bg-teal-50/70 border border-teal-200/70 space-y-1.5">
                    <div className="flex items-center gap-2">
                      <span className="px-2.5 py-0.5 rounded-md bg-teal-600 text-white text-[10px] font-black uppercase tracking-wider">
                        2. Kısım
                      </span>
                      <h4 className="font-black text-sm sm:text-base text-teal-950">
                        Sürekli Kaygı Ölçeği (Madde 21 – 40)
                      </h4>
                    </div>
                    <p className="text-xs text-teal-800 font-medium leading-relaxed">
                      <strong>Yönerge:</strong> Aşağıda kişilerin kendilerine ait duygularını anlatmada kullandıkları birtakım ifadeler verilmiştir. Her ifadeyi okuyun, sonra da <u>genellikle, çoğu zaman kendinizi nasıl hissettiğinizi</u> en iyi belirten seçeneği işaretleyiniz.
                    </p>
                  </div>

                  <div className="space-y-3">
                    {traitItems.map((item) => {
                      const isAnswered = answers.has(item.itemNo);
                      const selectedVal = answers.get(item.itemNo);
                      const isMissing = missingItemNos.includes(item.itemNo);

                      return (
                        <Card 
                          key={item.itemNo}
                          data-testid={`test-item-${item.itemNo}`}
                          className={`p-4 sm:p-5 rounded-2xl border transition-all ${
                            isMissing 
                              ? "border-rose-300 bg-rose-50/30 ring-1 ring-rose-400" 
                              : isAnswered 
                              ? "border-teal-200/80 bg-teal-50/20" 
                              : "border-slate-100 bg-white"
                          }`}
                        >
                          <div className="space-y-3">
                            <div className="flex items-start gap-2.5">
                              <span className={`px-2 py-0.5 rounded-lg text-xs font-black shrink-0 ${
                                isAnswered 
                                  ? "bg-teal-600 text-white" 
                                  : "bg-slate-100 text-slate-600"
                              }`}>
                                {item.itemNo}
                              </span>
                              <p className="font-bold text-sm text-slate-800 leading-snug">
                                {item.text}
                              </p>
                            </div>

                            {/* Seçenek Butonları (4 basamaklı Likert) */}
                            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 pt-1">
                              {item.options.map((opt) => {
                                const isSelected = selectedVal === opt.value;
                                return (
                                  <button
                                    key={opt.value}
                                    type="button"
                                    data-testid={`option-${item.itemNo}-${opt.value}`}
                                    onClick={() => handleSelectOption(item.itemNo, opt.value)}
                                    className={`tap-44 min-h-[44px] px-3 py-2 rounded-xl text-xs font-bold transition-all flex items-center justify-center text-center border focus-visible:ring-2 focus-visible:ring-teal-500 focus-visible:outline-none ${
                                      isSelected
                                        ? "bg-teal-600 border-teal-600 text-white shadow-md shadow-teal-200 font-black"
                                        : "bg-white border-slate-200 hover:border-teal-300 hover:bg-teal-50/40 text-slate-700"
                                    }`}
                                  >
                                    {opt.label}
                                  </button>
                                );
                              })}
                            </div>
                          </div>
                        </Card>
                      );
                    })}
                  </div>
                </div>
              )}
            </div>
          )}
        </div>

        {/* ALT ÇUBUK VE GÖNDERİM BUTONU (STICKY FOOTER) */}
        {!isCompleted && !isLoading && (
          <div className="p-4 sm:p-6 border-t border-slate-100 bg-white/95 backdrop-blur-xs sticky bottom-0 z-20 space-y-3">
            {/* EKSİK MADDE UYARISI (IST-02 §4.1) */}
            {missingItemNos.length > 0 && (
              <div 
                data-testid="missing-items-warning"
                className="p-3.5 rounded-xl bg-rose-50 border border-rose-200 text-rose-800 text-xs flex items-start gap-2.5 animate-in fade-in duration-200"
              >
                <AlertCircle className="h-4 w-4 text-rose-600 shrink-0 mt-0.5" />
                <div>
                  <p className="font-bold">Lütfen tüm maddeleri işaretleyiniz.</p>
                  <p className="text-[11px] text-rose-700 mt-0.5">
                    Eksik maddeler: {missingItemNos.slice(0, 10).map((n) => `Madde ${n}`).join(", ")}
                    {missingItemNos.length > 10 ? ` ve ${missingItemNos.length - 10} madde daha` : ""}
                  </p>
                </div>
              </div>
            )}

            <div className="flex flex-col sm:flex-row items-center justify-between gap-4">
              <p className="text-xs text-slate-500 font-medium text-center sm:text-left">
                Formu tamamladıktan sonra sonuçlarınız güvenle kaydedilecektir.
              </p>

              <Button
                type="button"
                data-testid="submit-test-btn"
                onClick={handleSubmit}
                disabled={isSubmitting}
                className="w-full sm:w-auto tap-44 min-h-[44px] px-8 rounded-2xl bg-cyan-600 hover:bg-cyan-700 text-white font-black text-xs uppercase tracking-widest shadow-lg shadow-cyan-200 transition-all flex items-center justify-center gap-2"
              >
                {isSubmitting ? (
                  <>
                    <Loader2 className="h-4 w-4 animate-spin" />
                    <span>Teslim Ediliyor...</span>
                  </>
                ) : (
                  <>
                    <span>Testi Tamamla ve Gönder</span>
                    <ArrowRight className="h-4 w-4" />
                  </>
                )}
              </Button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
