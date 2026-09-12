import { RankingBoard } from "@/features/analysis/components/RankingBoard";
import { Trophy } from "lucide-react";

/**
 * LeaderboardPage: Deneme sınavı sonuçlarına göre sınıf bazlı başarı sıralaması sayfası (T-047).
 *
 * <p>Sunucu taraflı maskeleme prensibi (APP-02 §1 / ENG-11 §3.3) gereği:
 * - Öğrenci yalnızca kendi sınıf grubunu ve maskelenmiş diğer öğrencileri görür.
 * - Öğretmen ve yönetici tüm sınıfları ve gerçek öğrenci adlarını görür.
 * Maskeleme ve yetki kararları RankingBoard bileşeni içinde sunucudan gelen veriye göre uygulanır.</p>
 */
export default function LeaderboardPage() {
  return (
    <div className="w-full space-y-8 animate-in fade-in duration-500 pb-20">
      <div className="flex flex-col gap-2">
        <h2 className="text-2xl sm:text-4xl font-black text-slate-900 tracking-tighter flex items-center gap-3">
          <Trophy className="h-7 w-7 sm:h-8 sm:w-8 text-amber-500" />
          Liderlik <span className="text-cyan-500 italic">Tablosu</span>
        </h2>
        <p className="text-[10px] font-black text-slate-400 uppercase tracking-[0.4em]">
          Onaylanmış Deneme Sınavları Başarı Sıralaması
        </p>
      </div>

      <div className="max-w-4xl">
        <RankingBoard />
      </div>
    </div>
  );
}
