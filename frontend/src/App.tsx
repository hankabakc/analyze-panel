import { AuthProvider } from './lib/auth-provider';
import { useAuth } from './lib/auth-context';
import { MustChangePasswordScreen } from './features/auth/components/MustChangePasswordScreen';
import { ChangePasswordScreen } from './features/auth/components/ChangePasswordScreen';
import { lazy, Suspense } from 'react';
import { PublicSite } from './features/site/PublicSite';
import HomePage from './features/site/pages/HomePage';
import FeaturesPage from './features/site/pages/FeaturesPage';
import AboutPage from './features/site/pages/AboutPage';
import FaqPage from './features/site/pages/FaqPage';
import ContactPage from './features/site/pages/ContactPage';
import PrivacyPage from './features/site/pages/PrivacyPage';
import LoginPage from './features/site/pages/LoginPage';
import NotFoundPage from './features/site/pages/NotFoundPage';
import { Toaster } from 'sonner';
import { confirmLeave } from './lib/unsavedChanges';
import { LogOut, LayoutDashboard, Settings, User, Trophy, FileBarChart2 } from 'lucide-react';
import { createBrowserRouter, RouterProvider, Routes, Route, Navigate, Outlet, useNavigate, useLocation } from 'react-router-dom';

// IST-03 §3: Panel sayfaları rota bazlı ayrı parçalara bölünür ve açıldıklarında yüklenir; karşılama sitesine
// gelen ziyaretçi panel kodunu (grafik kitaplığı dahil) indirmez (T-066).
const ManagerDashboard = lazy(() => import('./pages/ManagerDashboard'));
const TeacherDashboard = lazy(() => import('./pages/TeacherDashboard'));
const StudentDashboard = lazy(() => import('./pages/StudentDashboard'));
const AnalysisDetail = lazy(() => import('./pages/AnalysisDetail'));
const CumulativeAnalysis = lazy(() => import('./pages/CumulativeAnalysis'));
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
      <p className="text-[10px] font-black uppercase tracking-[0.4em] text-slate-400">Sistem Yükleniyor</p>
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

  return (
    <div className="min-h-screen bg-[#F8FAFC] flex flex-col">
      <nav className="h-16 sm:h-20 lg:h-24 bg-white/70 backdrop-blur-3xl border-b border-slate-100 flex items-center justify-between px-4 sm:px-8 lg:px-12 sticky top-0 z-50">
        <div className="flex items-center gap-3 sm:gap-6 lg:gap-16">
          <div className="flex items-center gap-2 sm:gap-3 cursor-pointer shrink-0" onClick={() => navigate('/')}>
             <div className="w-9 h-9 sm:w-10 sm:h-10 bg-slate-900 rounded-xl flex items-center justify-center text-white font-black text-lg sm:text-xl italic shrink-0">A</div>
             <h1 className="text-lg sm:text-xl lg:text-2xl font-black text-slate-900 tracking-tighter whitespace-nowrap">
                ANALİZ <span className="text-cyan-500 italic">SİSTEMİ</span>
             </h1>
          </div>
          <div className="hidden lg:flex items-center gap-10">
            {[
                { label: 'Dashboard', icon: LayoutDashboard, path: '/' },
                { label: 'Raporlar', icon: FileBarChart2, path: '/raporlar' },
                { label: 'Liderlik Tablosu', icon: Trophy, path: '/liderlik' },
                // T-018: Şifre değiştirme ekranı buradan açılır; nav paylaşıldığı için üç rolde de erişilebilir.
                { label: 'Ayarlar', icon: Settings, path: '/sifre-degistir' }
            ].map((item) => {
                const active = item.path !== undefined && location.pathname === item.path;
                return (
                  <button
                    key={item.label}
                    type="button"
                    onClick={() => { if (item.path) navigate(item.path); }}
                    aria-current={active ? 'page' : undefined}
                    className={`tap-44 flex items-center gap-2.5 font-black text-[10px] uppercase tracking-[0.2em] transition-all hover:text-cyan-600 rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-cyan-500 focus-visible:ring-offset-2 ${active ? 'text-cyan-600 border-b-2 border-cyan-500 pb-2' : 'text-slate-400 pb-2'}`}
                  >
                    <item.icon className="h-4 w-4" /> {item.label}
                  </button>
                );
            })}
          </div>
        </div>

        <div className="flex items-center gap-2 sm:gap-4 lg:gap-8 min-w-0">
          <div className="flex flex-col items-end min-w-0">
            <span className="text-xs sm:text-sm font-black text-slate-800 tracking-tight truncate max-w-[110px] sm:max-w-[160px] lg:max-w-none">{user?.fullName}</span>
            <span className="text-[8px] sm:text-[9px] font-black uppercase tracking-widest bg-slate-900 text-white px-2 py-0.5 rounded mt-0.5">{user?.role}</span>
          </div>
          <div className="hidden sm:flex h-10 w-10 sm:h-12 sm:w-12 rounded-2xl bg-slate-100 border-2 border-white shadow-xl items-center justify-center text-slate-500 group cursor-pointer hover:scale-110 transition-transform shrink-0">
             <User className="h-5 w-5 sm:h-6 sm:w-6 group-hover:text-cyan-600 transition-colors" />
          </div>
          <button onClick={handleLogout} aria-label="Çıkış Yap" className="tap-44 min-w-[44px] min-h-[44px] flex items-center justify-center p-2.5 sm:p-3 text-red-400 hover:text-red-600 hover:bg-red-50 rounded-2xl transition-all active:scale-90 shrink-0">
            <LogOut className="h-5 w-5 sm:h-6 sm:w-6" />
          </button>
        </div>
      </nav>

      <main className="flex-1 container mx-auto py-6 sm:py-10 lg:py-16 px-4 sm:px-8 lg:px-12 pb-24 lg:pb-16">
        <Suspense fallback={<PageLoading />}>
          <Outlet />
        </Suspense>
      </main>

      {/* Mobil Alt Gezinti Çubuğu (Bottom Navigation Bar - IST-02 §1.1 / T-048) */}
      <nav aria-label="Mobil Gezinme" className="lg:hidden fixed bottom-0 inset-x-0 bg-white/95 backdrop-blur-2xl border-t border-slate-200 z-50 px-2 py-1 flex items-center justify-around shadow-2xl safe-area-bottom">
        {[
            { label: 'Dashboard', icon: LayoutDashboard, path: '/' },
            { label: 'Raporlar', icon: FileBarChart2, path: '/raporlar' },
            { label: 'Liderlik Tablosu', icon: Trophy, path: '/liderlik' },
            { label: 'Ayarlar', icon: Settings, path: '/sifre-degistir' }
        ].map((item) => {
            const active = location.pathname === item.path;
            return (
              <button
                key={item.label}
                type="button"
                onClick={() => navigate(item.path)}
                aria-current={active ? 'page' : undefined}
                className={`tap-44 min-h-[44px] min-w-[44px] flex flex-col items-center justify-center gap-0.5 rounded-xl transition-all px-2 py-1 ${
                  active ? 'text-cyan-600 font-black' : 'text-slate-400 hover:text-slate-600 font-bold'
                }`}
              >
                <item.icon className={`h-5 w-5 ${active ? 'text-cyan-600 scale-110' : 'text-slate-400'} transition-transform`} />
                <span className="text-[8px] uppercase tracking-wider text-center leading-tight whitespace-nowrap">{item.label}</span>
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
      <Routes>
        <Route path="*" element={<MustChangePasswordScreen />} />
      </Routes>
    );
  }

  return (
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
            <Route path="/analysis/cumulative/:studentId" element={<CumulativeAnalysis />} />
          </Route>
        </>
      )}
    </Routes>
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
