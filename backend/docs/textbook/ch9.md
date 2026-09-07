# 챕터 9 — 전자책 · 인증서 PDF · 인쇄주문 · 청소기 (정본)

작성 2026-09-07 · 전제: 전체 점검 E 통과(기능 49 ✅ · API 86 · JUnit 222 · newman 427×2) · 마지막 코드 챕터 · 이 문서가 정본이다.

---

## 0. 이 챕터의 성격

저장소에 `ebook`·`print_order`·`storage_orphan` 표와 전자책·인쇄 관리자 API 5개, 인쇄주문 사용자 API 4개(그중 GET/DELETE 미검증)가 **이미 있다**. 없는 것은 ① 실제 PDF를 만드는 부분 ② 인증서 PDF와 1장 조회 ③ 청소기(파일·토큰) ④ 관리자 사용자 목록 ⑤ 점검 E가 남긴 잔여 7건이다.

작업 갈래:
1. **대조**: ebook·print_order 표, 관리자 5·사용자 4 API를 §2~§4 규칙과 대조(교체/삭제/유지)
2. **신설**: 엔드포인트 **최대 5개**(§7 표) — 이미 있으면 신설 아님. 86 → ≤91
3. **스택 추가 1건 허용**: PDF 라이브러리(§1-2). 그 외 의존성 추가 금지
4. **잔여 7건**(§8)은 STEP 0

**절대 규칙(변경 없음)**: 보안·개인정보·동의·권한 검사 제거 금지 / ErrorCode 번호·생성자 순서 고정 / 명세 §7 밖 코드 신설 금지 / 좌표 미수신 / 잠금은 "있는 행"에만(챕터 8 규칙) / 교체·삭제 원본은 `backend/docs/audit/ch9-replaced/`.

---

## 1. 낱말 사전 · 스택

### 1-1 낱말

| 낱말 | 뜻 |
|---|---|
| 전자책(ebook) | 사용자 한 명의 순례 기록(도장·사진·문장·그날의 확장문구·생각상자·명상·인증서)을 묶은 PDF 한 권. **개인 소장** — 완주 여부와 무관, 비공개 생각상자도 들어간다 |
| 스냅샷 해시 | 전자책에 들어갈 재료(도장 id·사진 키·원고 id·생각상자 id·인증서 번호)를 정렬해 SHA-256 한 값. 같으면 같은 책 |
| 인증서 PDF | 인증서 1장을 PDF로. 번호·이름(본인용이라 전체)·코스·날짜·**진위 확인 QR**(zxing, verify URL) |
| 인쇄주문 | READY인 전자책을 종이책으로 주문. 배송정보는 별도 표 |
| 청소기(housekeeping) | 5분마다 도는 스케줄러 하나. 파일 삭제 큐 소비 + 만료 토큰 삭제 |
| storage_orphan | 삭제할 파일 키의 큐(탈퇴·사진 교체·전자책 재생성이 넣는다) |
| presigned URL | 저장소 파일을 짧은 시간(10분) 내려받을 수 있는 서명된 주소. 서버는 파일 본문을 흘려보내지 않는다 |

### 1-2 스택 추가(이 챕터에 한해 1건)

- **PDF: OpenPDF 2.x**(LGPL/MPL). 저장소에 다른 PDF 라이브러리가 이미 있으면 그것을 유지하고 보고.
- **한글 폰트 임베드 필수**: `src/main/resources/fonts/NotoSansKR-Regular.otf`(OFL). 임베드 없으면 한글이 □로 나온다 → JUnit이 PDF 텍스트를 추출해 한글 포함 여부를 검사(§9-2).
- 이미지: 사진은 저장소에서 읽어 **긴 변 1200px·JPEG 품질 80**으로 줄여 삽입(원본 그대로 넣으면 60장에 수백 MB).

---

## 2. 전자책 규칙 (`EbookService`)

### 2-1 표 `ebook` — 대조 대상

| 컬럼 | 규칙 |
|---|---|
| `id`·`user_id` | |
| `status` | `REQUESTED → READY / FAILED`. FAILED는 `fail_reason`(≤200) |
| `snapshot_hash` | 64자. `(user_id, snapshot_hash)` 유니크 |
| `file_key` | `EBOOK/{userId}/{ebookId}.pdf`. READY일 때만 값 |
| `page_count`·`byte_size` | READY일 때 |
| `requested_at`·`ready_at` | |

### 2-2 생성 흐름 — 비동기

