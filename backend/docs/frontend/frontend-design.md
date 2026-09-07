# temple-stamp 프론트엔드 설계서 v1 (정본 · 노션용)

작성 2026-09-07 · 근거: 기획 명세(사용자 유형·타겟 3종·인트로·화면 S-01~S-19·화면↔API 매핑·예외 E-01~E-15) + 백엔드 실제 구현(API 91·응답 봉투·권한·frontend-handoff.md) · 이 문서는 **설계 정본**이며, Claude Code가 실제 코드 기준 설계도를 뽑아 이 문서와 대조한 뒤(§11) 구현에 들어간다.

---

## 0. 설계 원칙 (백엔드 원칙과 짝)

| # | 원칙 | 프론트에서의 뜻 |
|---|---|---|
| ① | 좌표는 단말에서 끝난다 | 거리·반경 판정은 브라우저(카카오맵 JS SDK + Geolocation)에서 하고 서버에는 `inRadius·accuracyGrade·siteId`만 보낸다. 요청 본문에 lat/lng 키를 만드는 코드 자체를 두지 않는다 |
| ② | 기록은 저장과 열람뿐 | 생각상자·사진에 점수·순위·답장 UI 없음 |
| ③ | 서버 응답이 정본 | `claimable`·`courseCompleted`·`downloadUrl`·`present:false` 등 서버가 준 값으로만 버튼 상태를 정한다. 프론트에서 재계산하지 않는다 |
| ④ | 토큰은 메모리 | access 토큰은 JS 메모리, refresh는 HttpOnly 쿠키. localStorage 금지 |
| ⑤ | 인트로는 8초 | 1단 8초 불가 스킵, 2단 스킵 가능. 이탈 방어는 길이뿐 |
| ⑥ | 산중에서 재요청 없음 | 코스 상세 한 번에 사찰 5곳 좌표·QR 힌트·원고를 받아 둔다 |
| ⑦ | 없음도 화면이 있다 | `present:false`·`null`·빈 목록마다 "없음" 상태 컴포넌트가 있다(404로 흐르지 않는다) |
| ⑧ | 4개 로케일 | ko·en·ja·zh. UI 문자열은 `/api/i18n/{locale}`, 콘텐츠는 서버 i18n 표. 미번역은 ko 폴백 표시 |

---

## 1. 스택 · 폴더

| 구분 | 선택 | 이유 |
|---|---|---|
| React 18 + Vite | 명세 고정 | |
| TypeScript | 채택 | API 응답 타입을 endpoints.md에서 생성해 봉투·필드 오타를 컴파일에서 잡는다 |
| Tailwind CSS | 명세 고정·예성 기본 | |
| 라우팅 | React Router v6 | |
| 서버 상태 | TanStack Query | 화면별 캐시(60초·24시간·캐시 안 함)와 무효화 규칙을 그대로 코드로 |
| 클라이언트 상태 | Zustand(작게) | 토큰·현재 사용자·로케일·타겟만 |
| 폼 | React Hook Form + Zod | 서버 `fields` 오류를 필드에 매핑 |
| 지도 | 카카오맵 JS SDK(JavaScript 키) | REST 키는 서버만 |
| QR | 기본 카메라 → 체크인 URL 진입(명세 3장) + 앱 내 스캔 폴백(`@zxing/browser`) | |
| PDF 보기 | 브라우저 내장(presigned URL 새 창) | 프론트가 PDF를 만들지 않는다 |
| 테스트 | Vitest + Testing Library · Playwright(핵심 흐름 5개) | |
| i18n | i18next(UI) + 서버 콘텐츠 | |

