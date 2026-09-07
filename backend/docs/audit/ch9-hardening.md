# 챕터 9 보강 — 최종 점검 F 가 막은 셋을 고쳤다 (2026-09-07)

최종 점검 F 는 배포 진입 조건 12개 중 3개를 ❌ 로 두고 멈췄다. 뿌리는 둘이었고, 여기에 설정 하나를 더해 셋을 고쳤다. 실행 원문은 `docs/audit/ch9-hardening-logs/`, 교체 전 파일은 `docs/audit/ch9-hardening-replaced/` 에 있다.

## 한 줄 요약

newman 515/515 ×2, 기능 ✅ 58 · ⚠ 0 · ❌ 0 · — 0 — 교착 재현 시험이 **교착 0 · 5xx 0** 이 됐다(고치기 전 같은 조건에서 교착 8 · 사용자 500 5건).

---

## 1. 결함 ② — 청소기와 미션 제출의 교착

### 무엇이 문제였나

청소기의 세션 만료는 조건을 스캔하며 한 번에 지우는 UPDATE 였다. 그러면 **보조 색인(idx_stamp_pending)을 먼저, PRIMARY 를 나중에** 잠근다. 미션 제출의 `markCompleted` 는 `WHERE stamp_id = ?` 라 **PRIMARY 를 먼저** 잠근다. 두 순서가 엇갈려 교착이 나고, 진 쪽이 사용자 요청이면 `DeadlockLoserDataAccessException` 이 그대로 500 `COMMON-5000` 으로 나갔다.

평소 검증에서 한 번도 보이지 않은 이유는 하나다. **만료 대상이 없으면 일괄 UPDATE 가 아무 행도 잠그지 않는다.** 검증 세트는 앞뒤로 정리를 하므로 만료 대상이 늘 0이었다. 운영은 반대다 — 5분마다 도는데 버려진 세션은 늘 쌓인다.

### 어떻게 고쳤나

고르기와 갱신을 나눴다. 고르기는 잠그지 않고 id 만 PK 순으로 읽고, 갱신은 그 id 들만 PK 로 집는다.

| 무엇 | 전 | 후 |
|---|---|---|
| 질의 | `UPDATE stamp SET verify_status='EXPIRED' WHERE verify_status IN (…) AND gps_verified_at < …` 하나 | `findStaleIds`(SELECT · ORDER BY stamp_id · LIMIT 200) → `expireByIds`(UPDATE … WHERE stamp_id IN (…)) |
| 잠금 순서 | 보조 색인 → PRIMARY | **PRIMARY 만** — 미션 제출과 같은 순서 |
| 트랜잭션 | 전체 하나(`expireStale` 에 `REQUIRES_NEW`) | **묶음마다 하나**(`StampExpireBatch.expireBatch` 가 `REQUIRES_NEW`) |
| 진행 | — | `afterId` 로 앞으로만 간다. 갱신이 0행인 묶음이 나와도 같은 자리를 다시 읽지 않는다 |
| 한 바퀴 상한 | 없음 | 200 × 100 = 20,000건. 나머지는 다음 바퀴가 가져간다 |

`StampExpireBatch` 를 따로 둔 이유는 `@Transactional` 이 프록시라 **같은 클래스 안에서 부르면 걸리지 않기** 때문이다. `StampExpireService` 가 같은 이유로 `StampService` 에서 떨어져 나온 전례가 있다.

처리 건수 로그(`housekeeping … session=N`)와 db-check H18 채점은 그대로다 — 세는 방식이 아니라 잠그는 방식만 바꿨다.

### 재현 시험을 상시화했다

`backend/docs/verify/load/deadlock-repro.sh` — 만료 대상 3,000건을 만들어 두고 동시 20 × 60초 부하에 청소기를 2초마다 돌린다. **교착 0 · 5xx 0** 이어야 통과한다.

| 무엇 | 고치기 전(최종 점검 F) | 고친 뒤 |
|---|---|---|
| 만료 대상 | 3,000건 | 3,000건 |
| 부하 | 동시 20 × 60초 | 동시 20 × 60초 |
| 요청 | 23,968 | 17,720 |
| 교착 | **8건** | **0건** |
| 사용자 500 | **5건** | **0건** |
| 평균 응답 | 50 ms | 68 ms |

