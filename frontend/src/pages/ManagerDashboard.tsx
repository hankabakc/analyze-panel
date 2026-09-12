import { useState, useEffect } from "react";
import { membershipApi, psychTestsApi } from "@/lib/api";
import type { Activity, Pairing, SchoolClass, User } from "@/lib/types";
import { toast } from "sonner";
import { 
  ShieldAlert, LayoutDashboard, Link2, 
  ChevronRight, UserPlus, GraduationCap, 
  Briefcase, ArrowRight, Loader2, KeyRound, Copy, Check, Info, UserMinus, Eye, Clock, Brain, Globe
} from "lucide-react";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import PsychTestAssignmentTab from "@/features/psychtest/components/PsychTestAssignmentTab";
import { SiteContentEditor } from "@/features/site/admin/SiteContentEditor";

interface CreatedUserInfo {
  fullName: string;
  email: string;
  password: string;
  role: string;
}

/**
 * ManagerDashboard: SaaS tarzı, sol navigasyonlu yönetim paneli.
 * T-004 ile "Kullanıcı Ekle" sekmesi ve T-010 ile tek seferlik şifre entegre edilmiştir.
 * T-052B ile "Psikolojik Testler" sekmesi entegre edilmiştir.
 * T-056 ile telefon alanı kaldırılmıştır.
 * T-062 ile psikolojik testler yeni sonuç bildirim rozeti eklenmiştir.
 */