```
frontend/
  src/
    app/            라우터·프로바이더·레이아웃
    api/            client.ts(봉투·401 refresh·requestId) · 도메인별 hooks(TanStack Query) · types(생성)
    features/       intro · auth · regions · courses · site-page · stamp(3단계) · passport · record(photo·thinkbox·meditation)
                    · completion(certificate·reward) · ebook(print) · settings · admin · editor
    components/     ui(Button·Card·Sheet·Toast·Empty·Skeleton) · map(KakaoMap·SiteMarker) · pdf(OpenInNewTab)
    design/         tokens.css · tailwind.config 확장 · 아이콘
    i18n/           ko·en·ja·zh(UI) · 폴백
    lib/            geo(거리·반경·정확도 등급) · qr · time(+09:00 표시) · masks
  public/           폰트(Noto Sans KR·Noto Serif KR subset)
```

---

## 2. 정보 구조 (IA) · 라우팅

| 경로 | 화면 | 인증 | 명세 ID |
|---|---|---|---|
| `/` | 인트로 3단 → 최초 1회, 이후 홈으로 | 공개 | S-01 |
| `/home` | 홈(오관게 카드·다음 사찰·여섯 달 전 오늘·명상 3) | 선택 | S-02 |
| `/regions` | 권역 9(+제주 준비 중) 지도·카드 | 선택 | S-03 |
| `/regions/:regionId` | 권역 상세 → **코스(라인) 목록**(1:N) | 선택 | S-04 |
| `/courses/:courseId` | 코스 상세 — 자리 5·후보 사찰·좌표 일괄·순례 시작 | 선택 | S-04b(신설) |
| `/sites/:siteId` | 사찰 싱글페이지 9블록 | 선택 | S-05 |
| `/stamp/:courseSiteId` | 현장 인증 3단계(GPS→QR→미션) | 필요 | S-06·S-07 |
| `/checkin?token=` | QR 체크인 진입(기본 카메라) → 로그인 확인 → `/stamp` 2단계로 | 필요 | S-06 |
| `/passport` | 여권(권역→코스→5칸·진행률) | 필요 | S-03b |
| `/photos/:siteId` | 사진·문장 | 필요 | S-08 |
| `/meditations` · `/meditations/:id` | 명상 108선·재생·기록 | 공개/기록만 필요 | S-09·S-10 |
| `/thinkbox` | 생각상자(sort 3종·비공개 토글) | 필요 | S-11 |
| `/ebooks` | 전자책(요청·상태·다운로드·인쇄주문) | 필요 | S-13 |
| `/certificates` · `/certificates/:id` | 인증서 목록·1장(PDF) | 필요 | S-14 |
| `/verify/:serial` | 진위 확인(제3자, 로그인 없음) | 공개 | S-19 |
| `/rewards` | 보상(claimable 배지·수령 신청) | 필요 | S-12 |
| `/settings` | 타겟(tier)·언어·알림·동의·탈퇴·비의료 안내 | 필요 | S-15 |
| `/login` · `/signup` | | 공개 | S-16 |
| `/guide` | 사찰 가는 법(공통 코너) | 공개 | S-17 |
| `/editor/*` | 원고 등록·제출·내 목록(EDITOR) | EDITOR | 신설 |
| `/admin/*` | 사찰·코스·QR·도장 심사·원고 심사·보상·인쇄·사용자·청소기 | ADMIN | 신설 |

플로팅 홈 버튼 상시(명세). 하단 탭 5개: 홈 · 권역 · 인증(가운데, 크게) · 기록 · 나.

---

## 3. 상태 · API 계층

### 3-1 API 클라이언트 (`api/client.ts`)
- 봉투 해제: `{success, data, error, timestamp}` → 성공은 `data`, 실패는 `error{code,message,fields}`를 `ApiError`로 throw.
- 401 → `POST /api/auth/refresh` 1회 → 재시도 → 또 401이면 로그아웃(E-11).
- `X-Request-Id` 응답 헤더를 오류 토스트에 작게 표시(문의용).
- 5xx·네트워크 오류 → "저장하지 못했습니다. 연결되면 다시 시도합니다"(E-14) — 재시도 큐는 Q8 미결이라 **표시만**.
- 모든 요청에 lat/lng 키 금지 — 타입 레벨에서 필드가 없고, 개발 모드에서 본문에 그 키가 있으면 throw.