되돌릴 도장이 모자라면 스크립트가 먼저 30초 부하를 최대 네 번까지 돌려 대상을 만든다. **대상이 없으면 "교착 0" 이 나오는데 그것은 통과가 아니라 재지 못한 것**이라, 이 시험이 조용히 무의미해지는 길을 막아 뒀다. 끝나면 `cleanup.sql` 로 부하가 만든 계정을 치운다(계정 이름을 `reg-load-…@test.com` 으로 지어 기존 정리가 그대로 지운다).

규칙은 `정리.md` §6-24 에 넣었다 — **일괄 UPDATE 는 PK 순 배치로 나눈다.**

---

## 2. 결함 ① — ALTER 파일에 빠진 것

`schema.sql` 로 새로 세운 DB 와 ALTER 로 올린 DB 가 갈라져 있었다. 최종 점검 F 는 색인 하나를 찾았고, 이번에 넣은 상시 검사가 **표 하나를 더** 찾았다.

| 무엇 | 증상 | 고친 곳 |
|---|---|---|
| `idx_storage_orphan_pending` | `schema.sql` 은 `(status, enqueued_at)`, ALTER 로 올린 DB 는 `(deleted_at, enqueued_at)` 그대로 | `alter-ch9.sql` ⑰-b 두 줄 + 개발 DB 적용 |
| **`cert_serial` 표** | `schema.sql` 에만 있고 **어떤 ALTER 파일도 만들지 않았다.** ALTER 로 올린 운영 DB 에는 표가 생기지 않고, 첫 완주가 나오는 날 인증서 발행에서 터진다 | `alter-ch7.sql` ⑥ (그 표는 챕터 7 이 만든 것이다) |
| 네 ALTER 파일의 `USE temple_stamp_project;` | 다른 스키마에 올리려고 불러도 파일이 스스로 개발 DB 로 돌아간다 | 네 파일에서 제거 — 부르는 쪽이 `mysql -u… -p <스키마> < 파일` 로 정한다 |

### 재발 방지 — db-check H17

임시 스키마 둘을 세워 `information_schema` 일곱 항목을 전수 비교하고 끝나면 지운다.

| 스키마 | 어떻게 세우나 | 뜻 |
|---|---|---|
| `temple_stamp_h17_chain` | 챕터 7 이전 `schema.sql` + `alter-ch7` → `ch1-withdraw` → `ch8` → `ch9` | 운영이 올라가는 길 |
| `temple_stamp_h17_fresh` | 지금 `schema.sql` 하나 | 새 환경이 세워지는 길 |

결과: 표 36 · 컬럼 302 · 인덱스 179 · 외래키 58 · CHECK 45 · 생성 컬럼 3 · 문자셋 36 — **차이 0**.

이 검사를 만들며 도구 결함 하나를 먼저 잡았다. 두 스키마를 **다른 문자셋으로** 올리면 CHECK 절의 문자열 리터럴에 `_euckr` 와 `_utf8mb4` 가 각각 찍혀 26건이 다르다고 나온다 — 실제로는 같은 제약이다. 처음 돌렸을 때 이것으로 헛것을 봤고, 두 스키마를 같은 문자셋으로 올리도록 고쳤다.

---

## 3. 결함 ③ — 설정이 거짓말하지 않게

`ObjectStorageClient` 는 `storage.provider` 를 한 번도 읽지 않는다. 그런데 `application-prod.yml` 에는 `provider: s3` 라고 적혀 있었다. 설정만 보고 "운영은 S3 로 간다" 고 읽으면 배포한 뒤에야 파일이 서버 디스크에 쌓이는 것을 알게 된다.

구현이 없는 값을 조용히 무시하느니 **그런 값으로는 뜨지 않는 편이 낫다.** 같은 원칙으로 하나 더 막았다.