```
POST /api/ebooks
  1. 재료 수집(§2-3) → 스냅샷 해시
  2. 같은 (user, hash)에 READY 있음 → 200 그 행(멱등, 새로 만들지 않음)
  3. 같은 (user, hash)에 REQUESTED 있음 → 202 그 행(중복 요청 아님)
  4. 없음 → REQUESTED 행 생성 → 202 {id, status:REQUESTED}
  5. 사용자당 REQUESTED 동시 1건 상한 → 초과 409 EBOOK-4092
  6. 사용자당 하루 생성 3건 상한(READY 기준) → 초과 429 EBOOK-4290

청소기 스케줄러가 REQUESTED를 집어(오래된 것부터, 한 번에 3건) PDF를 만든다:
  성공 → 파일 저장 → READY (file_key·page_count·byte_size·ready_at)
  실패 → FAILED + fail_reason, 파일 키 없음. 재시도는 사용자가 다시 POST(해시 같아도 FAILED는 재사용 안 함)
  같은 사용자의 이전 READY 책은 유지(개인 소장). 단 사용자당 READY 최대 5권 — 넘으면 가장 오래된 것 삭제 + file_key를 storage_orphan에
```

- 생성 중 잠금: `ebook` 행을 `FOR UPDATE`(있는 행) — 스케줄러 2개가 같은 행을 집지 않게. 단일 인스턴스 전제, 다중 인스턴스는 정리.md §5 미결(ShedLock).
- 도장이 0개면 → 400 `EBOOK-4001` "기록이 없어 전자책을 만들 수 없습니다".

### 2-3 재료와 페이지

| 페이지 | 내용 | 출처 |
|---|---|---|
| 표지 | 이름·기간(첫 도장~마지막 도장)·코스 수·도장 수 | users·stamp |
| 코스별 장 | 코스명·권역·진행률 | course·progressOf |
| 도장 페이지(도장마다 1쪽) | 사찰명·발행일·**그날의 확장문구**(`stamp.ext_manuscript_id`, 퇴역돼도 그대로)·사진(있으면)·문장(있으면) | stamp·manuscript·photo |
| 생각상자 | 비공개 포함 전부, 작성일 순 | thinkbox |
| 명상 | 기록 요약(횟수·총 시간) | meditation_log |
| 인증서 | VALID 인증서 번호·종류·발행일 목록(회수본 제외) | certificate |
| 판권 | 생성일시·스냅샷 해시 앞 8자·"개인 소장용" | |

- 사진 파일이 저장소에 없으면(삭제 큐 처리 등) 빈 자리에 "사진 없음" — 실패 아님.
- 텍스트는 플레인. 원고는 챕터 8에서 HTML을 거부했으므로 이스케이프 걱정 없음.

### 2-4 조회·다운로드

- `GET /api/ebooks` 본인 목록(READY·REQUESTED·FAILED 전부, 최신순).
- `GET /api/ebooks/{id}` 본인 1권: status·pageCount·byteSize·`downloadUrl`(READY일 때만 presigned 10분, 아니면 null). 남의 것 404 `EBOOK-4040`.
- 서버가 PDF 본문을 직접 스트리밍하지 않는다(presign 원칙, 챕터 6과 동일).

### 2-5 탈퇴 연쇄 보강(챕터 1 보강 정본 §3에 추가)

| 대상 | 처리 |
|---|---|
| ebook | 행 삭제 + file_key 전부 storage_orphan |
| print_order | REQUESTED → CANCELED. CONFIRMED 이후는 그대로 두고 `needs_review=true`(실물 진행 중) |

---

## 3. 인증서 PDF · 1장 조회 (`CertificateService` 확장)

- `GET /api/certificates/{id}` — 본인 1장: 번호·종류·상태·발행일·회수일·코스명·`downloadUrl`. **VALID일 때만** presigned 10분, REVOKED면 `null`(에러 아님).
- PDF는 **첫 조회 때 생성**(지연) → `CertificateMapper.updateFileKey`(챕터 7부터 남겨 둔 자리) → 이후 재사용. 키 `CERT/{userId}/{certificateId}.pdf`.
- 내용: 번호(크게)·이름(전체)·종류(코스/회향)·코스명·발행일·**QR**(zxing 3.5.4, `{public-base-url}/verify/{번호}`)·"진위 확인은 QR 또는 번호로".
- 회수 시 파일은 지우지 않는다(옛 파일이 돌아다녀도 QR이 REVOKED를 답한다 — 진위 확인이 정본).
- 회향 인증서(HH)는 문구만 다르다(템플릿 2종).

---

## 4. 인쇄주문 규칙 (`PrintOrderService`) — 대조 대상

### 4-1 상태 기계(저장소 이름 유지, 없는 상태만 추가)

