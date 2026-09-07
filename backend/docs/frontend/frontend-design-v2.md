# temple-stamp — 프론트엔드 설계서 v2 (정본 후보) · 2026-09-07

> 위치: `backend/docs/frontend/frontend-design-v2.md`. 기획 명세 ①~④ + 인수인계 v2 §4·§6·§11을 근거로 작성. **`정본정정표.md` > 이 문서 > 기획 명세** 순으로 우선한다. 원본 명세는 지우지 않는다(B안).
> 필수 스택(끝까지 준수): **React 18 · TypeScript · Vite · axios · Zustand · TanStack Query v5 · Tailwind · React Router v6**. 연결 대상: Spring Boot 3.5.16 (`/api/**`, 봉투 응답, JWT + refresh 쿠키).

---

## 0. 전체 과정 한눈에 (이 문서가 다루는 범위 = FE-1 ~ FE-8 전체 설계, 코드는 FE-1 정본 수준까지)

| 파트 | 내용 | 산출물 | 상태 |
|---|---|---|---|
| A | 결정사항·충돌 판정·폴더 구조 | §1 · §2 | 이 문서 |
| B | Vite·프록시(휴대폰)·Tailwind 토큰·환경변수 | §3 · §4 | 이 문서(코드) |
| C | axios 공통 클라이언트·에러 메시지·API 모듈 91문 | §5 · §6 · §7 | 이 문서(코드) |
| D | TanStack Query 키·캐시·무효화 / Zustand 3 store | §8 · §9 | 이 문서(코드) |
| E | 인증 전체(가입·로그인·refresh·복원·가드) | §10 | 이 문서(코드) |
| F | 라우터 21화면·App·main·레이아웃 5종·Header | §11 · §12 | 이 문서(코드) |
| G | 디자인 시스템·UX 원칙·화면별 설계(홈/사찰목록/사찰상세/로그인·가입/인증/관리자…) | §13 · §14 | 이 문서(설계+핵심 코드) |
| H | 에러 처리 체계(발랄한 문구표)·공통 컴포넌트 | §15 · §16 | 이 문서(코드) |
| I | 지도·위치·QR 스캔 | §17 · §18 | 이 문서(코드) |
| J | 검증(Vitest·Playwright)·FE-1~FE-8 진행표·마지막 줄 | §19 · §20 | 이 문서 |

노션 붙여넣기: 각 §를 하나의 페이지로. 코드블록은 그대로 붙는다.

---

## 1. 확정 결정 (명세 ↔ 인수인계 충돌은 여기서 끝낸다)

| # | 항목 | 명세 ①/노션 교안 | 인수인계 v2 | **결정** | 이유 |
|---|---|---|---|---|---|
| 1 | Access 토큰 보관 | 노션 교안: localStorage | 메모리(localStorage 금지) | **메모리(Zustand, persist 없음)** | 백엔드가 이미 refresh 쿠키 회전·재사용 감지까지 구현함. XSS 한 방에 토큰이 새는 localStorage를 쓸 이유가 없다. 새로고침 복원은 `POST /api/auth/refresh`(쿠키) → `GET /api/users/me` 2단으로 해결 |
| 2 | 새로고침 복원 | localStorage 토큰 → `/me` | – | **refresh → me** | 위와 동일. 쿠키가 없으면 비로그인으로 조용히 시작 |
| 3 | Access 수명 | 1시간 | 30분 | **30분(서버 값 따름)** | 프론트는 수명을 계산하지 않는다. 401이 오면 refresh 1회 |
| 4 | 세션 | 90분 | 60분 | **60분** | `expiresAt`을 서버가 주므로 프론트는 그 값으로 카운트다운만 |
| 5 | 스탬프 API | `/attempts/{id}/location` 등 | `gps-check → {id}/qr → {id}/mission` | **인수인계(실제 구현)** | `api-types.d.ts`가 정본 |
| 6 | 싱글페이지 | `/api/temples/{id}/page` | `/api/sites/{siteId}/page` | **`/api/sites`** | 실제 구현 |
| 7 | 사진 업로드 | `PUT /api/photos/{templeId}` | presign → PUT 저장소 → `photoKey`로 미션 제출 | **presign 방식** | 서버는 바이트를 만지지 않는다 |
| 8 | 인쇄 부수 | DTO: 1~3 | 1~5 | **1~5** | 정본정정표 |
| 9 | 전자책 종류 | REGION/INTERIM/FINAL | PILGRIMAGE/HOEHYANG/PERSONAL, INTERIM 없음 | **인수인계** | 실제 구현 |
| 10 | 카카오 | 사용자: JS 키로 프론트가 그림 / 관리자 검색: 서버 REST 키 | 동일 | **동일** | REST 키는 프론트에 절대 없음 |
| 11 | 좌표 | 서버에 안 보냄 | 원칙① + 요청에 `lat/lng` 키 있으면 개발 모드 throw | **인터셉터에서 재귀 검사 후 throw** | 서버 400보다 먼저 막는다 |
| 12 | 라우터 스타일 | "router 많이" | 21화면 | **중첩 라우트 + 레이아웃 라우트 + lazy** | §11 |
| 13 | main 확장자 | jsx 허용 | TS | **`main.tsx`** | 프로젝트 전체 TS. jsx 혼용 금지 |
| 14 | 에러 문구 | – | – | **코드표 기반 + 발랄한 톤, 단 인증·보안 오류는 담백하게** | §15 |
| 15 | 로그아웃 | – | 200 | 200/204 모두 성공 처리 | client가 204 본문 없음 처리 |

토큰 흐름(결정 1·2 확정판):

```
로그인      POST /api/auth/login  → 본문 accessToken(메모리) + Set-Cookie refresh(HttpOnly, Path=/api/auth, 14일)
요청        Authorization: Bearer <메모리 토큰>
401         → POST /api/auth/refresh (withCredentials) 1회 → 새 accessToken → 원 요청 재시도
            → refresh도 401이면 store.clear() + /login?next=현재경로
새로고침    main.tsx bootstrap(): refresh 시도 → 성공이면 GET /api/users/me → store.set(user)
로그아웃    POST /api/auth/logout → store.clear() → 홈
```

---

## 2. 폴더 구조 (루트 `frontend/`)

```
frontend/
├── index.html
├── package.json
├── vite.config.ts                 # 프록시(/api → 8080) · https(휴대폰) · alias @
├── tsconfig.json · tsconfig.node.json
├── tailwind.config.ts             # §4 디자인 토큰
├── postcss.config.cjs
├── .env.example                   # VITE_API_BASE_URL · VITE_KAKAO_JS_KEY (값 없음)
├── .env.local                     # gitignore
├── public/
│   ├── fonts/                     # NotoSansKR·NotoSerifKR (woff2, OFL)
│   └── intro/                     # 1단 산문 애니메이션 에셋(없으면 절차적 폴백)
├── e2e/                           # Playwright 5흐름
├── src/
│   ├── main.tsx                   # 부트스트랩(refresh→me) → QueryClient → Router
│   ├── App.tsx                    # RouterProvider + 전역 Toast/Boundary
│   ├── router/
│   │   ├── index.tsx              # createBrowserRouter (21화면, lazy)
│   │   ├── guards/ProtectedRoute.tsx · AdminRoute.tsx · EditorRoute.tsx · IntroGate.tsx
│   │   └── paths.ts               # 경로 상수(문자열 하드코딩 금지)
│   ├── api/
│   │   ├── client.ts              # axios 인스턴스 + 인터셉터(§5)
│   │   ├── envelope.ts            # ApiResponse 해제·ApiError 클래스
│   │   ├── errorMessage.ts        # 코드→문구(§15)
│   │   ├── coordinateGuard.ts     # lat/lng 키 재귀 검사
│   │   ├── types.d.ts             # generate-api-types.js 산출물(91문·DTO 104) — 손으로 안 고침
│   │   └── modules/               # 도메인별 함수 (§7)
│   │       auth.ts users.ts regions.ts courses.ts sites.ts verses.ts pilgrimages.ts
│   │       stamps.ts uploads.ts thinkbox.ts meditations.ts ebooks.ts printOrders.ts
│   │       certificates.ts rewards.ts content.ts admin/ (sites courses stamps content rewards ebooks kakao manuscripts)
│   ├── queries/                   # TanStack Query: keys + hooks (§8)
│   │   ├── keys.ts
│   │   ├── useHome.ts useRegions.ts useCourse.ts useSitePage.ts useVerses.ts usePassport.ts
│   │   ├── useStamp.ts useThinkbox.ts useMeditations.ts useEbooks.ts useCertificates.ts useRewards.ts
│   │   └── admin/…
│   ├── stores/                    # Zustand (§9)
│   │   ├── authStore.ts · uiStore.ts · stampStore.ts
│   ├── layouts/                   # (§12)
│   │   ├── RootLayout.tsx         # 공통: 폰트·테마·Toast·ScrollRestoration·오프라인 배너
│   │   ├── AppLayout.tsx          # 하단 탭 5 + 플로팅 홈 + Header
│   │   ├── AuthLayout.tsx         # 로그인/가입: 산문 배경 + 카드
│   │   ├── FocusLayout.tsx        # 인트로·현장 인증·QR 스캔·명상 재생: 탭 없음, 닫기 버튼만
│   │   └── AdminLayout.tsx        # 사이드바 + 상단바(데스크톱 우선)
│   ├── components/
│   │   ├── ui/                    # Button Input Textarea Select Card Badge Skeleton EmptyState Toast Modal Sheet Progress Tabs
│   │   ├── header/Header.tsx · UserMenu.tsx · LocaleSwitch.tsx · TierSwitch.tsx
│   │   ├── nav/BottomTabs.tsx · FloatingHome.tsx
│   │   ├── verse/VerseCard.tsx · VerseRibbon.tsx (5구 색)
│   │   ├── site/SiteCard.tsx · SiteStampMark.tsx · SiteBlocks/*(9블록)
│   │   ├── map/KakaoMap.tsx · SiteMarker.tsx · MyLocationDot.tsx
│   │   ├── stamp/GpsStep.tsx · QrStep.tsx · MissionStep.tsx · StampResult.tsx · SessionTimer.tsx
│   │   ├── qr/QrScanner.tsx
│   │   ├── photo/PhotoUploader.tsx
│   │   └── error/ErrorBoundary.tsx · RouteError.tsx · ErrorIllust.tsx
│   ├── pages/                     # 21화면 (§11 표)
│   │   ├── intro/IntroPage.tsx
│   │   ├── home/HomePage.tsx
│   │   ├── auth/LoginPage.tsx · SignupPage.tsx
│   │   ├── regions/RegionsPage.tsx · RegionDetailPage.tsx
│   │   ├── courses/CourseDetailPage.tsx          # = 사찰 목록 화면
│   │   ├── sites/SitePage.tsx                    # = 사찰 상세(싱글페이지 9블록)
│   │   ├── stamp/StampFlowPage.tsx · CheckinPage.tsx · EvidencePage.tsx
│   │   ├── passport/PassportPage.tsx
│   │   ├── thinkbox/ThinkboxPage.tsx
│   │   ├── meditation/MeditationListPage.tsx · MeditationPlayPage.tsx
│   │   ├── ebook/EbookPage.tsx · PrintOrderPage.tsx
│   │   ├── certificate/CertificatesPage.tsx · VerifyPage.tsx(공개)
│   │   ├── reward/RewardsPage.tsx
│   │   ├── settings/SettingsPage.tsx · GuidePage.tsx(가는 법)
│   │   ├── admin/AdminHome · AdminSites · AdminSiteForm · AdminKakaoSearch · AdminCourses · AdminCourseForm
│   │   │        AdminStampReview · AdminQr · AdminContent · AdminManuscripts · AdminRewards · AdminEbooks · AdminPrintOrders
│   │   └── error/NotFoundPage.tsx · ForbiddenPage.tsx
│   ├── hooks/useGeolocation.ts · useOnline.ts · useCountdown.ts · useKakaoLoader.ts · useIntroFlag.ts
│   ├── lib/distance.ts(haversine) · format.ts · mask.ts · validation.ts(zod 스키마) · tier.ts
│   ├── i18n/index.ts · ko.json(폴백만) — UI 문자열은 `/api/i18n/{locale}`
│   ├── styles/index.css           # @tailwind + 폰트 + CSS 변수(tier 변형)
│   └── test/setup.ts · client.test.ts · authStore.test.ts …
└── scripts/generate-api-types.js  # backend/docs/frontend/generate-api-types.js 재실행 래퍼
```

규칙: `pages/`는 조립만(데이터 훅 호출 + 컴포넌트 배치). 데이터 접근은 `queries/`·`api/modules/`에서만. 컴포넌트는 axios를 import하지 않는다.

---

## 3. Vite · 프록시(휴대폰 테스트) · 환경변수

