import { AuthProvider } from './lib/auth-provider';
import { useAuth } from './lib/auth-context';
import { lazy, Suspense } from 'react';
import { PublicSite } from './features/site/PublicSite';
import HomePage from './features/site/pages/HomePage';
import LoginPage from './features/site/pages/LoginPage';
import { Toaster } from 'sonner';
import { confirmLeave } from './lib/unsavedChanges';
import { LogOut, LayoutDashboard, Settings, User, Trophy, FileBarChart2 } from "@/components/icons";
import { Logo } from "@/components/Logo";

const ROLE_LABELS: Record<string, string> = { MANAGER: "Yönetici", TEACHER: "Öğretmen", STUDENT: "Öğrenci" };
import { createBrowserRouter, RouterProvider, Routes, Route, Navigate, Outlet, useNavigate, useLocation } from 'react-router-dom';

// IST-03 §3: Panel sayfaları rota bazlı ayrı parçalara bölünür ve açıldıklarında yüklenir; karşılama sitesine
// gelen ziyaretçi panel kodunu (grafik kitaplığı dahil) indirmez (T-066).
// İlk açılışta gerekmeyen sayfalar ayrı paketlerde: Phosphor ikonları ana paketi 600 kB sınırının üstüne çıkarıyordu.
const FeaturesPage = lazy(() => import('./features/site/pages/FeaturesPage'));
const AboutPage = lazy(() => import('./features/site/pages/AboutPage'));
const FaqPage = lazy(() => import('./features/site/pages/FaqPage'));
const ContactPage = lazy(() => import('./features/site/pages/ContactPage'));
const PrivacyPage = lazy(() => import('./features/site/pages/PrivacyPage'));
const NotFoundPage = lazy(() => import('./features/site/pages/NotFoundPage'));
const MustChangePasswordScreen = lazy(() => import('./features/auth/components/MustChangePasswordScreen').then((m) => ({ default: m.MustChangePasswordScreen })));
const ChangePasswordScreen = lazy(() => import('./features/auth/components/ChangePasswordScreen').then((m) => ({ default: m.ChangePasswordScreen })));
const ManagerDashboard = lazy(() => import('./pages/ManagerDashboard'));
const TeacherDashboard = lazy(() => import('./pages/TeacherDashboard'));
const StudentDashboard = lazy(() => import('./pages/StudentDashboard'));
const AnalysisDetail = lazy(() => import('./pages/AnalysisDetail'));
const LeaderboardPage = lazy(() => import('./pages/LeaderboardPage'));
const ReportsPage = lazy(() => import('./pages/ReportsPage'));

/**
 * Oturum gerektiren panel adresleri. Oturumsuz ziyaretçi bunlara girerse giriş ekranına yönlenir (S-026).
 */
const PANEL_PATHS = ['/raporlar', '/liderlik', '/sifre-degistir', '/analysis/*'];

/**
 * RoleBasedDashboard: Kullanıcının rolüne göre ana sayfasını belirler.
 */
function RoleBasedDashboard() {
  const { user } = useAuth();

  switch (user?.role) {
    case 'MANAGER': return <ManagerDashboard />;
    case 'TEACHER': return <TeacherDashboard />;
    case 'STUDENT': return <StudentDashboard />;
    default: return <Navigate to="/" />;
  }
}

function LoadingScreen() {
  return (
    <div className="h-screen w-full flex flex-col items-center justify-center gap-6 bg-[#F8FAFC]">
      <div className="w-12 h-12 border-[6px] border-cyan-500 border-t-transparent rounded-full animate-spin"></div>
      <p className="text-sm text-slate-500">Sistem Yükleniyor</p>
    </div>
  );
}

/** PageLoading: Panel sayfasının kod parçası yüklenirken içerik alanında gösterilir (T-066). */
function PageLoading() {
  return (
    <div role="status" className="flex justify-center py-32">
      <div className="w-10 h-10 border-[5px] border-cyan-500 border-t-transparent rounded-full animate-spin" aria-hidden="true"></div>
      <span className="sr-only">Sayfa yükleniyor</span>
    </div>
  );
}

