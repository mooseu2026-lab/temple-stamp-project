# 프론트엔드 설계 대조표 (2026-09-07)

**fe-design: 화면 21 · API 매핑 ✅ 52 🔁 9 ❌ 4 ⬆ 11 · 백엔드 추가 필요 5건 · FE-1 착수 가능**

정본 `frontend-design.md` ↔ 실제 코드. 실제 기준 설계도는 `frontend-design-actual.md`, 타입 초안은 `api-types.d.ts`(엔드포인트 91 · DTO 104, 자동 생성).
코드는 하나도 바꾸지 않았다. "백엔드 추가 필요" 는 감사 H 의 재구현 후보와 **같은 번호**를 쓴다.

---

## 1. 정본 §2 라우팅 — 화면 21

| 갈래 | 수 | 어느 화면 |
|---|---:|---|
| 일치 | 16 | 인트로 · 권역 · 권역 상세 · 코스 상세 · 싱글페이지 · 3단계 인증 · QR 체크인 · 여권 · 사진 · 명상 · 인증서 · 진위 확인 · 보상 · 로그인/가입 · 가는 법 · 편집자 |
| 차이(🔁) | 4 | 홈 · 생각상자 · 전자책 · 설정 |
| 정본이 틀림 | 0 | |
| 백엔드 추가 필요 | 1 | 관리자(정본이 "신설" 로만 적었는데 34문이 이미 있다 — 정본을 채우면 된다) |

### 차이 넷

| 화면 | 정본 | 실제 | 프론트가 할 일 |
|---|---|---|---|
| `/home` | 홈 화면 | **홈 전용 API 가 없다** | `GET /api/verses` · `/api/passport` · `/api/thinkbox/flashback` · `/api/meditations` 넷을 병렬로 부른다 |
| `/thinkbox` | 삭제 성공 판정 | **DELETE → 204 · 본문 없음** | `status === 204` 로 판정. `res.json()` 금지 |
| `/ebooks` | `cancelable` 필드 | **없다** | `PrintOrderResponse.status === 'REQUESTED'` 로 취소 버튼을 켠다 |
| `/settings` | 약관 철회 → 남은 목록 | **204 · 본문 없음** | 철회 뒤 `GET /api/users/me/agreements` 를 한 번 더 부른다 |

---

## 2. 정본 §3 상태·API 계층

| 항목 | 판정 | 실제 |
|---|---|---|
| 봉투 해제 `{success,data,error,timestamp}` | ✅ | 91문 전부. 봉투 밖으로 나가는 반환 0건 |
| 401 → refresh 1회 → 재시도 | ✅ | `POST /api/auth/refresh` · 재사용이 감지되면 **전 기기**가 죽는다(`AUTH-4015`) |
| `X-Request-Id` 응답 헤더 | ✅ | `RequestIdFilter` |
| 5xx·네트워크 → 표시만 | ✅ | 재시도 큐는 Q8 미결 |
| 요청에 `lat/lng` 금지 | ⬆ | 타입에 필드가 없고 **서버도 400 `COMMON-4001` 로 막는다**(세 겹) |
| 캐시 표 | 🔁 | 실제 쿼리 키로 다시 씀 — 설계도 §4 |
| `stamp{stampId, 단계, expiresAt}` 복원 | ✅ | `GET /api/stamps/{id}` · 자리로 찾으려면 `GET /api/stamps/by-slot/{courseSiteId}` |

**캐시 표에서 하나 더한 것** — 도장 발행 성공 시 무효화 대상에 `['certificates']`(응답에 `certificateSerial` 이 있을 때)와
`['ebooks']`(`courseCompleted: true` 면 코스본이 자동으로 큐에 들어간다)를 넣어야 한다. 정본에는 없다.

---

## 3. 정본 §5 화면별 설계 — 필드 단위

| # | 정본이 가정한 필드 | 실제 | 판정 |
|---|---|---|---|
| 1 | 코스 상세: 자리·후보·`congested`·좌표·QR 힌트 | 전부 있다 | ✅ |
| 2 | 코스 상세: **원고 미리보기** | 없다 — 문구·과제는 싱글페이지에 있다 | ❌ |
| 3 | 미션 응답: 원고 | 제출 응답에는 `extPhrase` 만. 원고 본문은 `GET /api/stamps/{id}` 의 `missionManuscript` | 🔁 |
| 4 | 미션 응답: 확장문구·`courseCompleted`·`certificateSerial`·`rewards` | 전부 있다 | ✅ |
| 5 | 전자책: `status`·`downloadUrl` | 있다 (`downloadUrl` 은 READY 일 때만, **10분 서명**) | ✅ |
| 6 | 전자책: `cancelable` | 없다 | ❌ |
| 7 | 인증서: `status`·`downloadUrl` | 있다 (+`verifyUrl`) | ⬆ |
| 8 | 진위 확인 7필드 | `serialNo`·`certType`·`status`·`courseName`·`holderMasked`·`issuedAt`·`revokedAt` | ✅ |
| 9 | 보상 `claimable` | 있다 — 서버가 매번 계산 | ✅ |
| 10 | 싱글페이지 9블록 | 있다 | ✅ |
| 11 | 여권 권역→코스→5칸·진행률 | `PassportResponse` | ✅ |
| 12 | 사진: presign → 직접 PUT | `POST /api/uploads/presign` | ⬆ |

