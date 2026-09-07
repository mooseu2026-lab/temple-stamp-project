# 챕터 10 — 명세 정합 결과 (2026-09-07)

감사 H 가 "명세대로 되돌릴 값이 분명하다" 고 본 넷과 프론트 요구 하나, 결정 기록 하나를 처리했다.
**엔드포인트 신설 0(91 불변)**, 교체 전 파일은 `docs/audit/ch10-replaced/`.

## 한 줄 요약

ch10: 정합 5/5 · 기록 1/1 · Q 9/9 · all.sh 통과 · 엔드포인트 91 · 매칭율 92%

---

## 1. 항목 여섯

| # | 무엇 | 전 | 후 | 판정 |
|---|---|---|---|---|
| 1 | refresh 토큰 수명 (H-1) | 7일 | **14일** — 쿠키 `Max-Age` 와 `refresh_token.expires_at` 이 같은 설정값 하나를 쓴다 | ✅ |
| 2 | 행동과제 50/30/20 가중 (H-2) | 축이 (구절 × 계층) 하나뿐 | **SITE / VERSE / COMMON 세 풀**, 비어 있지 않은 풀끼리 가중 | ✅ |
| 3 | 확장문구 권역 내 version 중복 금지 (H-3) | 미노출 우선 → LRU | **완화 3단계** — 미노출+버전 미중복 → 미노출 → LRU | ✅ |
| 4 | 전자책 `has_other_face` 제외 (H-5) | 플래그를 보지 않았다 | 재료에서 제외, 그 자리는 "사진 없음". **스냅샷 해시도 함께 달라진다** | ✅ |
| 5 | `PrintOrderResponse.cancelable` (FE-1) | 없음 | 서버 계산(`status == REQUESTED`), 목록·단건. 관리자 응답에는 없음 | ✅ |
| 6 | 인쇄 자격 결정 기록 (H-7) | 기록 없음 → ⛔ | `정리.md` §7-10 에 "본인 READY 전자책 전부, 명세와 의도적으로 다름" | 🔁 |

---

## 2. 되돌린 규칙이 실제로 도는가 — 숫자

| 무엇 | 잰 방법 | 결과 |
|---|---|---|
| refresh 14일 | 로그인 응답의 `Set-Cookie` | `Max-Age=1209600` (14일) |
| 〃 DB | `refresh_token.expires_at` | 발급 + 14일 (JUnit `AuthFlowIntegrationTest`) |
| 하드코딩 7 | grep | **0건** — 쿠키와 DB 가 `app.jwt.refresh-days` 하나에서 나온다 |
| 50/30/20 가중 | JUnit 5,000회 표본 | SITE 0.50 · VERSE 0.30 · COMMON 0.20 (±5%p 안) |
| 한 풀이 빌 때 | JUnit 1,000회 | VERSE 0.60 · COMMON 0.40 — 남은 풀끼리 다시 나눈다 |
| 세 풀이 다 빌 때 | JUnit | 대체안 1건 + `TASK-POOL-EMPTY` WARN |
| 확장문구 버전 | JUnit — 코스 다섯 자리 | 버전 다섯이 **전부 상이** |
| `has_other_face` | JUnit | 그 자리 `photoKey = null` · 책 생성은 성공 · **해시가 플래그 전후로 다름** |
| `cancelable` | JUnit | REQUESTED true → CONFIRMED false · 관리자 DTO 에는 필드 없음 |
| 스키마 | H17(ALTER 사슬 ↔ `schema.sql`) | 차이 0 (표 36 · 컬럼 306 · 색인 188 · CHECK 47 · 생성 컬럼 5) |
| 폴더 Q | newman | 18요청 · 단언 177 · **실패 0** |
| 전체 | `all.sh` | 초록 6분 32초 — newman 2회 동일 · H1~H18 · S1~S25 · 교착 0 |
| JUnit | 전체 | **279 / 279** (266 → +13) |
| 엔드포인트 | scan · probe | **91** · 어긋남 0 (신설 0) |

---

## 3. 스키마 — ALTER ⑲

```
mission + scope('SITE','VERSE','COMMON') · site_id · site_key · verse_key(생성 컬럼)
       유니크 (verse_no, tier, variant_no) → (scope, site_key, verse_key, tier, variant_no)
       CHECK chk_mission_scope · chk_mission_scope_ref
       색인 idx_mission_verse · idx_mission_pool
```

**순서가 중요했다.** `verse_no` 를 NULL 허용으로 바꾸기 전에 그 컬럼을 받치는 색인을 먼저 깔았다 —
옛 유니크가 `(verse_no, …)` 로 시작해 외래키를 받치고 있어, 그것부터 지우면 `ERROR 1553` 이 난다.
챕터 7 ⑥ 에서 인증서 유니크를 바꿀 때 밟았던 자리라 이번에는 먼저 피했다.

---