### 3-1 왜 프록시인가
휴대폰은 `localhost:8080`을 모른다. 휴대폰이 보는 주소는 PC의 LAN IP(`192.168.x.x:5173`) 하나뿐이어야 하고, `/api`는 Vite가 대신 8080으로 넘긴다. 그러면 **같은 출처**가 되어 refresh 쿠키(`Path=/api/auth`)가 그대로 붙고 CORS도 없다. 배포 후에는 `VITE_API_BASE_URL`에 Render 주소를 넣고, 쿠키는 `withCredentials` + 백엔드 `cors.allowed-origins`로 처리한다.

| 상황 | 주소 | 위치·카메라 | 쿠키 |
|---|---|---|---|
| PC 개발 | `http://localhost:5173` | localhost는 secure context 취급 → 됨 | 됨(프록시) |
| 휴대폰(같은 Wi-Fi) | `http://192.168.x.x:5173` | **http라서 Geolocation·카메라 거부** | 됨(프록시) |
| 휴대폰 + basic-ssl | `https://192.168.x.x:5173` | 자체서명 경고 1회 수락 후 됨 | 됨 |
| 휴대폰 + 터널(cloudflared) | `https://xxx.trycloudflare.com` | 됨 | 됨(같은 출처) |

권장: 평소 `basic-ssl`, 다른 사람 폰으로 시연할 때 cloudflared.

### 3-2 `vite.config.ts`
```ts
import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import basicSsl from '@vitejs/plugin-basic-ssl'
import path from 'node:path'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const useHttps = env.VITE_DEV_HTTPS === 'true'      // 휴대폰 위치·카메라 테스트 시 true
  return {
    plugins: [react(), ...(useHttps ? [basicSsl()] : [])],
    resolve: { alias: { '@': path.resolve(__dirname, 'src') } },
    server: {
      host: true,                    // 0.0.0.0 — 휴대폰이 LAN IP로 접속
      port: 5173,
      strictPort: true,
      proxy: {
        '/api': {
          target: env.VITE_PROXY_TARGET || 'http://localhost:8080',
          changeOrigin: true,
          // 쿠키는 같은 출처로 넘어오므로 cookieDomainRewrite 불필요.
          // 백엔드가 Secure 쿠키를 내릴 때 http 개발이면 브라우저가 버린다 → local 프로파일은 Secure=false 권장(정정표 등록)
        },
      },
    },
    build: {
      target: 'es2022',
      sourcemap: false,
      rollupOptions: {
        output: {
          manualChunks: {
            vendor: ['react', 'react-dom', 'react-router-dom', '@tanstack/react-query', 'axios', 'zustand'],
            zxing: ['@zxing/browser'],   // QR 스캔 화면에서만 lazy
          },
        },
      },
    },
    test: { environment: 'jsdom', setupFiles: './src/test/setup.ts', globals: true },
  }
})
```

### 3-3 환경변수 (`.env.example`)
```
VITE_API_BASE_URL=            # 비우면 상대경로 '/api'(프록시). 배포: https://temple-stamp.onrender.com
VITE_KAKAO_JS_KEY=            # 카카오 JavaScript 키 (REST 키 아님)
VITE_DEV_HTTPS=false          # 휴대폰 위치·카메라 테스트 시 true
VITE_PROXY_TARGET=http://localhost:8080
VITE_PUBLIC_BASE_URL=         # 인증서 진위 링크 표시용(서버 PUBLIC_BASE_URL과 동일)
```
`import.meta.env` 접근은 `src/lib/env.ts` 한 곳에서만(누락 시 개발 모드 console.warn, 배포 빌드는 throw).

### 3-4 카카오 도메인 등록
카카오 개발자 콘솔 → 플랫폼 Web → `http://localhost:5173`, `https://192.168.x.x:5173`(폰 테스트), Netlify 주소. 등록 안 된 출처에서는 SDK가 조용히 실패한다 → `useKakaoLoader`가 10초 타임아웃 후 "지도 대신 목록으로" 폴백.

---

## 4. Tailwind 디자인 토큰 ("산문을 지나 경내로")

| 토큰 | 값 | 쓰임 |
|---|---|---|
| `ink-900` | #1B1A17 | 본문·Header 글자 |
| `ink-600` | #4A473F | 보조 텍스트 |
| `paper-50` | #F6F1E7 | 배경(한지) |
| `paper-100` | #EDE6D6 | 카드·구분 |
| `ochre-500` | #B8862B | 주 액션·도장 인주 |
| `ochre-600` | #9A6F22 | hover |
| `verse-1 ~ verse-5` | #B8862B · #5B6B4A · #6C7A89 · #7E5A9B · #A24B3D | 오관게 5구(출처·감사·절제·포행·다짐) 리본·마커·진행 점 |
| `success` / `warn` / `danger` | #4F7A4A / #C58A2A / #A24B3D | 상태 |
| 서체 | `font-sans`: Noto Sans KR · `font-serif`: Noto Serif KR(구절·인용) | |
| 크기 | 기본 16px · RIDER 변형 본문 20px·버튼 56px | `data-tier` |

`tailwind.config.ts`
```ts
import type { Config } from 'tailwindcss'
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        ink: { 900: '#1B1A17', 600: '#4A473F', 300: '#A9A396' },
        paper: { 50: '#F6F1E7', 100: '#EDE6D6', 200: '#E1D8C2' },
        ochre: { 500: '#B8862B', 600: '#9A6F22', 100: '#F3E7CC' },
        verse: { 1: '#B8862B', 2: '#5B6B4A', 3: '#6C7A89', 4: '#7E5A9B', 5: '#A24B3D' },
        success: '#4F7A4A', warn: '#C58A2A', danger: '#A24B3D',
      },
      fontFamily: { sans: ['"Noto Sans KR"', 'system-ui', 'sans-serif'], serif: ['"Noto Serif KR"', 'serif'] },
      borderRadius: { seal: '0.9rem' },
      boxShadow: { card: '0 1px 2px rgba(27,26,23,.06), 0 8px 24px rgba(27,26,23,.06)' },
      keyframes: {
        ink: { '0%': { transform: 'scale(.6)', opacity: '0' }, '60%': { transform: 'scale(1.08)', opacity: '1' }, '100%': { transform: 'scale(1)' } },
        sway: { '0%,100%': { transform: 'rotate(-0.6deg)' }, '50%': { transform: 'rotate(0.6deg)' } },
      },
      animation: { ink: 'ink .6s cubic-bezier(.2,.8,.2,1) both', sway: 'sway 4s ease-in-out infinite' },
    },
  },
  plugins: [],
} satisfies Config
```

`styles/index.css` — 타겟 3종 변형은 CSS 변수 한 벌로 처리(컴포넌트마다 분기 금지):
```css
@tailwind base; @tailwind components; @tailwind utilities;
:root { --fs-base: 16px; --btn-h: 44px; --tap: 44px; }
[data-tier="RIDER"] { --fs-base: 20px; --btn-h: 56px; --tap: 56px; }
[data-tier="FOREIGN"] { --fs-base: 16px; --btn-h: 48px; --tap: 48px; }
html { font-size: var(--fs-base); background: theme('colors.paper.50'); color: theme('colors.ink.900'); }
.btn { @apply inline-flex items-center justify-center rounded-seal font-medium transition active:scale-[.98]; height: var(--btn-h); }
```
`data-tier`는 `RootLayout`이 `authStore.user.tier`(없으면 uiStore.previewTier, 없으면 AGE30)를 `<html>`에 붙인다. 타겟별 차이: **2030** 구절 먼저·감성 카드 / **RIDER** 큰 글자·큰 버튼·싱글페이지 주행 블록 상단 고정·홈 2번째 카드 "다음 사찰 주행정보" / **FOREIGN** 한자 원문 유지 + 로케일 스위치 상시 노출·인증 버튼에 아이콘.

---

## 5. axios 공통 클라이언트

### 5-1 책임 표 (client.ts가 하는 일 / 안 하는 일)

| 하는 일 | 안 하는 일 |
|---|---|
| baseURL·timeout(60초, E-13 콜드스타트)·`withCredentials` | 토큰을 localStorage에 넣기 |
| `Authorization: Bearer` 자동 부착(메모리 토큰) | 토큰 만료시각 계산 |
| `X-Request-Id` 생성(8자리)·응답 헤더 로그 | 화면 이동(→ 가드·훅이 함) |
| 요청 본문·params에 `lat/lng/latitude/longitude` 키 재귀 검사 → 개발 throw·배포 삭제+경고 | 좌표 계산 |
| 봉투 `{success,data,error}` 해제 → `data`만 반환 | 성공 토스트 |
| 401 → refresh 1회 → 재시도(동시 401은 한 번만 refresh, 나머지는 대기) | 403 처리(→ 가드) |
| 204·빈 본문 → `undefined` | |
| 네트워크 오류·타임아웃 → `ApiError('NET-0000')` 1회 재시도(GET·멱등 POST만) | |

### 5-2 `api/envelope.ts`
```ts
export interface ApiEnvelope<T> {
  success: boolean
  data: T | null
  error: { code: string; message: string; fields?: Record<string, string> } | null
  timestamp: string
}
export interface Page<T> { items: T[]; page: number; size: number; totalElements: number; totalPages: number; hasNext: boolean }

export class ApiError extends Error {
  constructor(
    public readonly code: string,        // 'STAMP-4091' · 'NET-0000' · 'NET-0504'
    public readonly status: number,      // HTTP 상태 (네트워크 오류 0)
    message: string,
    public readonly fields?: Record<string, string>,
    public readonly requestId?: string,
  ) { super(message); this.name = 'ApiError' }
  get domain() { return this.code.split('-')[0] }
  is(prefix: string) { return this.code.startsWith(prefix) }
}
```

### 5-3 `api/coordinateGuard.ts`
```ts
const BANNED = new Set(['lat', 'lng', 'latitude', 'longitude', 'lon', 'coords'])
export function assertNoCoordinates(value: unknown, path = 'body'): void {
  if (value == null || typeof value !== 'object') return
  if (value instanceof FormData || value instanceof Blob) return
  for (const [k, v] of Object.entries(value as Record<string, unknown>)) {
    if (BANNED.has(k.toLowerCase())) {
      const msg = `[원칙①] 서버로 좌표를 보내려 했습니다: ${path}.${k}`
      if (import.meta.env.DEV) throw new Error(msg)
      console.error(msg); delete (value as Record<string, unknown>)[k]; continue
    }
    assertNoCoordinates(v, `${path}.${k}`)
  }
}
```

### 5-4 `api/client.ts`
```ts
import axios, { AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '@/stores/authStore'
import { ApiError, type ApiEnvelope } from './envelope'
import { assertNoCoordinates } from './coordinateGuard'
import { env } from '@/lib/env'

const baseURL = env.API_BASE_URL ? `${env.API_BASE_URL}/api` : '/api'

export const http = axios.create({ baseURL, timeout: 60_000, withCredentials: true })
const bare = axios.create({ baseURL, timeout: 20_000, withCredentials: true })   // refresh 전용(인터셉터 없음)

type Cfg = InternalAxiosRequestConfig & { _retried?: boolean; _netRetried?: boolean; skipAuth?: boolean }

const rid = () => crypto.randomUUID().slice(0, 8)

http.interceptors.request.use((config: Cfg) => {
  assertNoCoordinates(config.data); assertNoCoordinates(config.params, 'params')
  config.headers['X-Request-Id'] = rid()
  const token = useAuthStore.getState().accessToken
  if (token && !config.skipAuth) config.headers.Authorization = `Bearer ${token}`
  return config
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
    const env = res.data as ApiEnvelope<unknown>
    if (env && typeof env === 'object' && 'success' in env) {
      if (env.success) return env.data as never
      throw new ApiError(env.error?.code ?? 'COMMON-5000', res.status, env.error?.message ?? '', env.error?.fields, res.headers['x-request-id'])
    }
    return res.data as never                                  // 파일 등 봉투 아닌 응답
  },
  async (err: AxiosError<ApiEnvelope<unknown>>) => {
    const cfg = err.config as Cfg | undefined
    const status = err.response?.status ?? 0
    const body = err.response?.data
    const reqId = err.response?.headers?.['x-request-id']

    // 401 → refresh 1회 → 재시도 (refresh·login·signup 자체는 제외)
    if (status === 401 && cfg && !cfg._retried && !cfg.skipAuth && !cfg.url?.startsWith('/auth/')) {
      cfg._retried = true
      const token = await refreshAccessToken()
      if (token) { cfg.headers.Authorization = `Bearer ${token}`; return http(cfg) }
    }
    // 네트워크·타임아웃 → 1회 재시도 (GET·멱등 지정만)
    if (!err.response && cfg && !cfg._netRetried && (cfg.method === 'get' || (cfg as AxiosRequestConfig & { idempotent?: boolean }).idempotent)) {
      cfg._netRetried = true
      await new Promise(r => setTimeout(r, 1500))
      return http(cfg)
    }
    if (!err.response) throw new ApiError(err.code === 'ECONNABORTED' ? 'NET-0504' : 'NET-0000', 0, err.message)
    throw new ApiError(body?.error?.code ?? `COMMON-${status}0`, status, body?.error?.message ?? err.message, body?.error?.fields, reqId)
  },
)

// 사용 예: const site = await http.get<never, SiteResponse>('/sites/1')
```
핵심: **`http.get<never, T>()`** 처럼 두 번째 제네릭에 응답 타입을 넣으면 인터셉터가 봉투를 벗긴 `data`가 곧 반환값이 된다. 모듈은 이 래퍼만 쓴다.

