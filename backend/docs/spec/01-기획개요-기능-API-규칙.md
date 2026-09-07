# temple-stamp-project — 기획 명세 ① 개요 · 사용자 흐름 · 기능 · API · 규칙

> 노션 원문을 md로 옮긴 것. 이미지(화면 전이도·흐름도)는 `[그림: …]`으로 자리만 표시. 내용은 원문 그대로이며 명세 안의 모순(세션 90분/60분, access 1시간/30분 등)도 그대로 둔다 — 감사 H가 ❓로 잡는다.

## 목적과 범위

전국 9권역 60개 사찰을 오관게 5구 체계로 순례하는 스탬프 투어 플랫폼을 구축한다. 사용자는 사찰에 도착해 세 가지 조건을 충족하면 스탬프를 얻고, 그 과정에서 남긴 다짐과 사진이 쌓여 전자책이 된다. 로그인하지 않은 사람도 명상 108선은 언제나 이용할 수 있다.

참고: https://apis.map.kakao.com/web/guide/

## 기술 스택

| 구분 | 기술 | 비고 |
|---|---|---|
| 런타임 | Java 21 (LTS) | 가상 스레드 · record · 패턴 매칭 · sealed 사용. 릴리스 타깃 21 고정 |
| 백엔드 | Spring Boot 3.5.16 | REST API. 서버 렌더링 없음. 패치 버전까지 고정 |
| 영속성 | MyBatis 3.x (mybatis-spring-boot-starter 3.0.x) | XML 매퍼. JPA 미사용 |
| DB | MySQL 8.x | utf8mb4. 사용자 좌표 컬럼 없음 |
| 인증 | JWT (HS256) · jjwt 0.12.x | 키 생성은 jwt generator로 발급한 시크릿 |
| 빌드 | Gradle (Kotlin DSL) | toolchain 21 고정 |
| 프론트 | React 18 + Vite | SPA |
| 스타일 | Tailwind CSS | 기본 스타일링 수단 |
| 지도 | 카카오맵 JavaScript SDK | 사찰 좌표 조회·표시. 좌표 판정은 단말에서 |
| 테스트 | Postman | API 검증 전량. 컬렉션 + 환경변수로 시나리오 자동화 |
| 스토리지 | 오브젝트 스토리지 (S3 호환) | 사진·전자책 파일 |

---

# 1. 핵심 사용자 흐름

## 사용자 유형과 상태

| 상태 | 할 수 있는 것 | 할 수 없는 것 |
|---|---|---|
| 관람자(비로그인) | 인트로, 권역·사찰 조회, 싱글페이지 열람, 명상 청취, 사찰 가는 법 | 스탬프, 다짐, 사진, 생각상자, 명상 기록, 전자책 |
| 순례자(로그인) | 위 전부 + 3조건 인증, 기록 저장 | – |
| 진행중 | + 여권 진행률, 여섯 달 전 오늘 | – |
| 권역완주 | + 권역 인증서, 권역 전자책 | – |
| 회향 | + 회향본, 소량 인쇄 신청, 협회 회향증서 명단 등재 | – |

### 타겟 3종

| 구분 | 2030 | 선라이더 | 외국인 |
|---|---|---|---|
| 오는 이유 | 이색 경험·기록 | 코스·주행 | 한국 불교문화 체험 |
| 앱에서 가장 먼저 보는 것 | 오관게 구절 | 다음 사찰 주행정보 | 오관게 한자 + 자국어 해석 |
| 하루 방문 수 | 1~2곳 | 3~5곳 | 1~2곳 |
| 확장문구 target_group | AGE20 / AGE30 | RIDER | FOREIGN |
| 결정적 이탈 지점 | 인트로가 길면 이탈 | 주차·노면 정보 없으면 이탈 | 언어 전환 안 되면 이탈 |
| 대응 설계 | 인트로 1단 8초·2단 스킵 | 싱글페이지에 주행 블록 상시 | 한자 원문 유지 + 4개 로케일 |

[그림: 진입에서 순례 준비까지 화면 전이도 ×5]

### 인트로 3단