---

## 4. 정본 §10 "백엔드에 요구하는 것" 일곱

| # | 정본이 요구 | 실제 | 판정 |
|---|---|---|---|
| 1 | 코스 상세에 자리·후보·좌표·QR 힌트·**원고 미리보기** 한 번에 | 원고 미리보기만 없다 | **백엔드 추가 필요 (H-14)** |
| 2 | tier 별 확장문구·미션 분기 | **있다** — `expansion_phrase`·`mission` 이 tier 축을 갖고, 없으면 `AGE30` 으로 물러난다 | ✅ |
| 3 | 원고·확장문구 다국어 | 없다 | **백엔드 추가 필요 (H-9)** |
| 4 | `GET /api/stamps/{id}` 복귀용 상태 | 있다(`StampStatusResponse` — 단계·`expiresAt`·원고·문구까지) | ✅ |
| 5 | 전자책 종류 구분 | `ebookType` 로 온다(`PILGRIMAGE`·`HOEHYANG`·`PERSONAL`). **`INTERIM` 은 미구현** | 🔁 (H-6) |
| 6 | 인쇄 `cancelable` · 전자책 `downloadable` | `downloadable` 은 있고 **`cancelable` 은 없다** | **백엔드 추가 필요 (신규 FE-1)** |
| 7 | `X-Request-Id` | `RequestIdFilter` | ✅ |

---

## 5. 백엔드 추가 필요 — 5건

감사 H 의 번호를 그대로 쓴다. 새로 나온 것만 `FE-` 번호를 붙였다.

| # | 무엇 | 왜 프론트가 필요로 하나 | 소요 | 없으면 프론트가 하는 일 |
|---|---|---|---|---|
| H-9 | 원고·확장문구 다국어 | FOREIGN 타겟 화면이 한국어로 남는다 | ? (콘텐츠 포함) | 사찰 설명·명상까지만 번역해 보여 준다 |
| H-14 | 코스 상세에 원고 미리보기 | 코스 화면에서 "이 자리에서 무엇을 하나" 를 보여 주려면 자리마다 싱글페이지를 또 불러야 한다(5회) | 4h | 자리를 눌렀을 때만 싱글페이지를 부른다 — **지금도 화면은 만들 수 있다** |
| FE-1 | `PrintOrderResponse.cancelable` | 취소 버튼을 켤지 프론트가 상태 문자열로 판단해야 한다 | 1h | `status === 'REQUESTED'` 로 판단(규칙이 프론트에 복사된다) |
| FE-2 | 홈 전용 묶음 API | 홈에서 API 를 넷 부른다 | 4h | 병렬 호출 — **성능 문제는 아니다** |
| H-6 | 전자책 `INTERIM` 중간본 | 전자책 화면에 종류가 하나 빈다 | 8h | 세 종류만 보여 준다 |

**다섯 중 어느 것도 프론트 착수를 막지 않는다.** H-9 만 외국인 타겟 화면의 완성도를 정한다.

---

## 6. FE-1 착수 조건

정본 §9 의 FE-1 범위는 **스택·클라이언트·디자인 토큰·라우터·인트로·로그인/가입/refresh** 다.

| 필요 | 실제 | 판정 |
|---|---|---|
| `POST /api/auth/signup` | 201 · `UserResponse` | ✅ |
| `POST /api/auth/login` | 200 · `LoginResponse`(`accessToken`·`expiresIn` 1800) | ✅ |
| `POST /api/auth/refresh` | 200 · `LoginResponse` · HttpOnly 쿠키 회전 | ✅ |
| `POST /api/auth/logout` | 200 · `Set-Cookie: Max-Age=0` | ✅ |
| 봉투·에러 형식 | 91문 예외 0 | ✅ |
| `X-Request-Id` | 있다 | ✅ |
| 타입 초안 | `api-types.d.ts` 자동 생성 | ✅ |

**FE-1 착수 가능.** 막는 것이 없다.

한 가지 어긋남을 적어 둔다 — 정본은 "FE-1 완료 시 **개발용 콘솔 삭제**(`dev-console-remove-check.sh` 통과가 닫힘 조건)" 이라고 적었는데,
**개발용 콘솔이 아직 없다.** 패키지도, 보안 설정 줄도, 삭제 확인 스크립트도 없다(`dev-console.md` 는 만들 계획을 적은 문서다).
콘솔을 만들지 않고 프론트로 바로 간다면 FE-1 의 닫힘 조건에서 그 항목을 빼야 한다.

---

## 7. 로그아웃 상태코드 — 정본이 틀린 곳 하나

정본과 손확인 문서 초안이 로그아웃을 204 로 적었는데 실제는 **200 · `Set-Cookie: Max-Age=0`** 이다.
`POST /api/auth/logout` 은 DELETE 가 아니라 POST 라 204 통일 대상이 아니었다. 프론트는 200 을 기대해야 한다.