### 5-5 `api/modules/_helpers.ts`
```ts
import { http } from '../client'
export const get  = <T>(url: string, params?: object) => http.get<never, T>(url, { params })
export const post = <T>(url: string, body?: object, idempotent = false) => http.post<never, T>(url, body, { idempotent } as never)
export const put  = <T>(url: string, body?: object) => http.put<never, T>(url, body)
export const patch = <T>(url: string, body?: object) => http.patch<never, T>(url, body)
export const del  = (url: string) => http.delete<never, void>(url)
```

---

## 6. 에러 메시지 함수 · 에러 분류

### 6-1 `api/errorMessage.ts`
```ts
import { ApiError } from './envelope'
import { MESSAGES, DOMAIN_FALLBACK } from '@/lib/errorTexts'   // §15 표

export interface UiError { title: string; body: string; action?: 'login' | 'retry' | 'passport' | 'wait' | 'settings' | 'none'; tone: 'fun' | 'plain' }
export function toUiError(e: unknown): UiError {
  if (e instanceof ApiError) {
    const hit = MESSAGES[e.code]
    if (hit) return hit
    const dom = DOMAIN_FALLBACK[e.domain]
    if (dom) return { ...dom, body: e.message || dom.body }
    if (e.status >= 500) return MESSAGES['COMMON-5000']
    return { title: '잠깐만요', body: e.message || '요청을 처리하지 못했어요.', action: 'retry', tone: 'plain' }
  }
  if (e instanceof Error && e.message.startsWith('[원칙①]')) return { title: '개발자 실수', body: e.message, action: 'none', tone: 'plain' }
  return MESSAGES['COMMON-5000']
}
export const fieldErrors = (e: unknown) => (e instanceof ApiError ? e.fields ?? {} : {})
```

### 6-2 어디서 무엇을 처리하나

| 층 | 잡는 것 | 하는 일 |
|---|---|---|
| `client.ts` | 401, 네트워크 | refresh·재시도 후 `ApiError` throw |
| `queries/*` (TanStack) | 쿼리 실패 | `retry: (n, err) => err.status >= 500 && n < 2`, 4xx는 재시도 안 함 |
| 페이지 `useQuery` 결과 | `isError` | `<QueryError error={error} onRetry={refetch} />` (문구는 `toUiError`) |
| `useMutation.onError` | 폼 제출 실패 | `fieldErrors` → RHF `setError`, 그 외 Toast |
| `ErrorBoundary`(레이아웃별) | 렌더 예외 | 발랄한 전면 화면 + 홈/재시도 |
| `router errorElement` | 404·lazy 실패 | `RouteError` |
| `useOnline` | 오프라인 | 상단 배너 "전파가 산문 밖에 있어요" + 뮤테이션 큐 보류(Q8 최소: 재시도 버튼) |
| `AdminRoute`/`ProtectedRoute` | 403·미로그인 | 리다이렉트 |

---

## 7. API 모듈 (91문 매핑) — 타입은 `api/types.d.ts`(자동 생성)에서만 import

### 7-1 엔드포인트 ↔ 모듈 ↔ 화면 표

| 모듈 | 엔드포인트 (권한) | 화면 |
|---|---|---|
| auth | `POST /auth/signup`(201) · `/auth/login` · `/auth/refresh`(쿠키) · `/auth/logout`(200) | S-16 로그인/가입, 부트스트랩 |
| users | `GET /users/me` · `PATCH /users/me`(tier·locale·nickname·알림) · `POST /users/me/agreements` · `DELETE /users/me`(탈퇴 204, 즉시 익명화) | 설정, 복원 |
| regions | `GET /regions` · `GET /regions/{id}` (공개, 로그인 시 진행률 결합) | 여권·권역 상세 |
| courses | `GET /courses?regionId=` · `GET /courses/{id}`(자리 5 + 후보·`congested`·좌표·QR 힌트) (선택) | **사찰 목록 화면** |
| sites | `GET /sites/{id}` · `GET /sites/{id}/page?tier=&locale=`(9블록, 캐시 안 함) (선택) | **사찰 상세(싱글페이지)** |
| verses | `GET /verses` · `GET /verses/{no}` (공개) | 홈·인트로 2단 |
| pilgrimages | `POST /pilgrimages {courseId}`(멱등 200) · `GET /passport` | 코스 상세·여권 |
| stamps | `POST /stamps/gps-check {pilgrimageId, courseSiteId, withinRadius, accuracyGrade}` → `{stampId,status,expiresAt,qrLocationHint}` · `POST /stamps/{id}/qr {qrToken}` · `POST /stamps/{id}/mission {userSentence, photoKey?}` · `POST /stamps/evidence` · `GET /stamps/{id}`(복귀) · `GET /stamps/by-slot/{courseSiteId}` | 현장 인증·체크인·예외접수 |
| uploads | `GET /uploads/presign?purpose=PHOTO\|EVIDENCE&contentType=` → `{uploadUrl,fileKey,method,expiresIn}` → 프론트가 저장소에 직접 PUT | 사진 |
| thinkbox | `GET /thinkbox?sort=&page=&size=` · `POST` · `PATCH /{id}` · `DELETE /{id}`(204) · `GET /thinkbox/flashback`(data null 가능) | 생각상자·홈 |
| meditations | `GET /meditations?category=&limit=&locale=`(공개) · `GET /meditations/{id}`(공개) · `POST /meditations/{id}/logs` | 명상 |
| ebooks | `GET /ebooks` · `POST /ebooks/personal`(소장본, 하루 3) · `GET /ebooks/{id}/download`(presigned 10분, READY만) | 전자책 |
| printOrders | `GET /print-orders` · `POST /print-orders {ebookId, copies 1~5, phone, zip, address}` · `DELETE /print-orders/{id}`(REQUESTED만) | 인쇄 |
| certificates | `GET /certificates` · `GET /certificates/{id}/pdf` · `GET /certificates/verify/{serialNo}`(**공개**, 7필드) | 인증서·진위 확인 |
| rewards | `GET /rewards`(`claimable` 계산값) · `POST /rewards/{id}/claim` · `PUT /rewards/{id}/shipping` | 보상 |
| content | `GET /i18n/{locale}` · `GET /guide?locale=`(가는 법) · `GET /health` | 전 화면·가는 법 |
| admin/* | 사찰 CRUD·i18n·viewpoint·badge·status / 코스·자리 5 일괄·status / site-distances / QR 발급·rotate / stamps PENDING approve·reject / rewards review·resolve / ebooks retry / expansion-phrases·missions·review / meditations / manuscripts(CSV dryRun·반입·변형·4-eyes·퇴역) / kakao/places / housekeeping/run | 관리자 13화면 |

정확한 필드명·개수(91)는 `generate-api-types.js`를 다시 돌려 얻은 `types.d.ts`가 정본. 이 표와 다르면 **표를 고친다**.

### 7-2 모듈 예시 — `api/modules/auth.ts`
```ts
import { get, post } from './_helpers'
import type { SignupRequest, LoginRequest, LoginResponse, UserResponse } from '../types'

export const authApi = {
  signup: (body: SignupRequest) => post<LoginResponse>('/auth/signup', body),
  login:  (body: LoginRequest)  => post<LoginResponse>('/auth/login', body),
  logout: () => post<void>('/auth/logout'),
  me:     () => get<UserResponse>('/users/me'),
}
```

### 7-3 `api/modules/stamps.ts` (원칙① 적용의 대표)
```ts
import { get, post } from './_helpers'
import type { GpsCheckRequest, GpsCheckResponse, QrVerifyRequest, StampStatusResponse, MissionSubmitRequest, MissionResultResponse, EvidenceRequest, EvidenceResponse } from '../types'

export const stampApi = {
  gpsCheck: (body: GpsCheckRequest) => post<GpsCheckResponse>('/stamps/gps-check', body),  // withinRadius: boolean, accuracyGrade: 'HIGH'|'MID' (LOW는 보내지 않음)
  qr:       (stampId: number, body: QrVerifyRequest) => post<StampStatusResponse>(`/stamps/${stampId}/qr`, body),
  mission:  (stampId: number, body: MissionSubmitRequest) => post<MissionResultResponse>(`/stamps/${stampId}/mission`, body),
  evidence: (body: EvidenceRequest) => post<EvidenceResponse>('/stamps/evidence', body),
  get:      (stampId: number) => get<StampStatusResponse>(`/stamps/${stampId}`),
  bySlot:   (courseSiteId: number) => get<StampStatusResponse | null>(`/stamps/by-slot/${courseSiteId}`),
}
```

### 7-4 `api/modules/uploads.ts` — 저장소 직접 PUT
```ts
import axios from 'axios'
import { get } from './_helpers'
import type { PresignResponse } from '../types'
export const uploadApi = {
  presign: (purpose: 'PHOTO' | 'EVIDENCE', contentType: string) => get<PresignResponse>('/uploads/presign', { purpose, contentType }),
  async putFile(file: File, purpose: 'PHOTO' | 'EVIDENCE', onProgress?: (p: number) => void): Promise<string> {
    const p = await this.presign(purpose, file.type)
    await axios.put(p.uploadUrl, file, { headers: { 'Content-Type': file.type }, withCredentials: false,
      onUploadProgress: e => onProgress?.(e.total ? Math.round(e.loaded / e.total * 100) : 0) })
    return p.fileKey                                    // 이 키만 서버로 간다(EXIF 제거는 앱 책임 → 업로드 전 canvas 재인코딩)
  },
}
```
EXIF: `lib/image.ts`의 `stripExif(file)` — `createImageBitmap` → canvas → `toBlob('image/jpeg', .9)`. 위치 메타가 서버로 가지 않는 두 번째 방어선.

---

## 8. TanStack Query — 키·캐시·무효화

### 8-1 `queries/keys.ts`
```ts
export const qk = {
  me: ['me'] as const,
  home: ['home'] as const,
  verses: ['verses'] as const,
  regions: ['regions'] as const,
  region: (id: number) => ['regions', id] as const,
  courses: (regionId?: number) => ['courses', { regionId }] as const,
  course: (id: number) => ['courses', id] as const,
  site: (id: number) => ['sites', id] as const,
  sitePage: (id: number, tier?: string, locale?: string) => ['sites', id, 'page', { tier, locale }] as const,
  passport: ['passport'] as const,
  stamp: (id: number) => ['stamps', id] as const,
  stampBySlot: (csId: number) => ['stamps', 'slot', csId] as const,
  thinkbox: (sort: string, page: number) => ['thinkbox', { sort, page }] as const,
  flashback: ['thinkbox', 'flashback'] as const,
  meditations: (category?: string, locale?: string) => ['meditations', { category, locale }] as const,
  meditation: (id: number) => ['meditations', id] as const,
  ebooks: ['ebooks'] as const,
  printOrders: ['print-orders'] as const,
  certificates: ['certificates'] as const,
  verify: (serial: string) => ['verify', serial] as const,
  rewards: ['rewards'] as const,
  i18n: (locale: string) => ['i18n', locale] as const,
  guide: (locale: string) => ['guide', locale] as const,
  admin: { sites: ['admin', 'sites'] as const, pending: ['admin', 'stamps', 'PENDING'] as const /* … */ },
}
```

### 8-2 캐시 정책 (명세 §10 + 인수인계)

| 쿼리 | staleTime | 비고 |
|---|---|---|
| verses · i18n · guide | 24h | 거의 안 변함 |
| meditations | 24h | 공개 |
| regions · region · courses · passport · home | 60s | 로그인 상태에 따라 값이 다름 → 키에 userId 안 넣고 **로그인/로그아웃 시 `queryClient.clear()`** |
| sitePage | 0 (`gcTime: 0`) | 확장문구·과제가 매번 달라야 함 |
| stamp · stampBySlot | 0 | 상태 전이 중 |
| thinkbox · ebooks · certificates · rewards · print-orders | 60s | |

### 8-3 무효화 규칙 (`queries/invalidate.ts`)
```ts
export const invalidateAfterStamp = (qc: QueryClient) =>
  Promise.all([qk.home, qk.passport, qk.regions, qk.certificates, qk.ebooks, qk.rewards].map(k => qc.invalidateQueries({ queryKey: k })))