| 단계 | 길이 | 스킵 | 화면 요소 | 이탈 방지 |
|---|---|---|---|---|
| 1단 | 8초 이내 | 불가 | 산문 열림 + 문구 한 줄씩 찍히듯 | 8초를 넘기지 않는 것이 유일한 방어 |
| 2단 | 30초 이내 | 가능 | 오관게 5구 원문·한글·주제, 각 구가 권역 5사찰 주제임을 연결 | 스킵 버튼 상시 |
| 3단 | 무제한 | – | 지도 9권역 점등 + 시작 버튼 | – |
| 공통 | – | – | 플로팅 홈 버튼 상시, 조각 모형 미세 흔들림 | 언제든 탈출 가능 |

# 2. 사용자 흐름에 매칭되는 프로그램 흐름

[그림: 프로그램 흐름도 ×4]

### 회원가입·로그인·토큰 (F-02, F-03) — 토큰 보관 규칙

| 토큰 | 위치 | 수명 | 재사용 감지 시 |
|---|---|---|---|
| Access | 프론트 메모리 (localStorage 금지) | 30분 | – |
| Refresh | HttpOnly·Secure·SameSite 쿠키 | 14일 | 해당 사용자 전체 토큰 revoke |

### 권역·사찰 탐색 (F-04)
권역 카드에 표시하는 값: 권역명, 진행 n/5, 완주 여부, 대표 사찰명, 다음 미방문 사찰까지 거리.

### 5.4 싱글페이지 조립 (F-05, F-06, F-07)

[그림: 싱글페이지 조립]

**확장문구 선택 규칙**

| 순서 | 규칙 | 실패 시 |
|---|---|---|
| 1 | target = 쿼리 → 사용자 설정 → AGE30 | – |
| 2 | verse_no = 사찰이 맡은 구 | – |
| 3 | 후보 5편 중 phrase_seen에 없는 것 우선 랜덤 | 4로 |
| 4 | 전부 노출됐으면 5편 전체 랜덤 | – |
| 5 | 같은 권역 안에서 같은 version_no 재출력 금지 | 조건 완화 후 재조회 |

**행동과제 출제 규칙**

| 항목 | 값 |
|---|---|
| 가중치 | 사찰 고유 50% / 구절별 30% / 공통 20% |
| 제외 1 | 같은 권역 내 task_seen에 있는 과제 |
| 제외 2 | 사진 과제인데 해당 사찰 사진이 이미 있음 |
| 최소 풀 | 공통 12 + 구절별 8 + 고유 6 = 사찰당 26편 이상 |
| 고갈 시 | 가중치 무시 전체 랜덤 → 그래도 0건이면 TASK-002 운영자 알림 |

### 5.5 현장 인증 3조건 — 핵심 흐름 (F-08~F-11)

[그림: 세 조건을 채우는 과정 / 발급 판정]

**서버 재검증 항목 (Blocked 사유)**

| 사유 | 조건 | 사용자에게 보이는 문구 |
|---|---|---|
| ATTEMPT_EXPIRED | 개시 후 90분 초과 | 인증 시간이 지났습니다. 다시 시작하세요 |
| ALREADY_ISSUED | uk_stamp 위반 | 이미 받은 스탬프입니다 |
| TOO_FAST | 직전 스탬프와의 간격 < 사찰 간 최소 이동시간 | 이동 시간을 확인하고 있습니다 |
| DAILY_LIMIT | 하루 발급 상한 초과 | 오늘 받을 수 있는 스탬프를 모두 받았습니다 |
| QR_REVOKED | 키버전 폐기 | 이 QR은 더 이상 쓰이지 않습니다 |

**좌표가 사라지는 지점** — 서버 DTO·DB 어디에도 사용자 좌표 컬럼이 없다. 원칙①이 스키마 차원에서 지켜진다.

### 5.6 사진 업로드 (F-12) · 5.7 여권과 완주 판정 (F-13) · 5.8 명상 108선 (F-16, F-17) · 5.9 생각상자와 여섯 달 전 오늘 (F-14, F-15)
[그림 ×4] 기록 흐름에는 분기가 적다. 의도적이다. 저장과 열람 외의 기능을 넣지 않는 것이 원칙②다.