/**
 * AppShell: Giriş yapmış kullanıcının üst menüsü, içerik alanı ve mobil alt menüsü.
 */
function AppShell() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  // Çıkış adres değiştirmeden paneli kapatır; kaydedilmemiş değişiklik varsa önce sorulur (IST-02 §4.3, T-063C).
  const handleLogout = () => {
    if (confirmLeave()) logout();
  };

  const navItems = [
    { label: "Panel", icon: LayoutDashboard, path: "/" },
    { label: "Raporlar", icon: FileBarChart2, path: "/raporlar" },
    { label: "Sıralama", icon: Trophy, path: "/liderlik" },
    // T-018: Şifre değiştirme ekranı buradan açılır; nav paylaşıldığı için üç rolde de erişilebilir.
    { label: "Ayarlar", icon: Settings, path: "/sifre-degistir" },
  ];

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col">
      <nav className="h-14 bg-white border-b border-slate-200 flex items-center justify-between px-4 sm:px-6 lg:px-10 sticky top-0 z-50">
        <div className="flex items-center gap-6 lg:gap-10 min-w-0">
          <button type="button" className="shrink-0 rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-500" onClick={() => navigate("/")} aria-label="Panele dön">
            <Logo />
          </button>
          <div className="hidden lg:flex items-center gap-1 h-14">
            {navItems.map((item) => {
              const active = location.pathname === item.path;
              return (
                <button
                  key={item.label}
                  type="button"
                  onClick={() => navigate(item.path)}
                  aria-current={active ? "page" : undefined}
                  className={`h-14 px-3 text-sm border-b-2 transition-colors focus-visible:outline-none focus-visible:bg-slate-100 ${active ? "border-slate-900 text-slate-900 font-medium" : "border-transparent text-slate-500 hover:text-slate-900"}`}
                >
                  {item.label}
                </button>
              );
            })}
          </div>
        </div>

        <div className="flex items-center gap-3 sm:gap-4 min-w-0">
          <div className="flex items-center gap-2 min-w-0">
            <User className="hidden sm:block h-4 w-4 text-slate-400 shrink-0" />
            <span className="text-sm text-slate-700 truncate max-w-[120px] sm:max-w-[200px]">{user?.fullName}</span>
            <span className="hidden sm:inline text-xs text-slate-500 border border-slate-200 rounded px-1.5 py-0.5">{ROLE_LABELS[user?.role ?? ""] ?? user?.role}</span>
          </div>
          <button onClick={handleLogout} aria-label="Çıkış Yap" title="Çıkış yap" className="tap-44 min-w-[44px] min-h-[44px] flex items-center justify-center text-slate-500 hover:text-slate-900 rounded-md transition-colors shrink-0">
            <LogOut className="h-5 w-5" />
          </button>
        </div>
      </nav>

      <main className="flex-1 container mx-auto py-6 sm:py-10 lg:py-16 px-4 sm:px-8 lg:px-12 pb-24 lg:pb-16">
        <Suspense fallback={<PageLoading />}>
          <Outlet />
        </Suspense>
      </main>

      {/* Mobil Alt Gezinti Çubuğu (Bottom Navigation Bar - IST-02 §1.1 / T-048) */}
      <nav aria-label="Mobil Gezinme" className="lg:hidden fixed bottom-0 inset-x-0 bg-white/95 border-t border-slate-200 z-50 px-2 py-1 flex items-center justify-around shadow-sm safe-area-bottom">
        {navItems.map((item) => {
            const active = location.pathname === item.path;
            return (
              <button
                key={item.label}
                type="button"
                onClick={() => navigate(item.path)}
                aria-current={active ? 'page' : undefined}
                className={`tap-44 min-h-[44px] min-w-[44px] flex flex-col items-center justify-center gap-0.5 rounded-xl transition-all px-2 py-1 ${
                  active ? 'text-slate-900 font-medium' : 'text-slate-400 hover:text-slate-600 font-medium'
                }`}
              >
                <item.icon className={`h-5 w-5 ${active ? 'text-slate-900' : 'text-slate-400'} transition-transform`} />
                <span className="text-xs tracking-normal text-center leading-tight whitespace-nowrap">{item.label}</span>
              </button>
            );
        })}
      </nav>
    </div>
  );
}