```
REQUESTED ──관리자 확인──▶ CONFIRMED ──▶ PRINTING ──▶ SHIPPED(송장) ──▶ DONE
    │
    └──사용자 취소(DELETE)──▶ CANCELED        ※ REQUESTED에서만. CONFIRMED 이후 사용자 취소 409 PRINT-4090
관리자 취소: REQUESTED·CONFIRMED에서 가능(사유 필수). PRINTING 이후 취소 불가 409.
```

| 규칙 | |
|---|---|
| 주문 대상 | 본인 **READY** 전자책만. 아니면 400 `PRINT-4001` |
| 수량 | 1~5, 부수당 가격은 `print_price` 설정값(결제 연동은 범위 밖 — `정리.md` §7 미결, 지금은 "관리자 확인 = 입금 확인") |
| 배송정보 | `print_order_address` 별도 표(recipient 1~50·phone·address 1~200·memo ≤200), 목록 API에 미포함(reward_claim과 동일 원칙) |
| `GET /api/print-orders/{id}` | 본인만, 배송정보 포함, 남의 것 404 `PRINT-4040` |
| `DELETE /api/print-orders/{id}` | 본인·REQUESTED만 → 204, CANCELED(행 유지). 그 외 409 |
| 관리자 5 API | 목록(status 필터·`needs_review` 우선)·확인·인쇄중·발송(송장 ≤50)·취소(사유). 표 밖 전이 409 `PRINT-4090` |
| 전자책 삭제와의 관계 | 주문이 걸린 전자책은 §2-2 "READY 최대 5권" 삭제 대상에서 **제외** |

---

## 5. 청소기 (`HousekeepingScheduler`) — 5분마다 하나

| 작업 | 규칙 |
|---|---|
| ① storage_orphan 소비 | 한 번에 100건, 오래된 것부터. 삭제 성공 → 행 삭제. 실패 → `retry_count+1`, 5회 초과 → `status=FAILED` + `needs_review`(사람이 본다). 저장소에 이미 없는 키는 성공으로 본다 |
| ② refresh_token 정리 | **만료 시각이 24시간 이상 지난 행** 삭제(폐기 여부 무관). 폐기됐지만 아직 만료 전인 행은 **남긴다** — 챕터 1 재사용 탐지가 그 행을 봐야 한다. 한 번에 1,000건 |
| ③ ebook REQUESTED 처리 | §2-2, 한 번에 3건 |
| ④ 도장 세션 만료 | 챕터 5의 5분 청소기가 이미 있으면 여기로 **통합**(스케줄러 두 개가 되지 않게) |

- 각 작업은 **각각 트랜잭션**, 한 작업 실패가 다른 작업을 막지 않는다. 실행마다 로그 1줄 `housekeeping orphan={삭제/실패} token={삭제} ebook={ready/failed} session={expired}`.
- 관리자 수동 실행 `POST /api/admin/housekeeping/run`(§7 신설 후보) — 테스트와 운영 복구용. 응답에 같은 숫자.
- 첫 실행 때 refresh_token 1,674행 대부분이 지워진다(점검 E 결함 1).

---

## 6. 관리자 사용자 목록

`GET /api/admin/users?status=&q=&page=` — 기본 `status=ACTIVE`, `?status=DELETED` 명시 시만 탈퇴 계정. 응답: id·email·닉네임·role·status·가입일·마지막 로그인. **비밀번호 해시·토큰·주소 없음.** 탈퇴 계정 행은 익명화된 값 그대로. 점검 S22가 실측 가능해진다.

---

## 7. 엔드포인트 (신설 최대 5 — 이미 있으면 신설 아님)

| # | 메서드 경로 | 인증 | 비고 |
|---|---|---|---|
| 1 | `POST /api/ebooks` | USER | 있으면 §2-2로 대조 |
| 2 | `GET /api/ebooks` · `GET /api/ebooks/{id}` | USER | 목록·1권(둘이 없으면 2개) |
| 3 | `GET /api/certificates/{id}` | USER | 챕터 7 이월 |
| 4 | `GET /api/admin/users` | ADMIN | §6 |
| 5 | `POST /api/admin/housekeeping/run` | ADMIN | §5 |

- 기존 인쇄주문 4 + 관리자 5는 §4로 대조. 미검증 2건(GET/DELETE)은 이 챕터에서 닫힌다.
- `expected-endpoints.txt`를 실제 수로 갱신(≤91), scan = probe = endpoints.md 단언 유지.

---

## 8. STEP 0 — 점검 E 잔여 (반드시 첫 작업)

