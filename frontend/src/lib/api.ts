import axios from 'axios';
import type { InternalAxiosRequestConfig } from 'axios';
import type { Activity, AnalysisData, AnalysisReportSummary, ApiResponse, ClassRankingGroup, SchoolClass, ChangePasswordPayload, CreateUserPayload, CreatedUserResponse, LoginPayload, Pairing, ReferenceSchool, ResetPasswordResponse, StudentProfile, User } from './types';

/**
 * api: Uygulama genelinde backend ile iletişim kurmak için kullanılan Axios instance'ı.
 * OWASP 2026: withCredentials zorunludur, böylece HttpOnly çerezler otomatik iletilir.
 */
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN', // Spring Boot'un gönderdiği çerez adı
  xsrfHeaderName: 'X-XSRF-TOKEN', // Axios'un göndereceği header adı
  // Axios 1.6.2'den beri XSRF başlığını kendiliğinden eklemiyor; açıkça istemek
  // gerekiyor. Bu satır olmadan CSRF korumalı bütün POST/PUT/DELETE istekleri
  // 403 döner (PDF yükleme dahil).
  withXSRFToken: true,
});

/**
 * refreshClient: Token yenileme isteği için ayrı istemci.
 *
 * Ayrı olmasının iki sebebi var: (1) interceptor'a takılmadığı için yenileme isteği
 * kendisi 401 dönse bile sonsuz döngü oluşmuyor, (2) CSRF başlığını kendisi ekliyor -
 * refresh ucu CSRF muafiyet listesinde değil, çıplak axios ile çağrıldığında 403 dönerdi.
 */
const refreshClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  withXSRFToken: true,
});

/**
 * csrfBasligiEkle: XSRF-TOKEN çerezini okuyup X-XSRF-TOKEN başlığına koyar.
 *
 * Axios'un kendi xsrf desteği (withXSRFToken) sürümden sürüme ve isteğin aynı
 * origin sayılıp sayılmamasına göre değişiyor; sessizce başlık eklemediğinde
 * CSRF korumalı her istek 403 dönüyor ve sebebi hiçbir yerde görünmüyor.
 * Bu yüzden başlık burada açıkça ekleniyor - davranış artık tahmine bağlı değil.
 *
 * Okuma istekleri (GET/HEAD/OPTIONS) CSRF token'ı gerektirmez.
 */
const csrfBasligiEkle = (config: InternalAxiosRequestConfig) => {
  const method = (config.method ?? 'get').toLowerCase();
  if (method === 'get' || method === 'head' || method === 'options') return config;

  let token = document.cookie
    .split('; ')
    .find((c) => c.startsWith('XSRF-TOKEN='))
    ?.split('=')[1];

  if (!token) {
    // Sunucu her yanıtta CSRF çerezini önce siliyor sonra yenisini yazıyor.
    // Eşzamanlı istekler (yükleme sürerken devam eden rapor sorgulaması gibi)
    // bu iki adımın arasına denk geldiğinde çerez bir an ortadan kalkıyor ve
    // istek 403 dönüyordu. Sunucu tarafı durumsuz "double submit" doğrulaması
    // yaptığı - yani sadece çerez ile başlığın eşit olmasına baktığı - için
    // istemci eksik çerezi kendisi üretebilir. Güvenlik korunur: başka bir
    // origin bu alan adına çerez yazamaz, mevcut çerezi de okuyamaz.
    token = crypto.randomUUID();
    document.cookie = `XSRF-TOKEN=${token}; path=/; SameSite=Lax`;
  }

  config.headers.set('X-XSRF-TOKEN', decodeURIComponent(token));
  return config;
};

api.interceptors.request.use(csrfBasligiEkle);
refreshClient.interceptors.request.use(csrfBasligiEkle);

