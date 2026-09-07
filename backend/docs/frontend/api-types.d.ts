/* temple-stamp — API 타입 초안 (자동 생성 · 2026-09-07)
 *
 * 손으로 고치지 말 것. `node backend/docs/frontend/generate-api-types.js` 가
 * 컨트롤러와 record 선언에서 다시 뽑는다 — 옮겨 적는 순간부터 낡기 때문이다.
 *
 * 규약
 *   성공: { success: true,  data: T,    error: null, timestamp }
 *   실패: { success: false, data: null, error: { code, message, fields }, timestamp }
 *   날짜는 전부 ISO + 09:00 문자열. "없음" 은 404 가 아니라 200 + null.
 *   DELETE 다섯은 204 · 본문 없음 — res.json() 을 부르면 안 된다.
 *   요청 본문에 latitude·longitude 를 넣으면 400 COMMON-4001 이다.
 */

export interface ApiResponse<T> {
  success: boolean;
  data: T | null;
  error: ApiError | null;
  /** ISO 8601 + 09:00 */
  timestamp: string;
}

export interface ApiError {
  /** {도메인}-{HTTP 3자리}{일련 1자리} — 예 "STAMP-4091" */
  code: string;
  message: string;
  /** 검증 오류일 때만 채워진다 */
  fields: Record<string, string> | null;
}

/** 페이지가 없는 목록 */
export interface ItemsResponse<T> { items: T[]; }

/** 페이지 목록. page 는 0부터 */
export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalCount: number;
  hasNext: boolean;
}

// ── enum — 화면이 이 값으로 분기한다 ──

export type AccuracyGrade = 'HIGH' | 'MID' | 'LOW';
export type AgreementType = 'LOCATION_SERVICE' | 'EBOOK_PUBLIC';
export type ErrorCode = 'COMMON_4000' | 'COMMON_4001' | 'COMMON_4002' | 'COMMON_4003' | 'COMMON_4004' | 'COMMON_4040' | 'COMMON_4050' | 'COMMON_4090' | 'COMMON_4130' | 'COMMON_4150' | 'COMMON_5000' | 'AUTH_4011' | 'AUTH_4012' | 'AUTH_4013' | 'AUTH_4014' | 'AUTH_4015' | 'AUTH_4031' | 'AUTH_4032' | 'AUTH_4090' | 'USER_4030' | 'USER_4031' | 'USER_4040' | 'COURSE_4000' | 'COURSE_4040' | 'COURSE_4001' | 'COURSE_4041' | 'COURSE_4092' | 'COURSE_4093' | 'SITE_4040' | 'SITE_4090' | 'SITE_5001' | 'VERSE_4040' | 'VERSE_4041' | 'PILGRIMAGE_4030' | 'PILGRIMAGE_4040' | 'STAMP_4000' | 'STAMP_4001' | 'STAMP_4002' | 'STAMP_4003' | 'STAMP_4004' | 'STAMP_4031' | 'STAMP_4040' | 'STAMP_4090' | 'STAMP_4091' | 'STAMP_4092' | 'STAMP_4093' | 'STAMP_4094' | 'STAMP_4291' | 'STAMP_4292' | 'UPLOAD_4001' | 'THINKBOX_4030' | 'THINKBOX_4040' | 'MEDITATION_4040' | 'REWARD_4001' | 'REWARD_4040' | 'REWARD_4090' | 'REWARD_4092' | 'CERT_4041' | 'CERT_4091' | 'MS_4002' | 'MS_4030' | 'MS_4040' | 'MS_4090' | 'MS_4091' | 'MS_4092' | 'MS_4093' | 'EBOOK_4001' | 'EBOOK_4030' | 'EBOOK_4040' | 'EBOOK_4090' | 'EBOOK_4092' | 'EBOOK_4290' | 'PRINT_4001' | 'PRINT_4040' | 'PRINT_4090' | 'PRINT_4091' | 'KAKAO_5030' | 'KAKAO_5031' | 'ADMIN_4092';
export type MissionScope = 'SITE' | 'VERSE' | 'COMMON';
export type StampStatus = 'GPS_DONE' | 'QR_DONE' | 'COMPLETED' | 'EXPIRED' | 'PENDING' | 'REJECTED';
export type Tier = 'AGE20' | 'AGE30' | 'AGE40' | 'AGE50' | 'AGE60' | 'RIDER' | 'FOREIGN';
export type UploadPurpose = 'PHOTO' | 'EVIDENCE';
export type VerifyMethod = 'GPS_QR' | 'EVIDENCE';

// ── DTO — record 선언에서 뽑았다 ──

/** user/dto/AccountDeleteRequest.java */
export interface AccountDeleteRequest {
  password?: string | null;
}

/** admin/dto/AdminCourseSaveRequest.java */
export interface AdminCourseSaveRequest {
  regionId?: number | null;
  name?: string | null;
  description?: string | null;
  status?: string | null;
  sortNo?: number | null;
  sites: SiteSlot[];
}

/** admin/dto/AdminEbookFileRequest.java */
export interface AdminEbookFileRequest {
  pdfKey?: string | null;
  epubKey?: string | null;
}