### 5.10 전자책과 인증서 (F-18, F-19)

| 종류 | 조건 | 내용물 | 추가 혜택 |
|---|---|---|---|
| 권역본 REGION | 한 권역 5곳 | 다짐 5편 + 사진 5장 + 오관게 5구 + 표지 | 권역 인증서 |
| 중간본 INTERIM | 3권역 완주 | 15곳 편집본 | – |
| 회향본 FINAL | 9권역 60곳 | 전체 회향본 | 소량 인쇄 신청, 협회 회향증서 명단 등재 |

**제외 규칙**: photo.has_other_face=1, photo.is_private=1, thinkbox.is_private=1

## 6. 기능 명세 F-01 ~ F-22

| ID | 기능 | 인증 | 입력 | 처리 | 출력 | 화면 | API | 주요 예외 |
|---|---|---|---|---|---|---|---|---|
| F-01 | 인트로 3단 | 불필요 | – | 최초 실행 판정, 3단 재생 | 첫 마음 로컬 저장 | S-01 | – | 영상 로드 실패 시 절차적 연출 폴백 |
| F-02 | 회원가입·로그인 | – | 이메일·비밀번호·닉네임·타겟 | 검증, BCrypt, 실패횟수 | accessToken + refresh 쿠키 | S-16 | POST /api/auth/signup, /login, /social/{provider} | AUTH-001, AUTH-003 |
| F-03 | 토큰 재발급 | 쿠키 | refresh 쿠키 | 회전 발급, 재사용 감지 | 새 accessToken | – | POST /api/auth/refresh | AUTH-002 |
| F-04 | 권역·사찰 조회 | 불필요 | regionId? | 9권역·5사찰 조회, 로그인 시 진행률 결합 | 목록·지도 | S-03, S-04 | GET /api/regions, /regions/{id} | – |
| F-05 | 사찰 싱글페이지 | 선택 | templeId, target, locale | 8블록 1회 조립 | TemplePageResponse | S-05 | GET /api/temples/{id}/page | 사찰 비활성 404 |
| F-06 | 확장문구 랜덤 | 선택 | target, verseNo | 미노출 우선 랜덤, 권역 내 중복 금지 | 문구 1편 | S-05 | F-05에 포함 | 후보 0건 시 완화 재조회 |
| F-07 | 행동과제 랜덤 | 선택 | templeId | 50/30/20 가중, 중복·사진 제외 | 과제 1편 | S-05 | F-05에 포함 | TASK-002 |
| F-08 | 위치 검증 | 필요 | **boolean만** | 단말이 거리 판정, 서버는 플래그 기록 | location_ok | S-06 | POST /api/stamps/attempts/{id}/location | 권한 거부·정확도 부족 |
| F-09 | 과제·다짐 저장 | 필요 | attemptId, taskId, body | 저장, 평가 없음 | resolutionId | S-06 | POST /api/resolutions | 빈 문구 VALID-001 |
| F-10 | QR 검증 | 필요 | token | HMAC 서명·키버전·폐기 검사 | qr_ok | S-06 | POST /api/stamps/attempts/{id}/qr | QR-001, QR-002 |
| F-11 | 스탬프 발급 | 필요 | attemptId | 3조건 AND 재검증 + 몰아찍기 차단 | StampDecision | S-07 | POST /api/stamps | STAMP-002·003·004 |
| F-12 | 사진 업로드 | 필요 | 파일, memo 필수 | UPSERT, 사찰당 1장 | photoId | S-08 | PUT /api/photos/{templeId} | PHOTO-001 |
| F-13 | 여권 | 필요 | – | 9권역 집계, 현재 권역 5칸 | 진행률 | S-03 | GET /api/passport | – |
| F-14 | 생각상자 | 필요 | body, region?, temple? | 저장·조회만 | 목록 | S-11 | POST/GET /api/thinkbox | – |
| F-15 | 여섯 달 전 오늘 | 필요 | – | 6개월 전 날짜 기록 조회 | 기록 1건 또는 null | S-02 | GET /api/thinkbox/flashback | 없으면 카드 미표시 |
| F-16 | 명상 108선 | **불필요** | category? | 목록·상세 조회 | 스크립트·오디오 | S-09, S-10 | GET /api/meditations | – |
| F-17 | 명상 기록 | 필요 | playedSec, memo? | 저장 | logId | S-10 | POST /api/meditations/{id}/logs | – |
| F-18 | 전자책 | 필요 | – | 완주 시 큐 적재, 워커 생성 | PDF·EPUB URL | S-13 | GET /api/ebooks | FAILED 재시도 |
| F-19 | 인증서 | 필요 | – | 권역·회향 발급, 일련번호 | 인증서 | S-14 | GET /api/certificates | – |
| F-20 | 다국어 | 불필요 | locale | i18n 리소스 + 콘텐츠 테이블 | 번역문 | 전 화면 | GET /api/i18n/{locale} | 미번역 시 ko 폴백 |
| F-21 | 사찰 가는 법 | 불필요 | – | 삼문·경내 요소·참배 순서 공통 코너 | 안내 | S-17 | 정적 | – |
| F-22 | 설정·서비스 안내 | 불필요 | – | 비의료·비진단 문구 상시 열람, 알림, 대상 연령대 변경 | 설정 | S-15 | PATCH /api/users/me | – |