### 3-2 캐시 규칙 (명세 10장 그대로)
| 데이터 | staleTime | 무효화 |
|---|---|---|
| 여권·홈·권역·코스 목록 | 60초 | 도장 발행 성공 시 |
| 싱글페이지 | 0(캐시 안 함) | — |
| 명상 목록·상세 | 24시간 | 언어 변경 |
| 생각상자·전자책·인증서·보상 | 60초 | 각 쓰기 성공 시 |
| 언어 변경 | 전체 무효화 | |

### 3-3 클라이언트 상태
`auth{accessToken, user{id,nickname,role,tier,locale}}` · `ui{locale, target}` · `stamp{현재 stampId, 단계, expiresAt}`(새로고침 복귀는 `GET /api/stamps/{id}`로 복원).

---

## 4. 디자인 시스템

### 4-1 방향
"산문(山門)을 지나 경내로" — 어두운 먹빛에서 밝은 마당으로 열리는 인트로의 감각을 앱 전체 톤으로. 화려하지 않고, 글자가 주인공. 한자 원문은 명조, 나머지는 고딕.

### 4-2 토큰
| 토큰 | 값 | 쓰임 |
|---|---|---|
| `ink-900` | #1B1A17 | 배경(인트로·다크 헤더)·본문 강조 |
| `paper-50` | #F6F1E7 | 기본 배경(한지) |
| `ochre-500` | #B8862B | 주 강조(버튼·진행률·도장) |
| `ochre-700` | #8A6119 | 눌림·강조 텍스트 |
| `moss-500` | #5B6B4A | 보조(완료·성공) |
| `verse-1~5` | #B8862B·#5B6B4A·#6C7A89·#7E5A9B·#A24B3D | 오관게 5구 색(출처·감사·절제·포행·다짐) — 여권 칸·도장·코스 카드에 일관 |
| `danger` | #A23B2E | 오류·회수 |
| 타이포 | 본문 Noto Sans KR 16/24 · 제목 20/28 · 한자 원문 Noto Serif KR 28/40 · 숫자 tabular | |
| 간격 | 4·8·12·16·24·32 | 터치 타깃 최소 44px, RIDER 모드 56px |
| 모서리 | 12(카드)·999(도장) | |
| 도장 | 원형 인장 스타일, 구 색상, 찍힘 애니메이션 400ms | |

### 4-3 컴포넌트(핵심 12)
`VerseCard`(오관게 구 한자·한글·주제) · `RegionMap`(9권역 점등) · `CourseCard`(라인·진행 n/5·구 색 띠) · `SiteBlockList`(9블록) · `StampSeal`(도장) · `StepBar`(GPS·QR·미션 3단계 + 남은 시간) · `PassportGrid`(5칸) · `EmptyState`(present:false 전용, 회색 칸 + 한 줄) · `PhraseSheet`(확장문구 바텀시트) · `ReviewBadge`(claimable·needs_review 등 서버 상태 배지) · `PdfOpenButton` · `LocaleSwitch`.

### 4-4 타겟 3종 변형 (명세 8장)
| | 2030 | RIDER | FOREIGN |
|---|---|---|---|
| 홈 첫 카드 | 오관게 | 오관게 + 다음 사찰 카드 2번째 | 오관게 + 언어 전환 배너 |
| 싱글페이지 | 기본 | 주행 블록(주차·노면·진입) 상단 고정 | 한자 원문 크게 + 자국어 |
| 인증 버튼 | 44px | 56px(장갑) | 44px + 아이콘 병기 |
| 지도 | 카카오맵 | 카카오맵 + 코스 폴리라인 | 카카오맵(Q6 미결 — 폴백 "주소 복사") |
| 하루 상한 표시 | 5 | 5(Q5 미결) | 5 |
타겟은 `settings`에서 바꾸고(`PATCH /api/users/me` tier), 비로그인은 쿼리/로컬 선택.