/** admin/dto/AdminPrintOrderResponse.java */
export interface AdminPrintOrderResponse {
  printOrderId?: number | null;
  userId?: number | null;
  nickname?: string | null;
  ebookId?: number | null;
  ebookType?: string | null;
  quantity?: number | null;
  recipientName?: string | null;
  recipientPhone?: string | null;
  postalCode?: string | null;
  address?: string | null;
  status?: string | null;
  note?: string | null;
  trackingNo?: string | null;
  cancelReason?: string | null;
  needsReview: boolean;
  requestedAt?: string | null;
  updatedAt?: string | null;
}

/** admin/dto/AdminSiteListResponse.java */
export interface AdminSiteListResponse {
  siteId?: number | null;
  name?: string | null;
  status?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  verifyRadius?: number | null;
  qrLocationHint?: string | null;
}

/** admin/dto/AdminSiteSaveRequest.java */
export interface AdminSiteSaveRequest {
  latitude?: number | null;
  longitude?: number | null;
  verifyRadius?: number | null;
  qrLocationHint?: string | null;
  parkingInfo?: string | null;
  accessInfo?: string | null;
  mealAvailable?: string | null;
  i18n: I18nBlock[];
}

/** admin/dto/AdminUserResponse.java */
export interface AdminUserResponse {
  userId?: number | null;
  email?: string | null;
  nickname?: string | null;
  role?: string | null;
  status?: string | null;
  createdAt?: string | null;
  deletedAt?: string | null;
}

/** user/dto/AgreementRequest.java */
export interface AgreementRequest {
  agreementType: AgreementType;
  version?: number | null;
}

/** user/dto/AgreementResponse.java */
export interface AgreementResponse {
  agreementType?: string | null;
  version?: number | null;
  agreedAt?: string | null;
}

/** global/config/AppProperties.java */
export interface AppProperties {
  jwt: Jwt;
  qr: Qr;
  cookie: Cookie;
}

/** global/security/AuthenticatedUser.java */
export interface AuthenticatedUser {
  userId?: number | null;
  email?: string | null;
  role?: string | null;
}

/** stamp/dto/BatchRecountResponse.java */
export interface BatchRecountResponse {
  scanned: number;
  created: number;
  canceled: number;
  failed: number[];
}

/** course/dto/CandidateResponse.java */
export interface CandidateResponse {
  siteId?: number | null;
  siteName?: string | null;
  track?: string | null;
  sortNo?: number | null;
  routeNote?: string | null;
  isStar: boolean;
  servingNote?: string | null;
  congested: boolean;
}

/** certificate/dto/CertificateResponse.java */
export interface CertificateResponse {
  certificateId?: number | null;
  serialNo?: string | null;
  certType?: string | null;
  status?: string | null;
  courseName?: string | null;
  issuedAt?: string | null;
  revokedAt?: string | null;
  verifyUrl?: string | null;
  downloadUrl?: string | null;
}

/** admin/dto/CertificateRevokeRequest.java */
export interface CertificateRevokeRequest {
  reason?: string | null;
}

/** certificate/dto/CertificateVerifyResponse.java */
export interface CertificateVerifyResponse {
  serialNo?: string | null;
  certType?: string | null;
  status?: string | null;
  courseName?: string | null;
  holderMasked?: string | null;
  issuedAt?: string | null;
  revokedAt?: string | null;
}

/** reward/dto/ClaimRequest.java */
export interface ClaimRequest {
  recipientName?: string | null;
  phone?: string | null;
  address?: string | null;
  memo?: string | null;
}

/** reward/dto/ClaimResponse.java */
export interface ClaimResponse {
  userRewardId?: number | null;
  status?: string | null;
  claimable: boolean;
  message?: string | null;
}

/** admin/dto/ClaimReviewRequest.java */
export interface ClaimReviewRequest {
  decision?: string | null;
  reason?: string | null;
  trackingNo?: string | null;
}

/** stamp/dto/CompletionResult.java */
export interface CompletionResult {
  courseCompleted: boolean;
  certificateId?: number | null;
  certificateSerial?: string | null;
  rewards: RewardResponse[];
}

/** global/config/CorsProperties.java */
export interface CorsProperties {
  allowedOrigins: string[];
}

/** global/housekeeping/OrphanCleaner.java */
export interface Counts {
  deleted: number;
  failed: number;
}

/** course/dto/CourseDetailResponse.java */
export interface CourseDetailResponse {
  courseId?: number | null;
  regionId?: number | null;
  regionName?: string | null;
  name?: string | null;
  description?: string | null;
  progress: ProgressResponse;
  sites: CourseSiteResponse[];
}

/** course/dto/CourseResponse.java */
export interface CourseResponse {
  courseId?: number | null;
  regionId?: number | null;
  regionName?: string | null;
  name?: string | null;
  progress: ProgressResponse;
}

/** course/dto/CourseSiteResponse.java */
export interface CourseSiteResponse {
  courseSiteId?: number | null;
  siteId?: number | null;
  position?: number | null;
  siteName?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  verifyRadius?: number | null;
  qrLocationHint?: string | null;
  candidates: CandidateResponse[];
}

/** ebook/dto/DownloadUrlResponse.java */
export interface DownloadUrlResponse {
  downloadUrl?: string | null;
  format?: string | null;
  expiresInSeconds: number;
}

