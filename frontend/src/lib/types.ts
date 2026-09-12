/**
 * types.ts: Uygulama genelinde kullanılan ana tip ve arayüz (interface) tanımlamaları.
 * Backend (Java) tarafındaki Entity ve DTO yapıları ile %100 uyumludur.
 */

/**
 * UserRole: Sistemdeki kullanıcı yetki seviyeleri.
 */
export type UserRole = "STUDENT" | "TEACHER" | "MANAGER";

/**
 * UserStatus: Kullanıcının sistemdeki aktiflik durumu.
 * PENDING: Kayıt olmuş ancak Admin (Manager) onayı bekliyor.
 */
export type UserStatus = "PENDING" | "ACTIVE" | "REJECTED";

/**
 * ApiResponse: Backend'den dönen standart zarf yapısı.
 * success: İşlemin başarı durumu.
 * message: Kullanıcıya gösterilecek Türkçe mesaj.
 * data: Backend'den dönen asıl veri (Generic).
 */
export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
}

/**
 * User: Sistemdeki kullanıcı profil bilgilerini temsil eder.
 */
export interface User {
  id: string; // UUID formatında
  email: string;
  fullName: string;
  role: UserRole;
  status: UserStatus;
  grade?: number | null;
  mustChangePassword?: boolean;
  currentPasswordRequired?: boolean;
}

/**
 * LoginPayload: E-posta ve şifre ile oturum açma istek yükü (T-009 / T-011).
 */
export interface LoginPayload {
  email: string;
  password: string;
  rememberMe?: boolean;
}

/**
 * ChangePasswordPayload: Zorunlu ilk şifre veya şifre güncelleme istek yükü (T-009 / T-011).
 */
export interface ChangePasswordPayload {
  currentPassword?: string;
  newPassword: string;
}

/**
 * CreateUserPayload: Yöneticinin yeni kullanıcı oluşturma istek yükü (T-004 / T-010 / T-016 / T-056).
 */
export interface CreateUserPayload {
  fullName: string;
  role: 'STUDENT' | 'TEACHER';
  grade?: number;
}

/**
 * CreatedUserResponse: Yöneticinin yeni kullanıcı oluşturması sonucu dönen yanıt tipi (T-010 / K3).
 */
export interface CreatedUserResponse {
  user: User;
  generatedPassword: string;
}

/**
 * ResetPasswordResponse: Yöneticinin kullanıcı şifresini sıfırlaması sonucu dönen yanıt tipi (T-013).
 */
export interface ResetPasswordResponse {
  user: User;
  generatedPassword: string;
}

/** AnalysisReportSummary: Rapor listesindeki bir satır (sunucudaki AnalysisReport kaydı). */
export interface AnalysisReportSummary {
  id: string;
  studentId: string;
  fileName: string;
  examTitle: string;
  processedAt: string;
  status: AnalysisData['status'];
  intendedExamCount: number | null;
  reportType: string;
  validationErrors?: string | null;
  cumulativeStatus?: 'INCLUDED' | 'NOT_INCLUDED' | null;
}

/** ReferenceSchool: Hedef okul aramasında dönen okul kaydı. */
export interface ReferenceSchool {
  id: string;
  city: string;
  schoolName: string;
  schoolType: string;
  baseScore: number;
  percentile: number;
}

/** StudentProfile: Öğrencinin hedefi (sunucudaki StudentProfileResponse, T-065). Boş alanlar yanıtta yer almaz. */
export interface StudentProfile {
  targetSchoolId?: string;
  targetSchoolName?: string;
  targetScore?: number;
}

// Backend'deki AnalysisResponse DTO'su ile eşleşen tipler
export interface AnalysisData {
  id: string;
  fileName: string;
  examTitle: string;
  processedAt: string;
  status: 'PROCESSING' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' | 'FAILED';
  intendedExamCount: number;
  reportType: 'SINGLE' | 'SUMMARY';
  mentorFeedback?: string;
  teacherActionPlan?: string;
  futureProjection?: string;
  strategicPriority?: string;
  targetSchoolName?: string;
  targetSchoolScore?: number;
  validationErrors?: string;
  examList: ExamSummary[];
  consolidatedResult: ConsolidatedResult;
  globalFeedback: string;
  topicTrendData?: TopicTrendData;
  /** Öncelik sırasına dizilmiş eksik konu listeleri (T-030). Sıralamayı sunucu yapar (APP-01 §2.1). */
  priorityLists?: PriorityLists;
  /** Hedef okul ile mevcut performansın LGS ölçeğinde karşılaştırması (T-028). */
  targetComparison?: TargetComparison | null;
}

