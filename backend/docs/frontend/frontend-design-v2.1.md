# temple-stamp — 프론트엔드 설계서 v2.1 (코드 대조판 · 재작성) · 2026-09-08

> **이 문서의 위치와 지위.** `backend/docs/frontend/frontend-design-v2.1.md`.
> v2(설계안) → 코드 대조 감사(❌83·⛔6) → 이 문서. **코드가 정본이고 이 문서가 따른다.**
> 고친 자리에 `(정정 #n)`, 코드가 흐름을 지원하지 못하는 자리에 `⛔`, 이번 회차(11-A/11-B)에서 백엔드를 고쳐 열리는 자리에 `⛔→닫힘예정` 을 붙였다.
> 우선순위: `정본정정표.md` > 이 문서 > 기획 명세 ①~④. 원본 문서는 지우지 않는다(B안).
> 필수 스택(끝까지 준수): **React 18 · TypeScript · Vite · axios · Zustand · TanStack Query v5 · Tailwind · React Router v6**. 대상: Spring Boot 3.5.16.

## 정정 이력 (v2 → v2.1)

| # | § | v2 표현 | v2.1 (코드) | 근거 |
|---|---|---|---|---|
| 1 | §7·§11 | `GET /regions/{id}` · 진행률 결합 | 없다. `GET /regions` + `GET /courses?regionId=` 조립 · 진행률은 `/passport` | RegionController |
| 2 | §7 | `/sites/{id}/page?tier=` · 9블록 | `?target=` · **8블록** · `/sites/{id}/guide` 별도 | SiteController·SitePageResponse |
| 3 | §7·§14-6 | `POST /stamps/gps-check {pilgrimageId,courseSiteId,…}` · `userSentence` | `POST /stamps/{courseSiteId}/gps-check {siteId,…}` · `sentence` | StampController |
| 4 | §7·§14-10 | `POST /meditations/{id}/logs {playedSec}` | `POST /meditations/logs {meditationId, playedSeconds, memo?}` | MeditationLogRequest |
| 5 | §7·§14-11 | `POST /ebooks/personal` · `/download`·10분 | `POST /ebooks`(202/200) · `/download-url`(JSON) · **5분** | EbookController |
| 6 | §7·§14-11 | `copies·phone·zip` | `quantity·recipientName·recipientPhone·postalCode·address·addressDetail·note` | PrintOrderRequest |
| 7 | §7 | `GET /certificates/{id}/pdf` | `GET /certificates/{id}` → `downloadUrl` | CertificateController |
| 8 | §7·§14-13 | `PUT /rewards/{id}/shipping` | 없다. claim 본문 `{recipientName,phone,address,memo}` | RewardController |
| 9 | §7·§2·§8 | `/i18n/{locale}`·`/guide?locale=`·언어 4 | `/content/i18n/{lang}`·`/content/guide`(Accept-Language)·**ko·en 둘** | ContentController |
| 10 | §7 | 약관 1문 | GET·POST(배열)·DELETE(204) 3문 | UserController |
| 11 | §7·§14-5 | signup → `LoginResponse` | **`UserResponse`** — 토큰 없음 → 가입은 4단 | AuthController |
| 12 | §7-3 | bySlot 없으면 null | **404** | StampController |
| 13 | §7-4 | `PresignResponse{…,method,expiresIn}` | `{uploadUrl,fileKey,expiresInSeconds}` · jpeg·png만 | PresignResponse |
| 14 | §1-7 | `PUT /photos/{id}` 미채택 | **실재한다.** presign 과 함께 쓴다 | PhotoController |
| 15 | §1-15 | 로그아웃 200/204 | **200 확정** · 비로그인도 200 | AuthController |
| 16 | §1 | 쿠키 `refresh` | **`refreshToken`** · SameSite=Strict · local Secure=false | AuthController·application.yml |
| 17 | §14-4 | 9블록 · `Phrase` | **8블록** · `expansionPhrase` | SitePageResponse |
| 18 | §14-4 | `guideAvailable` | **없다**(grep 0) | 전 소스 |
| 19 | §14-6 | `STAMP-4221`·`4101` | `STAMP-4002/4003/4004`·`4091` | ErrorCode |
| 20 | §14-6 | 다짐+사진 | **+`hasOtherFace`·`expansionPhraseId`** | MissionSubmitRequest |
| 21 | §14-6 | PENDING 을 오류처럼 | **200 응답의 status 값** | StampService |
| 22 | §18 | QR = `/checkin?token=` | **순수 토큰 99자** — 11-A0 실측 확인, issueQr 초기 커밋 이후 무변경 → **⛔1(11-A STEP 3에서 수정)** | AdminSiteController |
| 23~40 | §15 | 에러코드 다수 | `ErrorCode.java` 79개로 전량 치환 | ErrorCode |
| 41 | §15 | – | 코드 앞 3자리 = HTTP 상태(79/79) | ErrorCode |
| 42 | §15·§5 | `error.fields` 미기술 | **배열** `[{field, reason}]` — `message` 아님 | ErrorResponse |
| 43 | §14-2·8 | 9권역 | **10권역**(region_id 4 결번) · 제주는 코스 0 | region 표 |
| 44 | §14-8 | 회향 하한 12 | API 없음 → 프론트 상수. **11-A STEP 7-3에서 "완주 권역 수 9"로 재정의** | RewardProperties |
| 45 | §14-11 | QUEUED/GENERATING/READY/FAILED | **REQUESTED/READY/FAILED** | Ebook |
| 46 | §14-11 | ebookType 3종 | **4종**(INTERIM 잔존) → 탭 3 + 기타 | Ebook·schema |
| 47 | §14-11 | 302·10분 | JSON · **5분** | EbookController |
| 48 | §14-14 | 언어 4 | **2(ko·en)** · ja·zh는 ko 폴백 | StaticContentLoader |
| 49 | §14-10 | 카테고리 칩 | `category` 는 `"1"~"5"` 문자열, 이름 없음 | 실측 |
| 50 | §14-15 | `POST /sites/{id}/qr` | `/api/admin/sites/{id}/qr[?validitySeconds=]` · 기본 **5분** | AdminSiteController |
| 51 | §14-15 | AdminContent(EDITOR) | **ADMIN 전용.** EDITOR 는 `/api/editor/**` 뿐 | SecurityConfig |
| 52 | §7-4 | 저장소 직접 PUT | ⛔ 받는 서버 없음(:9000 연결 실패 · PHOTO/ 0개) → **⛔2(11-A STEP 4에서 수신 문 신설)** | ObjectStorageClient |
| 53 | §19 | e2e 고정값 없음 | 계정·코스·자리·권역 표 추가 | 시드 실측 |
| 54 | §7 | thinkbox 상대 경로 | `/thinkbox/{id}` 로 폄 | – |
| **55** | §7·§11·§14 | 화면 21 + 관리자 13 | **화면 22 + 관리자 16** — 사진첩·AdminUsers·AdminCertificates·EditorManuscripts 추가(백엔드에 이미 있는 문 9개가 화면 없이 방치돼 있었다) | endpoints.md |
| **56** | §14-6·§14-8 | 도장 유일성 = 자리당 1개 | **권역·구절당 1개**(예성 확정) — 같은 권역 다른 코스의 같은 구절은 `STAMP-4090` | 11-A STEP 7-2 |
| **57** | §14-8·§14-12 | 회향 = 전 코스 완주 + 하한 12 | **코스가 있는 모든 권역에서 코스 1개씩 완주**(9권역, 제주 제외) | 11-A STEP 7-3 |
| **58** | §3-1·§21 | CORS 패턴 불가 | **사설 IP 대역 패턴 허용**(local만, prod 금지) · `X-Request-Id` 허용·노출 | 11-B |
| **59** | §14-3·§10 | ACTIVE 코스 1개 | **전 코스 ACTIVE**(좌표 확보분, 고운사 제외) | 11-A STEP 5·6 |
| **60** | §7-4 | 사진 크기 무제한 | 앱: 긴 변 1280px·JPEG 0.8 · 서버 상한 **2 MB**(초과 `COMMON-4130`) | 11-A STEP 4 |

---

## 0. 이 문서가 다루는 범위

| 파트 | 내용 | § |
|---|---|---|
| A | 확정 결정 · 폴더 구조 | 1 · 2 |
| B | Vite · 프록시(휴대폰) · Tailwind 토큰 | 3 · 4 |
| C | axios 클라이언트 · 에러 함수 · API 92문 | 5 · 6 · 7 |
| D | Query 키·캐시 / Zustand 3 store | 8 · 9 |
| E | 인증 전체(가입 4단·로그인·refresh·복원·가드) | 10 |
| F | 라우터 22화면 + 관리자 16 · App · main · 레이아웃 5 · Header | 11 · 12 |
| G | UX 원칙 · 화면별 설계 | 13 · 14 |
| H | 에러 문구표 · 공통 컴포넌트 | 15 · 16 |
| I | 지도 · 위치 · QR | 17 · 18 |
| J | 검증 · FE-1~FE-8 · 남은 ⛔ | 19 · 20 · 21 |

---

## 1. 확정 결정