/** ebook/EbookMaterials.java */
export interface EbookMaterials {
  nickname?: string | null;
  stamps: EbookStampRow[];
  thinkboxes: EbookThinkboxRow[];
  certs: EbookCertRow[];
  medCount: number;
  medSeconds: number;
}

/** global/config/EbookProperties.java */
export interface EbookProperties {
  maxReady: number;
  dailyLimit: number;
  photoMaxPx: number;
  jpegQuality: number;
  buildPerRun: number;
  orphanPerRun: number;
  orphanMaxRetry: number;
  tokenPerRun: number;
  tokenGraceHours: number;
  printPrice: number;
  publicBaseUrl?: string | null;
}

/** ebook/dto/EbookResponse.java */
export interface EbookResponse {
  ebookId?: number | null;
  courseName?: string | null;
  ebookType?: string | null;
  status?: string | null;
  downloadable: boolean;
  pageCount?: number | null;
  byteSize?: number | null;
  failReason?: string | null;
  downloadUrl?: string | null;
  createdAt?: string | null;
}

/** global/error/ErrorResponse.java */
export interface ErrorResponse {
  code?: string | null;
  message?: string | null;
  fields: FieldError[];
}

/** stamp/dto/EvidenceRequest.java */
export interface EvidenceRequest {
  courseSiteId?: number | null;
  siteId?: number | null;
  sentence?: string | null;
  photoKey?: string | null;
}

/** stamp/dto/EvidenceResponse.java */
export interface EvidenceResponse {
  stampId?: number | null;
  status?: string | null;
  message?: string | null;
  requestedAt?: string | null;
}

/** manuscript/dto/ExtPhraseResponse.java */
export interface ExtPhraseResponse {
  title?: string | null;
  body?: string | null;
  variantNo?: number | null;
}

/** stamp/dto/GpsCheckRequest.java */
export interface GpsCheckRequest {
  siteId?: number | null;
  withinRadius?: boolean | null;
  accuracyGrade: AccuracyGrade;
}

/** stamp/dto/GpsCheckResponse.java */
export interface GpsCheckResponse {
  stampId?: number | null;
  status?: string | null;
  qrLocationHint?: string | null;
  expiresAt?: string | null;
}

/** manuscript/dto/ImportError.java */
export interface ImportError {
  row: number;
  field?: string | null;
  reason?: string | null;
}

/** manuscript/dto/ImportResultResponse.java */
export interface ImportResultResponse {
  ok: boolean;
  wouldInsert: number;
  inserted: number;
  errors: ImportError[];
}

/** auth/AuthService.java */
export interface IssuedTokens {
  body: LoginResponse;
  refreshToken?: string | null;
  refreshDays: number;
}

/** kakao/dto/KakaoPlaceDocument.java */
export interface KakaoPlaceDocument {
  id?: string | null;
  placeName?: string | null;
  categoryName?: string | null;
  phone?: string | null;
  addressName?: string | null;
  roadAddressName?: string | null;
  x?: string | null;
  y?: string | null;
  placeUrl?: string | null;
}

/** kakao/dto/KakaoPlaceResponse.java */
export interface KakaoPlaceResponse {
  placeName?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  addressName?: string | null;
  roadAddressName?: string | null;
  phone?: string | null;
  categoryName?: string | null;
  placeUrl?: string | null;
}

/** kakao/dto/KakaoPlaceSearchResult.java */
export interface KakaoPlaceSearchResult {
  places: KakaoPlaceResponse[];
  totalCount: number;
  end: boolean;
}

/** kakao/KakaoProperties.java */
export interface KakaoProperties {
  restKey?: string | null;
  baseUrl?: string | null;
  timeoutMillis: number;
}

/** kakao/dto/KakaoSearchRawResponse.java */
export interface KakaoSearchRawResponse {
  meta: Meta;
  documents: KakaoPlaceDocument[];
}

/** auth/dto/LoginRequest.java */
export interface LoginRequest {
  email?: string | null;
  password?: string | null;
}

/** auth/dto/LoginResponse.java */
export interface LoginResponse {
  accessToken?: string | null;
  tokenType?: string | null;
  expiresIn: number;
  user: UserResponse;
}

/** manuscript/dto/ManuscriptCreateRequest.java */
export interface ManuscriptCreateRequest {
  siteId?: number | null;
  verseNo?: number | null;
  kind?: string | null;
  title?: string | null;
  body?: string | null;
}

/** global/config/ManuscriptProperties.java */
export interface ManuscriptProperties {
  maxVariants: number;
  importMaxRows: number;
  importMaxBytes: number;
}

/** manuscript/dto/ManuscriptRejectRequest.java */
export interface ManuscriptRejectRequest {
  reason?: string | null;
}

/** manuscript/dto/ManuscriptResponse.java */
export interface ManuscriptResponse {
  manuscriptId?: number | null;
  siteId?: number | null;
  siteName?: string | null;
  verseNo?: number | null;
  kind?: string | null;
  variantNo?: number | null;
  status?: string | null;
  title?: string | null;
  body?: string | null;
  authorId?: number | null;
  authorNickname?: string | null;
  rejectReason?: string | null;
  reviewedAt?: string | null;
  retiredAt?: string | null;
  createdAt?: string | null;
}