## 4. 정본과 다르게 한 것 셋 — 이유와 함께

정본 ch10 §2-2 를 그대로 따르면 돌지 않는 자리가 있어 다르게 했다. 셋 다 규칙의 뜻은 그대로다.

| 정본 | 실제 | 왜 |
|---|---|---|
| 유니크 `(verse_no, tier, variant_no)` **유지** + SITE 는 `(site_id, tier, variant_no)` 별도 유니크 | **하나로 합쳤다** — `(scope, site_key, verse_key, tier, variant_no)` | 정본의 `site_key = IFNULL(site_id,0)` 를 그대로 쓰면 VERSE·COMMON 행이 전부 `site_key=0` 이 되어 한 유니크에 묶인다. 구절 1~5 가 각각 variant 1 을 갖는 지금 데이터가 그 자리에서 깨진다. 축 셋을 다 넣으면 그 문제 없이 세 종류를 각각 지킨다 |
| COMMON 과제는 "구절 무관" | `verse_no` 를 **NULL 허용**으로 바꿨다 | NOT NULL 을 유지하면 COMMON 행에도 아무 구절 번호나 박아야 한다. 데이터가 거짓말을 하게 된다 |
| CSV 반입 양식에 `scope`·`site_name` 열 추가 | **하지 않았다** | 챕터 8 CSV 반입기는 `manuscript`(원고 체계) 전용이고 `mission` 을 받지 않는다. 정본이 "아니면 관리자 등록 API 에 scope 필드만" 이라고 둔 갈래를 택했다 |
| 관리자 **목록** 응답에 `scope`·`siteName` | **자리가 없다** | 미션은 `POST /api/admin/contents/missions` 하나뿐이고 목록 API 가 없다. 만들면 엔드포인트 신설이라 금지다. 등록 요청에 `scope`·`siteId` 를 더하는 데까지 했다 |

---

## 5. 이번에도 도구가 먼저 틀렸다

| 무엇 | 어떻게 드러났나 |
|---|---|
| **앱이 옛 설정으로 떠 있었다** | 폴더 Q 가 `Max-Age=604800`(7일)을 잡았다. 소스도 빌드 산출물도 14 인데 **실행 중인 JVM 만 옛 값**이었다. 재기동하니 1209600. 검사가 없었으면 "설정은 14인데 서버는 7" 인 채로 지나갔다 |
| Q 폴더가 틀린 문을 두드렸다 | 싱글페이지는 `/api/sites/{id}/page` 인데 `/api/sites/{id}` 를 불렀고, 문구 필드도 `phrase` 가 아니라 `expansionPhrase` 였다. 9건·6건을 고쳤다 |
| 테스트가 TINYINT 를 넘겼다 | `variant_no` 에 900+ 를 넣어 `MysqlDataTruncation`. 100+ 로 바꿨다 |

Q02 의 단언을 "20회에서 과제가 한 종류로 고이지 않는다" 로 둔 것도 같은 이유다.
좁은 표본으로 50/30/20 을 단언하면 **우연에 흔들리는 검사**가 된다 — 정확한 비율은 JUnit 이 5,000회로 잰다.

---

## 6. 감사 H 재집계

| 판정 | 전 | 후 |
|---|---:|---:|
| ✅ | 62 | **66** |
| ⬆ | 14 | 14 |
| 🔁 | 12 | **13** |
| ⛔ | 13 | **8** |
| ❓ | 9 | 9 |
| **매칭율** | **87%** | **92%** |

닫힌 다섯 — H-1(refresh 14일) · H-2(가중) · H-3(version 중복) · H-5(has_other_face) 는 ⛔ → ✅,
H-7(인쇄 자격)은 결정 기록이 생겨 ⛔ → 🔁.

남은 ⛔ 여덟은 감사 H §11 에서 "유지" 를 권한 것들이다 — EPUB · INTERIM 중간본 · 명상 등록 API ·
원고 다국어 · 상태코드 셋 · EXIF.

---

## 7. [질문]

| # | 질문 | 메모 |
|---|---|---|
| 1 | COMMON 과제를 **AGE30 에만** 넣었다. 다른 계층은 COMMON 풀이 비어 SITE·VERSE 둘로만 가중된다 | 정본이 "COMMON 5편" 이라 한 것을 그대로 지켰다. 계층마다 채우려면 35편이 되고 그건 콘텐츠 트랙의 몫이다 |
| 2 | SITE 과제는 아직 한 편도 없다 | 콘텐츠가 들어오기 전에는 모든 사찰에서 VERSE·COMMON 두 풀로만 돈다. 규칙은 이미 돌고 있고 내용만 비었다 |
| 3 | 미션 목록 API 를 만들 것인가 | 지금은 등록만 있어 관리자가 무엇이 들어갔는지 화면으로 못 본다. 엔드포인트 신설이라 챕터 10 범위 밖으로 두었다 |