/**
 * Response Interceptor: Backend'den gelen cevapları izler. 
 * Eğer 401 (Unauthorized) hatası alınırsa, sessizce token yenilemeyi (refresh) dener.
 * /auth/me açılış sorgusu 401 aldığında döngüye girmemesi için refresh denenmez.
 */
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    // Bu uçlarda 401 "kimlik/oturum yok veya kod hatalı" demektir, "erişim jetonu tazelenmeli" demek değildir.
    // /auth/password/change bu listede yer almaz çünkü yanlış şifrede 400 döner; 401 alıyorsa jeton tazelenmelidir (T-034A).
    const isAuthEndpoint = originalRequest?.url?.includes('/auth/login') ||
                           originalRequest?.url?.includes('/auth/me') ||
                           originalRequest?.url?.includes('/auth/refresh') ||
                           originalRequest?.url?.includes('/auth/logout');

    if (error.response?.status === 401 && !originalRequest?._retry && !isAuthEndpoint) {
      originalRequest._retry = true;
      try {
        await refreshClient.post('/auth/refresh', {});
        return api(originalRequest);
      } catch (refreshError) {
        // Oturum tamamen bitmişse yönlendirme yapılabilir
        window.location.href = '/';
        return Promise.reject(refreshError);
      }
    }

    if (error.response?.status === 403) {
      console.error("Yetkisiz Erişim Hatası (403):", error.config?.url);
    }

    return Promise.reject(error);
  }
);

/**
 * authApi: Kimlik doğrulama süreçlerine ait HTTP isteklerini yöneten nesne.
 * T-056: Sistem yalnızca güvenli E-posta + Şifre modelini kullanır.
 */
export const authApi = {
  // E-posta ve şifre ile oturum açar (T-009 / T-011 / T-056)
  login: (payload: LoginPayload) =>
    api.post<ApiResponse<User>>('/auth/login', payload),

  // Zorunlu ilk şifre veya şifre güncelleme (T-009 / T-011 / T-056)
  changePassword: (payload: ChangePasswordPayload) =>
    api.post<ApiResponse<User>>('/auth/password/change', payload),

  // Oturumdaki kullanıcının profil bilgilerini sorgular
  me: () => 
    api.get<ApiResponse<User>>('/auth/me'),

  // Oturumu kapatır ve çerezleri temizler
  logout: () => 
    api.post<ApiResponse<void>>('/auth/logout'),
};

/**
 * membershipApi: Üyelik yönetimi, kullanıcı onayları ve hiyerarşik eşleşme 
 * işlemlerini yöneten API nesnesidir. Sadece MANAGER rolü için yetkilendirilmiştir.
 */
export const membershipApi = {
  // Yeni kullanıcı (öğretmen veya öğrenci) oluşturur (T-010)
  createUser: (payload: CreateUserPayload) =>
    api.post<ApiResponse<CreatedUserResponse>>('/membership/users', payload),

  // Sistemdeki tüm aktif (onaylanmış) öğretmenleri getirir
  getTeachers: () => 
    api.get<ApiResponse<User[]>>('/membership/teachers'),

  // Sistemdeki tüm aktif (onaylanmış) öğrencileri getirir
  getStudents: () => 
    api.get<ApiResponse<User[]>>('/membership/students'),

  // Sistemde henüz hiçbir eğitmenle eşleştirilmemiş aktif öğrencileri getirir (T-021)
  getUnpairedStudents: () => 
    api.get<ApiResponse<User[]>>('/membership/unpaired-students'),

  // Sistemdeki tüm aktif öğretmen-öğrenci eşleşmelerini getirir (T-036)
  // T-040: Sınıf yönetimi (yalnızca yönetici)
  getClasses: () =>
    api.get<ApiResponse<SchoolClass[]>>('/membership/classes'),

  createClass: (name: string) =>
    api.post<ApiResponse<SchoolClass>>('/membership/classes', { name }),

  deleteClass: (classId: string) =>
    api.delete<ApiResponse<void>>(`/membership/classes/${classId}`),

  addStudentToClass: (classId: string, studentId: string) =>
    api.post<ApiResponse<void>>(`/membership/classes/${classId}/students`, { studentId }),

  removeStudentFromClass: (classId: string, studentId: string) =>
    api.delete<ApiResponse<void>>(`/membership/classes/${classId}/students/${studentId}`),

  // T-037: Yönetici aktiflik özeti (son giriş + rapor görüntüleme)
  getActivity: () =>
    api.get<ApiResponse<Activity[]>>('/membership/activity'),

  getPairings: () => 
    api.get<ApiResponse<Pairing[]>>('/membership/pairings'),

  // Bir öğrenciyi bir öğretmenle eşleştirir
  pairStudentTeacher: (studentId: string, teacherId: string) => 
    api.post<ApiResponse<void>>('/membership/pair', { studentId, teacherId }),

  // Bir öğrenci ile öğretmen arasındaki eşleşmeyi sonlandırır (T-036)
  unpairStudentTeacher: (studentId: string, teacherId: string) => 
    api.post<ApiResponse<void>>('/membership/unpair', { studentId, teacherId }),

  // Giriş yapan öğretmenin kendi öğrencilerini getirir
  getMyStudents: () => 
    api.get<ApiResponse<User[]>>('/membership/my-students'),

  // Giriş yapan öğrencinin kendi öğretmenlerini getirir
  getMyTeachers: () => 
    api.get<ApiResponse<User[]>>('/membership/my-teachers'),

  // Bir kullanıcının şifresini sıfırlar ve yeni geçici şifre üretir (T-013)
  resetUserPassword: (userId: string) => 
    api.post<ApiResponse<ResetPasswordResponse>>(`/membership/users/${userId}/password/reset`),

  // Sistemdeki aktif yönetici sayısını getirir (T-075)
  getManagerCount: () =>
    api.get<ApiResponse<number>>('/membership/managers/count'),
};