/** manuscript/dto/ManuscriptTextResponse.java */
export interface ManuscriptTextResponse {
  manuscriptId?: number | null;
  siteId?: number | null;
  title?: string | null;
  body?: string | null;
  variantNo?: number | null;
}

/** manuscript/dto/ManuscriptUpdateRequest.java */
export interface ManuscriptUpdateRequest {
  title?: string | null;
  body?: string | null;
}

/** meditation/dto/MeditationDetailResponse.java */
export interface MeditationDetailResponse {
  meditationId?: number | null;
  title?: string | null;
  category?: string | null;
  script?: string | null;
  audioUrl?: string | null;
  durationSeconds?: number | null;
  lang?: string | null;
}

/** meditation/dto/MeditationListResponse.java */
export interface MeditationListResponse {
  items: MeditationSummaryResponse[];
  categoryCounts: CategoryCount[];
}

/** meditation/dto/MeditationLogRequest.java */
export interface MeditationLogRequest {
  meditationId?: number | null;
  playedSeconds?: number | null;
  memo?: string | null;
}

/** meditation/dto/MeditationLogResponse.java */
export interface MeditationLogResponse {
  meditationLogId?: number | null;
  meditationId?: number | null;
  title?: string | null;
  playedSeconds?: number | null;
  memo?: string | null;
  createdAt?: string | null;
}

/** meditation/dto/MeditationSummaryResponse.java */
export interface MeditationSummaryResponse {
  meditationId?: number | null;
  title?: string | null;
  category?: string | null;
  durationSeconds?: number | null;
}

/** stamp/dto/MissionResultResponse.java */
export interface MissionResultResponse {
  stampId?: number | null;
  status?: string | null;
  message?: string | null;
  progress: ProgressResponse;
  rewards: RewardResponse[];
  courseCompleted: boolean;
  certificateSerial?: string | null;
  extPhrase: ExtPhraseResponse;
}

/** admin/dto/MissionSaveRequest.java */
export interface MissionSaveRequest {
  verseNo?: number | null;
  tier: Tier;
  scope: MissionScope;
  siteId?: number | null;
  variantNo?: number | null;
  body?: string | null;
  originRef?: string | null;
  reviewStatus?: string | null;
}

/** stamp/dto/MissionSubmitRequest.java */
export interface MissionSubmitRequest {
  sentence?: string | null;
  photoKey?: string | null;
  hasOtherFace?: boolean | null;
  expansionPhraseId?: number | null;
}

/** pilgrimage/dto/PassportResponse.java */
export interface PassportResponse {
  summary: Summary;
  regions: RegionBlock[];
}

/** ebook/PdfBuilder.java */
export interface PdfResult {
  bytes: string;
  pageCount: number;
}

/** admin/dto/PendingStampResponse.java */
export interface PendingStampResponse {
  stampId?: number | null;
  userId?: number | null;
  nickname?: string | null;
  courseName?: string | null;
  siteName?: string | null;
  verifyMethod?: string | null;
  pendingReason?: string | null;
  sentence?: string | null;
  photoUrl?: string | null;
  requestedAt?: string | null;
}

/** photo/dto/PhotoResponse.java */
export interface PhotoResponse {
  siteId?: number | null;
  siteName?: string | null;
  photoKey?: string | null;
  sentence?: string | null;
  hasOtherFace: boolean;
  isPrivate: boolean;
  updatedAt?: string | null;
}

/** photo/dto/PhotoSaveRequest.java */
export interface PhotoSaveRequest {
  photoKey?: string | null;
  sentence?: string | null;
  hasOtherFace: boolean;
  isPrivate: boolean;
}

/** admin/dto/PhraseSaveRequest.java */
export interface PhraseSaveRequest {
  verseNo?: number | null;
  tier: Tier;
  versionNo?: number | null;
  textKo?: string | null;
  reviewStatus?: string | null;
}

/** pilgrimage/dto/PilgrimageResponse.java */
export interface PilgrimageResponse {
  pilgrimageId?: number | null;
  courseId?: number | null;
  status?: string | null;
  startedAt?: string | null;
  created: boolean;
}

/** pilgrimage/dto/PilgrimageStartRequest.java */
export interface PilgrimageStartRequest {
  courseId?: number | null;
}

/** upload/dto/PresignResponse.java */
export interface PresignResponse {
  uploadUrl?: string | null;
  fileKey?: string | null;
  expiresInSeconds: number;
}

/** upload/ObjectStorageClient.java */
export interface Presigned {
  fileKey?: string | null;
  url?: string | null;
  expiresInSeconds: number;
}

/** ebook/dto/PrintOrderDetailResponse.java */
export interface PrintOrderDetailResponse {
  printOrderId?: number | null;
  ebookId?: number | null;
  ebookType?: string | null;
  quantity?: number | null;
  status?: string | null;
  trackingNo?: string | null;
  cancelReason?: string | null;
  cancelable: boolean;
  requestedAt?: string | null;
  updatedAt?: string | null;
  shipping: Shipping;
}