/**
 * AppRoutes: Uygulamanın yönlendirme merkezi (T-063B / S-026).
 *
 * - Bilgi sayfaları (/karsilama, /ozellikler, /hakkinda, /sss, /iletisim, /gizlilik) herkese açıktır.
 * - Oturumsuz ziyaretçi / adresinde karşılama sayfasını, /giris adresinde giriş formunu görür;
 *   panel adreslerine girerse girişe, bilinmeyen adreste karşılamaya yönlenir.
 * - Giriş yapmış kullanıcı / adresinde kendi panelini görür (T-060 kararı korunur).
 * - İlk girişte şifre değiştirmek zorunda olan kullanıcı panel yerine şifre ekranını görür (IST-01 §3.2).
 */
export function AppRoutes() {
  const { user, isAuthenticated, isLoading } = useAuth();

  if (isLoading) return <LoadingScreen />;

  const mustChangePassword = isAuthenticated && !!user?.mustChangePassword;

  if (mustChangePassword) {
    return (
      <Suspense fallback={<LoadingScreen />}>
        <Routes>
          <Route path="*" element={<MustChangePasswordScreen />} />
        </Routes>
      </Suspense>
    );
  }

  return (
    <Suspense fallback={<LoadingScreen />}>
    <Routes>
      <Route element={<PublicSite />}>
        <Route path="/karsilama" element={<HomePage />} />
        <Route path="/ozellikler" element={<FeaturesPage />} />
        <Route path="/hakkinda" element={<AboutPage />} />
        <Route path="/sss" element={<FaqPage />} />
        <Route path="/iletisim" element={<ContactPage />} />
        <Route path="/gizlilik" element={<PrivacyPage />} />
        {!isAuthenticated && <Route path="/" element={<HomePage />} />}
        <Route path="*" element={<NotFoundPage />} />
      </Route>

      {!isAuthenticated && (
        <>
          <Route path="/giris" element={<LoginPage />} />
          {PANEL_PATHS.map((path) => (
            <Route key={path} path={path} element={<Navigate to="/giris" replace />} />
          ))}
        </>
      )}

      {isAuthenticated && (
        <>
          <Route path="/giris" element={<Navigate to="/" replace />} />
          <Route element={<AppShell />}>
            <Route path="/" element={<RoleBasedDashboard />} />
            <Route path="/liderlik" element={<LeaderboardPage />} />
            <Route path="/raporlar" element={<ReportsPage />} />
            <Route path="/sifre-degistir" element={<ChangePasswordScreen />} />
            <Route path="/analysis/:reportId" element={<AnalysisDetail />} />
            <Route path="/analysis/cumulative/:studentId" element={<AnalysisDetail />} />
          </Route>
        </>
      )}
    </Routes>
    </Suspense>
  );
}

/**
 * Veri yönlendiricisi: kaydedilmemiş değişiklik engeli (`useBlocker`, IST-02 §4.3) yalnızca bununla çalışır.
 * Rotaların kendisi AppRoutes içinde kalır (T-063C).
 */
const router = createBrowserRouter([
  {
    path: '*',
    element: (
      <AuthProvider>
        <AppRoutes />
        <Toaster position="top-right" richColors theme="light" />
      </AuthProvider>
    ),
  },
]);

function App() {
  return <RouterProvider router={router} />;
}

export default App;