---

## 5. 화면별 설계 (핵심 8개)

### S-01 인트로 3단
1단(8초, 스킵 불가): 검은 산문이 열리며 오관게 한 줄씩 타이핑 → 2단(30초, 스킵 상시): 5구 원문·한글·주제, 각 구가 권역의 5자리(구)임을 잇는 다이어그램 → 3단: 9권역 점등 지도 + [시작]. 영상 실패 시 CSS 절차 연출 폴백(F-01). 최초 1회는 localStorage(**토큰이 아닌 플래그만**).

### S-02 홈
카드 순: 오관게(오늘의 구) → 다음 사찰(진행 중 순례) → 여섯 달 전 오늘(null이면 카드 없음) → 명상 3 → 여권 요약. 비로그인은 오관게·명상만.

### S-04/S-04b 권역 → 코스
권역 카드: 이름·코스 수·진행·대표 사찰. **코스 상세**에서 자리 5칸(구 색)·자리마다 후보 사찰(`congested` 배지 숨기지 않음)·순례 시작(멱등, 200 둘 다). 지도에 5자리 좌표·현재 위치(단말만).

### S-05 싱글페이지 9블록
Site → Verse(항상) → Phrase(확장문구 시트) → Mission(도착 전 미리 보기) → Viewpoint → Badge → RiderInfo(타겟별 위치) → VerifyState(비로그인 null → 로그인 유도) → guideAvailable. 캐시 없음.

### S-06/S-07 현장 인증 3단계

**정확도 등급은 프론트가 정한다(챕터 11 결정 F).** 서버는 좌표를 받지 않아 미터를 다시 잴 수 없다.
`navigator.geolocation` 의 `coords.accuracy`(미터, 68% 신뢰반경)를 `location.js` 가 이렇게 접는다:

| `coords.accuracy` | `accuracyGrade` | 화면 |
|---|---|---|
| ≤ 30 | `HIGH` | 그대로 진행 |
| 30 초과 ~ 100 이하 | `MID` | 그대로 진행 |
| 100 초과 (또는 값이 없음) | `LOW` | **서버를 부르지 않는다.** E-02 "신호를 더 받는 중" + 30초 재시도 |

경계는 서버의 `AccuracyGrade` javadoc 과 한 쌍이다 — 한쪽만 고치면 같은 상황이 기기마다 다른 등급으로 올라간다.
현장 실측 뒤 조정할 값이고, 조정할 때는 **두 곳을 함께** 고친다.
`LOW` 를 그래도 보내면 서버가 `STAMP-4001` 로 막는다 — 화면이 먼저 막는 것은 헛걸음을 줄이기 위해서다.

StepBar에 남은 시간(60분, `expiresAt` 기준, 만료 시 STAMP-4101 안내·새로 시작). ① GPS: Geolocation → 반경·정확도(HIGH/MID/LOW)를 단말이 계산 → LOW면 서버 호출 없이 "신호를 더 받는 중"(E-02) → `POST gps-check{siteId,inRadius,accuracyGrade}` → `qrLocationHint` 표시. ② QR: 기본 카메라 체크인 URL 또는 앱 내 스캔 → `POST qr` → 4221이면 "이 사찰의 QR이 아닙니다". ③ 미션: 서버가 준 원고(세션 고정) 표시 → 다짐 10~300자 + 사진(presign PUT) → 제출 → 도장 연출(StampSeal) + 확장문구 + `courseCompleted`면 인증서 번호·보상 카드. 실내 GPS 실패는 예외접수(EVIDENCE) 경로.

### S-08/S-11 기록
사진: 사찰당 1장 UPSERT, memo 필수(E-09), 비공개 토글. 생각상자: sort date/course/site, 비공개, PATCH. flashback null → 카드 미표시.