/** ebook/dto/PrintOrderRequest.java */
export interface PrintOrderRequest {
  ebookId?: number | null;
  quantity?: number | null;
  recipientName?: string | null;
  recipientPhone?: string | null;
  postalCode?: string | null;
  address?: string | null;
  addressDetail?: string | null;
  note?: string | null;
}

/** ebook/dto/PrintOrderResponse.java */
export interface PrintOrderResponse {
  printOrderId?: number | null;
  ebookId?: number | null;
  ebookType?: string | null;
  quantity?: number | null;
  status?: string | null;
  trackingNo?: string | null;
  cancelReason?: string | null;
  cancelable: boolean;
  requestedAt?: string | null;
  updatedAt?: string | null;
}

/** admin/dto/PrintOrderStatusRequest.java */
export interface PrintOrderStatusRequest {
  status?: string | null;
  trackingNo?: string | null;
  reason?: string | null;
}

/** course/dto/ProgressResponse.java */
export interface ProgressResponse {
  pilgrimageId?: number | null;
  completedCount: number;
  total: number;
  status?: string | null;
}

/** admin/dto/QrIssueResponse.java */
export interface QrIssueResponse {
  siteId?: number | null;
  courseSiteId?: number | null;
  siteName?: string | null;
  qrToken?: string | null;
  qrImageBase64?: string | null;
  qrVersion?: number | null;
  issuedAt?: string | null;
  expiresAt?: string | null;
}

/** stamp/QrTokenProvider.java */
export interface QrToken {
  token?: string | null;
  issuedAt?: string | null;
  expiresAt?: string | null;
}

/** stamp/dto/QrVerifyRequest.java */
export interface QrVerifyRequest {
  qrToken?: string | null;
}

/** stamp/dto/RecountResponse.java */
export interface RecountResponse {
  created: number;
  canceled: number;
  noop: number;
}

/** course/dto/RegionResponse.java */
export interface RegionResponse {
  regionId?: number | null;
  code?: string | null;
  name?: string | null;
  courseCount?: number | null;
}

/** global/config/RewardProperties.java */
export interface RewardProperties {
  auditBlocking: boolean;
  reviewThreshold: number;
  minGapSeconds: number;
  hoehyangMinCourses: number;
}

/** reward/dto/RewardResponse.java */
export interface RewardResponse {
  userRewardId?: number | null;
  rewardName?: string | null;
  rewardType?: string | null;
  status?: string | null;
  claimable: boolean;
  earnedAt?: string | null;
}

/** reward/AuditScorer.java */
export interface Score {
  value?: number | null;
  detailJson?: string | null;
  needsReview: boolean;
}

/** auth/dto/SignupRequest.java */
export interface SignupRequest {
  email?: string | null;
  password?: string | null;
  nickname?: string | null;
}

/** admin/dto/SiteBadgeSaveRequest.java */
export interface SiteBadgeSaveRequest {
  badgeType?: string | null;
  description?: string | null;
}

/** admin/dto/SiteDistanceSaveRequest.java */
export interface SiteDistanceSaveRequest {
  items: Item[];
}

/** admin/dto/SiteElementSaveRequest.java */
export interface SiteElementSaveRequest {
  items: Item[];
}

/** site/dto/SiteGuideResponse.java */
export interface SiteGuideResponse {
  siteId?: number | null;
  siteName?: string | null;
  steps: Step[];
}

/** site/dto/SitePageResponse.java */
export interface SitePageResponse {
  site: SiteBlock;
  verse: VerseBlock;
  expansionPhrase: PhraseBlock;
  mission: MissionBlock;
  viewpoints: ViewpointBlock[];
  badges: BadgeBlock[];
  riderInfo: RiderInfoBlock;
  verifyState: VerifyStateBlock;
}

/** site/dto/SiteResponse.java */
export interface SiteResponse {
  siteId?: number | null;
  name?: string | null;
  description?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  verifyRadius?: number | null;
  qrLocationHint?: string | null;
}

/** content/seed/SiteSeedRow.java */
export interface SiteSeedRow {
  regionCode?: string | null;
  nameKo?: string | null;
  disambiguation?: string | null;
  nameEn?: string | null;
  roadAddress?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  diocese?: string | null;
  researchStatus?: string | null;
  iljumun?: string | null;
  iljumunName?: string | null;
  geumgangmun?: string | null;
  geumgangmunName?: string | null;
  cheonwangmun?: string | null;
  cheonwangmunName?: string | null;
  bulimun?: string | null;
  bulimunName?: string | null;
  pagoda?: string | null;
  pagodaName?: string | null;
  mainHallName?: string | null;
  annexHalls?: string | null;
  parkingInfo?: string | null;
  accessInfo?: string | null;
  mealAvailable?: string | null;
  flowerBadge?: string | null;
  sources?: string | null;
  note?: string | null;
}

/** admin/dto/SiteViewpointSaveRequest.java */
export interface SiteViewpointSaveRequest {
  sortNo?: number | null;
  locationDesc?: string | null;
  bestTime?: string | null;
  whatToSee?: string | null;
}

/** content/seed/SlotSeedRow.java */
export interface SlotSeedRow {
  regionCode?: string | null;
  regionName?: string | null;
  lineCode?: string | null;
  lineName?: string | null;
  verseNo: number;
  siteName?: string | null;
  disambiguation?: string | null;
  track?: string | null;
  servingNote?: string | null;
  sunroadRoute?: string | null;
  star: boolean;
  note?: string | null;
}