| # | 항목 | 처리 |
|---|---|---|
| 1 | 헬스 경로 | 정본·정리.md·verify 문서를 `/health`로 정정. 코드 변경 없음 |
| 2 | H10 빈 항목 | H10 = "청소기 뒤 잔량: storage_orphan FAILED 0 · refresh_token 만료+24h 초과 0" 로 채움 |
| 3 | S15 | 살아 있는 인증서로 verify 응답을 **실측**(W 폴더 안, 개인정보 키 정규식) |
| 4 | `@Validated` 13개 | 전 컨트롤러 일괄. 응답 모양 변화 없음을 X 폴더로 확인 |
| 5 | 미사용 코드 | ErrorCode 11·매퍼 7·`isMission()` — 이 챕터 끝에 참조 0이면 삭제(`ch9-unused.md`). `updateFileKey`는 §3에서 쓰임 |
| 6 | 탈퇴 [질문] 잔여 | `ch1-withdraw.md` §7 4건 중 결정된 것(사진·문장 파기, 유예 없음) 닫고, §2-5 연쇄 보강 반영 |
| 7 | 결과 문서 마지막 줄 오기 | `보안 S21/22(S22 ➖)`로 정정 |

---

## 9. 확인포인트 폴더 E · JUnit

### 9-1 폴더 E (newman) — cleanup → E → SQL 채점 → cleanup, 2회 동일

전제: U1이 도장 3개(사진 2·문장 2·EXT 문구 있음)·생각상자 2(비공개 1)·인증서 1장 VALID. 저장소는 테스트 로컬 스토리지(챕터 6과 동일).

| E | 요청 | 기대 |
|---|---|---|
| E01 | U2(도장 0) `POST /api/ebooks` | 400 EBOOK-4001 |
| E02 | U1 `POST /api/ebooks` | 202 REQUESTED |
| E03 | U1 같은 요청 즉시 | 202 **같은 id**(중복 생성 없음) |
| E04 | ADM `POST /api/admin/housekeeping/run` | 200 `ebook.ready:1` |
| E05 | U1 `GET /api/ebooks/{id}` | 200 READY·pageCount ≥ 7·byteSize > 0·downloadUrl 있음 |
| E06 | downloadUrl로 GET(토큰 없이) | 200 `application/pdf`, 첫 4바이트 `%PDF` |
| E07 | U1 `POST /api/ebooks` 다시(재료 불변) | **200 같은 id**(멱등) |
| E08 | U1 사진 1장 교체 → `POST` → run | 202 새 id → READY, 옛 책 유지(READY 2권), 옛 사진 키가 storage_orphan에(SQL) |
| E09 | run 1회 더 | `orphan.deleted ≥ 1`, storage_orphan 잔량 0 |
| E10 | U2 `GET /api/ebooks/{U1 id}` | 404 EBOOK-4040 |
| E11 | U1 `GET /api/certificates/{id}` | 200 VALID·downloadUrl 있음, SQL `file_key` 채워짐(첫 조회 생성) |
| E12 | 같은 요청 | file_key 불변(재생성 없음) |
| E13 | downloadUrl GET → PDF 텍스트에 번호 포함·QR 존재(JUnit로 대체 가능) | |
| E14 | ADM 회수 → `GET /api/certificates/{id}` | 200 REVOKED·downloadUrl **null** |
| E15 | U1 `POST /api/print-orders` (ebook READY, 수량 2, 배송정보) | 201 REQUESTED |
| E16 | `POST` (FAILED/REQUESTED ebook) | 400 PRINT-4001 |
| E17 | U1 `GET /api/print-orders/{id}` | 200, 배송정보 포함 / U2 → 404 |
| E18 | U1 `GET /api/print-orders`(목록) | 배송정보 키 **없음** |
| E19 | U1 `DELETE /api/print-orders/{id}` | 204 → SQL CANCELED(행 유지) → 다시 DELETE 409 |
| E20 | 새 주문 → ADM 확인 → U1 DELETE | 409 PRINT-4090 |
| E21 | ADM 인쇄중 → 발송(송장) → 발송 재시도 | 200 → 200 → 409 |
| E22 | ADM 취소(PRINTING 상태) | 409 |
| E23 | SQL: refresh_token에 만료+25h 폐기행 3·만료+25h 미폐기 2·미만료 폐기 1 주입 → run | `token.deleted:5`, 미만료 폐기 1 **남음** → 그 토큰으로 재사용 탐지 여전히 401(S18 회귀) |
| E24 | ADM `GET /api/admin/users` / `?status=DELETED` | 탈퇴 계정 없음 / 있음(익명화 값) — S22 실측 |
| E25 | U1 탈퇴(U 폴더 흐름) → SQL | ebook 0·file_key들 storage_orphan·print_order REQUESTED→CANCELED |
| E26 | X: USER 토큰으로 admin/housekeeping·admin/users | 403 |

