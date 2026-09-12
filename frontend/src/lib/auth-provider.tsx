import { useState, useEffect } from "react";
import type { ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import axios from "axios";
import type { User } from "./types";
import { authApi } from "./api";
import { toast } from "sonner";
import { AuthContext } from "./auth-context";

/**
 * AuthProvider: Uygulama genelinde kimlik doğrulama durumunu (state) yöneten sarmalayıcı bileşen.
 *
 * <p>K5 Kararı (IST-06 §1.1): Kişisel veriler (ad, e-posta) localStorage'a asla yazılmaz.
 * Oturum açılışta sunucudan /auth/me ile sorgulanır.</p>
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const navigate = useNavigate();

  // Sayfa ilk yüklendiğinde oturum durumunu sunucudan sorgula (K5)
  useEffect(() => {
    let isMounted = true;

    authApi.me()
      .then((response) => {
        if (isMounted && response.data?.success && response.data?.data) {
          setUser(response.data.data);
        }
      })
      .catch(() => {
        if (isMounted) {
          setUser(null);
        }
      })
      .finally(() => {
        if (isMounted) {
          setIsLoading(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, []);

  /**
   * login: E-posta ve şifre ile sisteme giriş yapar (T-009 / T-011).
   */
  const login = async (email: string, password: string, rememberMe?: boolean): Promise<User> => {
    try {
      const response = await authApi.login({ email, password, rememberMe });
      const loggedInUser = response.data.data;
      setUser(loggedInUser);
      navigate('/');
      toast.success(response.data.message || "Giriş başarılı.");
      return loggedInUser;
    } catch (error: unknown) {
      let message = "Giriş yapılamadı.";
      if (axios.isAxiosError(error)) {
        message = error.response?.data?.message || message;
      }
      toast.error(message);
      throw error;
    }
  };

  /**
   * changePassword: Zorunlu ilk şifre veya şifre güncelleme işlemini gerçekleştirir (T-009 / T-011 / T-015).
   */
  const changePassword = async (newPassword: string, currentPassword?: string): Promise<User> => {
    try {
      const response = await authApi.changePassword({ currentPassword, newPassword });
      const updatedUser = response.data.data;
      setUser(updatedUser);
      toast.success(response.data.message || "Şifreniz başarıyla değiştirildi.");
      return updatedUser;
    } catch (error: unknown) {
      let message = "Şifre değiştirilemedi.";
      if (axios.isAxiosError(error)) {
        message = error.response?.data?.message || message;
      }
      toast.error(message);
      throw error;
    }
  };

  /**
   * logout: Backend çerezlerini temizler ve yerel state'i sıfırlar.
   * T-060 / IST-01 §3.2: Çıkış yapıldığında her zaman dashboard/ana rota (/) rotasına geçilir.
   */
  const logout = async (): Promise<void> => {
    try {
      await authApi.logout();
    } catch (error) {
      console.error("Logout hatası:", error);
    } finally {
      setUser(null);
      navigate('/');
      toast.info("Oturum kapatıldı.");
    }
  };

  return (
    <AuthContext.Provider value={{
      user,
      isAuthenticated: !!user,
      isLoading,
      login,
      changePassword,
      logout,
      setUser
    }}>
      {children}
    </AuthContext.Provider>
  );
}