/** global/config/StampProperties.java */
export interface StampProperties {
  sessionMinutes: number;
  defaultTravelMinutes: number;
  dailyLimit: number;
  evidenceDailyLimit: number;
}

/** admin/dto/StampReviewRequest.java */
export interface StampReviewRequest {
  decision?: string | null;
  reason?: string | null;
}

/** stamp/StampReviewedEvent.java */
export interface StampReviewedEvent {
  userId?: number | null;
  pilgrimageId?: number | null;
  stampId?: number | null;
  approved: boolean;
}

/** stamp/dto/StampStatusResponse.java */
export interface StampStatusResponse {
  stampId?: number | null;
  courseSiteId?: number | null;
  status?: string | null;
  gpsVerifiedAt?: string | null;
  qrVerifiedAt?: string | null;
  completedAt?: string | null;
  expiresAt?: string | null;
  siteId?: number | null;
  siteName?: string | null;
  photoKey?: string | null;
  userSentence?: string | null;
  missionManuscript: ManuscriptTextResponse;
  extPhrase: ExtPhraseResponse;
}

/** admin/dto/StatusChangeRequest.java */
export interface StatusChangeRequest {
  status?: string | null;
}

/** global/config/StorageProperties.java */
export interface StorageProperties {
  provider?: string | null;
  bucket?: string | null;
  endpoint?: string | null;
  secretKey?: string | null;
  presignSeconds: number;
  maxUploadBytes: number;
  localDir?: string | null;
}

/** thinkbox/dto/ThinkboxCreateRequest.java */
export interface ThinkboxCreateRequest {
  content?: string | null;
  courseId?: number | null;
  siteId?: number | null;
  isPrivate?: boolean | null;
}

/** thinkbox/dto/ThinkboxResponse.java */
export interface ThinkboxResponse {
  thinkboxId?: number | null;
  content?: string | null;
  source?: string | null;
  isPrivate?: boolean | null;
  isEdited?: boolean | null;
  courseId?: number | null;
  courseName?: string | null;
  siteId?: number | null;
  siteName?: string | null;
  createdAt?: string | null;
}

/** thinkbox/dto/ThinkboxUpdateRequest.java */
export interface ThinkboxUpdateRequest {
  content?: string | null;
  isPrivate?: boolean | null;
}

/** user/dto/UserResponse.java */
export interface UserResponse {
  userId?: number | null;
  email?: string | null;
  nickname?: string | null;
  role?: string | null;
  tier?: string | null;
  locale?: string | null;
  notificationEnabled?: boolean | null;
  createdAt?: string | null;
}

/** user/dto/UserUpdateRequest.java */
export interface UserUpdateRequest {
  nickname?: string | null;
  tier: Tier;
  locale?: string | null;
  notificationEnabled?: boolean | null;
}

/** verse/dto/VerseResponse.java */
export interface VerseResponse {
  verseNo?: number | null;
  hanja?: string | null;
  textKo?: string | null;
  theme?: string | null;
}

// ── 엔드포인트 91 ──

export interface Endpoint {
  method: string; path: string; request: string | null; response: string; status: number;
}