# 2-1. 사용자 흐름에 따른 DB 흐름
[그림 ×8] 흐름 1 회원가입·로그인·토큰(F-02·03) / 흐름 2 홈·권역·코스·순례 시작(F-04·13) / 흐름 3 싱글페이지(F-05·06·07) / 흐름 4 3단계 인증 ★(F-08→09→10) / 흐름 5 완주 재집계·인증서·전자책(F-13·18·19) / 흐름 6 기록(F-14·15·16·17·12) / 흐름 7 관리자(권한 매트릭스, Q7 보완) / 흐름 8 보상 청구(F-23, Q8 보완)

# 2-2. 기능별 흐름도 (F-01~F-23)
[그림]

### QR 방문 인증
관리자가 장소별 QR 생성 → 사용자가 휴대전화 카메라로 QR 스캔 → QR 링크를 통해 방문 인증 → 중복 방문 방지 → 스탬프 지급 → 코스 완주 처리. Java 백엔드의 QR 생성에는 ZXing(core·javase).

# 3. QR은 이렇게 단순하게 구현
QR 안에 인증 URL을 넣는다: `https://busan-tour.com/checkin?token=abc123xyz`. QR 스캔 → React 체크인 페이지 → JWT 로그인 확인 → Spring Boot 체크인 요청 → QR 토큰 검증 → 방문 기록 저장.
`/checkin?placeId=3`처럼 장소 ID만 넣으면 조작이 쉬우므로 랜덤 토큰을 쓴다. DB에는 토큰 → 장소 → 방문 기록.

---

# 4. API 명세

**공통 응답 형식**
```json
{ "success": true, "data": { }, "error": null, "timestamp": "2026-09-02T14:03:22+09:00" }
```
실패 시 `success:false`, `data:null`, `error:{code, message, fields}`. 에러코드 형식 `{도메인}-{HTTP상태}{일련번호}` — 예 `STAMP-4091`.

### 인증
```
POST /api/auth/signup · /login (accessToken + refresh 쿠키) · /refresh (쿠키 회전) · /logout
```
액세스 토큰은 `Authorization: Bearer`, 유효기간 1시간. 재발급 토큰은 `HttpOnly; Secure; SameSite=Strict; Path=/api/auth` 쿠키, 14일. 사용 즉시 폐기하고 새로 발급(회전).

### 회원
```
GET /api/users/me · PATCH /api/users/me (tier, 언어, 알림, 닉네임) · POST /api/users/me/agreements
```

### 권역 · 코스 · 사찰
```
GET /api/regions · GET /api/courses?regionId= · GET /api/courses/{courseId} (사찰 5곳 좌표 일괄)
GET /api/sites/{siteId} · GET /api/sites/{siteId}/page (싱글페이지 9블록) · GET /api/verses · GET /api/verses/{verseNo}
```
코스 상세가 좌표를 내려주면 이후 거리 계산은 앱이 직접 한다. 좌표는 서버로 돌아오지 않는다.

