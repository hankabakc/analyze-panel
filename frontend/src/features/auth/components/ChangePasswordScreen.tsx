import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { toast } from "sonner";
import { useAuth } from "@/lib/auth-context";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { KeyRound, Eye, EyeOff, Loader2, ArrowLeft, AlertTriangle, ShieldCheck } from "lucide-react";

/**
 * ChangePasswordScreen: Giriş yapmış kullanıcının şifresini isteğine göre değiştirdiği ekrandır (T-018).
 *
 * <p>Mühendislik Standartları:</p>
 * <ul>
 *   <li>APP-01 §2.1: Mevcut şifre denetimi sunucuda yapılır (T-015). Buradaki boşluk denetimi
 *       yalnızca gereksiz ağ isteğini önler; karar sunucunundur.</li>
 *   <li>ENG-11 §2.4: Şifre değiştiğinde sunucu tüm oturumları düşürür ve bu isteğe yeni çerez yazar;
 *       kullanıcı bu cihazda oturumda kalır, diğer cihazlar düşer.</li>
 *   <li>IST-02 §3.3: Form yalnızca klavyeyle doldurulup gönderilebilir; odak göstergesi görünürdür.</li>
 * </ul>
 *
 * <p>Zorunlu ilk değişiklik ekranı (MustChangePasswordScreen) bundan ayrıdır ve mevcut şifre sormaz.</p>
 */