export default function ManagerDashboard() {
  // activeTab: Sol menüdeki aktif sekmeyi takip eder.
  const [activeTab, setActiveTab] = useState<"OVERVIEW" | "PAIRING" | "CREATE_USER" | "ACTIVITY" | "CLASSES" | "PSYCH_TESTS" | "SITE_CONTENT">("OVERVIEW");
  
  // T-062: Yönetici için sistem genelindeki görülmemiş psikolojik test sayısı
  const [unseenPsychCount, setUnseenPsychCount] = useState<number>(0);

  // T-075: Sistemdeki aktif yönetici sayısı (tek yönetici kalırsa uyarı gösterilir)
  const [managerCount, setManagerCount] = useState<number>(0);

  // T-063C: Site içeriği editörü ilk açıldıktan sonra bağlı kalır; sekme değişince taslak kaybolmaz (IST-02 §4.3).
  const [siteEditorOpened, setSiteEditorOpened] = useState(false);
  useEffect(() => {
    if (activeTab === "SITE_CONTENT") setSiteEditorOpened(true);
  }, [activeTab]);

  // State Yönetimi
  const [activeTeachers, setActiveTeachers] = useState<User[]>([]);
  const [activeStudents, setActiveStudents] = useState<User[]>([]);
  const [unpairedStudents, setUnpairedStudents] = useState<User[]>([]);
  const [pairings, setPairings] = useState<Pairing[]>([]);
  const [activity, setActivity] = useState<Activity[]>([]);
  const [classes, setClasses] = useState<SchoolClass[]>([]);
  const [newClassName, setNewClassName] = useState("");
  const [classBusy, setClassBusy] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [unpairingStudentId, setUnpairingStudentId] = useState<string | null>(null);

  // Eşleştirme Seçimleri
  const [selectedTeacher, setSelectedTeacher] = useState<string | null>(null);
  const [selectedStudent, setSelectedStudent] = useState<string | null>(null);

  // Yeni Kullanıcı Ekleme Form State'i (T-004 / T-010 / T-016 / T-056)
  const [createFullName, setCreateFullName] = useState("");
  const [createRole, setCreateRole] = useState<"STUDENT" | "TEACHER">("STUDENT");
  const [createGrade, setCreateGrade] = useState<number | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Tek Seferlik Şifre Gösterim State'i (T-010 / K3)
  const [createdUserInfo, setCreatedUserInfo] = useState<CreatedUserInfo | null>(null);
  const [copiedEmail, setCopiedEmail] = useState(false);
  const [copiedPassword, setCopiedPassword] = useState(false);

  /**
   * loadData: Tüm gerekli yönetim verilerini backend'den çeker.
   */
  const loadData = async () => {
    try {
      setIsLoading(true);
      const [teachersRes, studentsRes, unpairedStudentsRes, pairingsRes, activityRes, classesRes, psychUnseenRes, managerCountRes] = await Promise.all([
        membershipApi.getTeachers(),
        membershipApi.getStudents(),
        membershipApi.getUnpairedStudents(),
        membershipApi.getPairings(),
        membershipApi.getActivity(),
        membershipApi.getClasses(),
        psychTestsApi.getUnseenCounts(),
        membershipApi.getManagerCount(),
      ]);
      
      setActiveTeachers(teachersRes.data.data);
      setActiveStudents(studentsRes.data.data);
      setUnpairedStudents(unpairedStudentsRes.data.data);
      setPairings(pairingsRes.data.data);
      setActivity(activityRes.data.data);
      setClasses(classesRes.data.data);
      setUnseenPsychCount(psychUnseenRes.data?.data?.totalUnseen || 0);
      setManagerCount(managerCountRes.data?.data ?? 0);
    } catch {
      toast.error("Veriler yüklenirken bir hata oluştu.");
    } finally {
      setIsLoading(false);
    }
  };

  /**
   * T-040: Sınıf işlemleri. Öğrenciyi sınıftan çıkarmak yalnızca sınıf bağını koparır;
   * karne ve analiz verisi bu alana bağlı değildir, dolayısıyla etkilenmez.
   */
  const handleCreateClass = async () => {
    const ad = newClassName.trim();
    if (!ad) return;
    setClassBusy(true);
    try {
      await membershipApi.createClass(ad);
      toast.success(`"${ad}" sınıfı oluşturuldu.`);
      setNewClassName("");
      await loadData();
    } catch (e) {
      toast.error(axiosMesaji(e, "Sınıf oluşturulamadı."));
    } finally {
      setClassBusy(false);
    }
  };

  const handleDeleteClass = async (c: SchoolClass) => {
    const uyari = c.students.length > 0
      ? `"${c.name}" sınıfını silmek istediğinize emin misiniz?

İçindeki ${c.students.length} öğrenci SİLİNMEZ, yalnızca sınıfsız kalır; karne ve analiz verileri korunur.`
      : `"${c.name}" sınıfını silmek istediğinize emin misiniz?`;
    if (!window.confirm(uyari)) return;
    setClassBusy(true);
    try {
      await membershipApi.deleteClass(c.id);
      toast.success("Sınıf silindi; öğrenciler sınıfsız kaldı.");
      await loadData();
    } catch (e) {
      toast.error(axiosMesaji(e, "Sınıf silinemedi."));
    } finally {
      setClassBusy(false);
    }
  };

  const handleAddStudentToClass = async (classId: string, studentId: string) => {
    setClassBusy(true);
    try {
      await membershipApi.addStudentToClass(classId, studentId);
      toast.success("Öğrenci sınıfa eklendi.");
      await loadData();
    } catch (e) {
      toast.error(axiosMesaji(e, "Öğrenci eklenemedi."));
    } finally {
      setClassBusy(false);
    }
  };

  const handleRemoveStudentFromClass = async (classId: string, s: { id: string; fullName: string }) => {
    if (!window.confirm(`"${s.fullName}" öğrencisini bu sınıftan çıkarmak istediğinize emin misiniz?

Öğrencinin karne ve analiz verileri korunur; yalnızca sınıf bağı kopar.`)) return;
    setClassBusy(true);
    try {
      await membershipApi.removeStudentFromClass(classId, s.id);
      toast.success("Öğrenci sınıftan çıkarıldı; verisi korundu.");
      await loadData();
    } catch (e) {
      toast.error(axiosMesaji(e, "Öğrenci çıkarılamadı."));
    } finally {
      setClassBusy(false);
    }
  };

  /** axiosMesaji: Sunucunun döndürdüğü mesajı gösterir; yoksa genel metne düşer. */
  const axiosMesaji = (e: unknown, varsayilan: string) => {
    const r = (e as { response?: { data?: { message?: string } } })?.response;
    return r?.data?.message || varsayilan;
  };

  useEffect(() => {
    loadData();
  }, []);

  /**
   * handlePairing: Seçilen öğretmen ve öğrenciyi eşleştirir (T-021).
   */
  const handlePairing = async () => {
    if (!selectedTeacher || !selectedStudent) {
      toast.warning("Lütfen hem bir öğretmen hem de bir öğrenci seçin.");
      return;
    }
    try {
      const response = await membershipApi.pairStudentTeacher(selectedStudent, selectedTeacher);
      if (response.data.success) {
        toast.success(response.data.message || "Eşleştirme başarıyla tamamlandı.");
        setSelectedTeacher(null);
        setSelectedStudent(null);
        loadData();
      }
    } catch (error) {
      const axiosError = error as { response?: { data?: { message?: string } } };
      const errorMsg = axiosError.response?.data?.message || "Eşleştirme sırasında bir hata oluştu.";
      toast.error(errorMsg);
    }
  };

  /**
   * handleUnpair: Bir öğrenci ve öğretmen arasındaki eşleştirmeyi sonlandırır (T-036).
   * Ayırma işlemi kullanıcıdan onay alınmadan gerçekleştirilmez.
   * Öğrencinin analiz ve karne verileri korunur, öğrenci eşleşmemişler listesine aktarılır.
   */
  const handleUnpair = async (studentId: string, teacherId: string, studentName: string, teacherName: string) => {
    const confirmed = window.confirm(
      `"${studentName}" ile "${teacherName}" arasındaki eşleştirmeyi sonlandırmak istediğinize emin misiniz?\n\nBu işlem sonucunda öğrencinin analiz ve karne verileri korunacak, öğrenci yeniden eşleştirilmek üzere boşa çıkacaktır.`
    );
    if (!confirmed) return;

    try {
      setUnpairingStudentId(studentId);
      const response = await membershipApi.unpairStudentTeacher(studentId, teacherId);
      if (response.data.success) {
        toast.success(response.data.message || "Eşleştirme başarıyla sonlandırıldı.");
        loadData();
      }
    } catch (error) {
      const axiosError = error as { response?: { data?: { message?: string } } };
      const errorMsg = axiosError.response?.data?.message || "Eşleştirme sonlandırılırken bir hata oluştu.";
      toast.error(errorMsg);
    } finally {
      setUnpairingStudentId(null);
    }
  };

  /**
   * copyToClipboard: Tek seferlik giriş bilgilerini panoya kopyalar (T-010 / K3).
   */
  const copyToClipboard = async (text: string, type: "email" | "password") => {
    try {
      await navigator.clipboard.writeText(text);
      if (type === "email") {
        setCopiedEmail(true);
        setTimeout(() => setCopiedEmail(false), 2000);
      } else {
        setCopiedPassword(true);
        setTimeout(() => setCopiedPassword(false), 2000);
      }
      toast.success("Panoya kopyalandı.");
    } catch {
      toast.error("Panoya kopyalanamadı.");
    }
  };

  /**
   * handleResetPassword: Yöneticinin kullanıcı için yeni geçici şifre üretmesini sağlar (T-013).
   * Kullanıcının tüm açık oturumlarını düşürür ve yeni şifreyi tek seferlik kartla gösterir.
   */
  const handleResetPassword = async (user: User) => {
    const confirmed = window.confirm(
      `"${user.fullName}" kullanıcısının şifresini sıfırlamak istediğinize emin misiniz?\n\nKullanıcının tüm açık oturumları sonlandırılacak ve yeni bir geçici şifre üretilecektir.`
    );
    if (!confirmed) return;

    try {
      const response = await membershipApi.resetUserPassword(user.id);
      if (response.data.success && response.data.data) {
        toast.success(response.data.message || "Yeni geçici şifre üretildi.");
        setCreatedUserInfo({
          fullName: response.data.data.user.fullName,
          email: response.data.data.user.email,
          password: response.data.data.generatedPassword,
          role: response.data.data.user.role,
        });
      }
    } catch {
      toast.error("Şifre sıfırlama işlemi başarısız oldu.");
    }
  };

  /**
   * handleCreateUser: Yöneticinin yeni bir öğrenci veya eğitmen hesabı açmasını sağlar (T-004 / T-010 / T-016 / T-056).
   */
  const handleCreateUser = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!createFullName.trim()) {
      toast.error("Lütfen ad ve soyad giriniz.");
      return;
    }
    if (createRole === "STUDENT" && createGrade === null) {
      toast.error("Öğrenci için sınıf seviyesi seçilmelidir.");
      return;
    }

    setIsSubmitting(true);
    try {
      const response = await membershipApi.createUser({
        fullName: createFullName.trim(),
        role: createRole,
        grade: createRole === "STUDENT" && createGrade !== null ? createGrade : undefined,
      });

      if (response.data.success) {
        toast.success(response.data.message || "Kullanıcı başarıyla oluşturuldu.");
        setCreatedUserInfo({
          fullName: response.data.data.user.fullName,
          email: response.data.data.user.email,
          password: response.data.data.generatedPassword,
          role: response.data.data.user.role === "STUDENT" ? "Öğrenci" : "Eğitmen",
        });
        setCreateFullName("");
        setCreateGrade(null);
        setCreateRole("STUDENT");
        loadData();
      }
    } catch (err: unknown) {
      const errorObj = err as { response?: { data?: { message?: string } } };
      toast.error(errorObj.response?.data?.message || "Kullanıcı oluşturulurken bir hata oluştu.");
    } finally {
      setIsSubmitting(false);
    }
  };

  /**
   * Sidebar Item Bileşeni: Sol menüdeki her bir butonu temsil eder.
   */
  interface SidebarItemProps {
    id: "OVERVIEW" | "PAIRING" | "CREATE_USER" | "ACTIVITY" | "CLASSES" | "PSYCH_TESTS" | "SITE_CONTENT";
    label: string;
    icon: React.ComponentType<{ className?: string }>;
    badge?: number;
  }

  const SidebarItem = ({ id, label, icon: Icon, badge }: SidebarItemProps) => (
    <button
      type="button"
      data-testid={`sidebar-tab-${id.toLowerCase()}`}
      onClick={() => setActiveTab(id)}
      className={`w-full flex items-center gap-4 px-6 py-4 rounded-2xl transition-all duration-300 group ${
        activeTab === id 
        ? "bg-slate-900 text-white shadow-lg shadow-slate-200" 
        : "text-slate-400 hover:bg-slate-50 hover:text-slate-900"
      }`}
    >
      <Icon className={`h-5 w-5 ${activeTab === id ? "text-cyan-400" : "group-hover:text-slate-900"}`} />
      <span className="text-[11px] font-black uppercase tracking-[0.2em]">{label}</span>
      {badge !== undefined && badge > 0 && (
        <span
          data-testid={`sidebar-badge-${id.toLowerCase()}`}
          className="px-2 py-0.5 rounded-full text-[10px] font-black bg-purple-600 text-white animate-pulse"
          aria-label={`${badge} yeni sonuç`}
        >
          {badge}
        </span>
      )}
      {activeTab === id && <ChevronRight className="ml-auto h-4 w-4 text-cyan-400" />}
    </button>
  );

  if (isLoading) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center gap-6 py-32">
        <div className="w-10 h-10 border-[5px] border-cyan-500 border-t-transparent rounded-full animate-spin"></div>
        <p className="text-[10px] font-black uppercase tracking-[0.4em] text-slate-400">Veriler Senkronize Ediliyor</p>
      </div>
    );
  }

  return (
    <div className="flex flex-col lg:flex-row min-h-[80vh] gap-8 lg:gap-12 animate-in fade-in duration-700">
      {/* TEK SEFERLİK ŞİFRE GÖSTERİM MODALI (T-010 / T-013 / K3, K4) */}
      {createdUserInfo && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm animate-in fade-in duration-200">
          <Card className="max-w-md w-full rounded-[3rem] border-2 border-emerald-500/30 bg-white shadow-2xl p-8 sm:p-10 space-y-6 animate-in zoom-in-95 duration-200">
            <div className="flex items-center gap-4">
              <div className="w-12 h-12 rounded-2xl bg-emerald-600 text-white flex items-center justify-center shadow-lg shadow-emerald-200 shrink-0">
                <KeyRound className="h-6 w-6" />
              </div>
              <div>
                <h3 className="text-lg font-black text-slate-900 tracking-tight">Geçici Giriş Bilgileri</h3>
                <span className="text-[10px] font-black uppercase tracking-widest text-emerald-700 bg-emerald-100 px-3 py-1 rounded-lg">
                  {createdUserInfo.role}
                </span>
              </div>
            </div>

            <div className="bg-amber-500/10 border border-amber-500/20 rounded-2xl p-4 flex items-start gap-3">
              <Info className="h-5 w-5 text-amber-600 shrink-0 mt-0.5" />
              <p className="text-xs font-bold text-amber-900 leading-relaxed">
                ⚠️ Bu şifre güvenlik protokolü gereği veritabanında saklanmaz ve <strong>bir daha gösterilmeyecektir</strong>. Lütfen bilgileri kaydedip kullanıcıya iletiniz.
              </p>
            </div>

            <div className="space-y-3">
              {/* Ad Soyad */}
              <div className="bg-slate-50 rounded-2xl p-4 border border-slate-100 flex items-center justify-between">
                <div>
                  <p className="text-[9px] font-black text-slate-400 uppercase tracking-widest">Ad Soyad</p>
                  <p className="text-sm font-black text-slate-900">{createdUserInfo.fullName}</p>
                </div>
              </div>

              {/* E-Posta */}
              <div className="bg-slate-50 rounded-2xl p-4 border border-slate-100 flex items-center justify-between">
                <div>
                  <p className="text-[9px] font-black text-slate-400 uppercase tracking-widest">Giriş E-Postası</p>
                  <p className="text-sm font-black text-slate-900 font-mono">{createdUserInfo.email}</p>
                </div>
                <button
                  type="button"
                  onClick={() => copyToClipboard(createdUserInfo.email, "email")}
                  className="p-2.5 rounded-xl bg-white hover:bg-slate-100 text-slate-600 border border-slate-200 transition-all active:scale-95"
                  title="E-postayı Kopyala"
                >
                  {copiedEmail ? <Check className="h-4 w-4 text-emerald-600" /> : <Copy className="h-4 w-4" />}
                </button>
              </div>

              {/* Şifre */}
              <div className="bg-emerald-50/50 rounded-2xl p-4 border border-emerald-100 flex items-center justify-between">
                <div>
                  <p className="text-[9px] font-black text-emerald-700 uppercase tracking-widest">İlk Giriş Şifresi</p>
                  <p className="text-sm font-black text-emerald-900 font-mono tracking-wider">{createdUserInfo.password}</p>
                </div>
                <button
                  type="button"
                  onClick={() => copyToClipboard(createdUserInfo.password, "password")}
                  className="p-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white shadow-md shadow-emerald-200 transition-all active:scale-95"
                  title="Şifreyi Kopyala"
                >
                  {copiedPassword ? <Check className="h-4 w-4 text-white" /> : <Copy className="h-4 w-4" />}
                </button>
              </div>
            </div>

            <Button
              type="button"
              onClick={() => setCreatedUserInfo(null)}
              className="w-full h-14 rounded-2xl font-black text-xs uppercase tracking-widest bg-slate-900 hover:bg-slate-800 text-white shadow-lg"
            >
              Bilgileri Kaydettim / Kapat
            </Button>
          </Card>
        </div>
      )}

      {/* SaaS SIDEBAR */}
      <aside className="w-full lg:w-80 shrink-0 space-y-6 lg:space-y-8">
        <div className="bg-white rounded-[2.5rem] sm:rounded-[3rem] p-4 shadow-xl shadow-slate-100/50 border border-slate-50">
          <div className="p-4 sm:p-6 mb-2 sm:mb-4">
             <p className="text-[9px] font-black text-slate-400 uppercase tracking-[0.4em] mb-2">Yönetim Paneli</p>
             <h3 className="text-xl font-black text-slate-900 tracking-tighter">Navigasyon</h3>
          </div>
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-1 gap-2">
            <SidebarItem id="OVERVIEW" label="Genel Bakış" icon={LayoutDashboard} />
            <SidebarItem id="PSYCH_TESTS" label="Psikolojik Testler" icon={Brain} badge={unseenPsychCount} />
            <SidebarItem id="CREATE_USER" label="Kullanıcı Ekle" icon={UserPlus} />
            <SidebarItem id="PAIRING" label="Eşleştirme Merkezi" icon={Link2} />
            <SidebarItem id="CLASSES" label="Sınıflar" icon={GraduationCap} />
            <SidebarItem id="ACTIVITY" label="Aktiflik" icon={Eye} />
            <SidebarItem id="SITE_CONTENT" label="Site İçeriği" icon={Globe} />
          </div>
        </div>

        {/* Bilgi Kartı (Sidebar altı) */}
        <div className="bg-slate-900 rounded-[2.5rem] sm:rounded-[3rem] p-6 sm:p-10 text-white relative overflow-hidden group">
           <div className="absolute top-0 right-0 w-32 h-32 bg-cyan-500/10 rounded-full blur-3xl group-hover:bg-cyan-500/20 transition-all"></div>
           <ShieldAlert className="h-8 w-8 sm:h-10 sm:w-10 text-cyan-400 mb-4 sm:mb-6" />
           <p className="text-[10px] font-black uppercase tracking-[0.3em] text-cyan-400/80 mb-2">Hesap Yönetimi</p>
           <p className="text-xs font-bold leading-relaxed text-slate-300">
             Yeni öğretmen ve öğrenci hesapları güvenli e-posta ve şifre ile anında aktifleşir.
           </p>
        </div>
      </aside>

      {/* ANA İÇERİK ALANI */}
      <main className="flex-1 min-w-0">
        {activeTab === "OVERVIEW" && (
          <div className="space-y-8 animate-in slide-in-from-right-10 duration-500">
             {/* T-075: Tek Yönetici Güvenlik Uyarısı */}
             {managerCount <= 1 && (
               <div
                 data-testid="single-admin-warning"
                 className="bg-amber-50 border-2 border-amber-300/80 rounded-[2.5rem] p-6 sm:p-8 flex items-start gap-4 sm:gap-6 shadow-lg shadow-amber-500/5 animate-in fade-in duration-300"
               >
                 <div className="p-3.5 bg-amber-100 rounded-2xl text-amber-700 shrink-0 mt-0.5 shadow-sm">
                   <ShieldAlert className="h-6 w-6 sm:h-7 sm:w-7" />
                 </div>
                 <div className="space-y-2">
                   <h4 className="text-sm sm:text-base font-black text-amber-950 uppercase tracking-wide">
                     Önemli Güvenlik Önerisi: Tek Yönetici Hesabı Tespit Edildi
                   </h4>
                   <p className="text-xs sm:text-sm font-bold text-amber-900/90 leading-relaxed">
                     Sistemde şu anda yalnızca <strong>1 aktif yönetici</strong> bulunmaktadır. Olası kilitlenme ve acil durum erişim kayıplarını önlemek amacıyla sisteme en az bir adet yedek yönetici (ikinci yönetici) tanımlanması şiddetle önerilir.
                   </p>
                   <p className="text-[11px] font-semibold text-amber-800/80">
                     İkinci bir yönetici hesabı oluşturmak ve kilitlenme kurtarma adımlarını incelemek için sistem runbook dokümantasyonuna (<code className="bg-amber-100/80 px-1.5 py-0.5 rounded font-mono text-amber-950">YONETICI_KURTARMA_REHBERI.md</code>) başvurabilirsiniz.
                   </p>
                 </div>
               </div>
             )}

             <h2 className="text-2xl sm:text-4xl font-black text-slate-900 tracking-tighter">Sistem <span className="text-cyan-500 italic">Özeti</span></h2>
             <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 sm:gap-8">
                <Card className="rounded-[2.5rem] sm:rounded-[3rem] border-0 shadow-xl p-6 sm:p-10 bg-white">
                   <p className="text-[10px] font-black text-slate-400 uppercase tracking-widest mb-4">Aktif Kullanıcı Sayısı</p>
                   <p className="text-4xl sm:text-5xl font-black text-slate-900 tracking-tighter">
                     {activeTeachers.length + activeStudents.length}
                   </p>
                   <div className="mt-4 flex gap-4 text-xs font-bold text-slate-500">
                     <span>Öğretmen: <b className="text-slate-900">{activeTeachers.length}</b></span>
                     <span>•</span>
                     <span>Öğrenci: <b className="text-slate-900">{activeStudents.length}</b></span>
                   </div>
                </Card>
                <Card className="rounded-[2.5rem] sm:rounded-[3rem] border-0 shadow-xl p-6 sm:p-10 bg-slate-900 text-white">
                   <p className="text-[10px] font-black text-cyan-400 uppercase tracking-widest mb-4">Eşleşme Bekleyenler</p>
                   <p className="text-4xl sm:text-5xl font-black tracking-tighter">{unpairedStudents.length}</p>
                   <div className="mt-4 text-xs font-bold text-slate-400">
                     <span>Eşleşmemiş Öğrenci Sayısı</span>
                   </div>
                </Card>
             </div>

             {/* Aktif Kullanıcı Listeleri */}
             <div className="grid grid-cols-1 md:grid-cols-2 gap-8 pt-4">
                {/* Öğretmenler */}
                <div className="bg-white rounded-[3rem] p-8 shadow-xl border border-slate-50 space-y-4">
                  <h3 className="text-lg font-black text-slate-900 tracking-tight flex items-center justify-between">
                    <span>Aktif Eğitmenler</span>
                    <span className="text-xs bg-slate-100 text-slate-600 px-3 py-1 rounded-full">{activeTeachers.length}</span>
                  </h3>
                  <div className="space-y-3 max-h-[350px] overflow-y-auto">
                    {activeTeachers.length === 0 ? (
                      <p className="text-slate-400 text-xs py-8 text-center font-bold">Kayıtlı öğretmen bulunmuyor.</p>
                    ) : (
                      activeTeachers.map(t => (
                        <div key={t.id} className="p-4 rounded-2xl bg-slate-50 flex items-center justify-between gap-2">
                          <div className="min-w-0 flex-1">
                            <p className="font-black text-sm text-slate-900 truncate">{t.fullName}</p>
                            <p className="text-[10px] font-bold text-slate-400 truncate">{t.email}</p>
                          </div>
                          <div className="flex items-center gap-2 shrink-0">
                            <button
                              type="button"
                              onClick={() => handleResetPassword(t)}
                              className="tap-44 px-2.5 py-1.5 rounded-xl bg-white hover:bg-amber-50 hover:text-amber-700 text-slate-600 border border-slate-200 hover:border-amber-200 transition-all flex items-center gap-1 text-[9px] font-black uppercase tracking-wider"
                              title="Yeni Geçici Şifre Üret"
                            >
                              <KeyRound className="h-3.5 w-3.5 text-amber-600" />
                              <span>Şifre Sıfırla</span>
                            </button>
                            <span className="text-[9px] font-black uppercase tracking-widest text-blue-600 bg-blue-50 px-2.5 py-1 rounded-lg">
                              Eğitmen
                            </span>
                          </div>
                        </div>
                      ))
                    )}
                  </div>
                </div>

                {/* Öğrenciler */}
                <div className="bg-white rounded-[3rem] p-8 shadow-xl border border-slate-50 space-y-4">
                  <h3 className="text-lg font-black text-slate-900 tracking-tight flex items-center justify-between">
                    <span>Aktif Öğrenciler</span>
                    <span className="text-xs bg-slate-100 text-slate-600 px-3 py-1 rounded-full">{activeStudents.length}</span>
                  </h3>
                  <div className="space-y-3 max-h-[350px] overflow-y-auto">
                    {activeStudents.length === 0 ? (
                      <p className="text-slate-400 text-xs py-8 text-center font-bold">Kayıtlı öğrenci bulunmuyor.</p>
                    ) : (
                      activeStudents.map(s => (
                        <div key={s.id} className="p-4 rounded-2xl bg-slate-50 flex items-center justify-between gap-2">
                          <div className="min-w-0 flex-1">
                            <p className="font-black text-sm text-slate-900 truncate">{s.fullName}</p>
                            <p className="text-[10px] font-bold text-slate-400 truncate">{s.email}</p>
                          </div>
                          <div className="flex items-center gap-2 shrink-0">
                            <button
                              type="button"
                              onClick={() => handleResetPassword(s)}
                              className="tap-44 px-2.5 py-1.5 rounded-xl bg-white hover:bg-amber-50 hover:text-amber-700 text-slate-600 border border-slate-200 hover:border-amber-200 transition-all flex items-center gap-1 text-[9px] font-black uppercase tracking-wider"
                              title="Yeni Geçici Şifre Üret"
                            >
                              <KeyRound className="h-3.5 w-3.5 text-amber-600" />
                              <span>Şifre Sıfırla</span>
                            </button>
                            {s.grade && (
                              <span className="text-[9px] font-black uppercase tracking-wider text-emerald-700 bg-emerald-100 px-2.5 py-1 rounded-lg">
                                {s.grade}. Sınıf
                              </span>
                            )}
                            <span className="text-[9px] font-black uppercase tracking-widest text-cyan-600 bg-cyan-50 px-2.5 py-1 rounded-lg">
                              Öğrenci
                            </span>
                          </div>
                        </div>
                      ))
                    )}
                  </div>
                </div>
             </div>
          </div>
        )}

        {/* KULLANICI EKLEME FORMU (T-004 / T-010) */}
        {activeTab === "CREATE_USER" && (
          <div className="space-y-8 animate-in slide-in-from-right-10 duration-500 max-w-2xl">
             <div className="flex flex-col gap-2">
                <h2 className="text-4xl font-black text-slate-900 tracking-tighter">Yeni <span className="text-cyan-500 italic">Kullanıcı Ekle</span></h2>
                <p className="text-[11px] font-bold text-slate-400 uppercase tracking-widest">
                  Öğretmen ve öğrenci hesaplarını oluşturun. Girişler güvenli e-posta ve şifre ile sağlanır.
                </p>
             </div>


             <Card className="rounded-[3rem] border-0 shadow-2xl bg-white overflow-hidden p-8 sm:p-12">
               <form onSubmit={handleCreateUser} className="space-y-6">
                 {/* Ad Soyad */}
                 <div className="space-y-2">
                   <label className="text-[10px] font-black text-slate-400 uppercase tracking-widest ml-4">
                     Ad Soyad *
                   </label>
                   <Input 
                     placeholder="Örn: Ayşe Öztürk" 
                     value={createFullName} 
                     onChange={(e) => setCreateFullName(e.target.value)} 
                     disabled={isSubmitting}
                     className="h-16 rounded-2xl border-2 border-slate-50 bg-slate-50 focus:bg-white focus:border-slate-900 font-bold px-6 text-sm"
                   />
                 </div>

                 {/* Hesap Türü (Rol Seçimi) */}
                 <div className="space-y-2 pt-2">
                   <label className="text-[10px] font-black text-slate-400 uppercase tracking-widest ml-4">
                     Hesap Türü *
                   </label>
                   <div className="grid grid-cols-2 gap-4">
                     <button 
                       type="button"
                       onClick={() => setCreateRole("STUDENT")}
                       disabled={isSubmitting}
                       className={`p-5 rounded-[2rem] border-2 transition-all flex flex-col items-center gap-3 cursor-pointer ${
                         createRole === "STUDENT" 
                           ? "border-emerald-500 bg-emerald-50/40 shadow-lg text-emerald-900" 
                           : "border-slate-50 bg-slate-50/50 hover:border-slate-200 text-slate-600"
                       }`}
                     >
                       <GraduationCap className={`h-8 w-8 ${createRole === "STUDENT" ? "text-emerald-600" : "text-slate-300"}`} />
                       <span className="text-[10px] font-black uppercase tracking-widest">Öğrenci</span>
                     </button>

                     <button 
                       type="button"
                       onClick={() => setCreateRole("TEACHER")}
                       disabled={isSubmitting}
                       className={`p-5 rounded-[2rem] border-2 transition-all flex flex-col items-center gap-3 cursor-pointer ${
                         createRole === "TEACHER" 
                           ? "border-blue-500 bg-blue-50/40 shadow-lg text-blue-900" 
                           : "border-slate-50 bg-slate-50/50 hover:border-slate-200 text-slate-600"
                       }`}
                     >
                       <Briefcase className={`h-8 w-8 ${createRole === "TEACHER" ? "text-blue-600" : "text-slate-300"}`} />
                       <span className="text-[10px] font-black uppercase tracking-widest">Eğitmen</span>
                     </button>
                   </div>
                 </div>

                 {/* Sınıf Seviyesi (Yalnızca Öğrenci için görünür - T-016 / IST-02 §3) */}
                 {createRole === "STUDENT" && (
                   <fieldset className="space-y-2 animate-in fade-in slide-in-from-top-2 duration-300 border-0 p-0 m-0">
                     <legend className="text-[10px] font-black text-slate-400 uppercase tracking-widest ml-4 mb-2">
                       Sınıf Seviyesi *
                     </legend>
                     <div className="grid grid-cols-4 sm:grid-cols-8 gap-2">
                       {[5, 6, 7, 8, 9, 10, 11, 12].map((g) => (
                         <label
                           key={g}
                           className={`flex flex-col items-center justify-center p-3 rounded-2xl border-2 cursor-pointer transition-all has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-emerald-500 has-[:focus-visible]:ring-offset-2 has-[:focus-visible]:border-emerald-500 ${
                             createGrade === g
                               ? "border-emerald-500 bg-emerald-50 text-emerald-900 shadow-sm font-black"
                               : "border-slate-50 bg-slate-50 text-slate-700 hover:border-slate-200 font-bold"
                           }`}
                         >
                           <input
                             type="radio"
                             name="grade"
                             value={g}
                             checked={createGrade === g}
                             onChange={() => setCreateGrade(g)}
                             disabled={isSubmitting}
                             className="sr-only"
                           />
                           <span className="text-base">{g}</span>
                           <span className="text-[9px] font-bold text-slate-400">Sınıf</span>
                         </label>
                       ))}
                     </div>
                   </fieldset>
                 )}

                  <Button 
                    type="submit" 
                    disabled={
                      isSubmitting || 
                      !createFullName.trim() || 
                      (createRole === "STUDENT" && createGrade === null)
                    }
                    className="w-full h-16 rounded-2xl font-black text-sm uppercase tracking-widest bg-slate-900 hover:bg-slate-800 text-white transition-all shadow-xl active:scale-98 mt-4"
                  >
                    {isSubmitting ? (
                      <Loader2 className="h-5 w-5 animate-spin" />
                    ) : (
                      <>
                        Hesabı Oluştur <ArrowRight className="ml-2 h-5 w-5" />
                      </>
                    )}
                  </Button>
                </form>
              </Card>
           </div>
        )}

        {activeTab === "CLASSES" && (
          <div className="space-y-8 animate-in fade-in duration-500">
            <div>
              <h2 className="text-4xl font-black tracking-tight text-slate-900">Sınıflar</h2>
              <p className="text-[10px] font-black uppercase tracking-[0.3em] text-slate-400 mt-2">
                Sınıf oluştur, öğrencileri ekle ve çıkar
              </p>
            </div>

            <Card className="rounded-[2rem] border-0 shadow-lg bg-white p-8">
              <p className="text-[9px] font-black uppercase tracking-widest text-slate-400 mb-4">Yeni Sınıf</p>
              <div className="flex flex-wrap gap-3">
                <input
                  type="text"
                  value={newClassName}
                  onChange={(e) => setNewClassName(e.target.value)}
                  placeholder="Örn. 12-A"
                  maxLength={100}
                  className="flex-1 min-w-[220px] bg-slate-50 border-2 border-slate-100 rounded-2xl px-6 py-3 min-h-[44px] font-bold text-slate-700 focus:outline-none focus:border-cyan-500 transition-all"
                />
                <button
                  type="button"
                  onClick={handleCreateClass}
                  disabled={classBusy || !newClassName.trim()}
                  className="tap-44 px-8 py-3 rounded-2xl bg-slate-900 text-white font-black text-[10px] uppercase tracking-widest hover:bg-slate-800 disabled:opacity-40 transition-all"
                >
                  Sınıf Oluştur
                </button>
              </div>
            </Card>

            {classes.length === 0 ? (
              <Card className="rounded-[2rem] border-0 shadow-lg bg-white p-10 text-center">
                <p className="text-sm font-black text-slate-700">Henüz sınıf yok.</p>
                <p className="text-[11px] font-bold text-slate-400 mt-2">Yukarıdan ilk sınıfı oluşturabilirsiniz.</p>
              </Card>
            ) : (
              <div className="space-y-4">
                {classes.map((c) => {
                  const sinifsizlar = activeStudents.filter(
                    (st) => !classes.some((k) => k.students.some((x) => x.id === st.id))
                  );
                  return (
                    <Card key={c.id} className="rounded-[2rem] border-0 shadow-lg bg-white p-8 space-y-6">
                      <div className="flex flex-wrap items-center justify-between gap-4">
                        <div>
                          <h3 className="text-xl font-black text-slate-900">{c.name}</h3>
                          <p className="text-[10px] font-black uppercase tracking-widest text-slate-400 mt-1">
                            {c.students.length} öğrenci
                          </p>
                        </div>
                        <button
                          type="button"
                          onClick={() => handleDeleteClass(c)}
                          disabled={classBusy}
                          className="tap-44 px-5 py-2 rounded-xl bg-white border border-slate-200 text-slate-600 hover:bg-red-50 hover:text-red-600 hover:border-red-200 font-black text-[9px] uppercase tracking-widest transition-all disabled:opacity-40"
                        >
                          Sınıfı Sil
                        </button>
                      </div>

                      {c.students.length > 0 && (
                        <div className="space-y-2">
                          {c.students.map((st) => (
                            <div key={st.id} className="flex items-center justify-between gap-4 px-4 py-2.5 rounded-xl bg-slate-50">
                              <span className="text-xs font-bold text-slate-700">
                                {st.fullName}
                                {st.grade != null && (
                                  <span className="ml-2 text-[9px] font-black uppercase tracking-widest text-slate-400">
                                    {st.grade}. sınıf
                                  </span>
                                )}
                              </span>
                              <button
                                type="button"
                                onClick={() => handleRemoveStudentFromClass(c.id, st)}
                                disabled={classBusy}
                                className="tap-44 px-4 py-1.5 rounded-lg text-[9px] font-black uppercase tracking-widest text-slate-500 hover:text-red-600 transition-all disabled:opacity-40"
                              >
                                Çıkar
                              </button>
                            </div>
                          ))}
                        </div>
                      )}

                      {sinifsizlar.length > 0 && (
                        <div className="pt-4 border-t border-slate-100">
                          <p className="text-[9px] font-black uppercase tracking-widest text-slate-400 mb-3">
                            Sınıfsız öğrenciyi ekle
                          </p>
                          <div className="flex flex-wrap gap-2">
                            {sinifsizlar.map((st) => (
                              <button
                                key={st.id}
                                type="button"
                                onClick={() => handleAddStudentToClass(c.id, st.id)}
                                disabled={classBusy}
                                className="tap-44 px-4 py-2 rounded-xl bg-slate-50 hover:bg-cyan-50 hover:text-cyan-700 border border-slate-100 text-[11px] font-bold text-slate-600 transition-all disabled:opacity-40"
                              >
                                + {st.fullName}
                              </button>
                            ))}
                          </div>
                        </div>
                      )}
                    </Card>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {activeTab === "ACTIVITY" && (
          <div className="space-y-8 animate-in fade-in duration-500">
            <div>
              <h2 className="text-4xl font-black tracking-tight text-slate-900">Aktiflik</h2>
              <p className="text-[10px] font-black uppercase tracking-[0.3em] text-slate-400 mt-2">
                Kim ne zaman girdi, öğretmen öğrencisinin verisine baktı mı
              </p>
            </div>

            {activity.length === 0 ? (
              <Card className="rounded-[2rem] border-0 shadow-lg bg-white p-10 text-center">
                <p className="text-sm font-black text-slate-700">Henüz aktiflik kaydı yok.</p>
                <p className="text-[11px] font-bold text-slate-400 mt-2">
                  Kullanıcılar giriş yaptıkça ve karne görüntüledikçe burası dolar.
                </p>
              </Card>
            ) : (
              <div className="space-y-4">
                {activity.map((a) => (
                  <Card key={a.userId} className="rounded-[2rem] border-0 shadow-lg bg-white p-8">
                    <div className="flex flex-wrap items-center justify-between gap-4">
                      <div className="flex items-center gap-4">
                        <div className="w-12 h-12 rounded-2xl bg-slate-100 flex items-center justify-center font-black text-slate-500">
                          {(a.fullName || "?").charAt(0)}
                        </div>
                        <div>
                          <p className="font-black text-slate-900">{a.fullName}</p>
                          <span className="text-[9px] font-black uppercase tracking-widest text-slate-400">{a.role}</span>
                        </div>
                      </div>
                      <div className="flex flex-wrap items-center gap-3">
                        <span className="flex items-center gap-2 px-4 py-2 rounded-xl bg-slate-50 text-[11px] font-bold text-slate-600">
                          <Clock className="h-3.5 w-3.5 text-slate-400" />
                          {a.lastLoginAt ? `Son giriş: ${new Date(a.lastLoginAt).toLocaleString("tr-TR")}` : "Hiç giriş yapmadı"}
                        </span>
                        <span className="flex items-center gap-2 px-4 py-2 rounded-xl bg-slate-50 text-[11px] font-bold text-slate-600">
                          <Eye className="h-3.5 w-3.5 text-slate-400" />
                          {a.reportViewCount} karne görüntüleme
                        </span>
                      </div>
                    </div>

                    {a.viewedStudents.length > 0 && (
                      <div className="mt-6 pt-6 border-t border-slate-100 space-y-2">
                        <p className="text-[9px] font-black uppercase tracking-widest text-slate-400">
                          Eşleştiği öğrenciler
                        </p>
                        {a.viewedStudents.map((sv) => (
                          <div key={sv.studentId} className="flex items-center justify-between gap-4 px-4 py-2.5 rounded-xl bg-slate-50">
                            <span className="text-xs font-bold text-slate-700">{sv.studentName}</span>
                            {sv.lastViewedAt ? (
                              <span className="text-[11px] font-bold text-emerald-600">
                                Son bakış: {new Date(sv.lastViewedAt).toLocaleString("tr-TR")}
                              </span>
                            ) : (
                              <span className="text-[11px] font-black uppercase tracking-wider text-amber-600">
                                Hiç bakmadı
                              </span>
                            )}
                          </div>
                        ))}
                      </div>
                    )}
                  </Card>
                ))}
              </div>
            )}
          </div>
        )}

        {activeTab === "PAIRING" && (
          <div className="space-y-8 animate-in slide-in-from-right-10 duration-500">
             <div className="flex flex-col gap-2">
                <h2 className="text-4xl font-black text-slate-900 tracking-tighter">Eşleştirme <span className="text-cyan-500 italic">Merkezi</span></h2>
                <p className="text-[10px] font-black text-slate-400 uppercase tracking-widest">Eğitmen ve Öğrenci bağlarını buradan kurun</p>
             </div>
             
             <div className="grid grid-cols-2 gap-8">
                {/* Öğretmen Seçim Alanı */}
                <div className="space-y-4">
                   <p className="text-[10px] font-black text-slate-400 uppercase tracking-widest ml-4">1. Eğitmen Seçin ({activeTeachers.length})</p>
                   <div className="bg-white rounded-[3rem] p-6 shadow-xl border border-slate-50 min-h-[500px] max-h-[500px] overflow-y-auto space-y-3 custom-scrollbar">
                      {activeTeachers.length === 0 ? (
                        <p className="text-center text-slate-300 text-[10px] font-bold py-20">Aktif eğitmen bulunmuyor.</p>
                      ) : (
                        activeTeachers.map(teacher => (
                          <div 
                            key={teacher.id} 
                            onClick={() => setSelectedTeacher(teacher.id)}
                            className={`p-6 rounded-[2rem] border-2 cursor-pointer transition-all ${selectedTeacher === teacher.id ? 'border-slate-900 bg-slate-900 text-white shadow-lg' : 'border-slate-50 bg-slate-50 hover:border-slate-200'}`}
                          >
                             <h5 className="font-black text-sm tracking-tight">{teacher.fullName}</h5>
                             <p className={`text-[9px] font-bold uppercase tracking-widest mt-1 ${selectedTeacher === teacher.id ? 'text-cyan-400' : 'text-slate-400'}`}>{teacher.email}</p>
                          </div>
                        ))
                      )}
                   </div>
                </div>

                {/* Öğrenci Seçim Alanı (T-021: Yalnızca eşleşmemiş öğrenciler) */}
                <div className="space-y-4">
                   <p className="text-[10px] font-black text-slate-400 uppercase tracking-widest ml-4">2. Öğrenci Seçin ({unpairedStudents.length})</p>
                   <div className="bg-white rounded-[3rem] p-6 shadow-xl border border-slate-50 min-h-[500px] max-h-[500px] overflow-y-auto space-y-3 custom-scrollbar">
                      {unpairedStudents.length === 0 ? (
                        <p className="text-center text-slate-300 text-[10px] font-bold py-20">Eşleşmeye uygun öğrenci bulunmuyor.</p>
                      ) : (
                        unpairedStudents.map(student => (
                          <div 
                            key={student.id} 
                            onClick={() => setSelectedStudent(student.id)}
                            className={`p-6 rounded-[2rem] border-2 cursor-pointer transition-all ${selectedStudent === student.id ? 'border-cyan-500 bg-cyan-50/50 shadow-lg' : 'border-slate-50 bg-slate-50 hover:border-slate-200'}`}
                          >
                             <div className="flex items-center justify-between">
                               <h5 className="font-black text-sm tracking-tight text-slate-900">{student.fullName}</h5>
                               {student.grade && (
                                 <span className="text-[8px] font-black uppercase text-emerald-700 bg-emerald-100 px-2 py-0.5 rounded">
                                   {student.grade}. Sınıf
                                 </span>
                               )}
                             </div>
                             <p className="text-[9px] font-bold text-slate-400 uppercase tracking-widest mt-1">{student.email}</p>
                          </div>
                        ))
                      )}
                   </div>
                </div>
             </div>

             {/* Eşleştirme Butonu */}
             <button 
               onClick={handlePairing}
               disabled={!selectedTeacher || !selectedStudent}
               className={`w-full py-8 rounded-[2.5rem] font-black text-sm uppercase tracking-[0.3em] transition-all shadow-2xl ${
                 !selectedTeacher || !selectedStudent 
                 ? "bg-slate-100 text-slate-300 cursor-not-allowed shadow-none" 
                 : "bg-slate-900 text-white hover:bg-cyan-500 shadow-slate-200"
               }`}
             >
                {selectedTeacher && selectedStudent ? "Bağlantıyı Kur" : "Seçim Bekleniyor"}
             </button>

             {/* Aktif Eşleşmeler Listesi (T-036) */}
             <div className="pt-8 space-y-4">
               <div className="flex items-center justify-between">
                 <div className="flex items-center gap-3">
                   <h3 className="text-xl font-black text-slate-900 tracking-tight">Aktif Eşleşmeler</h3>
                   <span className="text-xs bg-slate-900 text-white font-black px-3 py-1 rounded-full">
                     {pairings.length}
                   </span>
                 </div>
                 <p className="text-[10px] font-black text-slate-400 uppercase tracking-widest">
                   Mevcut Eğitmen - Öğrenci Bağlantıları
                 </p>
               </div>

               {pairings.length === 0 ? (
                 <div className="bg-white rounded-[3rem] p-16 text-center border-2 border-dashed border-slate-100">
                   <Link2 className="h-12 w-12 text-slate-200 mx-auto mb-4" />
                   <p className="text-slate-400 font-black text-[10px] uppercase tracking-[0.3em]">
                     Henüz kurulmuş bir eşleşme bulunmuyor.
                   </p>
                 </div>
               ) : (
                 <div className="grid grid-cols-1 gap-4">
                   {pairings.map((p) => (
                     <div 
                       key={`${p.studentId}-${p.teacherId}`}
                       className="bg-white rounded-[2.5rem] p-6 shadow-xl border border-slate-50 flex items-center justify-between gap-6 hover:shadow-2xl transition-all group"
                     >
                       {/* Öğrenci Bilgisi */}
                       <div className="flex items-center gap-4 min-w-0 flex-1">
                         <div className="w-12 h-12 rounded-2xl bg-emerald-50 text-emerald-700 font-black text-base flex items-center justify-center shrink-0 border border-emerald-100">
                           {p.studentName.charAt(0)}
                         </div>
                         <div className="min-w-0 flex-1">
                           <div className="flex items-center gap-2">
                             <p className="font-black text-sm text-slate-900 truncate">{p.studentName}</p>
                             {p.studentGrade && (
                               <span className="text-[8px] font-black uppercase text-emerald-700 bg-emerald-100 px-2 py-0.5 rounded shrink-0">
                                 {p.studentGrade}. Sınıf
                               </span>
                             )}
                             <span className="text-[8px] font-black uppercase text-slate-500 bg-slate-100 px-2 py-0.5 rounded shrink-0">
                               Öğrenci
                             </span>
                           </div>
                           <p className="text-[10px] font-bold text-slate-400 truncate">{p.studentEmail}</p>
                         </div>
                       </div>

                       {/* Bağlantı İkonu & Tarih */}
                       <div className="flex flex-col items-center justify-center shrink-0 px-4">
                         <div className="w-8 h-8 rounded-full bg-slate-100 flex items-center justify-center text-slate-400 group-hover:bg-cyan-50 group-hover:text-cyan-600 transition-colors">
                           <Link2 className="h-4 w-4" />
                         </div>
                         {p.pairedAt && (
                           <span className="text-[8px] font-bold text-slate-400 mt-1">
                             {new Date(p.pairedAt).toLocaleDateString('tr-TR', { day: 'numeric', month: 'short', year: 'numeric' })}
                           </span>
                         )}
                       </div>

                       {/* Eğitmen Bilgisi */}
                       <div className="flex items-center gap-4 min-w-0 flex-1">
                         <div className="w-12 h-12 rounded-2xl bg-blue-50 text-blue-700 font-black text-base flex items-center justify-center shrink-0 border border-blue-100">
                           {p.teacherName.charAt(0)}
                         </div>
                         <div className="min-w-0 flex-1">
                           <div className="flex items-center gap-2">
                             <p className="font-black text-sm text-slate-900 truncate">{p.teacherName}</p>
                             <span className="text-[8px] font-black uppercase text-blue-700 bg-blue-100 px-2 py-0.5 rounded shrink-0">
                               Eğitmen
                             </span>
                           </div>
                           <p className="text-[10px] font-bold text-slate-400 truncate">{p.teacherEmail}</p>
                         </div>
                       </div>

                       {/* Ayır Butonu */}
                       <div className="shrink-0">
                         <button
                           type="button"
                           onClick={() => handleUnpair(p.studentId, p.teacherId, p.studentName, p.teacherName)}
                           disabled={unpairingStudentId === p.studentId}
                           className="tap-44 px-4 py-2.5 rounded-2xl bg-red-50 hover:bg-red-500 hover:text-white text-red-600 border border-red-100 transition-all flex items-center gap-2 text-[10px] font-black uppercase tracking-wider active:scale-95 disabled:opacity-50"
                           title="Eşleştirmeyi Ayır"
                         >
                           {unpairingStudentId === p.studentId ? (
                             <Loader2 className="h-4 w-4 animate-spin" />
                           ) : (
                             <UserMinus className="h-4 w-4" />
                           )}
                           <span>Ayır</span>
                         </button>
                       </div>
                     </div>
                   ))}
                 </div>
               )}
             </div>
          </div>
        )}

        {activeTab === "PSYCH_TESTS" && (
          <PsychTestAssignmentTab
            students={activeStudents}
            unseenCount={unseenPsychCount}
            onSeen={() => setUnseenPsychCount(0)}
          />
        )}

        {siteEditorOpened && (
          <div hidden={activeTab !== "SITE_CONTENT"}>
            <SiteContentEditor />
          </div>
        )}
      </main>
    </div>
  );
}
