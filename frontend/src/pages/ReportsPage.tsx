import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "@/lib/auth-context";
import { analysisApi, membershipApi } from "@/lib/api";
import type { User, AnalysisData } from "@/lib/types";
import { Card, CardContent } from "@/components/ui/card";
import { FileBarChart2, ChevronRight, Users, Layers, Info } from "lucide-react";

/**
 * ReportsPage: Kullanıcı rolüne göre analiz raporlarını listeleyen merkezi sayfa (T-047).
 *
 * <p>Erişim Prensipleri (ENG-11 §3.1 / APP-01 §2.2):
 * - Öğrenci: Yalnızca kendi raporlarını görür.
 * - Öğretmen: Yalnızca eşleştiği öğrencileri ve onların raporlarını görür.
 * - Yönetici: Tüm öğrencileri ve raporlarını görür.
 * Rapor kartına tıklandığında detay sayfasına yönlendirilir.</p>
 */
export default function ReportsPage() {
  const { user } = useAuth();
  const navigate = useNavigate();

  // Öğretmen / Yönetici için öğrenci listesi ve seçili öğrenci
  const [students, setStudents] = useState<User[]>([]);
  const [selectedStudentId, setSelectedStudentId] = useState<string | null>(null);

  // Rapor listesi ve yüklenme durumu
  const [reports, setReports] = useState<AnalysisData[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isReportsLoading, setIsReportsLoading] = useState(false);

  useEffect(() => {
    if (!user) return;

    if (user.role === "STUDENT") {
      // Öğrenci doğrudan kendi raporlarını çeker
      analysisApi
        .getStudentReports(user.id)
        .then((res) => {
          setReports((res.data.data as unknown as AnalysisData[]) || []);
        })
        .catch(() => setReports([]))
        .finally(() => setIsLoading(false));
    } else {
      // Öğretmen veya Yönetici öğrenci listesini çeker
      const fetchStudents = user.role === "TEACHER"
        ? membershipApi.getMyStudents()
        : membershipApi.getStudents();

      fetchStudents
        .then((res) => {
          const list = res.data.data || [];
          setStudents(list);
          if (list.length > 0) {
            setSelectedStudentId(list[0].id);
          }
        })
        .catch(() => setStudents([]))
        .finally(() => setIsLoading(false));
    }
  }, [user]);

  // Öğrenci seçimi değiştiğinde raporları getir (Öğretmen / Yönetici)
  useEffect(() => {
    if (!selectedStudentId || user?.role === "STUDENT") return;

    setIsReportsLoading(true);
    analysisApi
      .getStudentReports(selectedStudentId)
      .then((res) => {
        setReports((res.data.data as unknown as AnalysisData[]) || []);
      })
      .catch(() => setReports([]))
      .finally(() => setIsReportsLoading(false));
  }, [selectedStudentId, user?.role]);

  if (isLoading) {
    return (
      <div className="w-full flex flex-col items-center justify-center gap-6 py-32">
        <div className="w-10 h-10 border-[5px] border-cyan-500 border-t-transparent rounded-full animate-spin"></div>
        <p className="text-[10px] font-black uppercase tracking-[0.4em] text-slate-400">Raporlar Yükleniyor</p>
      </div>
    );
  }

  // ÖĞRENCİ GÖRÜNÜMÜ
  if (user?.role === "STUDENT") {
    return (
      <div className="w-full space-y-8 animate-in fade-in duration-500 pb-20">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div className="flex flex-col gap-2">
            <h2 className="text-2xl sm:text-4xl font-black text-slate-900 tracking-tighter flex items-center gap-3">
              <FileBarChart2 className="h-7 w-7 sm:h-8 sm:w-8 text-cyan-500" />
              Analiz <span className="text-cyan-500 italic">Raporlarım</span>
            </h2>
            <p className="text-[10px] font-black text-slate-400 uppercase tracking-[0.4em]">
              Tamamlanmış ve Onaylanmış Deneme Karneleri
            </p>
          </div>
          {reports.length > 0 && (
            <button
              onClick={() => navigate(`/analysis/cumulative/${user.id}`)}
              className="tap-44 self-start md:self-auto bg-cyan-600 text-white px-6 py-3 rounded-2xl font-black text-[10px] uppercase tracking-widest hover:bg-cyan-700 transition-all shadow-xl shadow-cyan-100 flex items-center gap-2"
            >
              <Layers className="h-4 w-4" /> Gelişim Dosyası
            </button>
          )}
        </div>

        {reports.length === 0 ? (
          <Card className="rounded-[3rem] border-2 border-dashed border-slate-200 bg-slate-50/50 p-12 sm:p-24 text-center">
            <Info className="h-10 w-10 text-slate-300 mx-auto mb-4" />
            <p className="text-slate-400 font-black text-[10px] uppercase tracking-[0.4em]">
              Henüz bir analiz raporun bulunmuyor.
            </p>
          </Card>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {reports.map((report) => (
              <Card
                key={report.id}
                onClick={() => navigate(`/analysis/${report.id}`)}
                className="rounded-[2.5rem] border-0 shadow-lg hover:shadow-xl transition-all bg-white group cursor-pointer overflow-hidden"
              >
                <CardContent className="p-5 sm:p-8 flex items-center justify-between">
                  <div className="flex items-center gap-4 sm:gap-6 min-w-0">
                    <div className="w-12 h-12 sm:w-14 sm:h-14 bg-cyan-50 text-cyan-600 rounded-2xl flex items-center justify-center shrink-0">
                      <FileBarChart2 className="h-5 w-5 sm:h-6 sm:w-6" />
                    </div>
                    <div className="min-w-0">
                      <h4 className="text-base sm:text-lg font-black text-slate-900 tracking-tight truncate">{report.examTitle}</h4>
                      <p className="text-[9px] font-black text-slate-400 uppercase tracking-widest mt-1 truncate">
                        {new Date(report.processedAt).toLocaleDateString("tr-TR")} • {report.intendedExamCount} Deneme Verisi
                      </p>
                    </div>
                  </div>
                  <ChevronRight className="h-6 w-6 text-slate-300 group-hover:text-cyan-500 transition-colors shrink-0" />
                </CardContent>
              </Card>
            ))}
          </div>
        )}
      </div>
    );
  }

  // ÖĞRETMEN VE YÖNETİCİ GÖRÜNÜMÜ
  const selectedStudent = students.find((s) => s.id === selectedStudentId);

  return (
    <div className="w-full space-y-8 animate-in fade-in duration-500 pb-20">
      <div className="flex flex-col gap-2">
        <h2 className="text-2xl sm:text-4xl font-black text-slate-900 tracking-tighter flex items-center gap-3">
          <FileBarChart2 className="h-7 w-7 sm:h-8 sm:w-8 text-blue-500" />
          Analiz <span className="text-blue-500 italic">Raporları</span>
        </h2>
        <p className="text-[10px] font-black text-slate-400 uppercase tracking-[0.4em]">
          Öğrenci Bazlı Karne ve Gelişim Raporları
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* SOL: Öğrenci Seçim Listesi */}
        <div className="lg:col-span-1 space-y-4">
          <h3 className="text-lg font-black text-slate-900 tracking-tight flex items-center gap-2">
            <Users className="h-5 w-5 text-blue-500" />
            {user?.role === "TEACHER" ? "Öğrencilerim" : "Tüm Öğrenciler"}
          </h3>

          {students.length === 0 ? (
            <Card className="rounded-[2.5rem] border-2 border-dashed border-slate-200 bg-slate-50/50 p-12 text-center">
              <p className="text-slate-400 font-black text-[10px] uppercase tracking-widest">
                Henüz kayıtlı öğrenci bulunmuyor.
              </p>
            </Card>
          ) : (
            <div className="space-y-3 max-h-[600px] overflow-y-auto pr-2">
              {students.map((student) => (
                <div
                  key={student.id}
                  onClick={() => setSelectedStudentId(student.id)}
                  className={`p-5 rounded-[2rem] border-2 cursor-pointer transition-all tap-44 flex items-center justify-between ${
                    selectedStudentId === student.id
                      ? "border-blue-500 bg-blue-50/40 shadow-md scale-[1.02]"
                      : "border-slate-100 bg-white hover:border-slate-200"
                  }`}
                >
                  <div className="min-w-0">
                    <h5 className="font-black text-sm text-slate-900 tracking-tight truncate">{student.fullName}</h5>
                    <p className="text-[9px] font-bold text-slate-400 uppercase tracking-widest mt-0.5 truncate">
                      {student.email}
                    </p>
                  </div>
                  {selectedStudentId === student.id && (
                    <div className="w-2 h-2 rounded-full bg-blue-500 shrink-0 ml-2"></div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>

        {/* SAĞ: Seçilen Öğrencinin Raporları */}
        <div className="lg:col-span-2 space-y-6">
          {!selectedStudentId ? (
            <Card className="rounded-[3rem] border-2 border-dashed border-slate-200 bg-slate-50/50 p-12 sm:p-24 text-center">
              <Info className="h-10 w-10 text-slate-300 mx-auto mb-4" />
              <p className="text-slate-400 font-black text-[10px] uppercase tracking-[0.4em]">
                Soldan bir öğrenci seçerek analiz raporlarını görüntüleyin.
              </p>
            </Card>
          ) : (
            <div className="space-y-6">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-5 sm:p-6 rounded-[2.5rem] border border-slate-100 shadow-sm">
                <div>
                  <h4 className="text-lg font-black text-slate-900 tracking-tight">
                    {selectedStudent?.fullName}
                  </h4>
                  <p className="text-[10px] font-bold text-slate-400 uppercase tracking-widest">
                    Toplam {reports.length} Analiz Raporu
                  </p>
                </div>
                {reports.length > 0 && (
                  <button
                    onClick={() => navigate(`/analysis/cumulative/${selectedStudentId}`)}
                    className="tap-44 self-start sm:self-auto bg-indigo-600 text-white px-6 py-3 rounded-2xl font-black text-[10px] uppercase tracking-widest hover:bg-indigo-700 transition-all shadow-lg shadow-indigo-100 flex items-center gap-2"
                  >
                    <Layers className="h-4 w-4" /> Gelişim Dosyası
                  </button>
                )}
              </div>

              {isReportsLoading ? (
                <div className="py-20 flex flex-col items-center justify-center gap-4">
                  <div className="w-8 h-8 border-4 border-blue-500 border-t-transparent rounded-full animate-spin"></div>
                  <p className="text-[10px] font-black uppercase tracking-widest text-slate-400">Raporlar yükleniyor…</p>
                </div>
              ) : reports.length === 0 ? (
                <Card className="rounded-[3rem] border-2 border-dashed border-slate-200 bg-slate-50/50 p-12 sm:p-20 text-center">
                  <Info className="h-8 w-8 text-slate-300 mx-auto mb-3" />
                  <p className="text-slate-400 font-black text-[10px] uppercase tracking-widest">
                    Bu öğrenciye ait onaylanmış veya bekleyen analiz raporu bulunmuyor.
                  </p>
                </Card>
              ) : (
                <div className="space-y-4">
                  {reports.map((report) => (
                    <Card
                      key={report.id}
                      onClick={() => navigate(`/analysis/${report.id}`)}
                      className={`rounded-[2.5rem] border-0 shadow-lg hover:shadow-xl transition-all bg-white group cursor-pointer overflow-hidden ${
                        report.status === "PROCESSING" ? "opacity-60 pointer-events-none" : ""
                      }`}
                    >
                      <CardContent className="p-5 sm:p-7 flex items-center justify-between">
                        <div className="flex items-center gap-4 sm:gap-6 min-w-0">
                          <div className={`w-12 h-12 sm:w-14 sm:h-14 rounded-2xl flex items-center justify-center shrink-0 ${
                            report.status === "PROCESSING" ? "bg-blue-50 text-blue-500 animate-spin" : "bg-slate-50 text-slate-900"
                          }`}>
                            <FileBarChart2 className="h-5 w-5 sm:h-6 sm:w-6" />
                          </div>
                          <div className="min-w-0">
                            <div className="flex items-center gap-2 sm:gap-3 flex-wrap">
                              <h4 className="text-base sm:text-lg font-black text-slate-900 tracking-tight truncate">
                                {report.examTitle}
                              </h4>
                              <span
                                className={`text-[8px] font-black uppercase tracking-widest px-2 py-1 rounded-md ${
                                  report.status === "APPROVED"
                                    ? "bg-emerald-50 text-emerald-600"
                                    : report.status === "PROCESSING"
                                    ? "bg-blue-50 text-blue-600"
                                    : report.status === "FAILED"
                                    ? "bg-red-50 text-red-600"
                                    : "bg-amber-50 text-amber-600"
                                }`}
                              >
                                {report.status === "APPROVED"
                                  ? "Yayımlandı"
                                  : report.status === "PROCESSING"
                                  ? "Hazırlanıyor"
                                  : report.status === "FAILED"
                                  ? "Başarısız"
                                  : "Onay Bekliyor"}
                              </span>
                            </div>
                            <p className="text-[9px] font-black text-slate-400 uppercase tracking-widest mt-1">
                              {new Date(report.processedAt).toLocaleDateString("tr-TR")} •{" "}
                              {report.intendedExamCount > 0
                                ? `${report.intendedExamCount} Deneme Verisi`
                                : "Tür Tespit Ediliyor..."}
                            </p>
                          </div>
                        </div>
                        <ChevronRight className="h-6 w-6 text-slate-300 group-hover:text-blue-500 transition-colors" />
                      </CardContent>
                    </Card>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