export function ChangePasswordScreen() {
  const { user, changePassword } = useAuth();
  const navigate = useNavigate();

  const isCurrentRequired = user?.currentPasswordRequired !== false;

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  const [showCurrentPass, setShowCurrentPass] = useState(false);
  const [showNewPass, setShowNewPass] = useState(false);
  const [showConfirmPass, setShowConfirmPass] = useState(false);

  const [isLoading, setIsLoading] = useState(false);
  const [validationError, setValidationError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setValidationError(null);

    if (isCurrentRequired && !currentPassword.trim()) {
      setValidationError("Lütfen mevcut şifrenizi giriniz.");
      return;
    }

    if (newPassword.length < 12) {
      setValidationError("Yeni şifreniz en az 12 karakter uzunluğunda olmalıdır.");
      return;
    }

    if (newPassword !== confirmPassword) {
      setValidationError("Yeni şifreler birbiriyle eşleşmiyor.");
      return;
    }

    setIsLoading(true);
    try {
      await changePassword(newPassword, isCurrentRequired ? currentPassword : undefined);
      toast.success("Şifreniz güncellendi. Diğer cihazlardaki oturumlarınız kapatıldı.");
      navigate("/");
    } catch (err: unknown) {
      // Mevcut şifrenin doğruluğu ve yeni şifrenin kabul edilebilirliği sunucuda karara bağlanır.
      const errorObj = err as { response?: { data?: { message?: string } } };
      setValidationError(errorObj.response?.data?.message ?? "Şifre değiştirilemedi.");
    } finally {
      setIsLoading(false);
    }
  };

  /** Göz düğmesi: odak sırasını bozmaması için tabIndex -1, alan zaten klavyeyle doldurulabilir. Dokunma alanı min 44×44 px (IST-02 §3.1 / T-048B). */
  const gozDugmesi = (gorunur: boolean, degistir: () => void) => (
    <button
      type="button"
      onClick={degistir}
      className="tap-44 min-w-[44px] min-h-[44px] flex items-center justify-center absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-700 transition-colors p-2.5"
      tabIndex={-1}
      aria-hidden="true"
    >
      {gorunur ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
    </button>
  );

  const alanSinifi =
    "h-14 rounded-2xl border-2 border-slate-50 bg-slate-50 focus:bg-white font-bold px-5 text-sm pr-12 transition-all";

  return (
    <div className="w-full flex justify-center">
      <div className="w-full max-w-[520px] animate-in fade-in zoom-in-95 duration-500">
        <Card className="rounded-[3rem] border-0 shadow-[0_40px_80px_-15px_rgba(0,0,0,0.10)] overflow-hidden bg-white">
          <div className="h-2.5 bg-gradient-to-r from-cyan-500 to-blue-500"></div>

          <CardContent className="p-8 sm:p-12">
            <div className="flex flex-col items-center text-center mb-8">
              <div className="w-20 h-20 bg-cyan-50 rounded-[2rem] flex items-center justify-center mb-4 border border-cyan-100/60 shadow-inner">
                <KeyRound className="h-9 w-9 text-cyan-600" />
              </div>
              <h2 className="text-2xl sm:text-3xl font-black text-slate-900 tracking-tighter">
                Şifremi Değiştir
              </h2>
              <p className="text-[11px] font-bold text-slate-400 uppercase tracking-widest mt-1">
                Hesap Güvenliği
              </p>
            </div>

            <div className="bg-cyan-500/10 border border-cyan-500/20 rounded-2xl p-4 mb-6 flex items-start gap-3 text-left">
              <ShieldCheck className="h-5 w-5 text-cyan-600 shrink-0 mt-0.5" />
              <p className="text-xs font-bold text-cyan-950 leading-relaxed">
                Şifreniz değiştiğinde <strong>diğer cihazlardaki oturumlarınız kapatılır.</strong> Bu cihazda
                oturumunuz açık kalır.
              </p>
            </div>

            {validationError && (
              <div
                role="alert"
                className="bg-red-50 border border-red-200 rounded-2xl p-3.5 mb-6 flex items-center gap-2.5 text-left animate-in fade-in"
              >
                <AlertTriangle className="h-4 w-4 text-red-600 shrink-0" />
                <p className="text-xs font-bold text-red-700">{validationError}</p>
              </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-5">
              {/* T-056: mustChangePassword=true haricinde mevcut şifre her zaman zorunludur */}
              {isCurrentRequired ? (
                <div className="space-y-1.5 text-left">
                  <label
                    htmlFor="mevcut-sifre"
                    className="text-[10px] font-black text-slate-400 uppercase tracking-widest ml-4 block"
                  >
                    Mevcut Şifre *
                  </label>
                  <div className="relative">
                    <Input
                      id="mevcut-sifre"
                      type={showCurrentPass ? "text" : "password"}
                      autoComplete="current-password"
                      placeholder="Şu anki şifreniz"
                      value={currentPassword}
                      onChange={(e) => setCurrentPassword(e.target.value)}
                      disabled={isLoading}
                      className={alanSinifi}
                    />
                    {gozDugmesi(showCurrentPass, () => setShowCurrentPass(!showCurrentPass))}
                  </div>
                </div>
              ) : (
                <div className="bg-emerald-500/10 border border-emerald-500/20 rounded-2xl p-4 flex items-start gap-3 text-left">
                  <ShieldCheck className="h-5 w-5 text-emerald-600 shrink-0 mt-0.5" />
                  <p className="text-xs font-bold text-emerald-950 leading-relaxed">
                    Geçici şifrenizle ilk oturumu açtığınız için <strong>mevcut şifrenizi girmeden</strong> doğrudan yeni bir şifre belirleyebilirsiniz.
                  </p>
                </div>
              )}

              <div className="space-y-1.5 text-left">
                <div className="flex items-center justify-between ml-4">
                  <label
                    htmlFor="yeni-sifre"
                    className="text-[10px] font-black text-slate-400 uppercase tracking-widest"
                  >
                    Yeni Şifre *
                  </label>
                  <span
                    className={`text-[10px] font-black ${
                      newPassword.length >= 12 ? "text-emerald-600" : "text-slate-400"
                    }`}
                  >
                    {newPassword.length}/12 min
                  </span>
                </div>
                <div className="relative">
                  <Input
                    id="yeni-sifre"
                    type={showNewPass ? "text" : "password"}
                    autoComplete="new-password"
                    placeholder="En az 12 karakterli yeni şifreniz"
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    disabled={isLoading}
                    className={alanSinifi}
                  />
                  {gozDugmesi(showNewPass, () => setShowNewPass(!showNewPass))}
                </div>
              </div>

              <div className="space-y-1.5 text-left">
                <label
                  htmlFor="yeni-sifre-tekrar"
                  className="text-[10px] font-black text-slate-400 uppercase tracking-widest ml-4 block"
                >
                  Yeni Şifre (Tekrar) *
                </label>
                <div className="relative">
                  <Input
                    id="yeni-sifre-tekrar"
                    type={showConfirmPass ? "text" : "password"}
                    autoComplete="new-password"
                    placeholder="Yeni şifrenizi tekrar yazınız"
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    disabled={isLoading}
                    className={alanSinifi}
                  />
                  {gozDugmesi(showConfirmPass, () => setShowConfirmPass(!showConfirmPass))}
                </div>
              </div>

              <Button
                type="submit"
                disabled={isLoading}
                className="w-full h-16 rounded-2xl font-black text-sm uppercase tracking-widest bg-slate-900 hover:bg-slate-800 text-white transition-all shadow-xl active:scale-98 mt-2"
              >
                {isLoading ? <Loader2 className="h-5 w-5 animate-spin" /> : "Şifreyi Güncelle"}
              </Button>

              <button
                type="button"
                onClick={() => navigate("/")}
                disabled={isLoading}
                className="tap-44 min-h-[44px] w-full flex items-center justify-center gap-2 text-[10px] font-black uppercase tracking-widest text-slate-400 hover:text-slate-700 transition-colors py-3 rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-500 focus-visible:ring-offset-2"
              >
                <ArrowLeft className="h-3.5 w-3.5" /> Vazgeç
              </button>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
