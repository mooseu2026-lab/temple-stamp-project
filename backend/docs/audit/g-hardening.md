# 감사 G 보강 — 배포 전 필수 셋 + 계약 통일 (2026-09-07)

감사 G 가 모아 둔 것 중 **배포 전 필수 셋**과 **DELETE 상태코드 통일**을 고쳤다.
교체 전 파일은 `docs/audit/g-hardening-replaced/`, 실행 원문은 `backend/docs/verify/all-result.txt`.

## 한 줄 요약

g-hardening: 필수 3/3 · DELETE 204 3/3 · all.sh 통과 · 첫실행 7분 19초 · H17 차이 0

---

## 1. 폰트 라이선스 (STEP 1)

`NotoSansKR-Regular.ttf` 는 SIL OFL 1.1 이고 jar 에 실려 배포된다. OFL 은 **사본 동봉**을 요구하는데(§2) 저장소에 원문이 없었다.

| 무엇 | 값 |
|---|---|
| 넣은 파일 | `src/main/resources/fonts/OFL.txt` (4,387 bytes) |
| 저작권 헤더 | `Copyright 2014-2021 Adobe (http://www.adobe.com/), with Reserved Font Name 'Source'` — 폰트 파일에 박힌 메타데이터에서 그대로 옮겼다 |
| jar 포함 확인 | `./gradlew bootJar` 로 만든 jar 를 풀어 `BOOT-INF/classes/fonts/OFL.txt` 존재 확인 |
| 문서 | 인수인계 §1 라이선스 열에 경로를 적었다 |

폰트를 빼는 선택지는 없다 — 빼면 PDF 한글이 예외도 경고도 없이 □ 로 나간다.

---

## 2. `refresh_token` 색인 (STEP 2)

청소기가 5분마다 만료 토큰을 지우는데, 그 질의가 표 전체를 훑고 정렬하고 있었다.

| 무엇 | 전 | 후 |
|---|---|---|
| EXPLAIN `type` | **ALL** | **range** |
| 쓰는 색인 | 없음 | `idx_refresh_token_expires` |
| 검사한 행 | **2,037** | **1** |
| Extra | `Using where; Using filesort` | `Using where` |

```sql
ALTER TABLE refresh_token ADD INDEX idx_refresh_token_expires (expires_at);
```

`schema.sql`(새로 세우는 길) · `alter-ch9.sql` ⑱(이미 있는 DB 를 올리는 길) · 개발 DB 셋 다 반영했다.
**H17(ALTER 사슬 ↔ `schema.sql`)을 다시 돌려 색인 180 개로 늘어난 채 차이 0** 을 확인했다 — 한쪽만 넣으면 여기서 바로 걸린다.

로그인 한 번에 한 행이 쌓이고 수명이 7일이라, 운영에서는 이 표가 수십만 행이 된다.

---

## 3. 시더 실행법 (STEP 3)

감사 G 의 첫 실행 리허설에서 **처음 보는 사람이 막히는 유일한 지점**이었다.
`SiteSeedImporter` 는 `@Profile("seed")` 라 그냥 띄우면 사찰 5곳만 들어가는데, 그 사실이 어느 문서에도 없었다.

| 어디 | 무엇 |
|---|---|
| `README.md` (신설) | 열 줄 안에 ① DB → ② `.env.local` → ③ **`SPRING_PROFILES_ACTIVE=seed ./gradlew bootRun`** → ④ 평소 기동 → ⑤ `all.sh` |
| `정리.md` §2-0 (신설) | 같은 절차 + 건너뛰면 무엇이 깨지는지 + 카카오 키가 비면 좌표 없는 사찰이 106곳이 된다는 것 |

### 리허설 재실행 — 저장소를 복사하고 `.env.local` 을 지운 상태에서

| 단계 | 결과 |
|---|---|
| 복사(748파일·22 MB) → DB 생성 → `.env.local` 작성 | README 그대로 |
| `SPRING_PROFILES_ACTIVE=seed` 기동 | `SEED DONE sites=110 courses=12 candidates=213` |
| 예열 30요청 → `DB_NAME=temple_stamp_first bash backend/docs/verify/all.sh` | **초록 — 6분 17초** |
| **처음부터 초록까지 총** | **7분 19초** |

감사 G 때 잰 3분 41초는 `run-all.sh` 한 번까지였다. 지금은 `all.sh` 가 newman 2회 · DB · 보안 · 교착 재현까지 도는 값이다.

---

## 4. DELETE 다섯을 204 로 통일 (STEP 4)

둘만 204 였고 셋은 `200 + 봉투` 였다. 같은 행위가 자리마다 다른 답을 내면 프론트가 자리마다 다르게 분기한다.