| 검사 | 언제 | 메시지 |
|---|---|---|
| `storage.provider` 가 local 이 아니면 | 항상 | "storage.provider=s3 는 미구현 — local 만 지원합니다." |
| `ebook.public-base-url` 에 localhost 가 들어 있으면 | prod 프로파일에서만 | "ebook.public-base-url 이 아직 localhost 입니다: …" |

두 번째가 더 무섭다. 이 값은 **인증서 QR 이 가리키는 주소**다. 로컬 값이 남은 채 배포되면 인증서는 정상 발행되고 PDF 도 멀쩡히 나오는데 찍힌 QR 만 localhost 를 가리킨다. 종이로 나간 뒤에는 되돌릴 수 없다.

`application-prod.yml` 의 `provider` 는 `local` 로 바꿨다. **S3 는 배포 후 별도 결정**이고, 붙일 때 `put·getBytes·exists·delete` 넷을 SDK 로 바꾸고 이 검사를 함께 푼다.

---

## 4. 문서

| 무엇 | 어떻게 |
|---|---|
| 배포 체크리스트 12번 | "파일 저장소 백업 — **DB 덤프와 같은 시각 일 1회 · 보관 30일 · 복구 리허설에 파일 포함**" 으로 구체화. 시각이 어긋나면 DB 에는 있는 키의 파일이 백업에 없다 |
| 탈퇴 유예 기간 | **없음(결정 완료)** — `인수인계` §7 과 `ch1-withdraw.md` §7 둘 다 닫았다. 즉시 익명화이고, 되돌리고 싶은 사람은 새로 가입한다 |
| `정리.md` | §5-1 덧(색인·`cert_serial`·H17) · §5-2 6-b(`PUBLIC_BASE_URL`) · §5-2-a(저장소) · §5-5(백업 정책) · §6-24(새 규칙) · §7-1·§7-6 |

---

## 5. 검증

| 무엇 | 결과 |
|---|---|
| JUnit | **266 / 266** · 실패 0 (배치 만료 검사 ⑦ 신설 — 3,000건 → 15묶음 → 전부 EXPIRED) |
| newman run-all.sh 2회 | 481+34 요청 · 2,211+177 단언 · 실패 0 · **채점 파일 바이트 동일** |
| runtime-check | ERROR 0 · 미참조 ErrorCode 0 · 미참조 매퍼 메서드 0 |
| security-check | S1~S25 세부 85항 **전부 통과 · 실패 0** |
| db-check | H1~H18 어긋남 0. **H17 차이 0**(새 방식) · H18 대상=처리 · H12 는 정본 순서로 다시 재어 **3/3 실측** |
| deadlock-repro.sh | 대상 3,000건 · 동시 20 × 60초 · **교착 0 · 5xx 0** |
| prod 드라이런 | 세 갈래 **통과 12 · 실패 0** — s3 차단 · localhost 차단 · 정상값 ERROR 0 |
| 엔드포인트 | 스캔 91 · 프로브 91 · 어긋남 0 (신설 0) |

### JUnit ⑦ 이 무엇을 보는가

3,000건을 넣고 `expireStale()` 을 부른 뒤 셋을 본다 — ① 3,000건이 전부 EXPIRED 인가, ② `StampExpireBatch.expireBatch` 가 **15번 이상** 불렸는가(3,000 ÷ 200), ③ 다음 바퀴의 처리 건수가 0인가. ②가 이 검사의 요점이다. 결과만 보면 한 번에 지웠는지 나눠 지웠는지 알 수 없고, 나눠 지우는 것이 이번 수정의 전부다.

---

## 6. [질문]

| # | 질문 | 메모 |
|---|---|---|
| 1 | 부하가 만든 계정을 `reg-load-…@test.com` 으로 지어 기존 정리가 지우게 했다. 검증 계정 이름 규칙을 문서에 못 박을 것인가 | 지금은 `cleanup.sql` 의 `LIKE` 목록이 사실상의 규칙이다 |
| 2 | 한 바퀴 상한 20,000건이 맞는가 | 5분마다 20,000건이면 하루 570만 건이다. 실제로 밀릴 일은 없지만 상한 자체는 임의로 정했다 |