/**
 * analysisApi: PDF yükleme, AI analiz raporlarını listeleme ve 
 * rapor detaylarını (grafik verileri dahil) getiren API nesnesidir.
 */
export const analysisApi = {
  // T-038B: Denemelere göre sınıf bazlı sıralama. Maskeleme sunucuda yapılır; istemci yalnızca gösterir.
  getRanking: () =>
    api.get<ApiResponse<ClassRankingGroup[]>>('/analysis/ranking'),

  // PDF dosyasını yükler ve analiz sürecini başlatır
  uploadPdf: (studentId: string, examCount: number, reportType: string, file: File) => {
    const formData = new FormData();
    formData.append('studentId', studentId);
    formData.append('examCount', examCount.toString());
    formData.append('reportType', reportType);
    formData.append('file', file);
    return api.post<ApiResponse<string>>('/analysis/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });
  },

  // Bir öğrenciye ait tüm analiz raporlarını listeler
  getStudentReports: (studentId: string) => 
    api.get<ApiResponse<AnalysisReportSummary[]>>(`/analysis/reports/${studentId}`),

  // Seçilen bir raporun tüm grafik ve AI detaylarını getirir
  getAnalysisDetail: (reportId: string, cumulative: boolean = false) => 
    api.get<ApiResponse<AnalysisData>>(`/analysis/details/${reportId}?cumulative=${cumulative}`),

  // Bekleyen analizi onaylar ve öğrenciye gönderir
  approveReport: (reportId: string) => 
    api.post<ApiResponse<void>>(`/analysis/approve/${reportId}`),

  // Bekleyen analizi reddeder
  rejectReport: (reportId: string) => 
    api.post<ApiResponse<void>>(`/analysis/reject/${reportId}`),

  // Raporu tamamen siler
  deleteReport: (reportId: string) => 
    api.delete<ApiResponse<void>>(`/analysis/${reportId}`),

  // OKUL VE PROFİL İŞLEMLERİ
  searchSchools: (query: string) => 
    api.get<ApiResponse<ReferenceSchool[]>>('/analysis/schools/search', { params: { query } }),

  getProfile: () => 
    api.get<ApiResponse<StudentProfile>>('/analysis/profile'),

  updateProfile: (schoolId?: string, manualScore?: number) => {
    const params = new URLSearchParams();
    if (schoolId) params.append('schoolId', schoolId);
    if (manualScore) params.append('manualScore', manualScore.toString());
    return api.post<ApiResponse<void>>(`/analysis/profile?${params.toString()}`);
  },

  getGlobalSummary: (studentId: string) => 
    api.post<ApiResponse<string>>(`/analysis/global-summary/${studentId}`),

  getCumulativeProfile: (studentId: string) => 
    api.get<ApiResponse<AnalysisData>>(`/analysis/profile/cumulative/${studentId}`),

  mergeCumulative: (studentId: string) => 
    api.post<ApiResponse<string>>(`/analysis/merge-cumulative/${studentId}`)
};

/**
 * Çalışma Planı (Study Plan) Tipleri ve API Nesnesi (T-050A, T-050B)
 */
export interface CreateStudyPlanItemPayload {
  topicName: string;
  questionCount: number;
}

export interface CreateStudyPlanPayload {
  studentId: string;
  dueDate: string; // YYYY-MM-DD
  items: CreateStudyPlanItemPayload[];
}

export interface StudyPlanItemDto {
  id: string;
  planId: string;
  topicName: string;
  questionCount: number;
  completedAt: string | null;
  teacherSeenAt?: string | null;
}

export interface StudyPlanDto {
  id: string;
  studentId: string;
  teacherId: string;
  dueDate: string;
  createdAt: string;
  items: StudyPlanItemDto[];
}

export const studyPlansApi = {
  createPlan: (payload: CreateStudyPlanPayload) =>
    api.post<ApiResponse<StudyPlanDto>>('/study-plans', payload),

  getPlansForStudent: (studentId: string) =>
    api.get<ApiResponse<StudyPlanDto[]>>(`/study-plans/student/${studentId}`),

  completeItem: (itemId: string) =>
    api.post<ApiResponse<StudyPlanItemDto>>(`/study-plans/items/${itemId}/complete`),

  markPlanAsSeen: (planId: string) =>
    api.post<ApiResponse<void>>(`/study-plans/${planId}/seen`),

  getUnseenCounts: () =>
    api.get<ApiResponse<Record<string, number>>>('/study-plans/unseen-count'),
};

/**
 * Psikolojik Testler (STAI vb.) Tipleri ve API Nesnesi (T-052A, T-052B)
 */
export interface AssignPsychTestPayload {
  testCode: string;
  studentIds: string[];
}

export interface AssignPsychTestResult {
  assignedStudentIds: string[];
  skippedStudentIds: string[];
  message: string;
}

export interface PsychTestResultDto {
  id: string;
  studentId: string;
  studentName: string;
  testCode: string;
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED';
  assignedAt: string;
  completedAt: string | null;
  stateScore?: number | null;
  traitScore?: number | null;
  stateLevel?: 'LOW' | 'MEDIUM' | 'HIGH' | null;
  traitLevel?: 'LOW' | 'MEDIUM' | 'HIGH' | null;
  bourdonTotalCorrect?: number | null;
  bourdonTotalOmitted?: number | null;
  bourdonTotalIncorrect?: number | null;
  bourdonDurationSeconds?: number | null;
  bourdonTimedOut?: boolean | null;
}

export interface BourdonBlockScoreDto {
  blockNumber: number;
  correct: number;
  omitted: number;
  incorrect: number;
  targetCount: number;
}

export interface BourdonResultDto {
  assignmentId: string;
  studentName: string;
  startedAt: string;
  submittedAt: string;
  durationSeconds: number;
  timedOut: boolean;
  block1: BourdonBlockScoreDto;
  block2: BourdonBlockScoreDto;
  block3: BourdonBlockScoreDto;
  totalCorrect: number;
  totalOmitted: number;
  totalIncorrect: number;
  totalTargets: number;
  observationNote: string;
}

export interface MyPsychTestAssignmentDto {
  id: string;
  testCode: string;
  testTitle: string;
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED';
  assignedAt: string;
  completedAt: string | null;
}

export interface PsychTestOptionDto {
  value: number;
  label: string;
}

export interface PsychTestItemDto {
  itemNo: number;
  text: string;
  section: string;
  options: PsychTestOptionDto[];
}

export interface PsychTestScaleInfoDto {
  testCode: string;
  title: string;
  description: string;
  items: PsychTestItemDto[];
}

export interface PsychTestAnswerItemDto {
  itemNo: number;
  answer: number;
}

export interface SubmitPsychTestPayload {
  answers: PsychTestAnswerItemDto[];
}

export interface PsychTestSubmitResponseDto {
  id: string;
  status: string;
  completedAt: string;
  message: string;
}

export interface BourdonStartResponseDto {
  assignmentId: string;
  testCode: string;
  testTitle: string;
  startedAt: string;
  durationSeconds: number;
  remainingSeconds?: number;
  grid: string[];
  totalRows: number;
  totalColumns: number;
}

export interface BourdonMarkedCellDto {
  row: number;
  col: number;
}

export interface BourdonSubmissionPayload {
  markedCells: BourdonMarkedCellDto[];
}

export interface UnseenPsychTestCountsDto {
  totalUnseen: number;
  studentCounts: Record<string, number>;
}

export const psychTestsApi = {
  // Yöneticinin öğrencilere psikolojik test atamasını sağlar (T-052A / T-052B)
  assignTest: (payload: AssignPsychTestPayload) =>
    api.post<ApiResponse<AssignPsychTestResult>>('/psych-tests/assignments', payload),

  // Yöneticinin tüm test atama ve sonuçlarını listelemesini sağlar (T-052A / T-052B)
  getAllResults: () =>
    api.get<ApiResponse<PsychTestResultDto[]>>('/psych-tests/results'),

  // Öğrencinin kendisine atanan testleri listelemesini sağlar (T-052A / T-052C)
  getMyAssignments: () =>
    api.get<ApiResponse<MyPsychTestAssignmentDto[]>>('/psych-tests/my-assignments'),

  // Bir psikolojik ölçeğin madde ve seçenek metinlerini getirir (T-052A / T-052C)
  getScaleItems: (testCode: string = 'STAI') =>
    api.get<ApiResponse<PsychTestScaleInfoDto>>(`/psych-tests/items?testCode=${testCode}`),

  // Öğrencinin doldurduğu test cevaplarını teslim etmesini sağlar (T-052A / T-052C)
  submitTest: (id: string, payload: SubmitPsychTestPayload) =>
    api.post<ApiResponse<PsychTestSubmitResponseDto>>(`/psych-tests/assignments/${id}/submit`, payload),

  // Öğretmen veya yöneticinin belirli bir öğrenciye ait psikolojik test sonuçlarını getirmesini sağlar (T-052A / T-052D)
  getStudentResults: (studentId: string) =>
    api.get<ApiResponse<PsychTestResultDto[]>>(`/psych-tests/results/student/${studentId}`),

  // Öğrencinin Bourdon dikkat testini başlatmasını sağlar (T-053A / T-053B)
  startBourdonTest: (id: string) =>
    api.post<ApiResponse<BourdonStartResponseDto>>(`/psych-tests/assignments/${id}/start`),

  // Öğrencinin işaretlediği Bourdon hücrelerini teslim etmesini sağlar (T-053A / T-053B)
  submitBourdonTest: (id: string, payload: BourdonSubmissionPayload) =>
    api.post<ApiResponse<PsychTestSubmitResponseDto>>(`/psych-tests/assignments/${id}/submit-bourdon`, payload),

  // Yönetici veya yetkili öğretmenin Bourdon dikkat testi detay sonucunu getirmesini sağlar (T-053C)
  getBourdonResult: (id: string) =>
    api.get<ApiResponse<BourdonResultDto>>(`/psych-tests/assignments/${id}/bourdon-result`),

  // Öğretmen veya yöneticinin görülmemiş psikolojik test sayılarını getirir (T-062)
  getUnseenCounts: () =>
    api.get<ApiResponse<UnseenPsychTestCountsDto>>('/psych-tests/unseen-counts'),

  // Test sonuçlarını çağıran kullanıcı için görüldü olarak işaretler (T-062 / T-062B)
  markResultsSeen: (studentId?: string, assignmentIds?: string[]) =>
    api.post<ApiResponse<void>>('/psych-tests/results/mark-seen', {
      studentId: studentId || undefined,
      assignmentIds: assignmentIds && assignmentIds.length > 0 ? assignmentIds : undefined,
    }),
};


/**
 * SiteContentFieldDto: Doldurulabilir site alanı ve azami uzunluğu (T-063A). Sınırın tek kaynağı sunucudur.
 */
export interface SiteContentFieldDto {
  key: string;
  maxLength: number;
}

/**
 * siteContentApi: Karşılama sitesi içeriği (T-063A / S-026).
 * Okuma herkese açıktır; alan listesi ve yazma yalnızca yöneticiye açıktır.
 */
export const siteContentApi = {
  get: () => api.get<ApiResponse<Record<string, string>>>('/site-content'),
  getFields: () => api.get<ApiResponse<SiteContentFieldDto[]>>('/site-content/fields'),
  update: (values: Record<string, string>) =>
    api.put<ApiResponse<Record<string, string>>>('/site-content', { values }),
};

export default api;