| # | 항목 | 결정 | 이유 |
|---|---|---|---|
| 1 | Access 토큰 | **메모리(Zustand, persist 금지)** | 백엔드가 refresh 쿠키 회전·재사용 감지를 이미 함. localStorage는 XSS 한 방 |
| 2 | 새로고침 복원 | `POST /auth/refresh`(쿠키) → `GET /users/me` | 쿠키 없으면 조용히 비로그인 |
| 3 | Access 수명 | 30분(서버값). 프론트는 계산하지 않고 401에 반응 | `JWT_ACCESS_TTL` 로 테스트 시 단축(11-A #2) |
| 4 | 세션 | 60분. `expiresAt` 을 서버가 준다 | 카운트다운만 |
| 5 | 스탬프 API | `POST /stamps/{courseSiteId}/gps-check` → `/{stampId}/qr` → `/{stampId}/mission` | 정정 #3 |
| 6 | 싱글페이지 | `GET /sites/{siteId}/page?target=&locale=` · 8블록 | 정정 #2·#17 |
| 7 | 사진 | presign → **`PUT /api/uploads/{fileKey}`(11-A 신설)** → 받은 `photoKey` 를 `PUT /photos/{siteId}` 또는 미션 본문에 | 정정 #14·#52 |
| 8 | 인쇄 부수 | `quantity` 1~5 | 정정 #6 |
| 9 | 전자책 | `PILGRIMAGE·INTERIM·HOEHYANG·PERSONAL` 4종 → 탭 3 + 기타 | 정정 #46 |
| 10 | 카카오 | 사용자 화면은 JS 키(프론트), 관리자 장소검색은 REST 키(서버) | REST 키는 프론트에 없다 |
| 11 | 좌표 | 서버에 안 보낸다. 인터셉터가 `lat/lng` 키를 개발 모드 throw | 원칙① |
| 12 | 라우터 | 레이아웃 라우트 5 × 가드 4 × lazy 페이지 38 | §11 |
| 13 | main | `main.tsx`(TS 일관) | – |
| 14 | 에러 문구 | 79코드 + FE 전용 7. 인증·보안은 담백, 나머지는 발랄 | §15 |
| 15 | 로그아웃 | **200 확정**, 비로그인도 200 | 정정 #15 |
| **16** | **도장 유일성** | **(user, region, verse_no) 1개** — 한 권역에서 같은 구절 도장은 한 번 | 예성 확정 · 정정 #56 |
| **17** | **회향** | **코스가 있는 모든 권역에서 코스 1개씩 완주**(현재 9권역) | 정정 #57 |
| **18** | **QR 내용** | `{FRONTEND_URL}/checkin?token=…` (11-A STEP 3 이후) | 정정 #22 |

토큰 흐름:
```
가입   POST /api/auth/signup → 201 UserResponse (토큰 없음)
       → POST /api/auth/login → accessToken(메모리) + Set-Cookie refreshToken(HttpOnly·Path=/api/auth·SameSite=Strict·14일)
       → PATCH /api/users/me {tier}
       → POST /api/users/me/agreements [{agreementType, version}, …]
요청   Authorization: Bearer <메모리 토큰> · X-Request-Id: <8자리>
401    → POST /api/auth/refresh 1회 → 새 accessToken → 원 요청 재시도
       → refresh 도 401 이면 store.clear() + /login?next=현재경로
새로고침 main.tsx bootstrap(): refresh → 성공이면 GET /api/users/me
로그아웃 POST /api/auth/logout(200) → store.clear() → 홈
```

---

## 2. 폴더 구조 (루트 `frontend/`)

```
frontend/
├── index.html · package.json · tsconfig.json · postcss.config.cjs
├── vite.config.ts              # 프록시(/api→8080) · basic-ssl(폰) · alias @
├── tailwind.config.ts          # §4 토큰
├── .env.example / .env.local(gitignore)
├── public/fonts/ · public/sites/(대표 사진 + credits.json)
├── e2e/                        # Playwright 5흐름
└── src/
    ├── main.tsx                # bootstrapAuth() → App
    ├── App.tsx                 # QueryClientProvider + RouterProvider + ToastHost
    ├── router/index.tsx · paths.ts · guards/{ProtectedRoute,AdminRoute,EditorRoute,IntroGate}.tsx
    ├── api/
    │   ├── client.ts envelope.ts errorMessage.ts coordinateGuard.ts
    │   ├── types.d.ts          # generate-api-types.js 산출물(11-A #1 이후 컴파일 가능)
    │   └── modules/ auth users regions courses sites verses pilgrimages stamps uploads photos
    │                thinkbox meditations ebooks printOrders certificates rewards content
    │                admin/{sites courses stamps content rewards ebooks kakao users certificates housekeeping}
    │                editor/manuscripts
    ├── queries/ keys.ts invalidate.ts queryClient.ts use*.ts admin/*
    ├── stores/ authStore.ts uiStore.ts stampStore.ts
    ├── layouts/ RootLayout AppLayout AuthLayout FocusLayout AdminLayout
    ├── components/
    │   ├── ui/ Button Input PasswordInput Textarea Select Card Badge Skeleton EmptyState Toast Modal Sheet Progress Ring Tabs Chips QueryError StampMark
    │   ├── header/ Header UserMenu LocaleSwitch TierSwitch
    │   ├── nav/ BottomTabs FloatingHome
    │   ├── verse/ VerseCard VerseRibbon VerseQuote
    │   ├── site/ SiteCard SiteBlocks/*(8블록) GuideSheet
    │   ├── map/ KakaoMap SiteMarker MyLocationDot MapFallback
    │   ├── stamp/ GpsStep QrStep MissionStep StampResult SessionTimer
    │   ├── qr/ QrScanner · photo/ PhotoUploader
    │   └── error/ ErrorBoundary RouteError ErrorIllust
    ├── pages/                  # §11 표 — 사용자 22 + 관리자 16
    ├── hooks/ useGeolocation useOnline useCountdown useKakaoLoader useFocusBar useI18n
    ├── lib/ env.ts distance.ts image.ts(stripExif·resize) format.ts mask.ts validation.ts tier.ts errorTexts.ts bootstrap.ts devGuards.ts
    ├── i18n/ index.ts ko.json(폴백)
    └── styles/index.css
```
`pages/` 는 조립만. 데이터 접근은 `queries/`·`api/modules/` 에서만. 컴포넌트는 axios 를 import 하지 않는다.

---

## 3. Vite · 프록시(휴대폰) · 환경변수

### 3-1 접속 경로별 가능 여부 (정정 #58 반영)

| 상황 | 주소 | 위치·카메라 | 조회 API | 로그인(쿠키) |
|---|---|---|---|---|
| PC 개발 | `http://localhost:5173` | 됨(secure context) | 프록시 | 됨 |
| 폰 + 프록시(http) | `http://192.168.x.x:5173` | **거부**(http) | 됨 | 됨 |
| 폰 + 프록시 + basic-ssl | `https://192.168.x.x:5173` | 됨(경고 1회 수락) | 됨 | 됨 ← **권장** |
| 폰 → 8080 직접 | `http://192.168.x.x:8080` | 앱이 아니라 API | **11-B 이후 됨**(사설 IP 패턴) | SameSite=Strict라 11-B STEP 5 결과에 따름 |
| 터널(cloudflared) | `https://xxx.trycloudflare.com` | 됨 | 됨 | 됨 |

프록시를 쓰면 같은 출처가 되어 CORS·쿠키 문제가 애초에 없다. 8080 직접은 디버깅용.

### 3-2 `vite.config.ts`
```ts
import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import basicSsl from '@vitejs/plugin-basic-ssl'
import path from 'node:path'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const useHttps = env.VITE_DEV_HTTPS === 'true'
  return {
    plugins: [react(), ...(useHttps ? [basicSsl()] : [])],
    resolve: { alias: { '@': path.resolve(__dirname, 'src') } },
    server: {
      host: true, port: 5173, strictPort: true,
      proxy: { '/api': { target: env.VITE_PROXY_TARGET || 'http://localhost:8080', changeOrigin: true } },
    },
    build: {
      target: 'es2022', sourcemap: false,
      rollupOptions: { output: { manualChunks: {
        vendor: ['react','react-dom','react-router-dom','@tanstack/react-query','axios','zustand'],
        zxing: ['@zxing/browser'],
      }}},
    },
    test: { environment: 'jsdom', setupFiles: './src/test/setup.ts', globals: true },
  }
})
```

### 3-3 `.env.example`
```
VITE_API_BASE_URL=        # 비우면 '/api'(프록시). 배포: https://temple-stamp.onrender.com
VITE_KAKAO_JS_KEY=        # JavaScript 키(REST 키 아님)
VITE_DEV_HTTPS=false      # 폰 위치·카메라 테스트 시 true
VITE_PROXY_TARGET=http://localhost:8080
VITE_PUBLIC_BASE_URL=     # 인증서 진위 링크 표시용
```
`import.meta.env` 접근은 `lib/env.ts` 한 곳. 누락 시 개발은 warn, 배포 빌드는 throw.

### 3-4 카카오 도메인 등록
콘솔 → 플랫폼 Web 에 `http://localhost:5173`, `https://192.168.x.x:5173`, Netlify 주소. 미등록 출처에서는 SDK 가 조용히 실패 → `useKakaoLoader` 10초 타임아웃 후 목록 폴백.

---

## 4. Tailwind 토큰 ("산문을 지나 경내로")

| 토큰 | 값 | 쓰임 |
|---|---|---|
| ink-900 / 600 / 300 | #1B1A17 / #4A473F / #A9A396 | 본문·보조·비활성 |
| paper-50 / 100 / 200 | #F6F1E7 / #EDE6D6 / #E1D8C2 | 한지 배경·카드·구분선 |
| ochre-500 / 600 / 100 | #B8862B / #9A6F22 / #F3E7CC | 주 액션·hover·연한 배경 |
| verse-1~5 | #B8862B #5B6B4A #6C7A89 #7E5A9B #A24B3D | 오관게 5구(출처·감사·절제·포행·다짐) |
| success / warn / danger | #4F7A4A / #C58A2A / #A24B3D | 상태 |
| 서체 | Noto Sans KR / Noto Serif KR(구절·인용) | |

```ts
// tailwind.config.ts
import type { Config } from 'tailwindcss'
export default {
  content: ['./index.html','./src/**/*.{ts,tsx}'],
  theme: { extend: {
    colors: {
      ink:{900:'#1B1A17',600:'#4A473F',300:'#A9A396'},
      paper:{50:'#F6F1E7',100:'#EDE6D6',200:'#E1D8C2'},
      ochre:{500:'#B8862B',600:'#9A6F22',100:'#F3E7CC'},
      verse:{1:'#B8862B',2:'#5B6B4A',3:'#6C7A89',4:'#7E5A9B',5:'#A24B3D'},
      success:'#4F7A4A', warn:'#C58A2A', danger:'#A24B3D',
    },
    fontFamily:{ sans:['"Noto Sans KR"','system-ui','sans-serif'], serif:['"Noto Serif KR"','serif'] },
    borderRadius:{ seal:'0.9rem' },
    boxShadow:{ card:'0 1px 2px rgba(27,26,23,.06), 0 8px 24px rgba(27,26,23,.06)' },
    keyframes:{
      ink:{'0%':{transform:'scale(.6)',opacity:'0'},'60%':{transform:'scale(1.08)',opacity:'1'},'100%':{transform:'scale(1)'}},
      sway:{'0%,100%':{transform:'rotate(-0.6deg)'},'50%':{transform:'rotate(0.6deg)'}},
    },
    animation:{ ink:'ink .6s cubic-bezier(.2,.8,.2,1) both', sway:'sway 4s ease-in-out infinite' },
  }},
  plugins: [],
} satisfies Config
```
```css
/* styles/index.css */
@tailwind base; @tailwind components; @tailwind utilities;
:root { --fs-base:16px; --btn-h:44px; --tap:44px; }
[data-tier="RIDER"]   { --fs-base:20px; --btn-h:56px; --tap:56px; }
[data-tier="FOREIGN"] { --fs-base:16px; --btn-h:48px; --tap:48px; }
html { font-size:var(--fs-base); background:theme('colors.paper.50'); color:theme('colors.ink.900'); }
.btn { @apply inline-flex items-center justify-center rounded-seal font-medium transition active:scale-[.98]; height:var(--btn-h); }
```
`data-tier` 는 RootLayout 이 `authStore.user.tier`(없으면 uiStore.previewTier, 없으면 AGE30)를 `<html>` 에 붙인다.
타겟 차이 — **2030** 구절 우선·감성 카드 / **RIDER** 큰 글자·큰 버튼·주행 블록 상단 고정 / **FOREIGN** 한자 유지·언어 스위치 상시·아이콘 병기(단 언어는 en 하나뿐, 정정 #48).

---

## 5. axios 공통 클라이언트

### 5-1 책임

| 한다 | 안 한다 |
|---|---|
| baseURL·timeout 60초·`withCredentials` | 토큰 저장(localStorage) |
| `Authorization: Bearer` 부착 | 만료시각 계산 |
| `X-Request-Id` 생성·전송, 응답 헤더 읽기(11-B 이후 교차 출처에서도 가능) | 화면 이동 |
| body·params 에 `lat/lng/latitude/longitude` 재귀 검사 → 개발 throw | 좌표 계산 |
| 봉투 해제 → `data` 반환 | 성공 토스트 |
| 401 → refresh 1회 → 재시도(동시 401은 한 번만) | 403(→ 가드) |
| 204·빈 본문 → undefined | |
| 네트워크·타임아웃 1회 재시도(GET·멱등) | |

### 5-2 `api/envelope.ts` — **`fields` 는 배열이다(정정 #42)**
```ts
export interface FieldError { field: string; reason: string }
export interface ApiEnvelope<T> {
  success: boolean
  data: T | null
  error: { code: string; message: string; fields?: FieldError[] | null } | null
  timestamp: string
}
export interface Page<T> { items: T[]; page: number; size: number; totalCount: number; hasNext: boolean }  // totalPages 없음(정정 #42)

export class ApiError extends Error {
  constructor(
    public readonly code: string,
    public readonly status: number,
    message: string,
    public readonly fields?: FieldError[] | null,
    public readonly requestId?: string,
  ) { super(message); this.name = 'ApiError' }
  get domain() { return this.code.split('-')[0] }
  is(prefix: string) { return this.code.startsWith(prefix) }
  /** RHF setError 용 맵으로 변환 */
  get fieldMap(): Record<string, string> {
    return (this.fields ?? []).reduce((a, f) => ({ ...a, [f.field]: f.reason }), {})
  }
}
```

### 5-3 `api/coordinateGuard.ts`
```ts
const BANNED = new Set(['lat','lng','latitude','longitude','lon','coords'])
export function assertNoCoordinates(v: unknown, path = 'body'): void {
  if (v == null || typeof v !== 'object') return
  if (v instanceof FormData || v instanceof Blob) return
  for (const [k, val] of Object.entries(v as Record<string, unknown>)) {
    if (BANNED.has(k.toLowerCase())) {
      const msg = `[원칙①] 서버로 좌표를 보내려 했습니다: ${path}.${k}`
      if (import.meta.env.DEV) throw new Error(msg)
      console.error(msg); delete (v as Record<string, unknown>)[k]; continue
    }
    assertNoCoordinates(val, `${path}.${k}`)
  }
}
```

### 5-4 `api/client.ts`
```ts
import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '@/stores/authStore'
import { ApiError, type ApiEnvelope } from './envelope'
import { assertNoCoordinates } from './coordinateGuard'
import { env } from '@/lib/env'

const baseURL = env.API_BASE_URL ? `${env.API_BASE_URL}/api` : '/api'
export const http = axios.create({ baseURL, timeout: 60_000, withCredentials: true })
const bare = axios.create({ baseURL, timeout: 20_000, withCredentials: true })   // refresh 전용

type Cfg = InternalAxiosRequestConfig & { _retried?: boolean; _netRetried?: boolean; skipAuth?: boolean; idempotent?: boolean }
const rid = () => crypto.randomUUID().slice(0, 8)

http.interceptors.request.use((c: Cfg) => {
  assertNoCoordinates(c.data); assertNoCoordinates(c.params, 'params')
  if (c.url?.endsWith('/')) throw new Error(`[경로] 끝 슬래시 금지(401 원인): ${c.url}`)   // Boot3 trailing slash 미매칭
  c.headers['X-Request-Id'] = rid()
  const t = useAuthStore.getState().accessToken
  if (t && !c.skipAuth) c.headers.Authorization = `Bearer ${t}`
  return c
})

let refreshing: Promise<string | null> | null = null
export async function refreshAccessToken(): Promise<string | null> {
  if (!refreshing) {
    refreshing = bare.post<ApiEnvelope<{ accessToken: string }>>('/auth/refresh')
      .then(r => { const t = r.data.data?.accessToken ?? null; useAuthStore.getState().setAccessToken(t); return t })
      .catch(() => { useAuthStore.getState().clear(); return null })
      .finally(() => { refreshing = null })
  }
  return refreshing
}

http.interceptors.response.use(
  (res) => {
    if (res.status === 204 || res.data === '' || res.data == null) return undefined as never
    const e = res.data as ApiEnvelope<unknown>
    if (e && typeof e === 'object' && 'success' in e) {
      if (e.success) return e.data as never
      throw new ApiError(e.error?.code ?? 'COMMON-5000', res.status, e.error?.message ?? '', e.error?.fields, res.headers['x-request-id'])
    }
    return res.data as never
  },
  async (err: AxiosError<ApiEnvelope<unknown>>) => {
    const cfg = err.config as Cfg | undefined
    const status = err.response?.status ?? 0
    const body = err.response?.data
    const reqId = err.response?.headers?.['x-request-id']
    if (status === 401 && cfg && !cfg._retried && !cfg.skipAuth && !cfg.url?.startsWith('/auth/')) {
      cfg._retried = true
      const t = await refreshAccessToken()
      if (t) { cfg.headers.Authorization = `Bearer ${t}`; return http(cfg) }
    }
    if (!err.response && cfg && !cfg._netRetried && (cfg.method === 'get' || cfg.idempotent)) {
      cfg._netRetried = true
      await new Promise(r => setTimeout(r, 1500))
      return http(cfg)
    }
    if (!err.response) throw new ApiError(err.code === 'ECONNABORTED' ? 'NET-0504' : 'NET-0000', 0, err.message)
    throw new ApiError(body?.error?.code ?? `COMMON-${status}0`, status, body?.error?.message ?? err.message, body?.error?.fields, reqId)
  },
)
```
`http.get<never, T>()` 형태로 쓰면 봉투가 벗겨진 `data` 가 반환값이 된다.

### 5-5 `api/modules/_helpers.ts`
```ts
import { http } from '../client'
export const get   = <T>(url: string, params?: object) => http.get<never, T>(url, { params })
export const post  = <T>(url: string, body?: object, idempotent = false) => http.post<never, T>(url, body, { idempotent } as never)
export const put   = <T>(url: string, body?: object) => http.put<never, T>(url, body)
export const patch = <T>(url: string, body?: object) => http.patch<never, T>(url, body)
export const del   = (url: string) => http.delete<never, void>(url)
```

---

## 6. 에러 처리 배치

### 6-1 `api/errorMessage.ts`
```ts
import { ApiError } from './envelope'
import { MESSAGES, DOMAIN_FALLBACK } from '@/lib/errorTexts'

export interface UiError { title: string; body: string; action?: 'login'|'retry'|'passport'|'wait'|'settings'|'none'; tone: 'fun'|'plain' }
export function toUiError(e: unknown): UiError {
  if (e instanceof ApiError) {
    const hit = MESSAGES[e.code]; if (hit) return hit
    const dom = DOMAIN_FALLBACK[e.domain]; if (dom) return { ...dom, body: e.message || dom.body }
    if (e.status >= 500) return MESSAGES['COMMON-5000']
    return { title: '잠깐만요', body: e.message || '요청을 처리하지 못했어요.', action: 'retry', tone: 'plain' }
  }
  if (e instanceof Error && e.message.startsWith('[원칙①]')) return { title: '개발자 실수', body: e.message, action: 'none', tone: 'plain' }
  return MESSAGES['COMMON-5000']
}
export const fieldErrors = (e: unknown) => (e instanceof ApiError ? e.fieldMap : {})
```

### 6-2 층별 책임

| 층 | 잡는 것 | 하는 일 |
|---|---|---|
| client.ts | 401·네트워크 | refresh·재시도 후 ApiError |
| queries/* | 쿼리 실패 | `retry: (n,e)=> e.status>=500 && n<2` |
| 페이지 useQuery | isError | `<QueryError error onRetry={refetch} />` |
| useMutation.onError | 폼 실패 | `fieldErrors` → RHF setError, 나머지 Toast |
| ErrorBoundary | 렌더 예외 | scope=block/route/app |
| router errorElement | 404·lazy 실패 | RouteError |
| useOnline | 오프라인 | 상단 배너 + 수동 재시도 |
| 가드 | 미로그인·403 | 리다이렉트 / 403 화면 |

---

## 7. API 모듈 (92문) — 타입은 `api/types.d.ts` 에서만 import

> 개수: 기존 91 + 11-A STEP 4 신설 `PUT /api/uploads/{fileKey}` = **92**. `GET /health` 는 actuator 라 이 수 밖이다.

### 7-1 사용자 문 (54)

| 모듈 | 문 | 권한 | 화면 |
|---|---|---|---|
| auth | `POST /auth/signup`(201·**UserResponse**) · `/auth/login` · `/auth/refresh` · `/auth/logout`(200) | 없음 | S-16 |
| users | `GET·PATCH /users/me` · `DELETE /users/me`(204·비번 재확인) · `GET /users/me/agreements` · `POST /users/me/agreements`(배열) · `DELETE /users/me/agreements/{type}`(204) | USER | S-15 |
| regions | `GET /regions`(목록만) | 없음 | S-03′ |
| courses | `GET /courses?regionId=` · `GET /courses/{id}?target=` | 선택 | S-04·04′ |
| sites | `GET /sites/{id}` · `GET /sites/{id}/page?target=&locale=`(8블록) · `GET /sites/{id}/guide`(7칸·Accept-Language 안 받음) | 선택/없음 | S-05·S-17 |
| verses | `GET /verses` · `/verses/{verseNo}` | 없음 | S-01·02 |
| pilgrimages | `POST /pilgrimages {courseId}`(멱등) · `GET /passport` | USER | S-04′·S-03 |
| stamps | `POST /stamps/{courseSiteId}/gps-check {siteId,withinRadius,accuracyGrade}` · `POST /stamps/{id}/qr {qrToken}` · `POST /stamps/{id}/mission {sentence,photoKey?,hasOtherFace?,expansionPhraseId?}` · `POST /stamps/evidence {courseSiteId,siteId,sentence,photoKey}` · `GET /stamps/{id}` · `GET /stamps/by-slot/{courseSiteId}`(없으면 **404**) | USER | S-06~07 |
| uploads | `GET /uploads/presign?purpose=&contentType=` → `{uploadUrl,fileKey,expiresInSeconds}` · **`PUT /uploads/{fileKey}`(신설·201)** | USER | S-08 |
| photos | `PUT /photos/{siteId}` · `GET /photos/{siteId}` · `GET /photos` | USER | S-08·**S-18 사진첩** |
| thinkbox | `GET /thinkbox?sort=date\|course\|site&page=&size=` · `POST` · `PATCH /{id}` · `DELETE /{id}`(204) · `GET /thinkbox/flashback`(없으면 200+null) | USER | S-11 |
| meditations | `GET /meditations?category=`(공개·`"1"~"5"`) · `GET /meditations/{id}` · `POST /meditations/logs {meditationId,playedSeconds,memo?}` | 없음/USER | S-09·10 |
| ebooks | `GET /ebooks` · `GET /ebooks/{id}` · `POST /ebooks`(202 새로/200 기존) · `GET /ebooks/{id}/download-url`(JSON·5분·READY만) | USER | S-13 |
| printOrders | `GET /print-orders` · `GET /print-orders/{id}`(배송정보) · `POST /print-orders {ebookId,quantity 1~5,recipientName,recipientPhone,postalCode,address,addressDetail?,note?}`(201) · `DELETE /{id}`(204·cancelable 일 때) | USER | S-13′ |
| certificates | `GET /certificates` · `GET /certificates/{id}`(downloadUrl·VALID만) · `GET /certificates/verify/{serialNo}`(**공개**·7필드) | USER/없음 | S-14·14′ |
| rewards | `GET /rewards`(claimable 서버 계산) · `POST /rewards/{id}/claim {recipientName,phone,address,memo?}` | USER | S-12 |
| content | `GET /content/i18n/{lang}`(ko·en) · `GET /content/guide`(Accept-Language) | 없음 | 전 화면 |

### 7-2 편집자 (4 · EDITOR+ADMIN) — **v2 에 화면이 없었다(정정 #55)**
`POST /editor/manuscripts`(201) · `PUT /editor/manuscripts/{id}` · `POST /editor/manuscripts/{id}/submit` · `GET /editor/manuscripts`
상태 `DRAFT → SUBMITTED → APPROVED → RETIRED`(+REJECTED). **삭제 없음 — 퇴역만.**

### 7-3 관리자 (34 · ADMIN)
사찰 8(`GET·POST /admin/sites` · `PUT /{id}` · `PATCH /{id}/status` · `/{id}/viewpoints` · `/{id}/badges` · `POST /{id}/elements` · `DELETE /{id}/elements/{code}`(204)) ·
QR 2(`POST /{id}/qr[?validitySeconds=]` · `/{id}/qr/rotate`) · 코스 4(`POST /admin/courses` · `PUT /{id}` · `PATCH /{id}/status` · `PUT /admin/site-distances`) ·
카카오 1(`GET /admin/kakao/places?query=`) · 도장 2(`GET /admin/stamps/pending` · `POST /{id}/review`) · 완주 2(`POST /admin/completions/recount[/{userId}]`) ·
인증서 1(`POST /admin/certificates/{id}/revoke`) · 보상 2 · 원고 5(목록·import?dryRun=·approve·reject·retire) · 전자책·인쇄 3 · 사용자 1(`GET /admin/users`) · 운영 1(`POST /admin/housekeeping/run`) · 콘텐츠 2(`POST /admin/contents/phrases`·`missions`)

### 7-4 모듈 예시

```ts
// api/modules/auth.ts — 가입은 토큰을 주지 않는다(정정 #11)
export const authApi = {
  signup: (b: SignupRequest) => post<UserResponse>('/auth/signup', b),
  login:  (b: LoginRequest)  => post<LoginResponse>('/auth/login', b),
  logout: () => post<void>('/auth/logout'),
  me:     () => get<UserResponse>('/users/me'),
  agreements: () => get<AgreementResponse[]>('/users/me/agreements'),
  agree:  (list: AgreementRequest[]) => post<void>('/users/me/agreements', list as unknown as object),
  updateMe: (b: UserUpdateRequest) => patch<UserResponse>('/users/me', b),
}
```
```ts
// api/modules/stamps.ts — 원칙①의 대표
export const stampApi = {
  gpsCheck: (courseSiteId: number, b: GpsCheckRequest) => post<GpsCheckResponse>(`/stamps/${courseSiteId}/gps-check`, b),
  qr:       (stampId: number, b: QrVerifyRequest) => post<StampStatusResponse>(`/stamps/${stampId}/qr`, b),
  mission:  (stampId: number, b: MissionSubmitRequest) => post<MissionResultResponse>(`/stamps/${stampId}/mission`, b),
  evidence: (b: EvidenceRequest) => post<EvidenceResponse>('/stamps/evidence', b),
  get:      (stampId: number) => get<StampStatusResponse>(`/stamps/${stampId}`),
  bySlot:   (courseSiteId: number) => get<StampStatusResponse>(`/stamps/by-slot/${courseSiteId}`),   // 없으면 404
}
```
```ts
// api/modules/uploads.ts — 11-A STEP 4 이후 실제로 바이트가 저장된다(정정 #52·#60)
import axios from 'axios'
import { resizeAndStrip } from '@/lib/image'
export const uploadApi = {
  presign: (purpose: 'PHOTO'|'EVIDENCE', contentType: string) =>
    get<PresignResponse>('/uploads/presign', { purpose, contentType }),   // jpeg·png 만
  async putFile(file: File, purpose: 'PHOTO'|'EVIDENCE', onProgress?: (p:number)=>void) {
    const small = await resizeAndStrip(file, 1280, 0.8)                    // 긴 변 1280 · EXIF 제거 · 2MB 미만 보장
    const p = await this.presign(purpose, 'image/jpeg')
    await axios.put(p.uploadUrl, small, {
      headers: { 'Content-Type': 'image/jpeg' }, withCredentials: false,
      onUploadProgress: e => onProgress?.(e.total ? Math.round(e.loaded/e.total*100) : 0),
    })
    return p.fileKey
  },
}
```
`lib/image.ts` — `createImageBitmap` → canvas → `toBlob('image/jpeg', q)`. 2 MB 초과면 q 를 0.7로 한 번 더. 서버 상한 초과는 `COMMON-4130`.

---

## 8. TanStack Query

### 8-1 키
```ts
export const qk = {
  me:['me'] as const, verses:['verses'] as const, regions:['regions'] as const,
  courses:(regionId?:number)=>['courses',{regionId}] as const, course:(id:number)=>['courses',id] as const,
  site:(id:number)=>['sites',id] as const,
  sitePage:(id:number,target?:string,locale?:string)=>['sites',id,'page',{target,locale}] as const,
  siteGuide:(id:number)=>['sites',id,'guide'] as const,
  passport:['passport'] as const,
  stamp:(id:number)=>['stamps',id] as const, stampBySlot:(cs:number)=>['stamps','slot',cs] as const,
  photos:['photos'] as const, photo:(siteId:number)=>['photos',siteId] as const,
  thinkbox:(sort:string)=>['thinkbox',{sort}] as const, flashback:['thinkbox','flashback'] as const,
  meditations:(c?:string)=>['meditations',{c}] as const, meditation:(id:number)=>['meditations',id] as const,
  ebooks:['ebooks'] as const, ebook:(id:number)=>['ebooks',id] as const,
  printOrders:['print-orders'] as const, printOrder:(id:number)=>['print-orders',id] as const,
  certificates:['certificates'] as const, verify:(s:string)=>['verify',s] as const,
  rewards:['rewards'] as const, i18n:(lang:string)=>['i18n',lang] as const, guide:['guide'] as const,
  admin:{ sites:['admin','sites'] as const, pending:['admin','stamps','PENDING'] as const,
          users:['admin','users'] as const, manuscripts:['admin','manuscripts'] as const },
  editor:{ manuscripts:['editor','manuscripts'] as const },
}
```

### 8-2 캐시

| 쿼리 | staleTime | 비고 |
|---|---|---|
| verses · i18n · guide · meditations | 24h | 거의 불변 |
| regions · courses · passport | 60s | 로그인 여부로 값이 달라짐 → 로그인/로그아웃 시 `qc.clear()` |
| sitePage | **0 · gcTime 0** | 확장문구·과제가 매번 달라야 함 |
| stamp · stampBySlot | 0 | 상태 전이 중 |
| ebook 상세 | 0 + `refetchInterval 30s`(REQUESTED 일 때만) | 조판 대기 |
| 나머지 | 60s | |

### 8-3 무효화
```ts
export const invalidateAfterStamp = (qc: QueryClient) =>
  Promise.all([qk.passport, qk.regions, qk.certificates, qk.ebooks, qk.rewards, qk.photos]
    .map(k => qc.invalidateQueries({ queryKey: k })))
export const invalidateAfterLocale = (qc: QueryClient) => qc.invalidateQueries()
export const resetOnAuthChange = (qc: QueryClient) => qc.clear()
```
QueryClient 기본: `retry:(n,e)=>(e as ApiError).status>=500 && n<2`, `refetchOnWindowFocus:false`, `mutations.retry:0`.

### 8-4 홈 4호출 병렬
```ts
export function useHome() {
  const authed = useAuthStore(s => !!s.user)
  return useQueries({ queries: [
    { queryKey: qk.verses,      queryFn: verseApi.list,                     staleTime: 86_400_000 },
    { queryKey: qk.passport,    queryFn: pilgrimageApi.passport,            enabled: authed, staleTime: 60_000 },
    { queryKey: qk.flashback,   queryFn: thinkboxApi.flashback,             enabled: authed, staleTime: 60_000 },
    { queryKey: qk.meditations(), queryFn: () => meditationApi.list(),      staleTime: 86_400_000 },
  ]})
}
```

---

## 9. Zustand — store 3개

```ts
// stores/authStore.ts — persist 금지
type Status = 'idle'|'restoring'|'authed'|'guest'
interface AuthState {
  status: Status; accessToken: string|null; user: UserResponse|null
  setAccessToken:(t:string|null)=>void; setSession:(t:string,u:UserResponse)=>void
  setUser:(u:UserResponse)=>void; setStatus:(s:Status)=>void; clear:()=>void
}
export const useAuthStore = create<AuthState>((set) => ({
  status:'idle', accessToken:null, user:null,
  setAccessToken:(accessToken)=>set({accessToken}),
  setSession:(accessToken,user)=>set({accessToken,user,status:'authed'}),
  setUser:(user)=>set({user,status:'authed'}),
  setStatus:(status)=>set({status}),
  clear:()=>set({accessToken:null,user:null,status:'guest'}),
}))
export const selectIsAdmin  = (s:AuthState)=> s.user?.role==='ADMIN'
export const selectIsEditor = (s:AuthState)=> s.user?.role==='EDITOR' || s.user?.role==='ADMIN'
export const selectTier     = (s:AuthState)=> s.user?.tier ?? 'AGE30'
```
```ts
// stores/uiStore.ts — persist 는 여기만(키 ts-ui, partialize: lang·previewTier·introDone)
interface UiState {
  lang:'ko'|'en'; previewTier:Tier|null; introDone:boolean          // 정정 #48: 언어는 둘
  toasts:Toast[]; pushToast:(t:Omit<Toast,'id'>)=>void; dismissToast:(id:string)=>void
  setLang:(l:UiState['lang'])=>void; setIntroDone:()=>void; setPreviewTier:(t:Tier|null)=>void
}
```
```ts
// stores/stampStore.ts — sessionStorage persist(좌표는 애초에 없다)
interface StampState {
  stampId:number|null; courseSiteId:number|null; siteId:number|null; pilgrimageId:number|null
  step:'GPS'|'QR'|'MISSION'|'DONE'; expiresAt:string|null; qrLocationHint:string|null
  pendingQrToken:string|null                                        // /checkin 으로 먼저 들어온 경우 보관
  begin:(p:{pilgrimageId:number;courseSiteId:number;siteId:number})=>void
  gpsDone:(r:GpsCheckResponse)=>void; qrDone:()=>void; finish:()=>void; reset:()=>void
}
```
복귀 시 `GET /stamps/{id}` 로 서버 상태와 대조해 step 을 맞춘다 — **서버가 진실**.

---

## 10. 인증 흐름

### 10-1 zod 스키마 — signup 은 이메일·비번·닉네임만 받는다(정정 #11)
```ts
export const signupSchema = z.object({
  email: z.string().trim().toLowerCase().email('이메일 모양이 아니에요').max(100),
  password: z.string().min(8,'8자 이상').max(50)
    .regex(/^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z\d]).+$/,'영문·숫자·특수문자를 섞어 주세요'),
  passwordConfirm: z.string(),
  nickname: z.string().trim().min(2,'2자 이상').max(20,'20자 이하'),
}).refine(d => d.password === d.passwordConfirm, { path:['passwordConfirm'], message:'비밀번호가 서로 달라요' })

export const profileSchema = z.object({ tier: z.enum(['AGE20','AGE30','AGE40','AGE50','AGE60','RIDER','FOREIGN']) })
export const agreeSchema = z.object({ location: z.literal(true,{errorMap:()=>({message:'위치 서비스 동의가 필요해요'})}), ebookPublic: z.boolean() })
export const loginSchema = z.object({ email: z.string().trim().min(1,'이메일을 적어 주세요'), password: z.string().min(1,'비밀번호를 적어 주세요') })
```

### 10-2 가입 4단 체이닝
```ts
export function useSignupFlow() {
  const qc = useQueryClient(); const setSession = useAuthStore(s=>s.setSession)
  return useMutation({
    mutationFn: async (v: SignupForm & { tier: Tier; ebookPublic: boolean }) => {
      await authApi.signup({ email:v.email, password:v.password, nickname:v.nickname })   // 201, 토큰 없음
      const s = await authApi.login({ email:v.email, password:v.password })
      setSession(s.accessToken, s.user)
      await authApi.updateMe({ tier: v.tier })
      const list = [{ agreementType:'LOCATION_SERVICE', version:'1.0' },
        ...(v.ebookPublic ? [{ agreementType:'EBOOK_PUBLIC', version:'1.0' }] : [])]
      await authApi.agree(list)
      return authApi.me()
    },
    onSuccess: (u) => { useAuthStore.getState().setUser(u); resetOnAuthChange(qc) },
  })
}
```
화면은 3스텝(계정 → 타겟 → 동의)이고 마지막 버튼 한 번에 위 4호출이 순서대로 나간다. 중간 실패 시: signup 실패 → 그 자리 / login 이후 실패 → "가입은 됐어요. 설정에서 마저 정하실 수 있어요" + 홈.

### 10-3 복원
```ts
export async function bootstrapAuth() {
  const s = useAuthStore.getState(); s.setStatus('restoring')
  const t = await refreshAccessToken()
  if (!t) return s.clear()
  try { s.setUser(await authApi.me()) } catch { s.clear() }
}
```
`restoring` 동안 스플래시. 10초 넘으면 "서버를 깨우는 중" 문구(콜드스타트).

### 10-4 가드
```tsx
export function ProtectedRoute() {
  const st = useAuthStore(s=>s.status); const loc = useLocation()
  if (st==='idle'||st==='restoring') return <FullscreenSkeleton />
  if (st==='guest') return <Navigate to={`/login?next=${encodeURIComponent(loc.pathname+loc.search)}`} replace />
  return <Outlet />
}
export function AdminRoute()  { const st=useAuthStore(s=>s.status); const ok=useAuthStore(selectIsAdmin);  if(st!=='authed') return <ProtectedRoute/>; return ok?<Outlet/>:<ForbiddenPage/> }
export function EditorRoute() { const st=useAuthStore(s=>s.status); const ok=useAuthStore(selectIsEditor); if(st!=='authed') return <ProtectedRoute/>; return ok?<Outlet/>:<ForbiddenPage/> }
export function IntroGate()   { return useUiStore(s=>s.introDone) ? <Outlet/> : <Navigate to="/intro" replace/> }
```

### 10-5 잠금 카운트다운 — 서버가 남은 시간을 주지 않는다
`AUTH-4031` 은 메시지에 "15분" 문자열만 있다. 프론트는 `lockedUntil = Date.now()+15분` 을 **sessionStorage** 에 넣어 새로고침해도 이어지게 한다(정확한 값이 아님을 문구로 알린다: "약 15분").

### 10-6 테스트 시나리오

| # | 시나리오 | 기대 |
|---|---|---|
| 1 | 가입 4단 | 201 → 로그인 → tier → 동의 → 헤더에 닉네임 |
| 2 | 새로고침 | refresh → me, 로그인 유지 |
| 3 | 잘못된 비번 | `AUTH-4011` · 계정 존재 여부 미노출 |
| 4 | 5회 실패 | `AUTH-4031` + 약 15분 카운트다운 |
| 5 | Access 만료(`JWT_ACCESS_TTL=10`) | 401 → refresh → 재시도, 깜빡임 없음 |
| 6 | 쿠키 삭제 후 요청 | `/login?next=` |
| 7 | 로그아웃 후 뒤로가기 | 보호 페이지 진입 불가 |
| 8 | USER 로 `/admin` | 403 화면 |
| 9 | 재사용 감지(`AUTH-4015`) | "보안을 위해 모든 기기에서 로그아웃했어요" 표시 후 `/login` |

---

## 11. 라우터 — 사용자 22 + 관리자 16 (정정 #55)

### 11-1 화면표

| S | 화면 | 경로 | 레이아웃 | 가드 | 주 쿼리 |
|---|---|---|---|---|---|
| S-01 | 인트로 3단 | `/intro` | Focus | – | – |
| S-02 | 홈 | `/` | App | IntroGate | verses·passport·flashback·meditations |
| S-03 | 여권 | `/passport` | App | Protected | passport |
| S-03′ | 권역 목록 | `/regions` | App | – | regions |
| S-04 | 권역 상세 | `/regions/:regionId` | App | – | regions + courses?regionId |
| S-04′ | 코스 상세 = **사찰 목록** | `/courses/:courseId` | App | – | course(+bySlot×5) |
| S-05 | 사찰 상세(8블록) | `/sites/:siteId` | App | – | sitePage |
| S-06 | 현장 인증 3단계 | `/sites/:siteId/verify` | Focus | Protected | bySlot·stamp |
| S-06′ | QR 체크인 | `/checkin?token=` | Focus | Protected | stamp |
| S-06″ | 예외 접수 | `/sites/:siteId/evidence` | Focus | Protected | – |
| S-07 | 발급 결과 | `/stamps/:stampId` | Focus | Protected | stamp |
| S-08 | 사진(미션 내부) | – | – | – | presign·PUT |
| S-09 | 명상 목록 | `/meditations` | App | – | meditations |
| S-10 | 명상 재생 | `/meditations/:id` | Focus | – | meditation |
| S-11 | 생각상자 | `/thinkbox` | App | Protected | thinkbox |
| S-12 | 보상 | `/rewards` | App | Protected | rewards |
| S-13 | 전자책 | `/ebooks` | App | Protected | ebooks |
| S-13′ | 인쇄 신청·목록 | `/ebooks/print` | App | Protected | print-orders |
| S-14 | 인증서 | `/certificates` | App | Protected | certificates |
| S-14′ | 진위 확인(공개) | `/verify/:serialNo` | Auth | – | verify |
| S-15 | 설정·탈퇴 | `/settings` | App | Protected | me·agreements |
| S-16 | 로그인 / 가입 | `/login`, `/signup` | Auth | authed면 홈 | – |
| S-17 | 사찰 가는 법 | `/guide` | App | – | content/guide |
| **S-18** | **사진첩(내 사진 전부)** | `/photos` | App | Protected | photos |
| – | 404 / 403 | `*`, `/403` | App | – | – |

관리자 16: AdminHome · Sites · SiteForm · KakaoSearch · Qr · Courses · CourseForm · SiteDistances · StampReview · Rewards · Ebooks · PrintOrders · Content(ADMIN) · Manuscripts(ADMIN 심사) · **Users** · **Certificates(회수)**
편집자 1: **EditorManuscripts**(`/editor/manuscripts` · EditorRoute) — 작성·수정·제출·내 목록

### 11-2 `router/paths.ts`
```ts
export const P = {
  intro:'/intro', home:'/', login:'/login', signup:'/signup',
  passport:'/passport', regions:'/regions', region:(id:number|string)=>`/regions/${id}`,
  course:(id:number|string)=>`/courses/${id}`, site:(id:number|string)=>`/sites/${id}`,
  verify:(id:number|string)=>`/sites/${id}/verify`, evidence:(id:number|string)=>`/sites/${id}/evidence`,
  checkin:'/checkin', stamp:(id:number|string)=>`/stamps/${id}`,
  meditations:'/meditations', meditation:(id:number|string)=>`/meditations/${id}`,
  thinkbox:'/thinkbox', rewards:'/rewards', ebooks:'/ebooks', print:'/ebooks/print',
  certificates:'/certificates', certVerify:(s:string)=>`/verify/${s}`,
  settings:'/settings', guide:'/guide', photos:'/photos',
  editor:{ manuscripts:'/editor/manuscripts' },
  admin:{ root:'/admin', sites:'/admin/sites', siteNew:'/admin/sites/new', site:(id:number|string)=>`/admin/sites/${id}`,
    kakao:'/admin/sites/search', qr:(id:number|string)=>`/admin/sites/${id}/qr`,
    courses:'/admin/courses', course:(id:number|string)=>`/admin/courses/${id}`, distances:'/admin/site-distances',
    stamps:'/admin/stamps', rewards:'/admin/rewards', ebooks:'/admin/ebooks', print:'/admin/print-orders',
    content:'/admin/content', manuscripts:'/admin/manuscripts', users:'/admin/users', certificates:'/admin/certificates' },
} as const
```

### 11-3 `router/index.tsx`
```tsx
export const router = createBrowserRouter([
  { element: <RootLayout />, errorElement: <RouteError />, children: [

    { element: <FocusLayout />, children: [
      { path:'/intro', Component: L(()=>import('@/pages/intro/IntroPage')) },
      { path:'/meditations/:id', Component: L(()=>import('@/pages/meditation/MeditationPlayPage')) },
      { element: <ProtectedRoute />, children: [
        { path:'/sites/:siteId/verify',   Component: L(()=>import('@/pages/stamp/StampFlowPage')) },
        { path:'/sites/:siteId/evidence', Component: L(()=>import('@/pages/stamp/EvidencePage')) },
        { path:'/checkin',                Component: L(()=>import('@/pages/stamp/CheckinPage')) },
        { path:'/stamps/:stampId',        Component: L(()=>import('@/pages/stamp/StampResultPage')) },
      ]},
    ]},

    { element: <AuthLayout />, children: [
      { path:'/login',  Component: L(()=>import('@/pages/auth/LoginPage')) },
      { path:'/signup', Component: L(()=>import('@/pages/auth/SignupPage')) },
      { path:'/verify/:serialNo', Component: L(()=>import('@/pages/certificate/VerifyPage')) },
    ]},

    { element: <IntroGate />, children: [{ element: <AppLayout />, children: [
      { index:true, Component: L(()=>import('@/pages/home/HomePage')) },
      { path:'/regions',            Component: L(()=>import('@/pages/regions/RegionsPage')) },
      { path:'/regions/:regionId',  Component: L(()=>import('@/pages/regions/RegionDetailPage')) },
      { path:'/courses/:courseId',  Component: L(()=>import('@/pages/courses/CourseDetailPage')) },
      { path:'/sites/:siteId',      Component: L(()=>import('@/pages/sites/SitePage')) },
      { path:'/meditations',        Component: L(()=>import('@/pages/meditation/MeditationListPage')) },
      { path:'/guide',              Component: L(()=>import('@/pages/settings/GuidePage')) },
      { element: <ProtectedRoute />, children: [
        { path:'/passport',     Component: L(()=>import('@/pages/passport/PassportPage')) },
        { path:'/thinkbox',     Component: L(()=>import('@/pages/thinkbox/ThinkboxPage')) },
        { path:'/photos',       Component: L(()=>import('@/pages/photo/PhotoAlbumPage')) },
        { path:'/rewards',      Component: L(()=>import('@/pages/reward/RewardsPage')) },
        { path:'/ebooks',       Component: L(()=>import('@/pages/ebook/EbookPage')) },
        { path:'/ebooks/print', Component: L(()=>import('@/pages/ebook/PrintOrderPage')) },
        { path:'/certificates', Component: L(()=>import('@/pages/certificate/CertificatesPage')) },
        { path:'/settings',     Component: L(()=>import('@/pages/settings/SettingsPage')) },
      ]},
      { path:'/403', Component: L(()=>import('@/pages/error/ForbiddenPage')) },
      { path:'*',    Component: L(()=>import('@/pages/error/NotFoundPage')) },
    ]}]},

    { path:'/editor', element: <EditorRoute />, children: [{ element: <AdminLayout />, children: [
      { path:'manuscripts', Component: L(()=>import('@/pages/editor/EditorManuscripts')) },
    ]}]},

    { path:'/admin', element: <AdminRoute />, children: [{ element: <AdminLayout />, children: [
      { index:true,             Component: L(()=>import('@/pages/admin/AdminHome')) },
      { path:'sites',           Component: L(()=>import('@/pages/admin/AdminSites')) },
      { path:'sites/new',       Component: L(()=>import('@/pages/admin/AdminSiteForm')) },
      { path:'sites/search',    Component: L(()=>import('@/pages/admin/AdminKakaoSearch')) },
      { path:'sites/:id',       Component: L(()=>import('@/pages/admin/AdminSiteForm')) },
      { path:'sites/:id/qr',    Component: L(()=>import('@/pages/admin/AdminQr')) },
      { path:'courses',         Component: L(()=>import('@/pages/admin/AdminCourses')) },
      { path:'courses/:id',     Component: L(()=>import('@/pages/admin/AdminCourseForm')) },
      { path:'site-distances',  Component: L(()=>import('@/pages/admin/AdminSiteDistances')) },
      { path:'stamps',          Component: L(()=>import('@/pages/admin/AdminStampReview')) },
      { path:'rewards',         Component: L(()=>import('@/pages/admin/AdminRewards')) },
      { path:'ebooks',          Component: L(()=>import('@/pages/admin/AdminEbooks')) },
      { path:'print-orders',    Component: L(()=>import('@/pages/admin/AdminPrintOrders')) },
      { path:'content',         Component: L(()=>import('@/pages/admin/AdminContent')) },
      { path:'manuscripts',     Component: L(()=>import('@/pages/admin/AdminManuscripts')) },
      { path:'users',           Component: L(()=>import('@/pages/admin/AdminUsers')) },
      { path:'certificates',    Component: L(()=>import('@/pages/admin/AdminCertificates')) },
    ]}]},
  ]},
])
```

### 11-4 `App.tsx` · `main.tsx`
```tsx
export default function App() {
  return (
    <ErrorBoundary scope="app">
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
        <ToastHost />
      </QueryClientProvider>
    </ErrorBoundary>
  )
}
```
```tsx
if (import.meta.env.DEV) installDevGuards()      // localStorage 에 token 류 키가 생기면 throw
const root = ReactDOM.createRoot(document.getElementById('root')!)
root.render(<React.StrictMode><RestoringSplash /></React.StrictMode>)
bootstrapAuth().finally(() => root.render(<React.StrictMode><App /></React.StrictMode>))
```
라우터보다 부트스트랩을 먼저 — 보호 라우트가 첫 렌더에서 guest 로 오판해 `/login` 으로 튀는 깜빡임을 없앤다.

---

## 12. 레이아웃 · Header

| 레이아웃 | 포함 | 쓰는 화면 |
|---|---|---|
| **Root** | 폰트·`data-tier`·`lang`·오프라인 배너·ScrollRestoration·전역 ErrorBoundary·i18n 로드 | 전부 |
| **App** | Header + Outlet + BottomTabs(5) + FloatingHome + safe-area | 탐색·기록 |
| **Auth** | 산문 배경·중앙 카드(max-w 420)·로고·언어 스위치 | 로그인·가입·진위 확인 |
| **Focus** | 얇은 상단 바(닫기 + 제목 + 타이머 슬롯) + 전체 높이 | 인트로·인증·QR·명상 재생 |
| **Admin** | 사이드바(md↑)/드로어·상단바·breadcrumb | `/admin/*`·`/editor/*` |

```tsx
// RootLayout
export default function RootLayout() {
  const tier=useAuthStore(selectTier), preview=useUiStore(s=>s.previewTier), lang=useUiStore(s=>s.lang)
  const online=useOnline(); useI18n(lang)                          // GET /api/content/i18n/{lang} (정정 #9)
  useEffect(()=>{ document.documentElement.dataset.tier = preview ?? tier; document.documentElement.lang = lang },[tier,preview,lang])
  return (<ErrorBoundary scope="route">{!online && <OfflineBanner/>}<ScrollRestoration/><Outlet/></ErrorBoundary>)
}
```
```tsx
// AppLayout
<div className="min-h-dvh flex flex-col bg-paper-50">
  <Header/>
  <main className="flex-1 pb-[calc(72px+env(safe-area-inset-bottom))]">
    <Suspense fallback={<PageSkeleton/>}><Outlet/></Suspense>
  </main>
  <BottomTabs/><FloatingHome/>
</div>
```
하단 탭 5: **여권 · 코스 · [홈 플로팅] · 명상 · 나**. "나" 는 시트로 설정·인증서·전자책·보상·**사진첩**·생각상자. 비로그인은 여권 탭 → `/regions` + 로그인 유도 배너.

```tsx
// Header
<header className="sticky top-0 z-30 backdrop-blur bg-paper-50/85 border-b border-paper-200">
  <div className="h-14 max-w-screen-md mx-auto px-4 flex items-center gap-3">
    <Link to={P.home} className="font-serif text-lg">산문<span className="text-ochre-500">.</span>경내</Link>
    <nav className="hidden md:flex gap-4 text-sm text-ink-600 ml-4">
      <NavLink to={P.regions}>코스</NavLink><NavLink to={P.meditations}>명상</NavLink><NavLink to={P.guide}>가는 법</NavLink>
      {user && <NavLink to={P.passport}>여권</NavLink>}
      {isEditor && <NavLink to={P.editor.manuscripts} className="text-ochre-600">원고</NavLink>}
      {isAdmin  && <NavLink to={P.admin.root} className="text-ochre-600">관리</NavLink>}
    </nav>
    <div className="ml-auto flex items-center gap-2">
      <LocaleSwitch/>                                   {/* ko·en 둘 */}
      {status==='restoring' && <Skeleton className="w-16 h-8 rounded-full"/>}
      {status==='guest' && <Link to={P.login} className="btn px-4 bg-ink-900 text-paper-50 text-sm">로그인</Link>}
      {status==='authed' && user && <UserMenu user={user} onLogout={...}/>}
    </div>
  </div>
</header>
```

---

## 13. UX 원칙

| 원칙 | 구현 |
|---|---|
| 한 화면 한 마음 | 주 액션 1개(ochre), 나머지는 텍스트 링크 |
| 한지 위 먹과 인주 | paper 배경·ink 글자·ochre 액션. 색은 5구 색 외에 더 쓰지 않는다 |
| 구절이 먼저 | 홈·사찰 상세 첫 스크롤에 오관게(serif) |
| 도장은 이벤트 | 발급 순간만 `animate-ink` + `navigator.vibrate` + 5구 색 파문 |
| 비어 있음도 설계 | 모든 목록에 EmptyState 삽화·한 줄·다음 행동 |
| 로딩은 형태로 | Spinner 금지, Skeleton |
| 실패는 부드럽게 | 발랄한 문구 + 되돌아갈 길(§15) |
| 엄지 영역 | 주 액션 하단 고정, 탭 ≥44(RIDER 56) |
| 오프라인 존중 | 조회는 캐시, 저장은 수동 재시도 |
| 좌표는 내 폰 안에서만 | 거리·반경은 `lib/distance.ts`, 화면에 "위치는 기기 밖으로 나가지 않아요" |

---

## 14. 화면별 설계

### 14-1 홈 `/`
| 순서 | 블록 | 데이터 | 비로그인 |
|---|---|---|---|
| 1 | 오늘의 구절(한자+한글+주제·5구 색 리본·스와이프) | verses | 동일 |
| 2 | 진행 요약(10권역 점 그리드·완주 n/13·다음 사찰) — RIDER 는 "다음 사찰 주행정보" | passport | 로그인 유도 카드 |
| 3 | 여섯 달 전 오늘(있을 때만) | flashback | 없음 |
| 4 | 명상 3편(비로그인 재생 가능) | meditations | 동일 |
| 5 | 가는 법 배너 | 정적 | 동일 |
4호출 병렬. 블록마다 자기 스켈레톤·자기 에러(`ErrorBoundary scope="block"`).

### 14-2 권역 목록 · 상세
10권역 카드(**개수를 박지 말고 응답 길이로 그린다** · 정정 #43): 권역명·코스 수·진행 n/5·다음 미방문까지 거리(로컬 계산). 제주는 코스 0 → "준비 중".
상세는 `GET /regions` 에서 해당 항목 + `GET /courses?regionId=` 조립(정정 #1). 상단 지도 + 코스 카드(ACTIVE 만, DRAFT 는 회색).

> **동명이찰은 표시 계층에서만 구분한다**(C1 보강 결정). 서버의 `site.name` 은 "용문사" 그대로다 —
> 이름에 지역을 붙이면 원고 CSV 반입이 이름 완전 일치라 넷이 깨진다.
> 같은 `name` 이 둘 이상인 세트가 **7개(15곳)** 있으므로, 목록·상세·검색 결과에서는
> **`name(시군구)`** 로 그린다 — "용문사(예천)". 시군구는 `regionName` 이 아니라 사찰 주소에서 온다.
> 세트 목록은 `c1-보강-결과.md` 에 있다.

### 14-3 코스 상세 = 사찰 목록 `/courses/:courseId`
| 영역 | 내용 |
|---|---|
| 헤더 | 코스명·권역·진행 5칸(5구 색, 찍힌 칸은 인주 마크) |
| 지도 | 자리 5 마커(번호+색) + 후보(회색·`congested` 면 "붐빔") + 내 위치 + "내 위치로" |
| 목록 | 자리 순 5장: 순번·구절 주제·사찰명·거리·상태·`qrLocationHint` 한 줄. **`courseSiteId ≠ siteId` 임을 잊지 말 것** |
| 정렬 | 자리 순 / 가까운 순(거리 없으면 비활성) |
| 하단 고정 | 비로그인 "로그인하고 순례 시작" / 로그인 "순례 시작"(`POST /pilgrimages` 멱등) → 진행 중이면 "이어서" |
| 유일성 안내 | **이 권역에서 이미 받은 구절 자리는 "받음" 뱃지 + 비활성**(정정 #56). 탭하면 그 도장을 보여준다 |

### 14-4 사찰 상세 `/sites/:siteId` — 8블록
| 블록 | UI | RIDER |
|---|---|---|
| site | 이름·설명·사진(없으면 한지 텍스처) | – |
| verse | 담당 구절(serif·항상 공개) | – |
| **expansionPhrase** | 확장문구 1편(키가 `phrase` 아님 · 정정 #17) | – |
| mission | 행동과제 미리보기 + 원고 안내 | – |
| viewpoints | 최대 3 아코디언 | – |
| badges | FLOWER/GUARDIAN | – |
| riderInfo | 주차·진입·**식사(`MEAL_LABEL[mealAvailable]` — §21-1)** | **상단 고정 배너로 승격** |
| verifyState | `{pilgrimageId, stampId, status, expiresAt}` → 하단 고정 버튼. **`pilgrimageId` 를 여기서 얻는다** | 56px |
`guideAvailable` 은 없다(정정 #18) → 「가는 법」 링크는 조건 없이 띄우고 `GET /sites/{id}/guide` 응답(7칸·`present:false`)으로 그린다. 캐시 0 → 재진입하면 새 문구.

### 14-5 로그인 · 가입 (럭셔리)
| 요소 | 설명 |
|---|---|
| 배경 | ink-900 → paper-50 세로 그라데이션 + 산문 실루엣 SVG(1분 주기 안개, `prefers-reduced-motion` 시 정지) |
| 카드 | paper-50 · radius 24 · shadow-card · 상단에 오관게 1구 인용(무작위) |
| 입력 | 플로팅 라벨 · 비번 보기 토글 · blur 후 검증 |
| 가입 3스텝 | ① 계정 ② 타겟 3장 카드(2030/라이더/외국인 → AGE30·RIDER·FOREIGN, 나머지 연령은 설정에서) ③ 동의(위치 필수·전자책 선택) → 버튼 하나로 4호출(§10-2) |
| 실패 | 단일 문구. 5회 후 버튼 비활성 + 약 15분 카운트다운 |
| 성공 | 카드가 위로 사라지며 인주 파문 → `next` 또는 홈 |

```tsx
// components/ui/Input.tsx
export const Input = forwardRef<HTMLInputElement, Props>(function Input({label,error,hint,className='',id,...rest}, ref) {
  const auto=useId(); const inputId=id??auto
  return (
    <div className={`relative ${className}`}>
      <input ref={ref} id={inputId} placeholder=" " aria-invalid={!!error}
        aria-describedby={error?`${inputId}-err`:undefined}
        className={`peer w-full rounded-seal border bg-white/70 px-4 pt-6 pb-2 text-base outline-none transition
          ${error?'border-danger focus:border-danger':'border-paper-200 focus:border-ochre-500'}`} {...rest}/>
      <label htmlFor={inputId} className="pointer-events-none absolute left-4 top-2 text-xs text-ink-600 transition-all
        peer-placeholder-shown:top-4 peer-placeholder-shown:text-base peer-focus:top-2 peer-focus:text-xs">{label}</label>
      {error ? <p id={`${inputId}-err`} className="mt-1 text-xs text-danger">{error}</p>
             : hint ? <p className="mt-1 text-xs text-ink-300">{hint}</p> : null}
    </div>
  )
})
```
```tsx
// pages/auth/LoginPage.tsx
export default function LoginPage() {
  const [sp]=useSearchParams(); const next=sp.get('next'); const nav=useNavigate()
  const login=useLogin(); const status=useAuthStore(s=>s.status)
  const { register, handleSubmit, setError, formState:{errors,isSubmitting} } =
    useForm<LoginForm>({ resolver: zodResolver(loginSchema) })
  const [lockedUntil,setLockedUntil]=useState<number|null>(readLock()); const remain=useCountdown(lockedUntil)
  if (status==='authed') return <Navigate to={next ?? P.home} replace/>
  const onSubmit = handleSubmit(v => login.mutate(v, {
    onSuccess: () => nav(next ?? P.home, { replace:true }),
    onError: (e) => {
      const ui = toUiError(e)
      if (e instanceof ApiError && e.code==='AUTH-4031') { const t=Date.now()+15*60_000; setLockedUntil(t); writeLock(t) }
      setError('root', { message: ui.body })
    },
  }))
  return (
    <form onSubmit={onSubmit} className="space-y-4" noValidate>
      <VerseQuote random/>
      <Input label="이메일" type="email" autoComplete="email" inputMode="email" {...register('email')} error={errors.email?.message}/>
      <PasswordInput label="비밀번호" autoComplete="current-password" {...register('password')} error={errors.password?.message}/>
      {errors.root && <FormMessage tone="danger">{errors.root.message}{remain>0 && ` (약 ${fmt(remain)} 뒤)`}</FormMessage>}
      <Button type="submit" full loading={isSubmitting||login.isPending} disabled={remain>0}>산문으로 들어가기</Button>
      <p className="text-center text-sm text-ink-600">아직 계정이 없나요?{' '}
        <Link to={`${P.signup}${next?`?next=${encodeURIComponent(next)}`:''}`} className="text-ochre-600 underline">가입하기</Link></p>
    </form>
  )
}
```

### 14-6 현장 인증 3단계 — GPS → **QR(필수)** → 다짐
| 단계 | 화면 | 판정 | 서버로 | 실패 |
|---|---|---|---|---|
| ① GPS | 원형 게이지 + 반경 안내(**사찰마다 다름**·`verifyRadius`) + 정확도 등급 | **기기**: haversine ≤ verifyRadius · accuracy ≤30 HIGH·≤100 MID·초과 LOW | `POST /stamps/{courseSiteId}/gps-check {siteId,withinRadius,accuracyGrade}` | `STAMP-4000` 반경 밖 / `STAMP-4001` 정확도 낮음 → 예외 접수 안내 / **`STAMP-4090` 이 권역의 이 구절 도장을 이미 받음**(정정 #56) |
| ② QR | 카메라 풀스크린(@zxing) + `qrLocationHint` + 60분 타이머 | 서버 | `{qrToken}` | `4002` 서명·회전 / `4003` QR 유효시간 만료 / `4004` 다른 사찰 / `4091` 세션 만료 |
| ③ 다짐 | 행동과제 + 원고 + 10~300자 + **다른 사람 얼굴 체크박스** + 사진(선택) | 서버 | `{sentence, photoKey?, hasOtherFace?, expansionPhraseId?}` | 길이 위반은 `COMMON-4000` + `fields[sentence]` / 사진 실패는 다짐만 제출 |
| 결과 | 도장 애니메이션 · progress · rewards · 완주면 인증서 번호 | – | – | `PENDING` 은 **200 의 status** — "확인 중" 화면 |
`STAMP-4092`(순서 위반)는 코드가 하나이고 **메시지로 갈린다** → 안내는 `error.message` 를 쓴다.
세션 타이머는 서버 `expiresAt`. 복귀 시 `GET /stamps/{id}` 로 재동기화. 하루 상한 `STAMP-4291` 은 첫 단계 전에 안내.

### 14-7 QR 체크인 `/checkin?token=`
11-A STEP 3 이후 QR 은 `{FRONTEND_URL}/checkin?token=…` 이다. 흐름:
미로그인 → `/login?next=/checkin?token=…` → 복귀 → `stampStore.stampId` 가 있으면 `POST /stamps/{id}/qr` → 미션 / 없으면 토큰을 `pendingQrToken` 에 보관하고 "먼저 위치 확인부터" 로 사찰 상세 안내 → GPS 통과 즉시 보관한 토큰으로 QR 자동 통과.
앱 내 스캐너와 카메라 앱 두 경로가 같은 토큰으로 같은 API 에 도달한다.

### 14-8 여권 `/passport`
10권역 → 코스 → 자리. 권역 아코디언, 칸은 원형(빈칸 점선 / 찍힘 5구 색 도장 + 날짜). 상단 Summary 4.
**회향 진행 바는 "완주 권역 n/9"** 로 그린다(정정 #57). 하한 값을 주는 API 가 없으므로 프론트 상수 `HOEHYANG_MIN_REGIONS = 9`, 값이 바뀌면 상수를 고친다.

### 14-9 생각상자 · 14-10 명상
생각상자: 정렬 3(`date·course·site`), 무한 스크롤(`hasNext`), 300자, 공개 토글(**`isPrivate:true` 가 비공개**), `source` 아이콘, 수정·삭제(204).
명상: 카테고리 칩 — `category` 는 `"1"~"5"` 문자열이라 **라벨은 프론트 매핑**. 재생은 Focus(어두운 배경·호흡 애니메이션·스크립트). `audioUrl` null 이면 텍스트 명상. 종료·이탈 시 `POST /meditations/logs {meditationId, playedSeconds, memo?}`.

### 14-11 전자책 · 인쇄
탭 순례본·회향본·소장본 + **기타(INTERIM)**. 상태 칩 **REQUESTED / READY / FAILED**. REQUESTED 는 30초 폴링(청소기 5분 주기 안내). READY 만 `GET /ebooks/{id}/download-url` → JSON 의 `downloadUrl` 을 새 탭(5분). "소장본 만들기" 는 `POST /ebooks`(202/200 구분 표시), 하루 3·READY 5 안내.
인쇄: READY 선택 → `quantity` 1~5 → `recipientName·recipientPhone·postalCode·address·addressDetail?·note?` → 목록에서 `cancelable` 일 때만 취소(204). 배송정보는 상세에만.

### 14-12 인증서 · 진위 확인
카드형(번호 크게·코스명·발급일·REVOKED 워터마크) · `downloadUrl`(VALID 만) · verifyUrl QR 공유. 진위 확인은 공개 카드 7필드, 마스킹 그대로, 없는 번호만 `CERT-4041`, 회수는 200+REVOKED.

### 14-13 보상
`GRANTED → CLAIMED → PAID/REJECTED`(+UNDER_REVIEW·REVOKED) 타임라인. `claimable` 일 때만 "받기" → 배송정보를 **claim 본문에 함께**(정정 #8).

### 14-14 설정 · 탈퇴
닉네임 · 타겟 7종 · **언어 ko·en 둘**(정정 #48) · 알림 · 동의 현황(철회 포함) · 비의료 안내 · 탈퇴(2단 확인 + 비밀번호 재확인 → 204 → clear → 홈, "즉시 익명화, 되돌릴 수 없음").

### 14-15 사진첩 `/photos` (신설 · 정정 #55)
`GET /photos` 그리드(사찰명·날짜). 탭하면 그 사찰 상세로. 사찰당 1장이라 교체는 인증 화면에서. 사진이 하나도 없으면 EmptyState "첫 도장을 찍으면 여기 사진이 쌓여요".

### 14-16 관리자 · 편집자
| 화면 | 핵심 |
|---|---|
| AdminSites | 표(이름·상태·좌표·QR버전·코스 배정)·검색·상태 토글·QR 버튼. **삭제 없음 — `PATCH status` 로 비활성** |
| AdminSiteForm | 좌표는 "카카오에서 찾기" → prefill. i18n·viewpoint 3·badge·요소. 좌표 한국 영역·반경 30~1000 zod. **본문에 `status` 를 실으면 400** |
| AdminKakaoSearch | `GET /admin/kakao/places?query=` → 카드 + 미니맵 → "이 장소로 등록" |
| AdminQr | `POST /admin/sites/{id}/qr` · **`validitySeconds` 필수 입력**(기본 300초라 인쇄용은 반드시 길게) · `qrImageBase64` 앞에 `data:image/png;base64,` · A4 인쇄 · rotate(2단 확인) |
| AdminCourses/Form · SiteDistances | 자리 5 일괄(position=verseNo) · 거리 편집 |
| AdminStampReview | PENDING 목록(증거 사진 임시 URL) · 승인/반려(사유 필수) |
| AdminContent(**ADMIN**) | 확장문구·행동과제 등록(정정 #51) |
| AdminManuscripts(ADMIN) | 목록·CSV `?dryRun=true` 먼저·승인·반려·**퇴역** |
| **EditorManuscripts(EDITOR)** | 작성·수정·제출·내 목록. 4-eyes 로 자기 원고 승인 불가(`MS-4030`) |
| **AdminUsers** | 목록(status·검색·페이지) — 해시·토큰·주소 없음 |
| **AdminCertificates** | 회수(2단 확인) |
| AdminRewards / Ebooks / PrintOrders | 심사·파일 교체·상태(SHIPPED 송장 필수) |
| AdminHome | 헬스·시드 숫자·`housekeeping/run`(처리 건수 + **사진 누락 n** 표시) |

---

## 15. 에러 문구표 (`lib/errorTexts.ts`)

> 근거는 `ErrorCode.java` 79개. 규칙 `{도메인}-{HTTP 3자리}{일련 1자리}` 이고 **79개 전부 앞 세 자리가 HTTP 상태와 같다**(정정 #41).
> **`error.fields` 는 배열 `[{field, reason}]`** — `ApiError.fieldMap` 으로 변환해 쓴다(정정 #42).
> 톤: 인증·보안·잠금·권한은 plain, 나머지는 fun.

| 코드 | 제목 | 본문 | 행동 | 톤 |
|---|---|---|---|---|
| NET-0000 *(FE)* | 전파가 산문 밖에 있어요 | 산속이라 그런가 봐요. 연결되면 다시 보내 드릴게요. | retry | fun |
| NET-0504 *(FE)* | 서버가 아직 잠에서 덜 깼어요 | 새벽 예불 전인가 봅니다. 잠깐만 기다려 주세요. | retry | fun |
| COMMON-4000 | 입력이 조금 삐뚤어요 | 빨간 줄 부분만 다시 봐 주세요. | none | fun |
| COMMON-4001 | 개발자가 계율을 어겼어요 | 좌표가 서버로 갈 뻔했어요. 이건 저희 잘못입니다. | none | plain |
| COMMON-4002 | 빠뜨린 값이 있어요 | 표시된 칸을 채워 주세요. | none | fun |
| COMMON-4003 | 값의 모양이 달라요 | 숫자·형식을 확인해 주세요. | none | fun |
| COMMON-4004 | 보낸 내용을 못 읽었어요 | 선택지에서 다시 골라 주세요. | none | fun |
| COMMON-4040 | 이 길은 막힌 길이에요 | 지도에 없는 곳까지 오셨네요. 경내로 돌아가요. | passport | fun |
| COMMON-4050 | 그 방법으로는 못 열어요 | – | none | plain |
| COMMON-4090 | 이미 그렇게 되어 있어요 | 상태가 바뀌어 있었어요. 화면을 새로 고쳤어요. | retry | fun |
| COMMON-4130 | 사진이 너무 커요 | 2 MB 아래로 줄여 주세요. 앱이 자동으로 줄이지만 실패했나 봐요. | retry | fun |
| COMMON-4150 | 그 형식은 못 받아요 | jpg 또는 png 로 부탁드려요. | none | fun |
| COMMON-5000 | 목탁이 잠깐 엇박자예요 | 서버가 헛디뎠어요. 요청번호 {requestId} 를 알려주시면 빨리 찾아요. | retry | fun |
| AUTH-4011 | 이메일 또는 비밀번호를 확인해 주세요 | (계정 존재 여부 미노출) | none | plain |
| AUTH-4012 / 4014 | 다시 로그인해 주세요 | 로그인 유효기간이 끝났어요. | login | plain |
| AUTH-4013 | 로그인이 필요한 곳이에요 | 순례 기록은 순례자만 볼 수 있어요. | login | plain |
| AUTH-4015 | 보안을 위해 모든 기기에서 로그아웃했어요 | 다른 곳에서 같은 로그인 정보가 쓰였어요. 다시 로그인해 주세요. | login | plain |
| AUTH-4031 | 잠시 문을 닫았어요 | 5번 틀려서 약 15분 잠겼어요. | wait | plain |
| AUTH-4032 | 여긴 스님만 들어가요 | 권한이 필요한 화면이에요. | passport | plain |
| AUTH-4090 | 이미 순례자인 이메일이에요 | 로그인으로 가 볼까요? | login | fun |
| USER-4030 / 4031 | 동의가 필요해요 | 위치 서비스·필수 약관에 동의해 주세요. | settings | plain |
| USER-4040 | 사용자를 찾을 수 없어요 | – | none | plain |
| COURSE-4000 | 어느 코스에도 없는 사찰이에요 | – | none | fun |
| COURSE-4001 | 구절 번호와 자리 번호가 달라요 | – | none | fun(관리자) |
| COURSE-4040 | 그 권역이 없어요 | – | passport | fun |
| COURSE-4041 | 아직 열리지 않은 코스예요 | 준비가 끝나면 알려드릴게요. | passport | fun |
| COURSE-4093 | 이미 다른 코스에 있는 사찰이에요 | – | none | plain(관리자) |
| SITE-4040 | 그 사찰은 지도에서 사라졌어요 | 준비 중이거나 잠시 쉬는 사찰이에요. | passport | fun |
| SITE-4090 | 지금은 순례할 수 없는 사찰이에요 | – | passport | fun |
| SITE-5001 | 문구가 아직 준비되지 않았어요 | 곧 채워 넣을게요. | retry | fun |
| VERSE-4040 / 4041 | 그 게송·과제가 없어요 | – | none | fun |
| PILGRIMAGE-4030 | 남의 순례 기록이에요 | 내 여권만 볼 수 있어요. | passport | plain |
| PILGRIMAGE-4040 | 순례 기록이 없어요 | 코스에서 순례를 시작해 보세요. | passport | fun |
| STAMP-4000 | 아직 산문 밖이에요 | 사찰 반경 안으로 조금만 더 들어와 주세요. | retry | fun |
| STAMP-4001 | 하늘이 잘 안 보이나 봐요 | GPS 가 약해요. 마당으로 나와 30초 기다리거나 예외 접수로 진행해 주세요. | retry | fun |
| STAMP-4002 | QR 을 못 읽었어요 | 서명이 맞지 않아요. 옛 QR 일 수 있어요 — 종무소에 새 QR 을 요청해 주세요. | retry | fun |
| STAMP-4003 | 이 QR 은 시간이 지났어요 | QR 은 발급 시각 기준으로 유효해요. 재발급을 요청해 주세요. | retry | fun |
| STAMP-4004 | 이 사찰의 QR 이 아니에요 | 다른 사찰 QR 이에요. | retry | fun |
| STAMP-4031 | 다른 분의 도장이에요 | 내 인증만 이어갈 수 있어요. | passport | plain |
| STAMP-4040 | 그 도장을 찾을 수 없어요 | – | passport | fun |
| **STAMP-4090** | 이 구절 도장은 이미 받으셨어요 | 한 권역에서 같은 구절은 한 번만 받을 수 있어요. 여권에서 확인해 보세요. | passport | fun |
| STAMP-4091 | 60분이 지나갔어요 | 차 한잔 하셨나 봐요. 위치 확인부터 다시 시작해요. | retry | fun |
| STAMP-4092 | 순서가 살짝 바뀌었어요 | (서버 메시지 그대로 — 남은 단계를 안내) | retry | fun |
| STAMP-4093 | 확인 중인 도장이에요 | 관리자가 확인하면 찍혀요. | passport | plain |
| STAMP-4094 | 지금은 예외 접수를 할 수 없어요 | – | passport | plain |
| STAMP-4291 | 오늘 도장은 여기까지 | 하루 5개가 상한이에요. 내일 새벽에 다시 열려요. | passport | fun |
| STAMP-4292 | 예외 접수도 하루 2건까지 | 내일 다시 접수할 수 있어요. | passport | fun |
| UPLOAD-4001 | 사진이 길을 잃었어요 | 다짐만 먼저 남기고 사진은 나중에 붙여도 돼요. | retry | fun |
| THINKBOX-4030 | 본인의 글만 고칠 수 있어요 | – | none | plain |
| THINKBOX-4040 | 그 생각은 이미 날아갔어요 | 삭제됐거나 없는 기록이에요. | none | fun |
| MEDITATION-4040 | 이 명상은 잠시 쉬는 중 | 다른 명상을 들어 볼까요? | none | fun |
| EBOOK-4001 / 4030 / 4040 | 책을 찾을 수 없어요 | 목록을 새로 고쳤어요. | retry | fun |
| EBOOK-4090 | 책이 아직 제본 중이에요 | 몇 분 걸려요. 다 되면 여기서 바로 열 수 있어요. | retry | fun |
| EBOOK-4290 | 오늘 소장본은 다 만들었어요 | 하루 3권, 완성본 5권까지예요. | none | fun |
| PRINT-4001 / 4040 / 4090 / 4091 | 지금은 신청·취소할 수 없어요 | 상태가 바뀌었어요. | retry | fun |
| CERT-4041 | 그런 번호는 없어요 | 번호를 다시 확인해 주세요. 회수된 인증서는 REVOKED 로 나와요. | none | plain |
| CERT-4091 | 이미 회수된 인증서예요 | – | none | plain |
| REWARD-4040 / 4090 | 지금은 받을 수 없어요 | 상태가 바뀌었어요. 목록을 새로 고쳤어요. | retry | fun |
| MS-4002/4030/4040/4090~4093 | 원고 처리 불가 | (서버 메시지) | none | plain(관리자) |
| KAKAO-5030 | 장소 검색이 꺼져 있어요 | 운영자에게 알려 주세요(키 미설정). | none | plain(관리자) |
| KAKAO-5031 | 카카오가 잠깐 안 받아요 | 잠시 뒤 다시 해 주세요. | retry | fun(관리자) |
| ADMIN-4092 | 이 상태에선 처리 불가 | 이미 처리됐거나 순서가 달라요. | retry | plain |
| RENDER *(FE)* | 서버를 깨우는 중이에요 | 처음 켜질 땐 최대 1분 걸려요. | wait | fun |
| OFFLINE *(FE)* | 전파가 산문 밖에 있어요 | 보던 화면은 그대로, 저장은 연결되면 보내요. | – | fun |
| 404 라우트 *(FE)* | 이 길은 지도에 없어요 | 산문으로 돌아가는 길을 켜 드릴게요. | home | fun |
| 렌더 예외 *(FE)* | 화면이 잠깐 넘어졌어요 | 저희가 챙겨 볼게요. | home/retry | fun |

**상태값은 에러가 아니다** — `StampStatus` 6종(`GPS_DONE·QR_DONE·COMPLETED·EXPIRED·PENDING·REJECTED`)은 상태표로 따로 그린다. `PENDING` = "이동 시간을 확인하고 있어요 — 관리자가 확인하면 도장이 찍혀요"(plain).

```tsx
// ToastHost
<div className="fixed inset-x-0 top-3 z-50 flex flex-col items-center gap-2 px-4 pointer-events-none" role="status" aria-live="polite">
  {toasts.map(t=>(
    <div key={t.id} className={`pointer-events-auto max-w-md w-full rounded-seal shadow-card px-4 py-3 text-sm animate-ink
      ${t.tone==='danger'?'bg-danger text-paper-50':t.tone==='success'?'bg-success text-paper-50':'bg-ink-900 text-paper-50'}`}>
      <strong className="block">{t.title}</strong>{t.body && <span className="opacity-90">{t.body}</span>}
      {t.action && <button onClick={()=>{t.action!.onClick(); dismiss(t.id)}} className="ml-3 underline">{t.action.label}</button>}
    </div>))}
</div>
```
```tsx
// ErrorBoundary — scope: app | route | block
componentDidCatch(error, info) { console.error(`[boundary:${this.props.scope}]`, error, info.componentStack) }
// block 이면 EmptyState("이 조각만 잠깐 넘어졌어요"), 아니면 FullscreenError(홈·재시도)
```

---

## 16. 공통 컴포넌트

| 컴포넌트 | props | 규칙 |
|---|---|---|
| Button | `variant primary\|ghost\|danger` · `full` · `loading` · `size` | 높이 `var(--btn-h)`, loading 중 텍스트 유지 |
| Input / PasswordInput / Textarea(카운터) / Select | `label error hint` | forwardRef · RHF register |
| Card | `tone` · `verse?:1~5`(좌측 리본) | |
| Badge | status 매핑(DRAFT/ACTIVE/REQUESTED/READY/FAILED/PENDING…) | 5구+상태색만 |
| Skeleton / PageSkeleton / FocusSkeleton | | Spinner 대체 |
| EmptyState | `illust title body action` | SVG 8종 |
| Toast / ToastHost · Modal / Sheet | focus trap · ESC | 탈퇴·rotate 2단 확인 |
| Progress(5칸) / Ring(게이지) | `value total verse` | |
| Tabs / Chips · QueryError · StampMark | | StampMark = 인주 SVG + `animate-ink` |

---

## 17. 지도 · 위치

```ts
// hooks/useKakaoLoader.ts — 10초 타임아웃 후 목록 폴백
s.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${env.KAKAO_JS_KEY}&autoload=false`
```
```ts
// hooks/useGeolocation.ts — geo 는 어떤 store 에도 넣지 않는다(컴포넌트 상태만)
const o: PositionOptions = { enableHighAccuracy:true, timeout:15_000, maximumAge:5_000 }
// 오류: denied(권한) · unavailable · insecure(http) · timeout
```
```ts
// lib/distance.ts
export function haversineM(a,b){ const R=6371e3,r=Math.PI/180,dLat=(b.lat-a.lat)*r,dLng=(b.lng-a.lng)*r
  const h=Math.sin(dLat/2)**2+Math.cos(a.lat*r)*Math.cos(b.lat*r)*Math.sin(dLng/2)**2
  return 2*R*Math.asin(Math.sqrt(h)) }
export const accuracyGrade = (m:number) => m<=30?'HIGH':m<=100?'MID':'LOW'
export const fmtDistance = (m:number) => m<1000?`${Math.round(m)} m`:`${(m/1000).toFixed(1)} km`
```
`KakaoMap` props: `center · markers[{id,lat,lng,verse?,label,congested?}] · myLocation? · path? · onMarkerClick`. 인포윈도우 하나만 열림. 마커 1개면 level 5. 한국 영역(위도 33~39·경도 124~132) 밖이면 마커 대신 "좌표 확인 필요"(관리자 화면에서만).

---

## 18. QR 스캔

```tsx
import { BrowserQRCodeReader, type IScannerControls } from '@zxing/browser'
export function QrScanner({ onToken, onError }) {
  const videoRef = useRef<HTMLVideoElement>(null)
  useEffect(() => {
    if (!window.isSecureContext) return onError('insecure')
    let controls: IScannerControls | undefined
    new BrowserQRCodeReader().decodeFromVideoDevice(undefined, videoRef.current!, (result,_e,c)=>{
      controls = c
      if (result) { const t = extractToken(result.getText()); if (t) { c.stop(); onToken(t) } }
    }).catch(e => onError(e?.name==='NotAllowedError'?'denied':'nocamera'))
    return () => controls?.stop()
  }, [])
  return <video ref={videoRef} className="w-full h-full object-cover" playsInline muted />
}
// URL 이면 token 파라미터, 아니면 순수 토큰 정규식 — 두 형식을 모두 받는다
const extractToken = (text: string) => {
  try { const u = new URL(text); return u.searchParams.get('token') } catch { /* fallthrough */ }
  return /^[A-Za-z0-9._-]{16,}$/.test(text) ? text : null
}
```
토큰 구조(실측): `base64url(v1|siteId|qrVersion|issuedAt|expiresAt|nonce12)` + `.` + `base64url(HMAC-SHA256)` · 99자.
**QR 유효시간 기본 300초** — 인쇄용은 `?validitySeconds=` 를 길게(§14-16 AdminQr). 그래서 `STAMP-4003` 화면이 필요하다.
회전 후 옛 QR 은 `STAMP-4002`. 같은 QR 재촬영은 `STAMP-4090`. GPS 가 먼저라 QR 사진 공유만으로는 통과할 수 없다 — 순서 고정의 이유.

---

## 19. 검증

### 19-0 e2e 고정값 (시드 실측 · 11-A STEP 6 이후 코스는 늘어난다)
```
계정  U1  rider@templestamp.local   USER · RIDER          일반 흐름·RIDER 변형
      U1' guest@templestamp.local   USER · FOREIGN · en   외국인 변형·en i18n
      ED1 editor@templestamp.local  EDITOR                /api/editor/**
      ADM admin@templestamp.local   ADMIN                 관리자 16화면
      ※ 비밀번호는 db/data.sql 주석. 문서·코드에 적지 않는다.
코스  11-A 이전: courseId 1(서울 도심 다섯 절)만 ACTIVE
      11-A STEP 6 이후: 좌표 확보된 사찰의 전 코스 ACTIVE(고운사 제외)
자리  courseSiteId 1~5 ↔ siteId 1~5 — ★ 둘이 같은 것은 데모 우연. 같다고 가정 금지
반경  150 / 200 / 200 / 250 / 250 m — 사찰마다 다르다
권역  10행(region_id 4 결번) · 제주 courseCount 0 · 2코스 권역 4곳(유일성 실측용)
```

| 종류 | 대상 | 기준 |
|---|---|---|
| Vitest | client: 봉투 해제·204·401→refresh 1회→재시도·동시 401 단일 refresh·refresh 실패 clear·lat/lng throw·끝 슬래시 throw·네트워크 1회 재시도 | 전부 초록 |
| Vitest | authStore·bootstrap(쿠키 유무)·distance·errorMessage(**79 코드 전부 문구 존재** — `error-codes.json` 을 정답지로) · image(resizeAndStrip 2MB 이하 보장) | |
| Playwright(390×844) | ① 인트로 1단 8초 스킵 불가·2단 스킵·최초 1회 ② 가입 4단→로그인→새로고침 유지→로그아웃 ③ 비로그인 홈 ④ 코스→사찰 상세→인증 버튼(로그인 유도) ⑤ 진위 확인 공개 | 5/5 |
| 네트워크 단언 | 모든 e2e 에서 body·query 에 `lat/lng/latitude/longitude` **0건** | 0 |
| 빌드 | 초기 청크 < 250 KB gz · zxing·kakao lazy | |
| 콘솔 삭제(FE-1 닫힘) | `dev-console/` 제거 → `dev-console-remove-check.sh` → `all.sh` 초록(엔드포인트 92 불변) | ✅ |
| 손 확인(폰) | basic-ssl 로 위치·카메라 허용 · 프록시로 쿠키 유지 · **카메라 앱 QR → `/checkin` 진입** · 사진 1장 업로드 후 디스크 존재 | |

---

## 20. FE-1 ~ FE-8

| 단계 | 범위 | § | 마지막 줄 |
|---|---|---|---|
| **FE-1** | 프로젝트·토큰·폴더·client·store·라우터 뼈대·레이아웃 5·Header·인트로·로그인/가입 4단·설정 최소·공통 UI·i18n·테스트 뼈대·**콘솔 삭제** | 2~12·15·16 | `fe1: 화면 N · Vitest N/N · Playwright 5/5 · 번들 N KB · lat/lng 0 · 콘솔 삭제 ✅ · all.sh 통과` |
| FE-2 | 홈·권역·코스(사찰 목록)·사찰 상세·가는 법·다국어·카카오맵 | 14-1~4·17 | `fe2: 화면 N · 지도 ✅ · 거리 로컬 ✅ · lat/lng 0` |
| FE-3 | 인증 3단계·QR 스캐너·`/checkin`·예외접수·여권·결과 도장·**유일성 안내** | 14-6~8·18 | `fe3: GPS→QR→미션 ✅ · 카메라앱 체크인 ✅ · 권역·구절 유일성 UI ✅` |
| FE-4 | **사진(resize·EXIF·PUT)**·사진첩·생각상자·flashback·명상 | 7-4·14-9·10·15 | `fe4: 사진 저장 ✅ 크기 ≤2MB ✅` |
| FE-5 | 완주·인증서·진위 확인·보상·배송 | 14-12·13 | |
| FE-6 | 전자책·소장본·인쇄 | 14-11 | |
| FE-7 | 설정·탈퇴·타겟 3종 마무리·접근성·오프라인 | 4·14-14 | |
| FE-8 | 관리자 16 + 편집자 1 | 14-16 | |

FE-1 STEP 골격
```
STEP 1  npm create vite@latest frontend -- --template react-ts + 의존성
STEP 2  vite.config.ts · .env.example · tailwind.config.ts · index.css · 폰트 · tsconfig paths
STEP 3  generate-api-types.js 재실행 → src/api/types.d.ts (미선언 0 · tsc --noEmit 통과)
STEP 4  envelope · coordinateGuard · client · errorMessage · errorTexts(§15 전량, error-codes.json 대조)
STEP 5  stores 3 · validation · useAuth(가입 4단) · bootstrap · devGuards
STEP 6  paths · router · guards 4 · App · main (미구현은 Placeholder)
STEP 7  레이아웃 5 · Header · BottomTabs · FloatingHome · 공통 UI · EmptyState 8
STEP 8  인트로 3단 · 로그인 · 가입 3스텝 · 설정 최소
STEP 9  Vitest · Playwright 5 · lat/lng 단언 · 번들 측정
STEP 10 백엔드 콘솔 삭제 → dev-console-remove-check.sh → all.sh → 정리.md §9
STEP 11 커밋·push · 마지막 줄
```

---

## 21. ⛔ 상태 (11-A / 11-B 반영)

| # | 흐름 | 상태 |
|---|---|---|
| ⛔1 | 카메라 앱 QR → `/checkin?token=` | **11-A STEP 3에서 닫는다.** 11-A0 실측: 순수 토큰 99자, `issueQr` 초기 커밋 이후 무변경. 앱 내 스캐너는 이미 동작 |
| ⛔2 | 사진 바이트 저장 | **11-A STEP 4에서 닫는다.** `PUT /api/uploads/{fileKey}` 신설(엔드포인트 92) · 상한 2 MB. 11-A0 실측: PHOTO/ 0개, :9000 연결 실패 |
| ⛔3 | `types.d.ts` 컴파일 | **11-A STEP 1에서 닫는다.** 생성기가 중첩 record 를 편다 |
| ⛔4 | 폰 → 8080 직접 CORS | **11-B에서 닫는다.** 사설 IP 대역 패턴 허용(local만·prod 금지) + `X-Request-Id` 허용·노출. 프록시를 쓰면 애초에 안 탄다 |
| ⛔5 | Access 만료 재현 | **11-A STEP 2에서 닫는다.** `JWT_ACCESS_TTL` |
| ⛔6 | 여권 Summary 필드 | ⛔3과 함께 닫힌다 |
| ⛔7 | signup 이 토큰을 안 준다 | **닫지 않는다(예성 결정).** 가입 4단으로 산다(§10-2) |

### 21-1 프론트 상수로 남는 것 (API 없음)
| 상수 | 값 | 근거 |
|---|---|---|
| `HOEHYANG_MIN_REGIONS` | 9 | 코스가 있는 권역 수(제주 제외). 서버 `reward.hoehyang-min-courses` 와 같은 뜻 |
| 명상 카테고리 라벨 | 1~5 매핑 | 서버가 번호만 준다 |
| 지원 언어 | ko·en | 사전이 둘뿐 |
| 사진 규격 | 긴 변 1280 · JPEG 0.8 · ≤2 MB | 11-A STEP 4 |
| `MEAL_LABEL` | 아래 표 | 서버는 값만 준다. 문구는 프론트가 매핑한다(i18n 대상) |

**`MEAL_LABEL` — `site.mealAvailable` 값 → 화면 문구** (C1 보강 · 2026-09-08)

| 값 | 문구 | 뜻 |
|---|---|---|
| `TEMPLE_MEAL` | **사찰음식 체험 — 사전 예약 필요** | 사찰음식으로 이름난 곳. 지금 **6곳**뿐이다 |
| `RESERVATION` | **공양 가능 — 방문 전 종무소에 시간·예약 문의** | **기본값.** 대부분의 절이 여기다 |
| `NONE` | **공양 없음** | 없음이 *확인된* 곳. 지금 **0곳** |
| `NEARBY` | **경내 공양 없음 · 인근 식당 이용** | 확인된 곳만. 지금 **0곳** |

> **값만 보고 "공양 가능" 이라고만 띄우면 안 된다.** 대부분의 절에 공양이 있지만
> **시간이 정해져 있고 미리 연락해야 한다**(2026-09-08 예성 확인). 그 조건이 값 이름에 들어가 있는
> 이유가 그것이다 — `RESERVATION` 은 "있다" 가 아니라 "있는데 예약이 필요하다" 는 뜻이다.
> 시간을 모르고 찾아간 사람이 헛걸음을 하는 것이 이 화면이 막아야 할 일이다.

`NONE` 과 `NEARBY` 는 아직 한 곳도 없지만 값 체계에 남긴다 — 확인되는 대로 그 값이 들어온다.
절별 공양 **시간**은 아직 조사되지 않았다(`checkedAt` 이 비어 있는 이유). 확인되면 사찰별 안내로 바꾼다.

### 21-2 소비자 흐름 점검
`인트로 → 가입(4단)/로그인 → 코스 → 사찰 상세 → GPS → QR → 다짐(+사진) → 도장 → 여권 → 인증서·전자책`
11-A·11-B 이후 **끊기는 지점 없음**. 가입만 내부 4호출이고 화면은 3스텝 한 흐름이다.