### 순례 · 여권
```
POST /api/pilgrimages { courseId } (멱등, 200) · GET /api/passport (권역 → 코스 → 5칸)
```

### 스탬프 인증 (3단계) ★
```
POST /api/stamps/gps-check · POST /api/stamps/{stampId}/qr · POST /api/stamps/{stampId}/mission
POST /api/stamps/evidence (예외 접수) · GET /api/stamps/{stampId} (앱 복귀용)
```
① 위치 확인 요청 `{ pilgrimageId, courseSiteId, inRadius, accuracyGrade }` — 위도·경도 필드 없음, 들어오면 400 `COMMON-4001`. 응답 `{ stampId, status:"GPS_DONE", expiresAt, qrLocationHint }`.
② QR `{ qrToken }`. ③ 미션 `{ userSentence, photoKey }` → 응답 `{ stampId, status:"COMPLETED", progress{pilgrimageId, completedCount, total}, rewards[], courseCompleted, certificateSerial }`. 5개째 완료 시 `courseCompleted:true` + `certificateSerial:"PG-2026-000012"`.

| 코드 | 상태 | 상황 |
|---|---|---|
| STAMP-4001 | 400 | 반경 밖 |
| STAMP-4002 | 400 | GPS 정확도 부족 |
| STAMP-4031 | 403 | 남의 스탬프 |
| STAMP-4091 | 409 | 이미 받은 스탬프 |
| STAMP-4092 | 409 | 인증 순서 위반 |
| STAMP-4101 | 410 | 60분 만료 |
| STAMP-4221 | 422 | QR 무효 (다른 사찰·구버전) |
| STAMP-4222 | 422 | 문장 3자 미만 |
| STAMP-4291 | 429 | 하루 5개 초과 |
| STAMP-4292 | 429 | 예외 접수 하루 2건 초과 |

### 사진 업로드
`GET /api/uploads/presign?purpose=PHOTO&contentType=image/jpeg` → `{ uploadUrl, fileKey, method:"PUT", expiresIn:300 }`. 서버는 바이너리를 받지 않는다.

### 생각상자
```
GET /api/thinkbox?sort=date|course|site&page=0&size=20 · POST /api/thinkbox · PATCH /api/thinkbox/{id} · GET /api/thinkbox/flashback (200 + data:null)
```

### 명상
```
GET /api/meditations?category=&limit=&locale= ★비로그인 · GET /api/meditations/{id} ★비로그인 · POST /api/meditations/{id}/logs (로그인)
```

### 전자책 · 인증서 · 보상
```
GET /api/ebooks · GET /api/ebooks/{ebookId}/download?format=pdf|epub
GET /api/certificates · GET /api/certificates/verify/{serialNo} ★비로그인 · GET /api/rewards · POST /api/rewards/{userRewardId}/claim
```

### 정적 콘텐츠
```
GET /api/i18n/{locale} ★ · GET /api/guide?locale= ★
```

### 지도 검색 (관리자 전용)
`GET /api/admin/kakao/places?query=통도사&page=1&size=15` → `{ totalCount, pageableCount, isEnd, places[{placeName, addressName, roadAddressName, latitude, longitude, phone, placeUrl}] }`. REST 키는 서버만 보유. 일반 사용자 지도는 프론트가 JavaScript 키로 직접 그린다.

### 관리자
```
사찰·코스: POST /api/admin/sites · PUT /api/admin/sites/{id} · PATCH …/status · POST …/i18n · POST …/viewpoints · POST …/badges
          POST /api/admin/courses · PUT /api/admin/courses/{id}/sites (자리 5개 일괄) · PATCH …/status · PUT /api/admin/site-distances
QR:      POST /api/admin/sites/{id}/qr · POST …/qr/rotate
심사:    GET /api/admin/stamps?status=PENDING · POST …/{id}/approve · POST …/{id}/reject
         GET /api/admin/rewards/review · POST /api/admin/rewards/{id}/resolve · POST /api/admin/ebooks/{id}/retry
콘텐츠:  POST /api/admin/expansion-phrases · POST /api/admin/missions · PATCH …/{id}/review ×2 · POST/PUT /api/admin/meditations
```