export const invalidateAfterLocale = (qc: QueryClient) => qc.invalidateQueries()   // 언어 변경 → 전체
export const resetOnAuthChange = (qc: QueryClient) => qc.clear()
```

### 8-4 훅 예시 — 홈 4호출 병렬
```ts
export function useHome() {
  const isAuthed = useAuthStore(s => !!s.user)
  return useQueries({ queries: [
    { queryKey: qk.verses, queryFn: verseApi.list, staleTime: 86_400_000 },
    { queryKey: qk.passport, queryFn: pilgrimageApi.passport, enabled: isAuthed, staleTime: 60_000 },
    { queryKey: qk.flashback, queryFn: thinkboxApi.flashback, enabled: isAuthed, staleTime: 60_000 },
    { queryKey: qk.meditations(undefined, undefined), queryFn: () => meditationApi.list({ limit: 3 }), staleTime: 86_400_000 },
  ]})
}
```
`QueryClient` 기본: `retry: (n, e) => (e as ApiError).status >= 500 && n < 2`, `refetchOnWindowFocus: false`(산속에서 포커스 왔다 갔다 할 때 재요청 방지), `mutations.retry: 0`.

---

## 9. Zustand — 3개 store (작게)

### 9-1 `stores/authStore.ts`
```ts
import { create } from 'zustand'
import type { UserResponse } from '@/api/types'