export const ENDPOINTS: readonly Endpoint[] = [
  { method: 'POST', path: '/api/admin/certificates/{certificateId}/revoke', request: 'CertificateRevokeRequest', response: 'null', status: 200 },
  { method: 'POST', path: '/api/admin/completions/recount', request: null, response: 'BatchRecountResponse', status: 200 },
  { method: 'POST', path: '/api/admin/completions/recount/{userId}', request: null, response: 'RecountResponse', status: 200 },
  { method: 'POST', path: '/api/admin/contents/missions', request: 'MissionSaveRequest', response: 'Long', status: 200 },
  { method: 'POST', path: '/api/admin/contents/phrases', request: 'PhraseSaveRequest', response: 'Long', status: 200 },
  { method: 'POST', path: '/api/admin/courses', request: 'AdminCourseSaveRequest', response: 'CourseDetailResponse', status: 201 },
  { method: 'PUT', path: '/api/admin/courses/{courseId}', request: 'AdminCourseSaveRequest', response: 'CourseDetailResponse', status: 200 },
  { method: 'PATCH', path: '/api/admin/courses/{courseId}/status', request: 'StatusChangeRequest', response: 'null', status: 200 },
  { method: 'PUT', path: '/api/admin/ebooks/{ebookId}/files', request: 'AdminEbookFileRequest', response: 'null', status: 200 },
  { method: 'GET', path: '/api/admin/ebooks/print-orders', request: null, response: 'PageResponse<AdminPrintOrderResponse>', status: 200 },
  { method: 'PATCH', path: '/api/admin/ebooks/print-orders/{printOrderId}/status', request: 'PrintOrderStatusRequest', response: 'null', status: 200 },
  { method: 'POST', path: '/api/admin/housekeeping/run', request: null, response: 'HousekeepingResult', status: 200 },
  { method: 'GET', path: '/api/admin/kakao/places', request: null, response: 'KakaoPlaceSearchResult', status: 200 },
  { method: 'GET', path: '/api/admin/manuscripts', request: null, response: 'PageResponse<ManuscriptResponse>', status: 200 },
  { method: 'POST', path: '/api/admin/manuscripts/{manuscriptId}/approve', request: null, response: 'null', status: 200 },
  { method: 'POST', path: '/api/admin/manuscripts/{manuscriptId}/reject', request: 'ManuscriptRejectRequest', response: 'null', status: 200 },
  { method: 'POST', path: '/api/admin/manuscripts/{manuscriptId}/retire', request: null, response: 'null', status: 200 },
  { method: 'POST', path: '/api/admin/manuscripts/import', request: null, response: 'ImportResultResponse', status: 200 },
  { method: 'POST', path: '/api/admin/rewards/claims/{userRewardId}/review', request: 'ClaimReviewRequest', response: 'null', status: 200 },
  { method: 'GET', path: '/api/admin/rewards/claims/pending', request: null, response: 'ItemsResponse<AdminClaimRow>', status: 200 },
  { method: 'PUT', path: '/api/admin/site-distances', request: 'SiteDistanceSaveRequest', response: 'null', status: 200 },
  { method: 'GET', path: '/api/admin/sites', request: null, response: 'PageResponse<AdminSiteListResponse>', status: 200 },
  { method: 'POST', path: '/api/admin/sites', request: 'AdminSiteSaveRequest', response: 'SiteResponse', status: 201 },
  { method: 'PUT', path: '/api/admin/sites/{siteId}', request: 'AdminSiteSaveRequest', response: 'SiteResponse', status: 200 },
  { method: 'POST', path: '/api/admin/sites/{siteId}/badges', request: 'SiteBadgeSaveRequest', response: 'null', status: 200 },
  { method: 'POST', path: '/api/admin/sites/{siteId}/elements', request: 'SiteElementSaveRequest', response: 'null', status: 200 },
  { method: 'DELETE', path: '/api/admin/sites/{siteId}/elements/{elementCode}', request: null, response: 'null', status: 204 },
  { method: 'POST', path: '/api/admin/sites/{siteId}/qr', request: null, response: 'QrIssueResponse', status: 200 },
  { method: 'POST', path: '/api/admin/sites/{siteId}/qr/rotate', request: null, response: 'Integer', status: 200 },
  { method: 'PATCH', path: '/api/admin/sites/{siteId}/status', request: 'StatusChangeRequest', response: 'null', status: 200 },
  { method: 'POST', path: '/api/admin/sites/{siteId}/viewpoints', request: 'SiteViewpointSaveRequest', response: 'null', status: 200 },
  { method: 'POST', path: '/api/admin/stamps/{stampId}/review', request: 'StampReviewRequest', response: 'null', status: 200 },
  { method: 'GET', path: '/api/admin/stamps/pending', request: null, response: 'PageResponse<PendingStampResponse>', status: 200 },
  { method: 'GET', path: '/api/admin/users', request: null, response: 'PageResponse<AdminUserResponse>', status: 200 },
  { method: 'POST', path: '/api/auth/login', request: 'LoginRequest', response: 'LoginResponse', status: 200 },
  { method: 'POST', path: '/api/auth/logout', request: null, response: 'null', status: 200 },
  { method: 'POST', path: '/api/auth/refresh', request: null, response: 'LoginResponse', status: 200 },
  { method: 'POST', path: '/api/auth/signup', request: 'SignupRequest', response: 'UserResponse', status: 201 },
  { method: 'GET', path: '/api/certificates', request: null, response: 'ItemsResponse<CertificateResponse>', status: 200 },
  { method: 'GET', path: '/api/certificates/{certificateId}', request: null, response: 'CertificateResponse', status: 200 },
  { method: 'GET', path: '/api/certificates/verify/{serialNo}', request: null, response: 'CertificateVerifyResponse', status: 200 },
  { method: 'GET', path: '/api/content/guide', request: null, response: 'JsonNode', status: 200 },
  { method: 'GET', path: '/api/content/i18n/{lang}', request: null, response: 'JsonNode', status: 200 },
  { method: 'GET', path: '/api/courses', request: null, response: 'ItemsResponse<CourseResponse>', status: 200 },
  { method: 'GET', path: '/api/courses/{courseId}', request: null, response: 'CourseDetailResponse', status: 200 },
  { method: 'GET', path: '/api/ebooks', request: null, response: 'ItemsResponse<EbookResponse>', status: 200 },
  { method: 'POST', path: '/api/ebooks', request: null, response: 'EbookResponse', status: 200 },
  { method: 'GET', path: '/api/ebooks/{ebookId}', request: null, response: 'EbookResponse', status: 200 },
  { method: 'GET', path: '/api/ebooks/{ebookId}/download-url', request: null, response: 'DownloadUrlResponse', status: 200 },
  { method: 'GET', path: '/api/editor/manuscripts', request: null, response: 'PageResponse<ManuscriptResponse>', status: 200 },
  { method: 'POST', path: '/api/editor/manuscripts', request: 'ManuscriptCreateRequest', response: 'ManuscriptResponse', status: 201 },
  { method: 'PUT', path: '/api/editor/manuscripts/{manuscriptId}', request: 'ManuscriptUpdateRequest', response: 'ManuscriptResponse', status: 200 },
  { method: 'POST', path: '/api/editor/manuscripts/{manuscriptId}/submit', request: null, response: 'null', status: 200 },
  { method: 'GET', path: '/api/meditations', request: null, response: 'MeditationListResponse', status: 200 },
  { method: 'GET', path: '/api/meditations/{meditationId}', request: null, response: 'MeditationDetailResponse', status: 200 },
  { method: 'POST', path: '/api/meditations/logs', request: 'MeditationLogRequest', response: 'MeditationLogResponse', status: 200 },
  { method: 'GET', path: '/api/passport', request: null, response: 'PassportResponse', status: 200 },
  { method: 'GET', path: '/api/photos', request: null, response: 'ItemsResponse<PhotoResponse>', status: 200 },
  { method: 'GET', path: '/api/photos/{siteId}', request: null, response: 'PhotoResponse', status: 200 },
  { method: 'PUT', path: '/api/photos/{siteId}', request: 'PhotoSaveRequest', response: 'PhotoResponse', status: 200 },
  { method: 'POST', path: '/api/pilgrimages', request: 'PilgrimageStartRequest', response: 'PilgrimageResponse', status: 200 },
  { method: 'GET', path: '/api/print-orders', request: null, response: 'ItemsResponse<PrintOrderResponse>', status: 200 },
  { method: 'POST', path: '/api/print-orders', request: 'PrintOrderRequest', response: 'PrintOrderResponse', status: 201 },
  { method: 'DELETE', path: '/api/print-orders/{printOrderId}', request: null, response: 'null', status: 204 },
  { method: 'GET', path: '/api/print-orders/{printOrderId}', request: null, response: 'PrintOrderDetailResponse', status: 200 },
  { method: 'GET', path: '/api/regions', request: null, response: 'ItemsResponse<RegionResponse>', status: 200 },
  { method: 'GET', path: '/api/rewards', request: null, response: 'ItemsResponse<RewardResponse>', status: 200 },
  { method: 'POST', path: '/api/rewards/{userRewardId}/claim', request: 'ClaimRequest', response: 'ClaimResponse', status: 200 },
  { method: 'GET', path: '/api/sites/{siteId}', request: null, response: 'SiteResponse', status: 200 },
  { method: 'GET', path: '/api/sites/{siteId}/guide', request: null, response: 'SiteGuideResponse', status: 200 },
  { method: 'GET', path: '/api/sites/{siteId}/page', request: null, response: 'SitePageResponse', status: 200 },
  { method: 'POST', path: '/api/stamps/{courseSiteId}/gps-check', request: 'GpsCheckRequest', response: 'GpsCheckResponse', status: 200 },
  { method: 'GET', path: '/api/stamps/{stampId}', request: null, response: 'StampStatusResponse', status: 200 },
  { method: 'POST', path: '/api/stamps/{stampId}/mission', request: 'MissionSubmitRequest', response: 'MissionResultResponse', status: 200 },
  { method: 'POST', path: '/api/stamps/{stampId}/qr', request: 'QrVerifyRequest', response: 'StampStatusResponse', status: 200 },
  { method: 'GET', path: '/api/stamps/by-slot/{courseSiteId}', request: null, response: 'StampStatusResponse', status: 200 },
  { method: 'POST', path: '/api/stamps/evidence', request: 'EvidenceRequest', response: 'EvidenceResponse', status: 200 },
  { method: 'GET', path: '/api/thinkbox', request: null, response: 'PageResponse<ThinkboxResponse>', status: 200 },
  { method: 'POST', path: '/api/thinkbox', request: 'ThinkboxCreateRequest', response: 'Long', status: 201 },
  { method: 'DELETE', path: '/api/thinkbox/{thinkboxId}', request: null, response: 'null', status: 204 },
  { method: 'PATCH', path: '/api/thinkbox/{thinkboxId}', request: 'ThinkboxUpdateRequest', response: 'null', status: 200 },
  { method: 'GET', path: '/api/thinkbox/flashback', request: null, response: 'ThinkboxResponse', status: 200 },
  { method: 'GET', path: '/api/uploads/presign', request: null, response: 'PresignResponse', status: 200 },
  { method: 'DELETE', path: '/api/users/me', request: 'AccountDeleteRequest', response: 'null', status: 204 },
  { method: 'GET', path: '/api/users/me', request: null, response: 'UserResponse', status: 200 },
  { method: 'PATCH', path: '/api/users/me', request: 'UserUpdateRequest', response: 'UserResponse', status: 200 },
  { method: 'GET', path: '/api/users/me/agreements', request: null, response: 'ItemsResponse<AgreementResponse>', status: 200 },
  { method: 'POST', path: '/api/users/me/agreements', request: null, response: 'ItemsResponse<AgreementResponse>', status: 200 },
  { method: 'DELETE', path: '/api/users/me/agreements/{agreementType}', request: null, response: 'null', status: 204 },
  { method: 'GET', path: '/api/verses', request: null, response: 'ItemsResponse<VerseResponse>', status: 200 },
  { method: 'GET', path: '/api/verses/{verseNo}', request: null, response: 'VerseResponse', status: 200 },
];
