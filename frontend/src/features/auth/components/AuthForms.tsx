import { useState } from "react";
import { useAuth } from "@/lib/auth-context";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent } from "@/components/ui/card";
import { ArrowRight, Loader2, Mail, KeyRound, Eye, EyeOff } from "lucide-react";

/**
 * AuthController: Yalnızca e-posta + şifre ile çalışan giriş bileşenidir (T-056).
 *
 * <p>Tasarım ve Güvenlik Standartları:</p>
 * <ul>
 *   <li>T-056 / K8: 375x812 mobil ekranlarda 0 px yatay taşma ve 44 px dokunma alanı (tap-44 / h-16) garantilenmiştir.</li>
 *   <li>T-007 / K6: Beni Hatırla (30 gün) oturum kalıcılık seçeneği mevcuttur.</li>
 * </ul>
 */
export function AuthController() {
  const { login } = useAuth();

  // E-Posta ile Giriş Form State'leri
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  /**
   * handleEmailLogin: E-posta ve şifre ile oturum açma isteğini tetikler.
   */
  const handleEmailLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email.trim() || !password) return;

    setIsLoading(true);
    try {
      await login(email.trim(), password, rememberMe);
    } catch {
      // Hata mesajı auth-context tarafından toast ile kullanıcıya gösterilir
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="w-full flex justify-center px-4">
      <div className="w-full max-w-[440px] animate-in fade-in slide-in-from-bottom-6 duration-500 select-none">
        <Card className="rounded-[3rem] border-0 shadow-[0_40px_80px_-15px_rgba(0,0,0,0.12)] overflow-hidden bg-white cursor-default">
          <div className="h-2 bg-slate-900"></div>
          <CardContent className="p-8 sm:p-12 text-center">
            {/* Logo / İkon */}
            <div className="w-20 h-20 bg-slate-50 rounded-[2rem] flex items-center justify-center mx-auto mb-5 border border-slate-100/50 shadow-inner">
              <KeyRound className="h-9 w-9 text-slate-800" />
            </div>

            <h2 className="text-3xl font-black text-slate-900 tracking-tighter">Giriş Yap</h2>
            <p className="text-[11px] font-bold text-slate-400 uppercase tracking-widest mt-2 mb-8">
              Kurumsal hesabınıza e-posta ve şifrenizle erişin
            </p>

            <form onSubmit={handleEmailLogin} className="space-y-5">
              {/* E-posta Adresi */}
              <div className="space-y-1.5 text-left">
                <label className="text-[10px] font-black text-slate-400 uppercase tracking-widest ml-4">
                  E-Posta Adresi
                </label>
                <div className="relative">
                  <Mail className="absolute left-5 top-1/2 -translate-y-1/2 h-5 w-5 text-slate-300 pointer-events-none" />
                  <Input 
                    type="email"
                    autoComplete="username"
                    placeholder="ad.soyad@analyzepanel.local" 
                    value={email} 
                    onChange={(e) => setEmail(e.target.value)} 
                    disabled={isLoading}
                    className="h-16 pl-14 rounded-2xl border-2 border-slate-50 bg-slate-50 focus:bg-white focus:border-slate-900 font-bold transition-all text-sm"
                  />
                </div>
              </div>

              {/* Şifre */}
              <div className="space-y-1.5 text-left">
                <label className="text-[10px] font-black text-slate-400 uppercase tracking-widest ml-4">
                  Şifre
                </label>
                <div className="relative">
                  <KeyRound className="absolute left-5 top-1/2 -translate-y-1/2 h-5 w-5 text-slate-300 pointer-events-none" />
                  <Input 
                    type={showPassword ? "text" : "password"}
                    autoComplete="current-password"
                    placeholder="••••••••••••" 
                    value={password} 
                    onChange={(e) => setPassword(e.target.value)} 
                    disabled={isLoading}
                    className="h-16 pl-14 pr-12 rounded-2xl border-2 border-slate-50 bg-slate-50 focus:bg-white focus:border-slate-900 font-bold transition-all text-base"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute right-2 top-1/2 -translate-y-1/2 h-11 w-11 flex items-center justify-center text-slate-400 hover:text-slate-700 transition-colors"
                    tabIndex={-1}
                    aria-label={showPassword ? "Şifreyi gizle" : "Şifreyi göster"}
                  >
                    {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
              </div>

              {/* Beni Hatırla (30 gün) */}
              <label htmlFor="rememberMe" className="flex items-start space-x-3 text-left p-3.5 rounded-2xl bg-slate-50 border border-slate-100/80 cursor-pointer">
                <input
                  id="rememberMe"
                  type="checkbox"
                  checked={rememberMe}
                  onChange={(e) => setRememberMe(e.target.checked)}
                  disabled={isLoading}
                  className="mt-0.5 h-4 w-4 rounded border-slate-300 text-slate-900 focus:ring-slate-900 cursor-pointer"
                />
                <span className="text-xs text-slate-600 select-none">
                  <span className="font-bold text-slate-800">Beni hatırla (30 gün)</span>
                  <p className="text-[10px] text-slate-400 mt-0.5 leading-tight">
                    Ortak kullanılan veya halka açık cihazlarda işaretlemeyiniz.
                  </p>
                </span>
              </label>

              {/* Giriş Yap Butonu */}
              <Button 
                type="submit" 
                disabled={isLoading || !email.trim() || !password}
                className="w-full h-16 rounded-2xl font-black text-sm uppercase tracking-widest bg-slate-900 hover:bg-slate-800 text-white transition-all shadow-lg active:scale-98"
              >
                {isLoading ? (
                  <Loader2 className="h-5 w-5 animate-spin" />
                ) : (
                  <>
                    Giriş Yap <ArrowRight className="ml-2 h-5 w-5" />
                  </>
                )}
              </Button>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