| 엔드포인트 | 전 | 후 |
|---|---|---|
| `DELETE /api/users/me` | 204 | 204 (그대로) |
| `DELETE /api/print-orders/{id}` | 204 | 204 (그대로) |
| `DELETE /api/thinkbox/{id}` | 200 + 봉투 | **204 · 본문 없음** |
| `DELETE /api/users/me/agreements/{type}` | 200 + **남은 목록** | **204 · 본문 없음** |
| `DELETE /api/admin/sites/{siteId}/elements/{code}` | 200 + 봉투 | **204 · 본문 없음** |

함께 고친 곳 — 컨트롤러 3 · 컬렉션 단언 3(`U08a`·`R04`·`S12`, "본문이 없다" 단언 추가) ·
JUnit 2(`AdminSiteElementIntegrationTest`·`RecordIntegrationTest`) · `endpoints.md` · `frontend-handoff.md` · `정리.md` §4-3·§4-16(신설).

**프론트가 고칠 것 둘.** ① 세 곳의 성공 판정을 `status === 204` 로 — 204 에는 본문이 없어 `res.json()` 이 파싱 오류를 낸다.
② 약관 철회 뒤 남은 목록이 필요하면 `GET /api/users/me/agreements` 를 한 번 더 부른다.

### 여기서 도구가 나를 잡았다

`endpoints.md` 표의 경로 뒤에 `**204**` 를 적었더니 `open-endpoint-probe.js` 가 **다섯 줄을 못 읽어 91 을 86 으로 셌고**,
`all.sh` 가 보안 84/1 로 그 자리에서 멈췄다. 두 가지를 몰랐던 탓이다 —
`endpoints.md` 는 **`endpoint-scan.js` 가 생성하는 파일**이라 손으로 고쳐도 다음 스캔에 지워지고,
프로브는 `| N | METHOD \`경로\` | 권한 |` 모양으로 표를 읽는다.

그래서 상태 표기를 **표가 아니라 생성기의 문단**으로 옮겼고(`## 삭제의 응답`), 왜 그렇게 했는지를 스캐너 주석에 남겼다.
개수 단언이 없었다면 이 변경은 "어긋남 0" 을 유지한 채 분모만 줄인 상태로 지나갔을 것이다.

---

## 5. `all.sh` (STEP 5)

검증 한 벌을 사람이 기억하지 않아도 되게 한 문으로 모았다.

```
run-all.sh 1회차 → 2회차 → 채점 파일 바이트 비교 → db-check(H1~H18) → security-check(S1~S25) → 교착 재현
```

**하나라도 실패하면 그 자리에서 멈추고 종료코드 1** 이다. 앞 단계가 깨진 채로 뒤를 돌리면 뒤의 숫자가 무엇을 말하는지 알 수 없어서다.
마지막에 여섯 줄 요약을 낸다. `정리.md` §5 의 첫 줄을 "`all.sh` 가 초록인지 본다" 로 바꿨다.

| 항목 | 결과 |
|---|---|
| ① newman 1회차 | 통과 |
| ② newman 2회차 | 통과 |
| ③ 2회 동일 | 채점 파일 **바이트 동일** |
| ④ DB H1~H18 | 어긋남 0 |
| ⑤ 보안 S1~S25 | 통과 85 · 실패 0 |
| ⑥ 교착 재현 | 교착 0 · 5xx 0 |
| 걸린 시간 | 6분 13초 |

앱이 안 떠 있으면 첫 줄에서 멈춘다. **예열된 뒤에 돌려야 한다** — 기동 직후에는 교착이 아니라 커넥션 획득 실패로 500 이 난다(§5-6).

---

## 6. 검증 (STEP 6)

| 무엇 | 결과 |
|---|---|
| JUnit | **266 / 266** · 실패 0 (DELETE 204 로 단언 둘 갱신) |
| `all.sh` | **초록** · 6분 13초 |
| 엔드포인트 | 스캔 91 · 프로브 91 · 어긋남 0 (신설 0) |
| H17 | ALTER 사슬 ↔ `schema.sql` **차이 0** (색인 179 → 180) |
| 첫 실행 리허설 | README 대로 **7분 19초**에 초록 |
| bootJar | `BOOT-INF/classes/fonts/OFL.txt` 포함 |

---

## 7. [질문]

| # | 질문 | 메모 |
|---|---|---|
| 1 | `all.sh` 를 CI 에 걸 것인가 | 지금은 사람이 부른다. 앱이 떠 있어야 하고 예열이 필요해 CI 에서는 기동·예열 단계가 앞에 붙는다 |
| 2 | 약관 철회가 204 가 되면서 프론트가 목록을 다시 부른다 — 왕복이 하나 는다 | 계약 일관성과 왕복 하나를 맞바꿨다. 화면이 실제로 목록을 바로 쓰는지 프론트 설계에서 확인하면 좋겠다 |