/** PriorityLists: Puana ve orana göre sunucuda sıralanmış ilk 5 öncelikli konu listeleri (T-030). */
export interface PriorityLists {
  byPoints: PriorityTopic[];
  byRate: PriorityTopic[];
}

/** TargetComparison: Hedeflenen okul ile performansın LGS ölçeğinde karşılaştırması (T-028). */
export interface TargetComparison {
  schoolName: string;
  targetScore: number;
  averageNet: number;
  predictedLgsScore: number;
  compatibilityPercent: number;
  scoreGap: number;
  netGap: number;
}

/** PriorityTopic: Öncelikle çalışılacak bir konu ve karneden gelen kanıtı (T-023). */
export interface PriorityTopic {
  lessonName: string;
  topicName: string;
  totalQuestions: number;
  correctCount: number;
  wrongCount: number;
  emptyCount: number;
  successRate: number;
  lostPoints: number;
  chronic?: boolean;
}

export interface TopicTrendData {
  heatmap: TopicHistory[];
  chronicTopics: string[];
  improvedTopics: string[];
  inconsistentTopics: string[];
}

export interface TopicHistory {
  lessonName: string;
  topicName: string;
  statusHistory: ('CORRECT' | 'WRONG' | 'EMPTY')[];
}

export interface ExamSummary {
  examName: string;
  examDate: string;
  totalScore: number;
}

export interface ConsolidatedResult {
  lessons: LessonAnalysis[];
}

export interface LessonAnalysis {
  id: string;
  lessonName: string;
  correct: number;
  wrong: number;
  empty: number;
  successRate: number;
  topics: TopicDetail[];
}

export interface TopicDetail {
  id: string;
  topicName: string;
  status: 'CORRECT' | 'WRONG' | 'EMPTY';
  aiSuggestion: string;
  totalQuestions?: number;
  correctCount?: number;
  wrongCount?: number;
  /** Konu başarı bandı: STRONG (>= %85), MEDIUM (>= %50), WEAK (< %50) (T-030C). */
  performanceBand?: 'STRONG' | 'MEDIUM' | 'WEAK';
}

/**
 * Pairing: Bir öğretmen ve öğrenci arasındaki aktif eşleşme kaydını temsil eder (T-036).
 */
export interface Pairing {
  studentId: string;
  studentName: string;
  studentEmail: string;
  studentGrade: number | null;
  teacherId: string;
  teacherName: string;
  teacherEmail: string;
  pairedAt: string;
}

/** StudentView: Öğretmenin eşleştiği bir öğrenciye son bakışı (T-037). null = hiç bakmamış. */
export interface StudentView {
  studentId: string;
  studentName: string;
  lastViewedAt: string | null;
}

/** Activity: Yönetici aktiflik ekranı için tek kullanıcının özeti (T-037). */
export interface Activity {
  userId: string;
  fullName: string;
  role: 'MANAGER' | 'TEACHER' | 'STUDENT';
  lastLoginAt: string | null;
  reportViewCount: number;
  lastReportViewAt: string | null;
  viewedStudents: StudentView[];
}

/** ClassStudent: Bir sınıftaki öğrenci (T-040). grade = seviye (5-12), sınıf adı ayrıdır. */
export interface ClassStudent {
  id: string;
  fullName: string;
  grade: number | null;
}

/** SchoolClass: Adlandırılmış sınıf ve içindeki öğrenciler (T-040). */
export interface SchoolClass {
  id: string;
  name: string;
  createdAt: string;
  students: ClassStudent[];
}

/**
 * RankingEntry: Sıralama tablosunun tek satırı (T-038).
 *
 * displayName sunucuda karara bağlanır: görme hakkı olmayan satırlarda "3. Öğrenci" gibi
 * sıraya bağlı bir etiket gelir. İstemci maskeleme YAPMAZ, yalnızca geleni basar.
 */
export interface RankingEntry {
  rank: number;
  displayName: string;
  averageNet: number;
  examCount: number;
  isSelf: boolean;
}

/**
 * ClassRankingGroup: Sınıf bazlı sıralama grubu (T-038B).
 */
export interface ClassRankingGroup {
  classId: string | null;
  className: string;
  entries: RankingEntry[];
}