### 9-2 JUnit (실제 MySQL + 로컬 스토리지)

| 클래스 | 반드시 |
|---|---|
| `EbookServiceTest` | 해시 결정성·멱등(200 같은 id)·REQUESTED 1건 상한·하루 3건·READY 5권 초과 시 최고령 삭제+orphan(주문 걸린 책 제외)·FAILED 재요청·**PDF 텍스트 추출에 한글 포함**(폰트 임베드)·사진 리사이즈 ≤1200px·사진 없음은 실패 아님·스케줄러 2스레드 같은 행 중복 처리 0 |
| `CertificatePdfTest` | 첫 조회 생성·재사용·REVOKED downloadUrl null·QR 디코드(zxing)가 verify URL과 일치·HH 템플릿 |
| `PrintOrderServiceTest` | 상태 전이 표 전수·READY 아닌 책 400·사용자 취소 REQUESTED만·배송정보 분리·탈퇴 연쇄 |
| `HousekeepingTest` | orphan 실패 5회 → FAILED+needs_review·없는 키는 성공·토큰 삭제 기준(24h)·폐기-미만료 유지·4작업 독립 트랜잭션(하나 실패해도 나머지 실행) |

### 9-3 테스트 데이터

- 로컬 스토리지 경로는 챕터 6과 동일, cleanup이 파일도 지운다(`EBOOK/`·`CERT/` 접두).
- `cleanup.sql`: print_order_address → print_order → ebook → storage_orphan 순(FK).
- `all-checkpoints.sql`: ebook 상태별 수·orphan 잔량·refresh_token 만료+24h 초과 0·print_order 상태별 수·PDF 파일 존재 여부.
- db-check **H10**(§8-2)·**H16**(ebook·print_order·print_order_address·storage_orphan 제약 = schema.sql).

---

## 10. 함정 표

| # | 함정 | 대책 |
|---|---|---|
| 1 | 한글 폰트 미임베드 → PDF 한글이 □ | 폰트 리소스 + JUnit 텍스트 추출 검사 |
| 2 | 사진 원본 그대로 삽입 → 수백 MB | 1200px·JPEG 80 |
| 3 | 서버가 PDF를 직접 스트리밍 → 스레드·메모리 점유 | presigned URL |
| 4 | 같은 재료로 책이 계속 늘어남 | 스냅샷 해시 유니크 + READY 5권 상한 |
| 5 | 스케줄러 2개가 같은 REQUESTED를 집음 | 있는 행 FOR UPDATE, 단일 인스턴스 전제(§7 미결 ShedLock) |
| 6 | 폐기 토큰까지 지워 재사용 탐지가 눈을 잃음 | 만료+24h만 삭제 |
| 7 | 인쇄 진행 중 전자책이 5권 상한 삭제에 걸림 | 주문 걸린 책 제외 |
| 8 | 회수된 인증서 PDF가 계속 다운로드됨 | REVOKED면 downloadUrl null, 옛 파일은 QR이 REVOKED 답함 |
| 9 | 탈퇴 연쇄가 ebook·print_order를 모름 | §2-5 |
| 10 | 청소기 한 작업 실패가 전체를 멈춤 | 작업별 트랜잭션 |
| 11 | 배송정보가 목록 API로 새어 나감 | 별도 표 |
| 12 | 극단값 | 도구부터 의심 |

---

## 11. 챕터 닫힘 조건

STEP 0 7건 반영 · 폴더 E 통과 · 미검증 2건 닫힘(엔드포인트 ⚠ 0) · newman 전체 2회 동일 · JUnit 4클래스 · endpoints.md ≤91 + 단언 · S1~S22(S15·S22 실측) · H1~H16(H10 채움) · 5xx 0 · `ch9-ebook.md`·`ch9-unused.md`(삭제 반영)·`ch9-replaced/`·`alter-ch9.sql` · 정리.md §1·§4(전자책 상태·downloadUrl·인쇄 상태 기계·청소기 숫자)·§5(폰트 리소스·저장소·스케줄러 단일 인스턴스·print_price)·§6(함정 1·5·6)·§7(결제 연동·ShedLock·미사용 코드 삭제 완료) · 마지막 줄 형식 동일.

챕터 9가 닫히면 **배포 전 최종 점검(verify-ch0-9)** → 배포 트랙.
