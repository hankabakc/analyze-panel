import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "@/lib/auth-context";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { 
  KeyRound, Eye, EyeOff, 
  ArrowRight, Loader2, LogOut, Info, AlertTriangle 
} from "lucide-react";

/**
 * MustChangePasswordScreen: Kullanıcının ilk girişinde (mustChangePassword = true)
 * şifresini güvenli yeni bir şifreyle değiştirmesini zorunlu kılan güvenlik ekranıdır (T-009 / T-011).
 * 
 * <p>Mühendislik Standartları:</p>
 * <ul>
 *   <li>IST-01 §3.2 & IST-06 §1.1: Kullanıcı şifreyi değiştirmeden korumalı rotalara erişemez.</li>
 *   <li>ENG-11 §1.2: Yeni şifre en az 12 karakterden oluşmalıdır (NIST SP 800-63B / OWASP 2026).</li>
 * </ul>
 */
export function MustChangePasswordScreen() {
  const { user, changePassword, logout } = useAuth();
  const navigate = useNavigate();

  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  const [showNewPass, setShowNewPass] = useState(false);
  const [showConfirmPass, setShowConfirmPass] = useState(false);

  const [isLoading, setIsLoading] = useState(false);
  const [validationError, setValidationError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setValidationError(null);

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
      await changePassword(newPassword);
      // T-022: Zorunlu şifre değişikliği başarılı olduğunda kullanıcı adres çubuğundaki
      // önceki rotadan bağımsız olarak doğrudan ana sayfaya (Dashboard '/') yönlendirilir (IST-01 §3.2, ENG-04).
      navigate("/", { replace: true });
    } catch (err: unknown) {
      const errorObj = err as { response?: { data?: { message?: string } } };
      const serverMessage = errorObj.response?.data?.message;
      if (serverMessage) {
        setValidationError(serverMessage);
      }
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="auth-gradient min-h-screen w-full flex items-center justify-center p-4 relative overflow-hidden select-none">
      <div className="absolute top-[-10%] left-[-10%] w-[40%] h-[40%] bg-amber-500/5 rounded-full blur-[120px]"></div>
      <div className="absolute bottom-[-10%] right-[-10%] w-[40%] h-[40%] bg-cyan-500/5 rounded-full blur-[120px]"></div>

      <div className="w-full max-w-[480px] z-10 animate-in fade-in zoom-in-95 duration-500">
        <Card className="rounded-[3rem] border-0 shadow-[0_40px_80px_-15px_rgba(0,0,0,0.15)] overflow-hidden bg-white">
          <div className="h-2.5 bg-gradient-to-r from-amber-500 to-emerald-500"></div>

          <CardContent className="p-8 sm:p-12">
            {/* Header */}
            <div className="flex flex-col items-center text-center mb-6">
              <div className="w-20 h-20 bg-amber-50 rounded-[2rem] flex items-center justify-center mb-4 border border-amber-100/60 shadow-inner">
                <KeyRound className="h-9 w-9 text-amber-600" />
              </div>
              <h2 className="text-2xl sm:text-3xl font-black text-slate-900 tracking-tighter">
                Şifrenizi Belirleyin
              </h2>
              <p className="text-[11px] font-bold text-slate-400 uppercase tracking-widest mt-1">
                İlk Giriş Güvenlik Protokolü
              </p>
            </div>

            {/* Bilgilendirme Kutusu */}
            <div className="bg-amber-500/10 border border-amber-500/20 rounded-2xl p-4 mb-6 flex items-start gap-3 text-left">
              <Info className="h-5 w-5 text-amber-600 shrink-0 mt-0.5" />
              <p className="text-xs font-bold text-amber-950 leading-relaxed">
                Hesabınız yöneticiniz tarafından geçici bir şifre ile oluşturuldu. Güvenliğiniz için lütfen en az <strong>12 karakterlik</strong> yeni bir şifre belirleyiniz.
              </p>
            </div>

            {/* Hata Bildirimi */}
            {validationError && (
              <div className="bg-red-50 border border-red-200 rounded-2xl p-3.5 mb-6 flex items-center gap-2.5 text-left animate-in fade-in">
                <AlertTriangle className="h-4 w-4 text-red-600 shrink-0" />
                <p className="text-xs font-bold text-red-700">{validationError}</p>
              </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-5">
              {/* Yeni Şifre */}
              <div className="space-y-1.5 text-left">
                <div className="flex items-center justify-between ml-4">
                  <label className="text-[10px] font-black text-slate-400 uppercase tracking-widest">
                    Yeni Şifre *
                  </label>
                  <span className={`text-[10px] font-black ${newPassword.length >= 12 ? "text-emerald-600" : "text-slate-400"}`}>
                    {newPassword.length}/12 min
                  </span>
                </div>
                <div className="relative">
                  <Input
                    type={showNewPass ? "text" : "password"}
                    autoComplete="new-password"
                    placeholder="En az 12 karakterli yeni şifreniz"
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    disabled={isLoading}
                    className="h-14 rounded-2xl border-2 border-slate-50 bg-slate-50 focus:bg-white focus:border-slate-900 font-bold px-5 text-sm pr-12 transition-all"
                  />
                  <button
                    type="button"
                    onClick={() => setShowNewPass(!showNewPass)}
                    className="tap-44 min-w-[44px] min-h-[44px] flex items-center justify-center absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-700 transition-colors p-2.5"
                    tabIndex={-1}
                  >
                    {showNewPass ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
              </div>

              {/* Yeni Şifre Tekrar */}
              <div className="space-y-1.5 text-left">
                <label className="text-[10px] font-black text-slate-400 uppercase tracking-widest ml-4">
                  Yeni Şifre (Tekrar) *
                </label>
                <div className="relative">
                  <Input
                    type={showConfirmPass ? "text" : "password"}
                    autoComplete="new-password"
                    placeholder="Yeni şifrenizi tekrar yazınız"
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    disabled={isLoading}
                    className="h-14 rounded-2xl border-2 border-slate-50 bg-slate-50 focus:bg-white focus:border-slate-900 font-bold px-5 text-sm pr-12 transition-all"
                  />
                  <button
                    type="button"
                    onClick={() => setShowConfirmPass(!showConfirmPass)}
                    className="tap-44 min-w-[44px] min-h-[44px] flex items-center justify-center absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-700 transition-colors p-2.5"
                    tabIndex={-1}
                  >
                    {showConfirmPass ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
              </div>

              {/* Güncelle Butonu */}
              <Button
                type="submit"
                disabled={isLoading || newPassword.length < 12 || newPassword !== confirmPassword}
                className="w-full h-16 rounded-2xl font-black text-sm uppercase tracking-widest bg-slate-900 hover:bg-slate-800 text-white transition-all shadow-xl active:scale-98 mt-2"
              >
                {isLoading ? (
                  <Loader2 className="h-5 w-5 animate-spin" />
                ) : (
                  <>
                    Şifremi Güncelle ve Devam Et <ArrowRight className="ml-2 h-5 w-5" />
                  </>
                )}
              </Button>
            </form>

            {/* Oturumu Kapat Butonu */}
            <div className="mt-6 pt-4 border-t border-slate-100 flex items-center justify-between">
              <span className="text-[10px] font-bold text-slate-400 truncate max-w-[240px]">
                {user?.fullName} ({user?.email})
              </span>
              <button
                type="button"
                onClick={logout}
                disabled={isLoading}
                className="flex items-center gap-1.5 text-[10px] font-black uppercase tracking-widest text-red-500 hover:text-red-700 transition-colors"
              >
                <LogOut className="h-3.5 w-3.5" /> Çıkış Yap
              </button>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