### 설계 규칙
- URL: 접두 `/api`, 복수 명사, 하이픈. 동사는 상태 전이 행위에만 하위 경로(`/qr`, `/mission`, `/approve`, `/rotate`, `/claim`). 관리자 `/api/admin/{리소스}`.
- HTTP 상태: 조회·행위 200, 생성 201, 충돌 409, 만료 410, 처리 불가 422, 상한 429.
- 페이징: `page`(0부터)·`size`(기본 20, 최대 100)·`sort`(화이트리스트). 응답 `{ items, page, size, totalElements, totalPages, hasNext }`.
- 날짜: ISO-8601 `+09:00`. 시각은 서버가 정한다.
- null: 응답에서 값이 없어도 키는 항상 내려준다. 요청에서 필드를 안 보내면 "변경 없음".

## 6. 상태 전이

### 6.1 stamp_attempt
| 상태 | 의미 | 다음 행동 |
|---|---|---|
| OPEN | 인증 진행 중 | 남은 조건 안내 |
| ISSUED | 발급 완료 | 사진 업로드 유도 |
| EXPIRED | 90분 초과 | 새 attempt 생성 |
| BLOCKED | 규칙 위반 | 사유별 안내 |

### 6.2 조건 조합별 결과
| 위치 | 다짐 | QR | 결과 |
|---|---|---|---|
| ✗ | ✗ | ✗ | Pending |
| ✓ | ✗ | ✗ | Pending |
| ✓ | ✓ | ✗ | Pending — 가장 흔한 상태 |
| ✓ | ✗ | ✓ | Pending |
| ✗ | ✓ | ✓ | Pending — 실내 GPS 실패 가능 |
| ✓ | ✓ | ✓ | **Issued** |

## 7. 공통 규약 + 권한 매트릭스

### 에러코드 체계 `{도메인}-{HTTP상태 3자리}{일련번호 1자리}` — 앞 세 자리는 실제 HTTP 상태와 같다.

| 도메인 | 코드 |
|---|---|
| COMMON | 4000 검증 실패 · 4001 좌표 필드 포함 · 4040 없음 · 4050 메서드 불허 · 4090 상태 충돌 · 5000 서버 오류 |
| AUTH | 4011 자격증명 불일치 · 4012 refresh 무효 · 4013 토큰 없음 · 4031 계정 잠금 · 4032 권한 없음 · 4091 이메일 중복 |
| USER | 4001 잘못된 tier · 4041 사용자 없음 |
| COURSE | 4001 다른 코스의 자리 · 4041 코스 없음 · 4042 자리 없음 · 4092 비활성 코스 · 4093 사찰 중복 배정 |
| SITE | 4041 사찰 없음 |
| PILGRIM | 4031 소유자 아님 · 4041 순례 없음 |
| STAMP | 4001 · 4002 · 4031 · 4091 · 4092 · 4101 · 4221 · 4222 · 4291 · 4292 |
| UPLOAD | 4001 용도 오류 · 4221 EXIF 포함 · 5021 저장소 오류 |
| THINKBOX | 4031 소유자 아님 · 4041 없음 |
| MED | 4041 없음 |
| EBOOK | 4041 없음 · 4092 아직 생성 중 |
| CERT | 4041 확인 불가 |
| REWARD | 4001 실물 아님 · 4041 없음 · 4092 상태 오류 |
| KAKAO | 5021 카카오 통신 실패 |
| ADMIN | 4092 처리 불가 상태 |

### 인가 등급
| 등급 | 의미 | 예 |
|---|---|---|
| 공개 | 토큰 없어도 200 | 권역·코스·사찰·명상 조회, 인증서 진위 확인, 가는 법, 다국어 |
| 선택 | 토큰 있으면 개인화 | 싱글페이지, 코스 목록 |
| 필요 | 토큰 없으면 401 | 나머지 전부 |
| ADMIN | `ROLE_ADMIN` | `/api/admin/**` |
"선택"은 `permitAll` + 토큰 있을 때만 SecurityContext, Controller는 `@AuthenticationPrincipal(required=false)`.

