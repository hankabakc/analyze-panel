import { Link } from "react-router-dom";
import { ArrowLeft } from "lucide-react";
import { AuthController } from "@/features/auth/components/AuthForms";
import { useDocumentTitle } from "../useDocumentTitle";

/**
 * LoginPage: Giriş ekranı artık /giris adresinde (T-063B / S-026). Görünüm önceki giriş ekranıyla aynıdır;
 * yalnızca siteye dönüş bağlantısı eklendi.
 */
export default function LoginPage() {
  useDocumentTitle("Giriş");
  return (
    <div className="auth-gradient relative flex min-h-screen w-full items-center justify-center overflow-hidden p-4">
      <Link
        to="/"
        className="absolute left-4 top-4 z-20 inline-flex min-h-[44px] items-center gap-2 rounded-xl px-3 text-[11px] font-black uppercase tracking-[0.2em] text-slate-500 hover:text-slate-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-500 focus-visible:ring-offset-2"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden="true" />
        Siteye dön
      </Link>
      <div className="absolute left-[-10%] top-[-10%] h-[40%] w-[40%] rounded-full bg-cyan-500/5 blur-[120px]"></div>
      <div className="absolute bottom-[-10%] right-[-10%] h-[40%] w-[40%] rounded-full bg-blue-500/5 blur-[120px]"></div>
      <div className="z-10 flex w-full flex-col items-center gap-12 pt-12">
        <div className="animate-in fade-in slide-in-from-top-10 text-center duration-1000">
          <p className="flex flex-wrap items-center justify-center gap-2 text-3xl font-black tracking-tighter text-slate-900 sm:gap-3 sm:text-5xl lg:text-6xl">
            <span>ANALİZ</span>
            <span className="italic text-cyan-500">SİSTEMİ</span>
          </p>
        </div>
        <AuthController />
        <p className="text-[10px] font-black uppercase tracking-[0.4em] text-slate-300">Analysis Platform &bull; Core System</p>
      </div>
    </div>
  );
}