### S-13/S-14 전자책·인증서
전자책: [만들기] → 202 REQUESTED → 5초 폴링 → READY → [열기](presigned 새 창) · FAILED → [다시] · 인쇄주문(READY만, 취소는 REQUESTED만 — 서버 `cancelable`대로).
**전자일기장**은 같은 버튼에 `?type=INTERIM` 을 붙인 것이다(챕터 11). 3코스 미만이면 서버가 개인 소장본으로 내려 만들고,
이미 그 마일스톤 책이 있으면 그 행을 돌려준다 — 프론트는 응답의 `ebookType`·`milestone` 을 보고 제목을 정한다.
기록이 하나도 없을 때만 400 `EBOOK-4001` 이다(도장이 없어도 사진·생각상자가 있으면 만들어진다). 인증서: 목록 → 1장(PDF·QR) · REVOKED는 "무효" 배지 + 다운로드 없음.

### S-19 진위 확인
로그인 없음. 번호 입력/QR 진입 → VALID(초록)·REVOKED(회색, 회수일)·없음(404 "확인할 수 없습니다"). 마스킹된 이름·종류·코스·발급일만.

---

## 6. 예외 → UI (명세 9장 매핑)
| 코드/상황 | UI |
|---|---|
| GPS 권한 거부 | 설정 여는 법 시트(E-01) |
| STAMP-4001/4002 | 반경 밖/정확도 — 재시도 버튼 30초 |
| STAMP-4091 | "이미 받은 스탬프" + 여권으로(사찰·날짜는 서버 메시지) |
| STAMP-4101 | 만료 — 새로 시작 |
| STAMP-4221 | QR 무효 — 재스캔 |
| STAMP-4291/4292 | 상한 — 내일 안내 |
| AUTH-4031 | 잠금 15분 카운트다운(E-12) |
| 5xx | requestId와 함께 "잠시 뒤 다시" |
| 콜드스타트(E-13) | 60초 타임아웃 + 1회 재시도 스켈레톤 |

---

## 7. 접근성 · 성능 · 보안
- 색 대비 4.5:1, 구 색은 색 + 번호로 이중 표시, 포커스 링, 스크린리더 도장 상태 읽기.
- 초기 번들 < 250KB(gz), 코스 상세·명상은 라우트 분할, 지도 SDK는 지연 로드, 이미지는 서버가 준 리사이즈본.
- CSP: 카카오 도메인·API 도메인만. 토큰 메모리. presigned URL은 화면에 표시하지 않는다.

---

## 8. 관리자·편집자 화면(최소)
공통 표 + 폼 생성기(Zod 스키마 → 폼)로 12개 화면: 사찰 UPSERT(카카오 검색 → 좌표 채움) · 코스 자리 배정 · 이동시간 · QR 발급/회전(PNG) · 도장 심사(사진 임시 URL) · 원고 심사(4-eyes 표시) · CSV 반입(dryRun 먼저) · 보상 심사(needs_review 상단) · 인쇄주문 · 사용자 목록(DELETED 필터) · 청소기 실행(건수) · 재집계.

---

## 9. 구현 순서 (프론트 챕터 FE-1~FE-8, 각각 백엔드 챕터와 같은 방식으로 "정본 → 지시문 → 검증")
| FE | 범위 | 검증 |
|---|---|---|
| FE-1 | 스택·클라이언트·디자인 토큰·라우터·인트로·로그인/가입/refresh | Playwright: 가입→로그인→refresh→로그아웃 |
| FE-2 | 홈·권역·코스·싱글페이지 9블록·가는 법·다국어 | 비로그인/로그인 두 갈래 스냅샷 |
| FE-3 | 현장 인증 3단계·QR 체크인·예외접수·여권 | 좌표가 요청에 없음(네트워크 단언) |
| FE-4 | 사진·생각상자·flashback·명상 | |
| FE-5 | 완주·인증서·보상·진위 확인 | |
| FE-6 | 전자책·인쇄 | |
| FE-7 | 설정·탈퇴·타겟 3종 변형 | |
| FE-8 | 관리자·편집자 | |
FE-1 완료 시 **개발용 콘솔 삭제**(dev-console-remove-check.sh 통과가 FE-1 닫힘 조건).

