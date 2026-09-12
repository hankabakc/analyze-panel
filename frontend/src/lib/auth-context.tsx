import { createContext, useContext } from "react";
import type { User } from "./types";

/**
 * AuthContextType: Kimlik doğrulama bağlamında sunulan tüm fonksiyon ve verilerin tipi.
 * T-056: Sistem yalnızca güvenli E-posta + Şifre modelini kullanır.
 */
export interface AuthContextType {
  user: User | null;         // Giriş yapmış kullanıcının bilgileri
  isAuthenticated: boolean;  // Oturumun açık olup olmadığı
  isLoading: boolean;        // İlk yükleme aşamasında mı?
  login: (email: string, password: string, rememberMe?: boolean) => Promise<User>;
  changePassword: (newPassword: string, currentPassword?: string) => Promise<User>;
  logout: () => Promise<void>;
  setUser: (user: User | null) => void;
}

/**
 * AuthContext: Sağlayıcı bileşen `auth-provider.tsx`'tedir. Bağlam ve kanca ayrı dosyada durur;
 * bileşen ile bileşen olmayan dışa aktarımlar aynı dosyada olunca hızlı yenileme (HMR) çalışmaz (T-064).
 */
export const AuthContext = createContext<AuthContextType | null>(null);

/**
 * useAuth: Context verilerine kolay erişim sağlayan özel hook.
 */
export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used within AuthProvider");
  return context;
};