type Status = 'idle' | 'restoring' | 'authed' | 'guest'
interface AuthState {
  status: Status
  accessToken: string | null          // 메모리 전용 — persist 미들웨어 절대 금지
  user: UserResponse | null
  setAccessToken: (t: string | null) => void
  setSession: (t: string, u: UserResponse) => void
  setUser: (u: UserResponse) => void
  setStatus: (s: Status) => void
  clear: () => void
}
export const useAuthStore = create<AuthState>((set) => ({
  status: 'idle', accessToken: null, user: null,
  setAccessToken: (accessToken) => set({ accessToken }),
  setSession: (accessToken, user) => set({ accessToken, user, status: 'authed' }),
  setUser: (user) => set({ user, status: 'authed' }),
  setStatus: (status) => set({ status }),
  clear: () => set({ accessToken: null, user: null, status: 'guest' }),
}))
export const selectIsAdmin = (s: AuthState) => s.user?.role === 'ADMIN'
export const selectIsEditor = (s: AuthState) => s.user?.role === 'EDITOR' || s.user?.role === 'ADMIN'
export const selectTier = (s: AuthState) => s.user?.tier ?? 'AGE30'
```

### 9-2 `stores/uiStore.ts` — 화면 잡동사니(persist는 **이 것만**, 키 `ts-ui`)
```ts
interface UiState {
  locale: 'ko' | 'en' | 'ja' | 'zh'; previewTier: Tier | null; introDone: boolean
  toasts: Toast[]; pushToast: (t: Omit<Toast, 'id'>) => void; dismissToast: (id: string) => void
  setLocale: (l: UiState['locale']) => void; setIntroDone: () => void; setPreviewTier: (t: Tier | null) => void
}
// persist partialize: ({ locale, previewTier, introDone }) — 토스트는 저장 안 함
```
localStorage에 들어가는 것은 **locale · 비로그인 미리보기 tier · 인트로 1회 플래그** 세 개뿐이다(인수인계 §11-1).

### 9-3 `stores/stampStore.ts` — 현장 인증 진행 상태(앱 전환·복귀용)
```ts
interface StampState {
  stampId: number | null; courseSiteId: number | null; pilgrimageId: number | null
  step: 'GPS' | 'QR' | 'MISSION' | 'DONE'; expiresAt: string | null; qrLocationHint: string | null
  begin: (p: { pilgrimageId: number; courseSiteId: number }) => void
  gpsDone: (r: GpsCheckResponse) => void; qrDone: () => void; finish: () => void; reset: () => void
}
```
`sessionStorage`에 persist(탭 닫히면 소멸, 좌표는 애초에 없음). 앱 복귀 시 `GET /stamps/{id}`로 서버 상태와 대조해 `step`을 맞춘다 — **로컬이 아니라 서버가 진실**.

---

## 10. 인증 전체 흐름 (회원가입·로그인·JWT·복원·가드)

### 10-1 `lib/validation.ts` (zod — 백엔드 SignupRequest 규칙과 1:1)
```ts
import { z } from 'zod'
export const signupSchema = z.object({
  email: z.string().trim().toLowerCase().email('이메일 모양이 아니에요').max(100),
  password: z.string().min(8, '8자 이상').max(50).regex(/^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z\d]).+$/, '영문·숫자·특수문자를 섞어 주세요'),
  passwordConfirm: z.string(),
  nickname: z.string().trim().min(2, '2자 이상').max(20, '20자 이하'),
  tier: z.enum(['AGE20','AGE30','AGE40','AGE50','AGE60','RIDER','FOREIGN']).default('AGE30'),
  agreeLocation: z.literal(true, { errorMap: () => ({ message: '위치 서비스 동의가 필요해요' }) }),
}).refine(d => d.password === d.passwordConfirm, { path: ['passwordConfirm'], message: '비밀번호가 서로 달라요' })
export const loginSchema = z.object({ email: z.string().trim().min(1, '이메일을 적어 주세요'), password: z.string().min(1, '비밀번호를 적어 주세요') })
```

### 10-2 `queries/useAuth.ts`
```ts
export function useLogin() {
  const qc = useQueryClient(); const setSession = useAuthStore(s => s.setSession)
  return useMutation({
    mutationFn: authApi.login,
    onSuccess: (res) => { setSession(res.accessToken, res.user); resetOnAuthChange(qc) },
  })
}
export function useSignup() { /* 동일, authApi.signup — 201 응답도 LoginResponse */ }
export function useLogout() {
  const qc = useQueryClient(); const clear = useAuthStore(s => s.clear)
  return useMutation({ mutationFn: authApi.logout, onSettled: () => { clear(); resetOnAuthChange(qc) } })  // 실패해도 로컬은 지운다
}
```

### 10-3 새로고침 복원 — `lib/bootstrap.ts` (main.tsx가 라우터 마운트 **전에** 실행)
```ts
export async function bootstrapAuth() {
  const s = useAuthStore.getState()
  s.setStatus('restoring')
  const token = await refreshAccessToken()          // 쿠키 없으면 null (에러 토스트 없음)
  if (!token) return s.clear()
  try { s.setUser(await authApi.me()) } catch { s.clear() }
}
```
| 단계 | 화면 | 시간 |
|---|---|---|
| `restoring` | 한지 배경 + 산문 로고 페이드(스켈레톤) | 보통 300ms, 콜드스타트 최대 60초 → 10초 지나면 "서버를 깨우는 중" 문구 |
| `authed` | 정상 | |
| `guest` | 비로그인 | 보호 라우트는 `/login?next=` |

### 10-4 가드 3종 (`router/guards/`)
```tsx
export function ProtectedRoute() {
  const status = useAuthStore(s => s.status); const loc = useLocation()
  if (status === 'idle' || status === 'restoring') return <FullscreenSkeleton />
  if (status === 'guest') return <Navigate to={`/login?next=${encodeURIComponent(loc.pathname + loc.search)}`} replace />
  return <Outlet />
}
export function AdminRoute() {
  const isAdmin = useAuthStore(selectIsAdmin); const status = useAuthStore(s => s.status)
  if (status !== 'authed') return <ProtectedRoute />            // 미로그인은 로그인으로
  return isAdmin ? <Outlet /> : <ForbiddenPage />                 // 로그인했지만 권한 없음 → 403 화면(리다이렉트 아님)
}
export function EditorRoute() { /* selectIsEditor */ }
export function IntroGate() {                                     // 최초 1회만 인트로
  const done = useUiStore(s => s.introDone)
  return done ? <Outlet /> : <Navigate to="/intro" replace />
}
```

### 10-5 로그인 페이지의 `next` 처리
로그인 성공 → `navigate(next ?? '/', { replace: true })`. QR 체크인(`/checkin?token=`)에서 왔다면 `next`에 토큰이 포함된 경로가 실려 로그인 직후 체크인 화면으로 돌아간다(노션 교안 "LoginPage 하나 수정"에 해당).

### 10-6 테스트 시나리오 (노션 교안 18~23 대응)

| # | 시나리오 | 기대 |
|---|---|---|
| 1 | 가입 → 자동 로그인 | 201 + 헤더에 닉네임, 쿠키 탭에 refresh 존재 |
| 2 | 로그인 → 새로고침 | 로그인 유지(네트워크 탭: refresh → me) |
| 3 | 잘못된 비밀번호 | `AUTH-4011` 문구, 계정 존재 여부 안 새어야 함 |
| 4 | 5회 실패 | `AUTH-4031` "15분 뒤" 문구, 버튼 비활성 + 타이머 |
| 5 | Access 만료(개발: JWT 수명 10초로 낮춰) | 401 → refresh → 원 요청 재시도, 화면 깜빡임 없음 |
| 6 | refresh 쿠키 삭제 후 요청 | `/login?next=` 이동 |
| 7 | 로그아웃 후 뒤로가기 | 보호 페이지 진입 불가 |
| 8 | USER로 `/admin` | 403 화면 |
| 9 | 다른 기기에서 재사용 감지 | 전 기기 로그아웃 → 이 기기도 다음 요청에서 `/login` |

---

## 11. 라우터 (21화면 + 관리자 13) · App · main

### 11-1 화면 ↔ 경로 ↔ 레이아웃 ↔ 가드

| S | 화면 | 경로 | 레이아웃 | 가드 | 진입 쿼리 |
|---|---|---|---|---|---|
| S-01 | 인트로 3단 | `/intro` | Focus | – | 없음 |
| S-02 | 홈 | `/` | App | IntroGate | verses·passport·flashback·meditations |
| S-03 | 여권(9권역) | `/passport` | App | Protected | passport |
| S-03′ | 권역 목록(비로그인 여권 대용) | `/regions` | App | – | regions |
| S-04 | 권역 상세 | `/regions/:regionId` | App | – | region·courses |
| S-04′ | 코스 상세 = **사찰 목록** | `/courses/:courseId` | App | – | course(+stampBySlot ×5 로그인 시) |
| S-05 | 사찰 싱글페이지 = **사찰 상세** | `/sites/:siteId` | App | – | sitePage |
| S-06 | 현장 인증 3단계 | `/sites/:siteId/verify` | Focus | Protected | stampBySlot |
| S-06′ | QR 체크인(카메라 앱 진입) | `/checkin?token=` | Focus | Protected | stamp(진행 중 것) |
| S-06″ | 예외 접수(EVIDENCE) | `/sites/:siteId/evidence` | Focus | Protected | – |
| S-07 | 발급 결과 | `/stamps/:stampId` | Focus | Protected | stamp |
| S-08 | 사진(미션 단계 내부) | – | – | – | presign |
| S-09 | 명상 108선 | `/meditations` | App | – | meditations |
| S-10 | 명상 재생 | `/meditations/:id` | Focus | –(기록만 Protected) | meditation |
| S-11 | 생각상자 | `/thinkbox` | App | Protected | thinkbox |
| S-12 | 보상 | `/rewards` | App | Protected | rewards |
| S-13 | 전자책·인쇄 | `/ebooks`, `/ebooks/print` | App | Protected | ebooks·print-orders |
| S-14 | 인증서 | `/certificates` | App | Protected | certificates |
| S-14′ | 진위 확인(공개) | `/verify/:serialNo` | Auth(카드형) | – | verify |
| S-15 | 설정·탈퇴 | `/settings` | App | Protected | me |
| S-16 | 로그인 / 가입 | `/login`, `/signup` | Auth | 로그인 상태면 `/`로 | – |
| S-17 | 사찰 가는 법 | `/guide` | App | – | guide |
| – | 관리자 | `/admin/*` | Admin | AdminRoute(콘텐츠 일부 EditorRoute) | admin/* |
| – | 404 / 403 | `*`, `/403` | App | – | – |

### 11-2 `router/paths.ts`
```ts
export const P = {
  intro: '/intro', home: '/', login: '/login', signup: '/signup',
  passport: '/passport', regions: '/regions', region: (id: number | string) => `/regions/${id}`,
  course: (id: number | string) => `/courses/${id}`, site: (id: number | string) => `/sites/${id}`,
  verify: (id: number | string) => `/sites/${id}/verify`, evidence: (id: number | string) => `/sites/${id}/evidence`,
  checkin: '/checkin', stamp: (id: number | string) => `/stamps/${id}`,
  meditations: '/meditations', meditation: (id: number | string) => `/meditations/${id}`,
  thinkbox: '/thinkbox', rewards: '/rewards', ebooks: '/ebooks', print: '/ebooks/print',
  certificates: '/certificates', certVerify: (s: string) => `/verify/${s}`, settings: '/settings', guide: '/guide',
  admin: { root: '/admin', sites: '/admin/sites', siteNew: '/admin/sites/new', site: (id: number | string) => `/admin/sites/${id}`,
    kakao: '/admin/sites/search', courses: '/admin/courses', course: (id: number | string) => `/admin/courses/${id}`,
    stamps: '/admin/stamps', qr: (id: number | string) => `/admin/sites/${id}/qr`, content: '/admin/content',
    manuscripts: '/admin/manuscripts', rewards: '/admin/rewards', ebooks: '/admin/ebooks', print: '/admin/print-orders' },
} as const
```

### 11-3 `router/index.tsx`
```tsx
import { createBrowserRouter } from 'react-router-dom'
import { lazy } from 'react'
import RootLayout from '@/layouts/RootLayout'; import AppLayout from '@/layouts/AppLayout'
import AuthLayout from '@/layouts/AuthLayout'; import FocusLayout from '@/layouts/FocusLayout'; import AdminLayout from '@/layouts/AdminLayout'
import { ProtectedRoute, AdminRoute, EditorRoute, IntroGate } from './guards'
import RouteError from '@/components/error/RouteError'
const L = (f: () => Promise<{ default: React.ComponentType }>) => lazy(f)

export const router = createBrowserRouter([
  { element: <RootLayout />, errorElement: <RouteError />, children: [
    // 인트로·집중 화면 (탭 없음)
    { element: <FocusLayout />, children: [
      { path: '/intro', Component: L(() => import('@/pages/intro/IntroPage')) },
      { path: '/meditations/:id', Component: L(() => import('@/pages/meditation/MeditationPlayPage')) },
      { element: <ProtectedRoute />, children: [
        { path: '/sites/:siteId/verify', Component: L(() => import('@/pages/stamp/StampFlowPage')) },
        { path: '/sites/:siteId/evidence', Component: L(() => import('@/pages/stamp/EvidencePage')) },
        { path: '/checkin', Component: L(() => import('@/pages/stamp/CheckinPage')) },
        { path: '/stamps/:stampId', Component: L(() => import('@/pages/stamp/StampResultPage')) },
      ]},
    ]},
    // 인증 화면 (로그인 상태면 홈으로)
    { element: <AuthLayout />, children: [
      { path: '/login', Component: L(() => import('@/pages/auth/LoginPage')) },
      { path: '/signup', Component: L(() => import('@/pages/auth/SignupPage')) },
      { path: '/verify/:serialNo', Component: L(() => import('@/pages/certificate/VerifyPage')) },   // 공개·카드형
    ]},
    // 앱 본체 (하단 탭)
    { element: <IntroGate />, children: [{ element: <AppLayout />, children: [
      { index: true, Component: L(() => import('@/pages/home/HomePage')) },
      { path: '/regions', Component: L(() => import('@/pages/regions/RegionsPage')) },
      { path: '/regions/:regionId', Component: L(() => import('@/pages/regions/RegionDetailPage')) },
      { path: '/courses/:courseId', Component: L(() => import('@/pages/courses/CourseDetailPage')) },
      { path: '/sites/:siteId', Component: L(() => import('@/pages/sites/SitePage')) },
      { path: '/meditations', Component: L(() => import('@/pages/meditation/MeditationListPage')) },
      { path: '/guide', Component: L(() => import('@/pages/settings/GuidePage')) },
      { element: <ProtectedRoute />, children: [
        { path: '/passport', Component: L(() => import('@/pages/passport/PassportPage')) },
        { path: '/thinkbox', Component: L(() => import('@/pages/thinkbox/ThinkboxPage')) },
        { path: '/rewards', Component: L(() => import('@/pages/reward/RewardsPage')) },
        { path: '/ebooks', Component: L(() => import('@/pages/ebook/EbookPage')) },
        { path: '/ebooks/print', Component: L(() => import('@/pages/ebook/PrintOrderPage')) },
        { path: '/certificates', Component: L(() => import('@/pages/certificate/CertificatesPage')) },
        { path: '/settings', Component: L(() => import('@/pages/settings/SettingsPage')) },
      ]},
      { path: '/403', Component: L(() => import('@/pages/error/ForbiddenPage')) },
      { path: '*', Component: L(() => import('@/pages/error/NotFoundPage')) },
    ]}]},
    // 관리자
    { path: '/admin', element: <AdminRoute />, children: [{ element: <AdminLayout />, children: [
      { index: true, Component: L(() => import('@/pages/admin/AdminHome')) },
      { path: 'sites', Component: L(() => import('@/pages/admin/AdminSites')) },
      { path: 'sites/new', Component: L(() => import('@/pages/admin/AdminSiteForm')) },
      { path: 'sites/search', Component: L(() => import('@/pages/admin/AdminKakaoSearch')) },
      { path: 'sites/:id', Component: L(() => import('@/pages/admin/AdminSiteForm')) },
      { path: 'sites/:id/qr', Component: L(() => import('@/pages/admin/AdminQr')) },
      { path: 'courses', Component: L(() => import('@/pages/admin/AdminCourses')) },
      { path: 'courses/:id', Component: L(() => import('@/pages/admin/AdminCourseForm')) },
      { path: 'stamps', Component: L(() => import('@/pages/admin/AdminStampReview')) },
      { path: 'rewards', Component: L(() => import('@/pages/admin/AdminRewards')) },
      { path: 'ebooks', Component: L(() => import('@/pages/admin/AdminEbooks')) },
      { path: 'print-orders', Component: L(() => import('@/pages/admin/AdminPrintOrders')) },
      { element: <EditorRoute />, children: [
        { path: 'content', Component: L(() => import('@/pages/admin/AdminContent')) },
        { path: 'manuscripts', Component: L(() => import('@/pages/admin/AdminManuscripts')) },
      ]},
    ]}]},
  ]},
])
```
"router 많이"의 실체: **레이아웃 라우트(5) × 가드 라우트(4) × lazy 페이지(34)**. 페이지는 자기 레이아웃·권한을 모른다.

### 11-4 `App.tsx`
```tsx
import { RouterProvider } from 'react-router-dom'
import { QueryClientProvider } from '@tanstack/react-query'
import { router } from '@/router'
import { queryClient } from '@/queries/queryClient'
import ErrorBoundary from '@/components/error/ErrorBoundary'
import { ToastHost } from '@/components/ui/Toast'

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

### 11-5 `main.tsx`
```tsx
import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import './styles/index.css'
import { bootstrapAuth } from '@/lib/bootstrap'
import { installDevGuards } from '@/lib/devGuards'      // DEV: localStorage에 'token' 류 키가 생기면 throw

if (import.meta.env.DEV) installDevGuards()
const root = ReactDOM.createRoot(document.getElementById('root')!)
root.render(<React.StrictMode><RestoringSplash /></React.StrictMode>)   // 300ms 이내면 안 보이게 지연 표시
bootstrapAuth().finally(() => root.render(<React.StrictMode><App /></React.StrictMode>))
```
왜 라우터보다 먼저: 보호 라우트가 첫 렌더에서 `guest`로 오판해 `/login`으로 튕기는 깜빡임을 없애기 위해. `status`가 `restoring`인 동안엔 가드가 스켈레톤을 낸다(이중 안전).

---

## 12. 레이아웃 체계 · Header

### 12-1 레이아웃 5종 역할표

| 레이아웃 | 포함 | 제외 | 쓰는 화면 | 근거 |
|---|---|---|---|---|
| **RootLayout** | 폰트·`data-tier`·`lang`·오프라인 배너·ScrollRestoration·전역 ErrorBoundary·i18n 로드 | UI 크롬 전부 | 전부 | 한 번만 있어야 하는 것 |
| **AppLayout** | Header(상단) + `<Outlet/>` + BottomTabs(5) + FloatingHome + 안전영역 padding | – | 탐색·기록 화면 | 앱의 기본 |
| **AuthLayout** | 산문 배경(그라데이션+안개)·중앙 카드(max-w 420)·로고·언어 스위치 | 탭·Header | 로그인·가입·진위 확인 | 몰입·럭셔리 |
| **FocusLayout** | 상단 얇은 바(닫기 X + 제목 + 세션 타이머 슬롯) + 전체 높이 `<Outlet/>` | 탭 | 인트로·인증·QR·명상 재생 | 한 가지 일에 집중, 카메라·지도 풀스크린 |
| **AdminLayout** | 좌측 사이드바(md 이상) / 상단 드로어(모바일)·상단바(검색·계정)·breadcrumb | 하단 탭 | `/admin/*` | 데스크톱 우선 |

FloatingHome은 **인트로에도** 있어야 한다(명세: 언제든 탈출) → FocusLayout에 `showHome` prop.

### 12-2 `layouts/RootLayout.tsx`
```tsx
export default function RootLayout() {
  const tier = useAuthStore(selectTier); const preview = useUiStore(s => s.previewTier); const locale = useUiStore(s => s.locale)
  const online = useOnline()
  useI18n(locale)                                        // GET /api/i18n/{locale} → 캐시 24h, 실패 시 ko.json 폴백
  useEffect(() => { document.documentElement.dataset.tier = preview ?? tier; document.documentElement.lang = locale }, [tier, preview, locale])
  return (
    <ErrorBoundary scope="route">
      {!online && <OfflineBanner />}
      <ScrollRestoration />
      <Outlet />
    </ErrorBoundary>
  )
}
```

### 12-3 `layouts/AppLayout.tsx`
```tsx
export default function AppLayout() {
  return (
    <div className="min-h-dvh flex flex-col bg-paper-50">
      <Header />
      <main className="flex-1 pb-[calc(72px+env(safe-area-inset-bottom))]"><Suspense fallback={<PageSkeleton />}><Outlet /></Suspense></main>
      <BottomTabs />
      <FloatingHome />
    </div>
  )
}
```
하단 탭 5: **여권 · 코스(권역) · [홈 플로팅] · 명상 · 기록(생각상자) · 나(설정·인증서·전자책·보상 진입)**. 홈은 탭이 아니라 중앙 위로 솟은 원형 도장 버튼(ochre, `animate-ink` 최초 1회). 비로그인은 여권 탭을 누르면 `/regions`로 자연 대체(로그인 유도 배너 포함).

### 12-4 `layouts/FocusLayout.tsx`
```tsx
export default function FocusLayout() {
  const nav = useNavigate(); const { title, timerSlot } = useFocusBar()    // 페이지가 useFocusBar().set({title, timerSlot}) 으로 채움
  return (
    <div className="min-h-dvh flex flex-col bg-ink-900 text-paper-50">
      <div className="h-12 flex items-center justify-between px-3">
        <button aria-label="닫기" onClick={() => nav(-1)} className="w-11 h-11 grid place-items-center">✕</button>
        <span className="font-serif">{title}</span>
        <span className="min-w-11 text-right">{timerSlot}</span>
      </div>
      <div className="flex-1"><Suspense fallback={<FocusSkeleton />}><Outlet /></Suspense></div>
      <FloatingHome dark />
    </div>
  )
}
```

### 12-5 `components/header/Header.tsx`
```tsx
export default function Header() {
  const user = useAuthStore(s => s.user); const status = useAuthStore(s => s.status); const isAdmin = useAuthStore(selectIsAdmin)
  const logout = useLogout(); const nav = useNavigate()
  return (
    <header className="sticky top-0 z-30 backdrop-blur bg-paper-50/85 border-b border-paper-200">
      <div className="h-14 max-w-screen-md mx-auto px-4 flex items-center gap-3">
        <Link to={P.home} className="font-serif text-lg tracking-tight">산문<span className="text-ochre-500">.</span>경내</Link>
        <nav className="hidden md:flex gap-4 text-sm text-ink-600 ml-4">
          <NavLink to={P.regions}>코스</NavLink><NavLink to={P.meditations}>명상</NavLink><NavLink to={P.guide}>가는 법</NavLink>
          {user && <NavLink to={P.passport}>여권</NavLink>}
          {isAdmin && <NavLink to={P.admin.root} className="text-ochre-600">관리</NavLink>}
        </nav>
        <div className="ml-auto flex items-center gap-2">
          <LocaleSwitch />
          {status === 'restoring' && <Skeleton className="w-16 h-8 rounded-full" />}
          {status === 'guest' && <Link to={P.login} className="btn px-4 bg-ink-900 text-paper-50 text-sm">로그인</Link>}
          {status === 'authed' && user && <UserMenu user={user} onLogout={() => logout.mutate(undefined, { onSettled: () => nav(P.home) })} />}
        </div>
      </div>
    </header>
  )
}
```
`UserMenu`: 아바타 원(닉네임 첫 글자, 5구 색 중 userId%5) → 시트: 여권·인증서·전자책·보상·설정·로그아웃. 로그인 상태 반영은 **store 구독 하나**로 끝난다(교안 15).

---

## 13. UI · UX 원칙 ("산문을 지나 경내로")

| 원칙 | 구현 | 왜 |
|---|---|---|
| **한 화면 한 마음** | 화면당 주 액션 1개(ochre 버튼), 나머지는 텍스트 링크 | 산속·장갑·외국인 모두 헷갈리지 않게 |
| **한지 위 먹과 인주** | 배경 paper, 글자 ink, 액션·도장 ochre. 색은 5구 색 외엔 더 안 쓴다 | 절제가 곧 브랜드 |
| **구절이 먼저** | 홈·사찰 상세 첫 스크롤에 오관게 구절(serif) | 타겟 3종 모두 "가장 먼저 보는 것"이 구절 |
| **도장은 이벤트** | 발급 순간만 `animate-ink` 큰 도장 + 진동(`navigator.vibrate`) + 5구 색 파문 | 하루 1~5번뿐인 보상 감각 |
| **비어 있음도 설계** | 모든 목록에 EmptyState 삽화·한 줄·다음 행동 버튼 | 첫 사용자는 전부 빈 화면 |
| **로딩은 형태로** | Spinner 금지, Skeleton만(카드 모양 그대로) | 레이아웃 흔들림 0 |
| **실패는 부드럽게** | 발랄한 문구 + 되돌아갈 길 항상 표시 | §15 |
| **엄지 영역** | 주 액션은 화면 하단 고정, 탭 높이 ≥44(RIDER 56) | 한 손 |
| **오프라인 존중** | 조회는 캐시로 보이고, 저장은 "연결되면 보내기" | E-14 |
| **좌표는 내 폰 안에서만** | 거리·반경 계산 전부 `lib/distance.ts`, 화면에 "위치는 기기 밖으로 나가지 않아요" 문구 | 원칙① 신뢰 |

---

## 14. 화면별 설계

### 14-1 홈 `/` (S-02)

| 순서 | 블록 | 데이터 | 로그인 | 비로그인 |
|---|---|---|---|---|
| 1 | 오늘의 구절 카드(serif 한자 + 한글 + 주제, 5구 색 리본, 좌우 스와이프) | verses | ○ | ○ |
| 2 | 진행 요약(9권역 점 그리드, 완주 n/12, 다음 사찰) — RIDER는 "다음 사찰 주행정보"(주차·진입·거리) 카드 | passport | ○ | 로그인 유도 카드 "첫 도장 찍으러 가기" |
| 3 | 여섯 달 전 오늘(있을 때만) | flashback | ○ | ✕ |
| 4 | 명상 3편(재생 버튼, 로그인 없이 됨) | meditations limit 3 | ○ | ○ |
| 5 | 사찰 가는 법 배너 | 정적 | ○ | ○ |

4호출 병렬(§8-4). 각 블록은 자기 스켈레톤·자기 에러(한 블록 실패가 홈 전체를 죽이지 않음).

### 14-2 권역 목록 `/regions` · 권역 상세 `/regions/:id` (S-03′·S-04)
9권역 카드: 권역명·코스 수·진행 n/5·완주 배지·대표 사찰명·**다음 미방문 사찰까지 거리**(`useGeolocation` 허용 시, 로컬 계산). 상세: 상단 지도(카카오, 코스 라인 색=5구 그라데이션)·아래 코스 카드 목록(`status ACTIVE`만, DRAFT는 "준비 중" 뱃지로 회색).

### 14-3 코스 상세 = **사찰 목록 화면** `/courses/:courseId` (S-04′)

| 영역 | 내용 |
|---|---|
| 헤더 | 코스명·권역·진행 5칸(5구 색 원, 찍힌 칸은 인주 도장 마크) |
| 지도 | 자리 5 마커(번호+색) + 후보 사찰(회색 작은 점, `congested`면 "붐빔" 라벨) + 내 위치 점 + "내 위치로" 버튼 |
| 목록 | 자리 순 5장 카드: 순번·구절 주제·사찰명·거리(km, 로컬)·상태(미방문/진행 중/완료)·`qrLocationHint` 한 줄. 카드 탭 → 싱글페이지. 카드의 "인증" 버튼은 로그인+반경 계산 결과에 따라 활성 |
| 정렬 | 기본 자리 순, 토글 "가까운 순"(거리 없으면 비활성) |
| 하단 고정 | 비로그인: "로그인하고 순례 시작" / 로그인: "순례 시작"(`POST /pilgrimages` 멱등) → 이미 시작이면 "이어서 순례" |
| 자리 탭 | 싱글페이지로. 코스 상세에는 원고 미리보기 없음(인수인계) |

### 14-4 사찰 싱글페이지 = **사찰 상세** `/sites/:siteId` (S-05) — 9블록 한 요청
| 블록 | UI | RIDER 변형 |
|---|---|---|
| Site | 사찰명·설명·사진(없으면 한지 텍스처) | – |
| Verse | 담당 구절(serif, 항상 공개) | – |
| Phrase | 확장문구 1편(카드, "다른 문구 보기"는 없음 — 재조회는 재진입) | – |
| Mission | 행동과제 미리보기(도착 전) + `missionManuscript` 안내 | – |
| Viewpoint | 최대 3곳 아코디언 | – |
| Badge | FLOWER/GUARDIAN 뱃지 | – |
| RiderInfo | 주차·진입·식사 | **상단 고정 배너로 승격** |
| VerifyState | 로그인 시 현재 스탬프 상태 → 하단 고정 버튼 "여기서 인증하기 / 이어서 인증 / 완료 ✓" | 버튼 56px |
| guideAvailable | true면 "가는 법" 링크 | – |
캐시 0 → 뒤로가기 후 재진입하면 새 문구·새 과제(서버 규칙 그대로).

### 14-5 로그인 `/login` · 회원가입 `/signup` (S-16) — 럭셔리 구성
| 요소 | 설명 |
|---|---|
| 배경 | ink-900 → paper-50로 흐르는 세로 그라데이션 + 산문 실루엣(SVG, 1분 주기 안개 애니메이션, `prefers-reduced-motion` 시 정지) |
| 카드 | paper-50, radius 24, shadow-card, 상단에 오관게 1구 serif 인용(무작위) |
| 입력 | `Input` 플로팅 라벨, 비밀번호 보기 토글, 실시간 zod 검증(blur 후만 빨간색) |
| 가입 추가 | 타겟 선택 3장 카드(2030·라이더·외국인) → tier 매핑(AGE20/30 · RIDER · FOREIGN, 나머지 연령은 설정에서) · 위치 서비스 동의(필수)·전자책 공개 동의(선택) |
| 실패 | 계정 존재 여부 안 새는 단일 문구. 5회 실패 시 버튼 비활성 + 15분 카운트다운 |
| 성공 | 카드가 위로 사라지며 도장 인주 파문 → `next` 또는 홈 |
| 전환 | 하단 "아직 계정이 없나요 → 가입" / "이미 순례자예요 → 로그인" |

`components/ui/Input.tsx`
```tsx
import { forwardRef, useId, type InputHTMLAttributes } from 'react'
type Props = InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string; hint?: string }
export const Input = forwardRef<HTMLInputElement, Props>(function Input({ label, error, hint, className = '', id, ...rest }, ref) {
  const auto = useId(); const inputId = id ?? auto
  return (
    <div className={`relative ${className}`}>
      <input ref={ref} id={inputId} placeholder=" " aria-invalid={!!error} aria-describedby={error ? `${inputId}-err` : undefined}
        className={`peer w-full rounded-seal border bg-white/70 px-4 pt-6 pb-2 text-base outline-none transition
          ${error ? 'border-danger focus:border-danger' : 'border-paper-200 focus:border-ochre-500'}`} {...rest} />
      <label htmlFor={inputId} className="pointer-events-none absolute left-4 top-2 text-xs text-ink-600 transition-all
        peer-placeholder-shown:top-4 peer-placeholder-shown:text-base peer-focus:top-2 peer-focus:text-xs">{label}</label>
      {error ? <p id={`${inputId}-err`} className="mt-1 text-xs text-danger">{error}</p> : hint ? <p className="mt-1 text-xs text-ink-300">{hint}</p> : null}
    </div>
  )
})
```

`pages/auth/LoginPage.tsx`
```tsx
export default function LoginPage() {
  const [sp] = useSearchParams(); const next = sp.get('next'); const nav = useNavigate()
  const login = useLogin(); const status = useAuthStore(s => s.status)
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<LoginForm>({ resolver: zodResolver(loginSchema) })
  const [lockedUntil, setLockedUntil] = useState<number | null>(null); const remain = useCountdown(lockedUntil)
  if (status === 'authed') return <Navigate to={next ?? P.home} replace />
  const onSubmit = handleSubmit(v => login.mutate(v, {
    onSuccess: () => nav(next ?? P.home, { replace: true }),
    onError: (e) => {
      const ui = toUiError(e)
      if (e instanceof ApiError && e.code === 'AUTH-4031') setLockedUntil(Date.now() + 15 * 60_000)
      setError('root', { message: ui.body })
    },
  }))
  return (
    <form onSubmit={onSubmit} className="space-y-4" noValidate>
      <VerseQuote random />
      <Input label="이메일" type="email" autoComplete="email" inputMode="email" {...register('email')} error={errors.email?.message} />
      <PasswordInput label="비밀번호" autoComplete="current-password" {...register('password')} error={errors.password?.message} />
      {errors.root && <FormMessage tone="danger">{errors.root.message}{remain > 0 && ` (${fmt(remain)} 뒤)`}</FormMessage>}
      <Button type="submit" full loading={isSubmitting || login.isPending} disabled={remain > 0}>산문으로 들어가기</Button>
      <p className="text-center text-sm text-ink-600">아직 계정이 없나요? <Link to={`${P.signup}${next ? `?next=${encodeURIComponent(next)}` : ''}`} className="text-ochre-600 underline">가입하기</Link></p>
    </form>
  )
}
```
SignupPage는 동일 골격 + 3단 스텝(계정 → 타겟 → 동의), `useSignup` 성공 시 `POST /users/me/agreements` 2건을 이어서 보낸 뒤 이동.

### 14-6 현장 인증 3단계 `/sites/:siteId/verify` (S-06) — 순서 고정 GPS→QR→미션
| 단계 | 화면 | 판정 위치 | 서버로 가는 것 | 실패 UX |
|---|---|---|---|---|
| ① GPS | 지도 없음. 큰 원형 게이지 + "사찰 반경 300m 안" 안내 + 정확도 등급 표시 | **기기**: haversine(내 좌표, 사찰 좌표) ≤ radius, accuracy ≤100m→HIGH/MID, >100→LOW | `{pilgrimageId, courseSiteId, withinRadius:true, accuracyGrade}` | E-01 권한 설정 안내 / E-02 30초 재시도 / E-03 "경내 마당으로" / 반경 밖이면 서버 호출 안 함(버튼 비활성) |
| ② QR | 카메라 풀스크린(`@zxing/browser`) + `qrLocationHint` 배너 + 60분 타이머 | 서버 | `{qrToken}` | `STAMP-4221` 재스캔 / `STAMP-4101` 만료 → 새로 시작 / 카메라 거부 → "카메라 앱으로 QR을 찍어도 돼요" (`/checkin?token=`로 돌아옴) |
| ③ 미션 | 행동과제 + 원고 안내 + 다짐 10~300자 + 사진(선택, presign PUT, EXIF 제거) | 서버 | `{userSentence, photoKey?}` | `STAMP-4222` 글자 수 / 사진 실패는 다짐만 제출 허용 |
| 결과 | 도장 애니메이션 · progress · rewards · `courseCompleted`면 인증서 번호 카드 | – | – | `PENDING`(이동시간 미달)은 "확인 중" 화면 + 여권으로 |
세션 타이머: `expiresAt`(서버)로 `useCountdown`. 앱 전환 후 복귀 시 `GET /stamps/{id}`로 단계 재동기화. 하루 상한 `STAMP-4291`은 첫 단계 전에 안내.

### 14-7 QR 체크인 `/checkin?token=` (S-06′)
카메라 앱으로 찍으면 `https://{FRONTEND_URL}/checkin?token=…`으로 들어온다. 흐름: 미로그인 → `/login?next=/checkin?token=…` → 로그인 후 복귀 → `stampStore`에 진행 중 stampId가 있으면 `POST /stamps/{id}/qr` → 미션 단계로 / 없으면 "먼저 사찰 상세에서 위치 확인부터"(GPS 단계로 안내, 토큰은 store에 임시 보관해 QR 단계 자동 통과).

### 14-8 여권 `/passport` (S-03): 9권역 → 12코스 → 60칸. 권역 아코디언, 칸은 원형(빈칸=점선, 찍힘=5구 색 도장 마크 + 날짜). 상단 Summary 4개(찍은 도장·완주 코스·인증서·전자책). 회향 하한 12 진행 바.

### 14-9 생각상자 `/thinkbox` (S-11): 정렬 3(날짜·코스·사찰), 무한 스크롤(`useInfiniteQuery`, `hasNext`), 300자 작성 시트, 공개 토글, `source`(미션 자동/직접) 아이콘, 수정·삭제(204).

### 14-10 명상 `/meditations` · `/meditations/:id` (S-09·10): 카테고리 칩 + 개수, 비로그인 OK. 재생 화면은 Focus(어두운 배경, 원형 호흡 애니메이션, 스크립트 스크롤, 오디오 URL null이면 텍스트 명상). 재생 종료·이탈 시 로그인 상태면 `POST /logs {playedSec, memo?}`(비로그인은 "기록하려면 로그인" 한 줄).

### 14-11 전자책·인쇄 `/ebooks` (S-13): 종류 탭(순례본·회향본·소장본). 상태 칩 QUEUED/GENERATING/READY/FAILED. READY만 다운로드(presigned 10분, 새 탭). "소장본 만들기"(하루 3·READY 5 안내, 409 문구). 인쇄: READY 책 선택 → 부수 1~5 → 연락처·주소(zod) → 목록에서 `cancelable`일 때만 취소.

### 14-12 인증서 `/certificates` · 진위 확인 `/verify/:serialNo` (S-14): 카드형(번호 `PG/HH-yyyy-000000` 크게, 코스명, 발급일, REVOKED 워터마크), PDF 열기, QR(verifyUrl) 공유. 진위 확인은 공개·AuthLayout 카드: 7필드만, 닉네임 마스킹 그대로 표시, `CERT-4041`이면 "그런 번호는 없어요".

### 14-13 보상 `/rewards` (S-12): GRANTED→CLAIMED→PAID/REJECTED 타임라인, `claimable`일 때만 "받기". 실물이면 배송정보 시트(별도 PUT).

### 14-14 설정 `/settings` (S-15): 닉네임·타겟(7종 전부)·언어 4·알림 · 동의 현황 · 비의료·비진단 안내 상시 · **탈퇴**(2단 확인 + 닉네임 타이핑, DELETE 204 → clear → 홈, "즉시 익명화, 되돌릴 수 없음").

### 14-15 관리자 (`/admin/*`, 데스크톱 우선)
| 화면 | 핵심 | 교안 대응 |
|---|---|---|
| AdminSites | 표(이름·상태·좌표·QR버전·코스 배정)·검색·상태 토글·**QR 버튼** | 관리자 CRUD 5 |
| AdminSiteForm | 등록/수정 공통. 좌표는 손입력 대신 **"카카오에서 찾기"** → AdminKakaoSearch 결과 선택 시 폼 prefill. i18n 3개 탭·viewpoint 3·badge 2. 좌표 한국 영역·반경 30~1000 zod | CRUD 6, 검색 7·8 |
| AdminKakaoSearch | `GET /admin/kakao/places?query=` (REST 키는 서버) → 카드 목록 + 미니맵 → "이 장소로 등록" / 이미 등록된 사찰이면 `COURSE-4093`류 409 문구 | 검색 12~14 |
| AdminQr | `POST /sites/{id}/qr` → 이미지(base64 PNG) 표시·인쇄용 A4 다운로드·`rotate`(2단 확인: 기존 QR 전부 무효) | QR 23 |
| AdminCourses/Form | 자리 5 일괄(드래그 정렬, position=verseNo), site-distances 20행 편집 | – |
| AdminStampReview | PENDING 목록(증거 사진 임시 URL)·승인/반려(사유 필수) | – |
| AdminContent(EDITOR) | 확장문구·행동과제 등록·리뷰 | – |
| AdminManuscripts(EDITOR) | CSV 반입 dryRun → 결과표 → 반입, 변형 ≤3, 4-eyes 승인, 퇴역 | – |
| AdminRewards / Ebooks / PrintOrders | 큐 처리·retry·상태 변경(SHIPPED 송장 필수) | – |
| AdminHome | 헬스·시드 숫자·`housekeeping/run` 버튼(처리 건수 표시) | – |

---

## 15. 에러 처리 — 문구표(발랄) · 처리 방안

톤 규칙: **행동을 막는 오류(인증·보안·잠금)는 담백하게(plain)**, 나머지는 발랄하게(fun). 문구는 `lib/errorTexts.ts` 한 파일. 코드가 곧 키.

| 코드 | 제목 | 본문 | 행동 | 톤 |
|---|---|---|---|---|
| NET-0000 | 전파가 산문 밖에 있어요 | 산속이라 그런가 봐요. 연결되면 다시 보내 드릴게요. | retry | fun |
| NET-0504 | 서버가 아직 잠에서 덜 깼어요 | 새벽 예불 전인가 봅니다. 잠깐만 기다려 주세요. | retry(60초 후 자동) | fun |
| COMMON-4000 | 입력이 조금 삐뚤어요 | 빨간 줄 부분만 다시 봐 주세요. | none(필드) | fun |
| COMMON-4001 | 개발자가 계율을 어겼어요 | 좌표가 서버로 갈 뻔했어요. 이건 저희 잘못입니다. | none | plain |
| COMMON-4040 | 이 길은 막힌 길이에요 | 지도에 없는 곳까지 오셨네요. 경내로 돌아가요. | passport | fun |
| COMMON-4090 | 이미 그렇게 되어 있어요 | 상태가 바뀌어 있었어요. 화면을 새로 고쳤어요. | retry | fun |
| COMMON-5000 | 목탁이 잠깐 엇박자예요 | 서버가 잠시 헛디뎠어요. 요청번호 {requestId}를 알려주시면 빨리 찾아요. | retry | fun |
| AUTH-4011 | 이메일 또는 비밀번호를 확인해 주세요 | (계정 존재 여부 미노출) | none | plain |
| AUTH-4012 | 다시 로그인해 주세요 | 로그인 유효기간이 끝났어요. | login | plain |
| AUTH-4013 | 로그인이 필요한 곳이에요 | 순례 기록은 순례자만 볼 수 있어요. | login | plain |
| AUTH-4031 | 잠시 문을 닫았어요 | 5번 틀려서 15분 동안 잠겼어요. {mm:ss} 뒤에 다시 열려요. | wait | plain |
| AUTH-4032 | 여긴 스님만 들어가요 | 관리자 권한이 필요한 화면이에요. | passport | plain |
| AUTH-4091 | 이미 순례자인 이메일이에요 | 로그인으로 가 볼까요? | login | fun |
| USER-4001 | 그런 타겟은 없어요 | 2030·라이더·외국인·연령대 중에서 골라 주세요. | none | fun |
| COURSE-4041 / SITE-4041 | 그 사찰은 지도에서 사라졌어요 | 준비 중이거나 잠시 쉬는 사찰이에요. | passport | fun |
| COURSE-4092 | 아직 열리지 않은 코스예요 | 준비가 끝나면 알려드릴게요. | passport | fun |
| PILGRIM-4031 | 남의 순례 기록이에요 | 내 여권만 볼 수 있어요. | passport | plain |
| STAMP-4001 | 아직 산문 밖이에요 | 사찰 반경 안으로 조금만 더 들어와 주세요. (기기에서 먼저 막아 거의 안 뜸) | retry | fun |
| STAMP-4002 | 하늘이 잘 안 보이나 봐요 | GPS 신호가 약해요. 마당으로 나와 30초만 기다려 주세요. | retry | fun |
| STAMP-4031 | 다른 분의 도장이에요 | 내 인증만 이어갈 수 있어요. | passport | plain |
| STAMP-4091 | 여기 도장은 이미 찍었어요 | 같은 자리에 두 번은 안 찍혀요. 여권에서 확인해 보세요. | passport | fun |
| STAMP-4092 | 순서가 살짝 바뀌었어요 | 위치 → QR → 다짐 순서예요. 남은 단계로 안내할게요. | retry | fun |
| STAMP-4101 | 60분이 지나갔어요 | 차 한잔 하셨나 봐요. 위치 확인부터 다시 시작해요. | retry | fun |
| STAMP-4221 | 이 사찰의 QR이 아니에요 | 다른 사찰 QR이거나 옛날 QR이에요. 종무소 QR을 다시 찍어 주세요. | retry | fun |
| STAMP-4222 | 다짐이 너무 짧아요 | 10자만 넘겨 주세요. 마음은 길어도 돼요. | none | fun |
| STAMP-4291 | 오늘 도장은 여기까지 | 하루 5개가 상한이에요. 내일 새벽에 다시 열려요. | passport | fun |
| STAMP-4292 | 예외 접수도 하루 2건까지 | 내일 다시 접수할 수 있어요. | passport | fun |
| PENDING(상태) | 이동 시간을 확인하고 있어요 | 직전 사찰과 너무 빨리 왔어요. 관리자가 확인하면 도장이 찍혀요. | passport | plain |
| UPLOAD-4001 / 4221 / 5021 | 사진이 길을 잃었어요 | 다짐만 먼저 남기고 사진은 나중에 붙여도 돼요. | retry | fun |
| THINKBOX-4041 | 그 생각은 이미 날아갔어요 | 삭제됐거나 없는 기록이에요. | none | fun |
| MED-4041 | 이 명상은 잠시 쉬는 중 | 다른 명상을 들어 볼까요? | none | fun |
| EBOOK-4041 / 4092 | 책이 아직 제본 중이에요 | 만드는 데 몇 분 걸려요. 다 되면 여기서 바로 열 수 있어요. | retry(30초 폴링) | fun |
| EBOOK-429x | 오늘 소장본은 다 만들었어요 | 하루 3권, 완성본 5권까지예요. | none | fun |
| CERT-4041 | 그런 번호는 없어요 | 번호를 다시 확인해 주세요. 회수된 인증서는 REVOKED로 나와요. | none | plain |
| REWARD-4001 / 4092 | 지금은 받을 수 없어요 | 상태가 바뀌었어요. 목록을 새로 고쳤어요. | retry | fun |
| KAKAO-5021 | 카카오가 잠깐 안 받아요 | 장소 검색을 잠시 뒤 다시 해 주세요. | retry | fun |
| ADMIN-4092 | 이 상태에선 처리 불가 | 이미 처리됐거나 순서가 달라요. | retry | plain |
| RENDER(410 이상 대기) | 서버를 깨우는 중이에요 | 처음 켜질 땐 최대 1분 걸려요. | wait | fun |
| OFFLINE(배너) | 전파가 산문 밖에 있어요 | 보던 화면은 그대로, 저장은 연결되면 보내요. | – | fun |
| 404 라우트 | 이 길은 지도에 없어요 | 산문으로 돌아가는 길을 켜 드릴게요. | home | fun |
| 렌더 예외 | 화면이 잠깐 넘어졌어요 | 저희가 챙겨 볼게요. 홈으로 가거나 다시 시도해 주세요. (요청번호/스택은 콘솔) | home/retry | fun |

### 15-1 `components/ui/Toast.tsx` (uiStore 기반)
```tsx
export function ToastHost() {
  const toasts = useUiStore(s => s.toasts); const dismiss = useUiStore(s => s.dismissToast)
  return (
    <div className="fixed inset-x-0 top-3 z-50 flex flex-col items-center gap-2 px-4 pointer-events-none" role="status" aria-live="polite">
      {toasts.map(t => (
        <div key={t.id} className={`pointer-events-auto max-w-md w-full rounded-seal shadow-card px-4 py-3 text-sm animate-ink
          ${t.tone === 'danger' ? 'bg-danger text-paper-50' : t.tone === 'success' ? 'bg-success text-paper-50' : 'bg-ink-900 text-paper-50'}`}>
          <strong className="block">{t.title}</strong>{t.body && <span className="opacity-90">{t.body}</span>}
          {t.action && <button onClick={() => { t.action!.onClick(); dismiss(t.id) }} className="ml-3 underline">{t.action.label}</button>}
        </div>))}
    </div>
  )
}
export const toast = { error: (e: unknown) => { const u = toUiError(e); useUiStore.getState().pushToast({ title: u.title, body: u.body, tone: 'danger' }) },
  success: (title: string, body?: string) => useUiStore.getState().pushToast({ title, body, tone: 'success' }) }
```

### 15-2 `components/error/ErrorBoundary.tsx`
```tsx
export default class ErrorBoundary extends React.Component<{ scope: 'app' | 'route' | 'block'; children: React.ReactNode }, { error?: Error }> {
  state = {} as { error?: Error }
  static getDerivedStateFromError(error: Error) { return { error } }
  componentDidCatch(error: Error, info: React.ErrorInfo) { console.error(`[boundary:${this.props.scope}]`, error, info.componentStack) }
  render() {
    if (!this.state.error) return this.props.children
    if (this.props.scope === 'block') return <EmptyState title="이 조각만 잠깐 넘어졌어요" body="다른 부분은 그대로예요." action={{ label: '다시', onClick: () => this.setState({ error: undefined }) }} />
    return <FullscreenError title="화면이 잠깐 넘어졌어요" body="저희가 챙겨 볼게요." onHome={() => location.assign('/')} onRetry={() => this.setState({ error: undefined })} />
  }
}
```
홈 블록·관리자 위젯은 `scope="block"`으로 감싼다.

---

## 16. 공통 컴포넌트 목록 (`components/ui`)

| 컴포넌트 | props 요점 | 규칙 |
|---|---|---|
| Button | `variant: primary\|ghost\|danger` · `full` · `loading` · `size: md\|lg` | 높이 `var(--btn-h)`, loading 중 텍스트 유지+스피너 점 3개 |
| Input / PasswordInput / Textarea(글자수 카운터) / Select | `label error hint` | forwardRef, RHF `register` 그대로 |
| Card | `tone: paper\|ink` · `verse?: 1~5`(좌측 리본) | |
| Badge | `status` 매핑(DRAFT/ACTIVE/READY/PENDING…) | 색은 5구+상태색만 |
| Skeleton / PageSkeleton / FocusSkeleton | | Spinner 대체 |
| EmptyState | `illust title body action` | 삽화는 SVG 8종(빈 여권·빈 상자·구름·산문…) |
| Toast / ToastHost | | §15-1 |
| Modal / Sheet(하단) | `open onClose` · focus trap · ESC | 탈퇴·QR rotate 2단 확인 |
| Progress(5칸) / Ring(게이지) | `value total verse` | |
| Tabs / Chips | | 카테고리·정렬 |
| QueryError | `error onRetry` | `toUiError` |
| StampMark | `verse date` | 인주 도장 SVG + `animate-ink` |

---

## 17. 지도 · 위치 (원칙①: 좌표는 기기 안에서만)

### 17-1 `hooks/useKakaoLoader.ts`
```ts
export function useKakaoLoader() {
  const [state, setState] = useState<'idle' | 'loading' | 'ready' | 'error'>('idle')
  useEffect(() => {
    if ((window as any).kakao?.maps) return setState('ready')
    if (!env.KAKAO_JS_KEY) return setState('error')
    setState('loading')
    const s = document.createElement('script')
    s.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${env.KAKAO_JS_KEY}&autoload=false`
    const t = setTimeout(() => setState('error'), 10_000)
    s.onload = () => (window as any).kakao.maps.load(() => { clearTimeout(t); setState('ready') })
    s.onerror = () => setState('error')
    document.head.appendChild(s)
  }, [])
  return state
}
```
`error`면 KakaoMap이 목록 폴백(`<MapFallback />`)을 낸다 — 외국인용 지도 SDK(Q6)가 정해질 때까지 이 폴백이 외국인 화면.

### 17-2 `hooks/useGeolocation.ts`
```ts
export type Geo = { lat: number; lng: number; accuracy: number; at: number }
export function useGeolocation(opts: { watch?: boolean } = {}) {
  const [geo, setGeo] = useState<Geo | null>(null); const [error, setError] = useState<'denied' | 'unavailable' | 'insecure' | 'timeout' | null>(null)
  const secure = typeof window !== 'undefined' && window.isSecureContext
  useEffect(() => {
    if (!secure) return setError('insecure')                 // http로 폰 접속 시 — §3 basic-ssl 안내
    if (!navigator.geolocation) return setError('unavailable')
    const ok = (p: GeolocationPosition) => { setError(null); setGeo({ lat: p.coords.latitude, lng: p.coords.longitude, accuracy: p.coords.accuracy, at: p.timestamp }) }
    const fail = (e: GeolocationPositionError) => setError(e.code === 1 ? 'denied' : e.code === 3 ? 'timeout' : 'unavailable')
    const o: PositionOptions = { enableHighAccuracy: true, timeout: 15_000, maximumAge: 5_000 }
    if (opts.watch) { const id = navigator.geolocation.watchPosition(ok, fail, o); return () => navigator.geolocation.clearWatch(id) }
    navigator.geolocation.getCurrentPosition(ok, fail, o)
  }, [opts.watch, secure])
  return { geo, error, secure }
}
```
`geo`는 **어떤 store에도 넣지 않는다**(컴포넌트 상태만). 서버로 가는 것은 `withinRadius`·`accuracyGrade` 두 값뿐.

### 17-3 `lib/distance.ts`
```ts
export function haversineM(a: { lat: number; lng: number }, b: { lat: number; lng: number }) {
  const R = 6371e3, r = Math.PI / 180, dLat = (b.lat - a.lat) * r, dLng = (b.lng - a.lng) * r
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(a.lat * r) * Math.cos(b.lat * r) * Math.sin(dLng / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(h))
}
export const accuracyGrade = (m: number): 'HIGH' | 'MID' | 'LOW' => (m <= 30 ? 'HIGH' : m <= 100 ? 'MID' : 'LOW')
export const fmtDistance = (m: number) => (m < 1000 ? `${Math.round(m)} m` : `${(m / 1000).toFixed(1)} km`)
```

### 17-4 `components/map/KakaoMap.tsx` (요약)
props: `center`, `markers[{id, lat, lng, verse?, label, congested?}]`, `myLocation?`, `path?`(코스 라인), `onMarkerClick`. 인포윈도우는 하나만 열림(교안 16). 마커 1개면 level 5로 확대(교안 17). 좌표가 한국 영역 밖이면 마커 대신 콘솔 경고 + "좌표 확인 필요" 뱃지(교안 18, 관리자 화면에서만 표시).

---

## 18. QR 스캔 (`components/qr/QrScanner.tsx`)
```tsx
import { BrowserQRCodeReader, type IScannerControls } from '@zxing/browser'
export function QrScanner({ onToken, onError }: { onToken: (t: string) => void; onError: (e: 'denied' | 'nocamera' | 'insecure') => void }) {
  const videoRef = useRef<HTMLVideoElement>(null)
  useEffect(() => {
    if (!window.isSecureContext) return onError('insecure')
    let controls: IScannerControls | undefined
    const reader = new BrowserQRCodeReader()
    reader.decodeFromVideoDevice(undefined, videoRef.current!, (result, _err, c) => {
      controls = c
      if (result) { const token = extractToken(result.getText()); if (token) { c.stop(); onToken(token) } }
    }).catch(e => onError(e?.name === 'NotAllowedError' ? 'denied' : 'nocamera'))
    return () => controls?.stop()
  }, [])
  return <video ref={videoRef} className="w-full h-full object-cover" playsInline muted />
}
const extractToken = (text: string) => { try { return new URL(text).searchParams.get('token') } catch { return /^[A-Za-z0-9._-]{16,}$/.test(text) ? text : null } }
```
QR 내용은 `https://{FRONTEND_URL}/checkin?token=…`(서버 `PUBLIC_BASE_URL`/`FRONTEND_URL` 기준). 앱 내 스캐너와 카메라 앱 두 경로가 **같은 토큰**으로 같은 API에 도달한다. 같은 QR 재촬영은 `STAMP-4091`(교안 33). 보안 한계(교안 36): QR 사진 공유로 위치 없이 통과하는 것은 GPS 단계가 먼저라서 막힌다 — 그래서 순서 고정.

---

## 19. 검증

| 종류 | 대상 | 통과 기준 |
|---|---|---|
| Vitest | `client.test.ts`: 봉투 해제·204·401→refresh 1회→재시도·동시 401 단일 refresh·refresh 실패 시 clear·lat/lng throw·네트워크 1회 재시도 | 전부 초록 |
| Vitest | `authStore.test.ts`·`bootstrap.test.ts`(쿠키 없음→guest, 있음→authed)·`distance.test.ts`(통도사↔부산역 근사)·`errorMessage.test.ts`(모든 코드에 문구 존재) | |
| Playwright(모바일 뷰포트 390×844) | ① 인트로 1단 8초 스킵 불가·2단 스킵·최초 1회만 ② 가입→로그인→새로고침 유지→로그아웃 ③ 비로그인 홈(구절·명상 보임, 여권 카드는 로그인 유도) ④ 코스 상세→사찰 상세→인증 버튼(로그인 유도) ⑤ 진위 확인 공개 페이지 | 5/5 |
| 네트워크 단언 | 모든 e2e에서 요청 본문·쿼리에 `lat/lng/latitude/longitude` 0건 (Playwright `route` 훅으로 검사) | 0 |
| 빌드 | `npm run build` 초기 청크 < 250 KB gz, zxing·kakao는 lazy | |
| 콘솔 삭제(FE-1 닫힘) | 백엔드 `dev-console/` 제거 → `dev-console-remove-check.sh` → `all.sh` 초록 91 불변 | ✅ |
| 손 확인(휴대폰) | basic-ssl로 위치·카메라 허용, 프록시로 쿠키 유지, 카메라 앱 QR → `/checkin` 진입 | |

---

## 20. 진행표 FE-1 ~ FE-8 (예성이 "시작"이라 할 때 한 단계씩 정본+지시문 발행)

| 단계 | 범위 | 이 문서의 § | 닫힘 조건 / 마지막 줄 |
|---|---|---|---|
| **FE-1** | 프로젝트 생성·토큰·폴더·client·store·라우터 뼈대·레이아웃 5·Header·인트로·로그인/가입·설정 최소·공통 UI·i18n 뼈대·Vitest/Playwright 뼈대·**콘솔 삭제** | §2~§12, §15~§16 | `fe1: 화면 N · Vitest N/N · Playwright 5/5 · 번들 N KB · lat/lng 0 · 콘솔 삭제 ✅ · all.sh 통과` |
| FE-2 | 홈·권역·코스(사찰 목록)·싱글페이지(사찰 상세)·가는 법·다국어·카카오맵 | §14-1~4, §17 | `fe2: 화면 N · 지도 ✅ · 거리 로컬 ✅ · lat/lng 0` |
| FE-3 | 현장 인증 3단계·QR 스캐너·`/checkin`·예외접수·여권·결과 도장 | §14-6~8, §18 | `fe3: GPS→QR→미션 ✅ · 복귀 동기화 ✅ · 카메라앱 체크인 ✅ · lat/lng 0` |
| FE-4 | 사진(presign·EXIF 제거)·생각상자·flashback·명상 재생·기록 | §7-4, §14-9~10 | `fe4: …` |
| FE-5 | 완주 결과·인증서·진위 확인·보상·배송 | §14-12~13 | |
| FE-6 | 전자책·소장본·인쇄 신청 | §14-11 | |
| FE-7 | 설정·탈퇴·타겟 3종 변형 마무리·접근성·오프라인 큐 | §4, §14-14 | |
| FE-8 | 관리자 13화면(카카오 검색·QR 발급·심사·원고·큐) | §14-15 | |

각 단계 정본은 `backend/docs/frontend/feN.md` + Claude Code 지시문(STEP 번호·STEP마다 결과 저장 후 한 줄 보고·마지막 줄 형식). 역할·절대 규칙은 인수인계 v2 §1 그대로.

### 20-1 FE-1 첫 지시문 STEP 골격 (정본 `fe1.md`에 그대로 들어갈 뼈대)
```
STEP 1  루트 frontend/ 생성: npm create vite@latest frontend -- --template react-ts · 의존성 설치
        (react-router-dom @tanstack/react-query axios zustand zod react-hook-form @hookform/resolvers tailwindcss postcss autoprefixer
         @vitejs/plugin-basic-ssl @zxing/browser · dev: vitest jsdom @testing-library/react @playwright/test)
STEP 2  §3 vite.config.ts · .env.example · §4 tailwind.config.ts · styles/index.css · 폰트 배치 · tsconfig paths(@)
STEP 3  backend/docs/frontend/generate-api-types.js 재실행 → src/api/types.d.ts 반입(91문·DTO 104 개수 단언)
STEP 4  §5 envelope.ts · coordinateGuard.ts · client.ts · §6 errorMessage.ts · lib/errorTexts.ts(§15 표 전량)
STEP 5  §9 stores 3개 · §10 validation.ts · useAuth.ts · bootstrap.ts · devGuards.ts
STEP 6  §11 paths.ts · router/index.tsx · guards 4 · App.tsx · main.tsx (미구현 페이지는 Placeholder 컴포넌트)
STEP 7  §12 레이아웃 5 · Header · BottomTabs · FloatingHome · §16 공통 UI 전량 · EmptyState 삽화 8
STEP 8  인트로 3단(1단 8초·2단 스킵·3단 지도 점등·introDone 플래그) · 로그인 · 가입(3단) · 설정 최소(tier·locale) · i18n 훅
STEP 9  Vitest 6파일 · Playwright 5흐름 · lat/lng 네트워크 단언 · npm run build 번들 측정
STEP 10 백엔드 콘솔 삭제: dev-console/ · DevConsoleConfig · SecurityConfig 한 줄 → dev-console-remove-check.sh → all.sh → 정리.md §9 갱신
STEP 11 커밋·push · 마지막 줄 보고
```

---

## 21. 이 설계서가 요구하는 백엔드 정정 (정본정정표 등록 후보)

| # | 항목 | 내용 |
|---|---|---|
| B-1 | refresh 쿠키 `Secure` | local 프로파일에서는 `Secure=false`(http 개발·프록시), prod는 true. 없으면 폰 http 테스트에서 쿠키가 버려진다 |
| B-2 | `cors.allowed-origins` | `http://localhost:5173`, `https://192.168.*.*:5173`(개발), Netlify 주소(배포). `allowCredentials=true` |
| B-3 | QR 내용 | `${FRONTEND_URL}/checkin?token=…` 형식 확정(현재 값 확인) |
| B-4 | `GET /stamps/by-slot/{courseSiteId}` 없을 때 | 200 + `data:null`인지 404인지 — 프론트는 둘 다 처리하지만 정본 표기 필요 |
| B-5 | JWT 만료 테스트용 | local 프로파일 `JWT_ACCESS_TTL` 환경변수(기본 30분) — 만료→refresh 시나리오 재현용 |