---

## 10. 이 설계가 백엔드에 요구하는 것 (감사 H와 연결)
| # | 필요 | 지금 |
|---|---|---|
| 1 | 코스 상세 응답에 자리 5·후보·좌표·QR 힌트·원고 미리보기 한 번에 | 대부분 있음 — 원고 미리보기 확인 |
| 2 | 타겟(tier)별 확장문구·미션 분기 | 감사 H STEP 4 결과에 따름 |
| 3 | 원고·확장문구 다국어 | 없음(감사 H) |
| 4 | `GET /api/stamps/{id}` 복귀용 상태 | 있음 |
| 5 | 전자책 종류(권역본/회향본) 구분 | 감사 H 결정 |
| 6 | 인쇄주문 `cancelable`·전자책 `downloadable` 계산값 | 있음 |
| 7 | `X-Request-Id` 헤더 | 있음(RequestIdFilter 확인) |

---

## 11. Claude Code 지시문 — 실제 코드 기준 설계도 + 대조

```
[프론트엔드 설계 대조] 정본은 docs/frontend/frontend-design.md(이 문서) 다. 코드 무변경. 먼저 정리.md §4·frontend-handoff.md·docs/audit/endpoints.md·audit-H-명세매칭_노션용.md(있으면)를 읽어라. 산출물은 docs/frontend/frontend-design-actual.md(실제 코드 기준 설계도)와 docs/frontend/frontend-design-diff.md(대조표) 두 장, 표와 문단만.

STEP 1 API 계약 추출: endpoints.md 91개마다 요청 DTO·응답 DTO(record 필드·타입·null 규칙·present 필드)·에러코드·인증 등급을 코드에서 뽑아 TypeScript 타입 초안(docs/frontend/api-types.d.ts)으로 생성. 페이징 봉투·날짜 형식·ItemsResponse/PageResponse 구분 명시.

STEP 2 실제 기준 설계도: 정본 §2 라우팅 표의 화면마다 "실제로 부를 수 있는 API·필드"로 다시 그린다. 정본이 가정한 필드가 응답에 없으면 ❌, 이름이 다르면 🔁(실제 이름), 서버가 더 주는 것은 ⬆. 특히 코스 상세(자리·후보·congested·좌표·QR 힌트·원고 미리보기), 미션 응답(원고·확장문구·courseCompleted·certificateSerial·rewards), 전자책(status·downloadUrl·cancelable), 인증서(status·downloadUrl), 진위 확인(7필드), 보상(claimable).

STEP 3 캐시·무효화: 정본 §3-2 표를 실제 API 이름으로 다시 쓰고, "도장 발행 성공 시 무효화" 대상 쿼리 키 목록을 확정.

STEP 4 타겟 3종·다국어: tier가 어느 API의 어느 필드로 오가는지, 콘텐츠 i18n이 실제로 나가는 API·필드, 원고/확장문구 다국어 부재 여부 — 감사 H STEP 4·다국어 결과와 일치시켜 적는다.

STEP 5 대조표: 정본 §2·§3·§5·§10 항목마다 일치/차이/정본이 틀림/백엔드 추가 필요 네 갈래로. "백엔드 추가 필요"는 감사 H 재구현 후보와 합쳐 하나의 번호를 쓴다.

STEP 6 FE-1 착수 조건: 정본 §9 FE-1 범위에 필요한 API가 전부 ✅인지 확인하고, 아니면 무엇이 막는지 한 줄. 개발용 콘솔 삭제 스크립트 존재 확인.

마지막 줄: fe-design: 화면 N · API 매핑 ✅a 🔁b ❌c ⬆d · 백엔드 추가 필요 e건 · FE-1 착수 가능/불가
```