### 기능별 권한 매트릭스
| 기능 | 비로그인 | 로그인 | 관리자 | 등급 |
|---|---|---|---|---|
| 인트로 | ○ | ○ | ○ | 서버 호출 없음 |
| 회원가입·로그인 | ○ | ○ | ○ | 공개 |
| 토큰 재발급·로그아웃 | ✕ | ○ | ○ | 쿠키 필요 |
| 권역·코스·사찰 조회 | ○ | ○ | ○ | 선택 |
| 싱글페이지 열람 | ○ | ○ | ○ | 선택 |
| 확장문구·미션 노출 | ○(기록 안 함) | ○(seen 기록) | ○ | 선택 |
| 사찰 가는 법·다국어 | ○ | ○ | ○ | 공개 |
| 명상 조회·재생 | ○ | ○ | ○ | 공개 |
| 명상 기록 | ✕ | ○ | ○ | 필요 |
| 순례 시작 | ✕ | ○ | ○ | 필요 |
| 3단계 인증·스탬프 | ✕ | ○ | ○ | 필요 |
| EVIDENCE 예외 접수 | ✕ | ○ | ○ | 필요 |
| 사진 업로드 주소 발급 | ✕ | ○ | ○ | 필요 |
| 다짐·사진·생각상자 | ✕ | ○ | ○ | 필요 |
| 여권·전자책 | ✕ | ○ | ○ | 필요 |
| 내 인증서 목록 | ✕ | ○ | ○ | 필요 |
| 인증서 진위 확인 | **○** | ○ | ○ | **공개** |
| 보상 목록·청구 | ✕ | ○ | ○ | 필요 |
| 설정·동의 | ✕ | ○ | ○ | 필요 |
| 사찰·코스·문구·미션·명상 등록 | ✕ | ✕ | ○ | ADMIN |
| 코스-사찰 배정·이동시간 등록 | ✕ | ✕ | ○ | ADMIN |
| QR 발급·폐기 | ✕ | ✕ | ○ | ADMIN |
| EVIDENCE 승인·반려 | ✕ | ✕ | ○ | ADMIN |
| 보상 감사 큐 처리 | ✕ | ✕ | ○ | ADMIN |
| 전자책 재생성 요청 | ✕ | ✕ | ○ | ADMIN |
| 카카오 장소 검색 | ✕ | ✕ | ○ | ADMIN |

관리자도 일반 계정이다(`users.role=ADMIN`일 뿐 순례 가능). 비로그인 ○라고 같은 데이터가 나가는 것은 아니다(싱글페이지 `verifyState` null, 코스 목록 `progress` null).

### 인증서 진위 확인을 공개로 두는 이유
조회자가 주인이 아닌 제3자(사찰 관계자·협회·사은품 현장)이므로 로그인을 요구하면 QR의 의미가 사라진다. 노출 범위는 `/api/certificates/verify/**` 하나만 공개, 응답은 일련번호·종류·코스 이름·발급일·마스킹 닉네임(`순*자`)만. 번호를 알아야 조회되므로 수집이 성립하지 않는다.

### 트랜잭션과 잠금
경계는 Service 메서드. 조회 전용 `readOnly`. **잠금 순서 `pilgrimage → stamp → user_reward` 고정.** 카운터는 `SET col = col + 1`. 정상 중복(`phrase_seen`·`task_seen`·`user_agreement`)은 `INSERT IGNORE`, 그 외 제약 위반은 409. 예외 전에 커밋돼야 하는 변경(60분 만료)은 `REQUIRES_NEW` 별도 클래스.

### 로그
요청마다 `requestId`(UUID 8자리) MDC + 응답 헤더 `X-Request-Id`. 로그 금지: 비밀번호, 토큰 원문, `user_sentence`, `thinkbox.body`, `memo`, 전체 이메일(마스킹 `d***@x.com`).

## 8. 타겟별 여정 차이
| 항목 | 2030 | 라이더 | 외국인 |
|---|---|---|---|
| 홈 첫 카드 | 오관게 | 오관게(다음 사찰 카드 2번째로 검토) | 오관게 + 언어 전환 유도 |
| 하루 발급 상한 | 5 | 5(상향 검토) | 5 |
| 지도 | 카카오맵 | 카카오맵 + 코스 | Q6 미결 |
| 인증 버튼 | 표준 | 크게(장갑) | 표준 + 아이콘 |

## 9. 예외·에러 시나리오
| # | 상황 | 사용자에게 | 코드 | 복구 |
|---|---|---|---|---|
| E-01 | GPS 권한 거부 | 위치 권한 필요 + 설정 | – | 권한 재요청 |
| E-02 | 정확도 100m 초과 | 신호를 더 받는 중 | – | 30초 재시도 |
| E-03 | 실내 GPS 실패 | 경내 마당에서 재시도 | – | 위치만 재시도 |
| E-04 | QR 서명 불일치 | 이 사찰의 QR이 아님 | QR-001 | 재스캔 |
| E-05 | 폐기된 QR | 더 이상 쓰이지 않음 | QR-002 | 사찰 문의 |
| E-06 | 90분 초과 | 인증 시간 지남 | STAMP-002 | 새 attempt |
| E-07 | 이미 받은 스탬프 | | STAMP-003 | 여권으로 |
| E-08 | 이동시간 미달 | 이동 시간 확인 중 | STAMP-004 | 대기 시간 표시 |
| E-09 | 사진 메모 누락 | 한 줄을 남겨 주세요 | PHOTO-001 | 메모 입력 |
| E-10 | 과제 풀 고갈 | 잠시 뒤 | TASK-002 | 운영자 알림 |
| E-11 | 토큰 만료 | (안 보임) | AUTH-002 | 자동 refresh 1회 |
| E-12 | 로그인 5회 실패 | 15분 뒤 | AUTH-003 | 대기 |
| E-13 | Render 콜드스타트 | 서버를 깨우는 중 | – | 60초 타임아웃 + 1회 재시도 |
| E-14 | 전파 없음 | 저장 못 함, 연결되면 재시도 | – | 재시도 큐(Q8) |
| E-15 | 전자책 생성 실패 | 만드는 중 | – | FAILED → QUEUED |

## 10. 화면 ↔ API 매핑
| 화면 | 진입 시 | 행동 시 | 캐시 |
|---|---|---|---|
| S-02 홈 | GET /api/passport, /thinkbox/flashback, /meditations?limit=3 | – | 60초 |
| S-03 여권 | GET /api/regions | – | 60초 |
| S-04 권역 상세 | GET /api/regions/{id} | – | 60초 |
| S-05 싱글페이지 | GET /api/temples/{id}/page | – | 캐시 안 함 |
| S-06 현장 인증 | POST /api/stamps/attempts | location·resolutions·qr·stamps | 캐시 안 함 |
| S-08 사진 | – | PUT /api/photos/{templeId} | – |
| S-09 명상 | GET /api/meditations | – | 24시간 |
| S-10 명상 재생 | GET /api/meditations/{id} | POST …/logs | 24시간 |
| S-11 생각상자 | GET /api/thinkbox | POST | 60초 |
| S-13 전자책 | GET /api/ebooks | – | 60초 |
| S-14 인증서 | GET /api/certificates | – | 60초 |
무효화: 스탬프 발급 성공 → home·passport·region. 언어 변경 → 전체.

## 11. 아직 정해지지 않은 것
| # | 질문 | 영향 | 우선순위 |
|---|---|---|---|
| Q1 | QR 위조 대응 — 정적/회전/좌표 반경 | 5.5, 스키마 | 최우선 |
| Q4 | 사진·전자책 저장소 | 5.6, 5.10 | 최우선 |
| Q2 | 소셜 로그인 범위 | 5.2 | 높음 |
| Q3 | 전자책 PDF 엔진·폰트 라이선스 | 5.10 | 높음 |
| Q5 | 하루 발급 상한(라이더) | 5.5, 9장 | 중간 |
| Q6 | 외국인용 지도 SDK | 5.3, 9장 | 중간 |
| Q7 | 전자책 별도 탭 | 4장 | 낮음 |
| Q8 | 오프라인 캐시 PWA | E-14 | 낮음 |
