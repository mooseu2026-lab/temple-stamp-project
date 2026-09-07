# 챕터 8 — 확장문구 · 미션 원고 등록 · 심사 (정본)

작성 2026-09-06 · 전제: 챕터 0~7 + 보강 3건 닫힘(기능 33 ✅ · 엔드포인트 77 · newman 361/361 · JUnit 실제 MySQL) · 이 문서가 챕터 8의 **정본**이다.

---

## 0. 이 챕터의 성격

챕터 5의 미션 단계는 이미 **원고를 읽어서** 보여 준다(도장 행이 원고를 FK로 참조 — 점검 C·D에서 R23 원고로 확인). 즉 **원고 표와 읽는 쪽은 있고, 넣는 쪽·심사하는 쪽이 없다.** 콘텐츠 트랙에 원고 140편이 대기 중인데 넣을 자리가 없다. 이 챕터가 그 자리를 만든다.

따라서 작업은 두 갈래다.
1. **대조**: 기존 원고 표·읽기 코드를 §2~§3 규칙과 대조(교체/삭제/유지). 특히 미션 단계가 어떤 원고를 고르는지(§3-3).
2. **신설**: 편집자 등록·제출·수정, 관리자 CSV 일괄 반입·심사·퇴역 — 엔드포인트 **9개 신설 허용**(77 → 86). §6 표 외 신설 금지.

**절대 규칙(변경 없음)**: 보안·개인정보·동의·권한 검사 제거 금지 / ErrorCode 생성자 순서·번호 고정 / 명세 §7 밖 코드 신설 금지(이 챕터의 `MS-*`는 명세 §7 대조 후 없으면 "구현 누락"으로 추가) / 서버는 좌표를 받지 않는다 / 교체·삭제 원본은 `backend/docs/audit/ch8-replaced/`.

---

## 1. 낱말 사전

| 낱말 | 뜻 |
|---|---|
| 원고(manuscript) | 사찰 × 오관게 구(1출처·2감사·3절제·4포행·5다짐)에 붙는 글 한 편. 두 종류가 있다 |
| 미션 원고(`MISSION`) | 현장 인증 3단계에서 사용자에게 보여 주는 참배 안내·질문·다짐 유도문. 도장이 이것을 참조한다 |
| 확장문구(`EXT`) | 오관게 한 구를 그 사찰 맥락으로 풀어 쓴 짧은 문장. 도장 발행 응답·여권·전자책(챕터 9)에 실린다 |
| 변형(variant) | 같은 (사찰, 구, 종류)에 여러 편이 있을 수 있다. `variantNo`는 그 안에서 1부터 매기는 번호 |
| 상한 | (사찰, 구, 종류)당 **퇴역 안 된** 변형의 최대 수. 설정값 `temple.manuscript-max-variants` 기본 **3** |
| 기본 원고 | `site_id = NULL`인 구별 원고. 사찰에 승인 원고가 없을 때 대신 쓴다. 구마다 종류별 1편, 시더가 넣는다 |
| 편집자(`EDITOR`) | 원고를 쓰고 제출하는 역할. 심사는 못 한다 |
| 심사 | 관리자가 제출된 원고를 승인·반려하는 것. **자기 원고는 심사 못 한다**(4-eyes) |
| 퇴역(`RETIRED`) | 승인 원고를 더 이상 내보내지 않게 하는 것. 삭제 아님 — 옛 도장이 계속 참조한다 |

---

## 2. 원고 규칙

### 2-1 표 (`manuscript`) — 대조 대상

| 컬럼 | 규칙 |
|---|---|
| `id` | PK |
| `site_id` | NULL 허용(기본 원고). 값이 있으면 `site` FK, 사찰 status는 묻지 않는다(DRAFT 사찰에도 미리 쓴다) |
| `verse_no` | 1~5 |
| `kind` | `MISSION` / `EXT` |
| `variant_no` | 1부터, 서버가 매김. `(site_id, verse_no, kind, variant_no)` 유니크 — MySQL은 NULL을 유니크에서 따로 보므로 **기본 원고는 생성 컬럼 `site_key = IFNULL(site_id, 0)`로 유니크** |
| `status` | `DRAFT → SUBMITTED → APPROVED / REJECTED` · `APPROVED → RETIRED` · `REJECTED → DRAFT`(수정 시). 그 외 전이 409 |
| `title` | 1~50 |
| `body` | MISSION 1~2000 · EXT 1~200 |
| `author_id` | 편집자 user FK. 탈퇴 시 유지(원고는 개인정보 아님) |
| `reviewer_id`·`reviewed_at`·`reject_reason` | 심사 기록. 반려 사유 1~200 필수 |
| `retired_at` | 퇴역 시각 |

저장소 표가 위와 다르면 **컬럼 이름은 저장소 유지**, 규칙(유니크·전이·길이)만 맞춘다. 없는 컬럼은 ALTER 후보 → `alter-ch8.sql` + `정리.md` §5-1.

### 2-2 내용 검증

- 제목·본문 앞뒤 공백 제거 후 길이 검사. 빈 문자열 400.
- **금칙**: URL(`http://`·`https://`·`www.`), 전화번호 패턴(`0\d{1,2}-?\d{3,4}-?\d{4}`), 이메일 패턴 → 400 `MS-4002` "원고에 연락처·링크를 넣을 수 없습니다". 원고는 공개 콘텐츠이므로 개인정보 유입을 구조로 막는다.
- 같은 (사찰, 구, 종류)에 **본문이 완전히 같은** 원고가 이미 있으면 409 `MS-4091`(중복).
- HTML 태그는 저장 시 제거하지 않고 **거부**(400) — 출력 쪽은 챕터 9 전자책이 플레인 텍스트만 다룬다.

### 2-3 변형 번호와 상한

```
활성 변형 수 = COUNT(status != RETIRED)  for (site_key, verse_no, kind)
등록 허용   = 활성 변형 수 < max-variants (기본 3)
variantNo   = 그 키에서 지금까지 매긴 최대 번호 + 1  (RETIRED 포함, 번호 재사용 금지)
```
- 상한 초과 → 409 `MS-4092` "변형 상한({max})에 도달했습니다 — 먼저 퇴역시키세요".
- 번호는 **잠금 아래**에서 매긴다(같은 키 동시 등록 2건 → 번호 중복 0, 상한 초과 0). 유니크가 마지막 방어선.
- 기본 원고(`site_id NULL`)는 상한 무관하게 **종류·구당 정확히 1편만**(변형 개념 없음). 2편째 → 409 `MS-4092`.

### 2-4 상태 전이와 권한

| 전이 | 누가 | 조건 |
|---|---|---|
| (없음) → DRAFT | EDITOR·ADMIN | §2-2·§2-3 통과 |
| DRAFT → DRAFT (수정) | 작성자 본인 | 본문·제목만. 사찰·구·종류는 못 바꾼다(변형 키가 바뀌므로 새로 등록) |
| DRAFT → SUBMITTED | 작성자 본인 | |
| SUBMITTED → APPROVED | ADMIN, **작성자 ≠ 심사자** | 승인 시 같은 키의 다른 APPROVED는 그대로 둔다(변형 공존). 기본 원고는 승인 즉시 이전 기본 원고 RETIRED |
| SUBMITTED → REJECTED | ADMIN, 작성자 ≠ 심사자 | 사유 1~200 필수 |
| REJECTED → DRAFT | 작성자 본인 | 수정하면 자동으로 DRAFT, 사유는 이력으로 남김 |
| APPROVED → RETIRED | ADMIN | 그 원고를 참조하는 도장은 그대로. 마지막 APPROVED를 퇴역시켜도 허용(기본 원고로 fallback) |
| 그 외 | — | 409 `MS-4090` |

- 자기 원고 심사 시도 → 403 `MS-4030` "자신의 원고는 심사할 수 없습니다".
- 편집자가 남의 원고를 수정/제출 → 404 `MS-4040`(존재 노출 안 함).
- **삭제 API 없음.** DRAFT도 지우지 않는다(퇴역 개념으로 통일, 감사 추적).

### 2-5 EDITOR 역할

- 저장소에 역할 체계가 `USER/ADMIN`뿐이면 `EDITOR`를 추가한다(ALTER 후보). ADMIN은 EDITOR 권한을 포함한다.
- 역할 부여 API는 만들지 않는다(운영 SQL, 정리.md §7). 시더가 테스트용 편집자 계정 1개를 넣는다.
- `/api/editor/**`는 EDITOR 또는 ADMIN, `/api/admin/**`는 ADMIN — `security-check.sh` S13~S14에 두 경로군을 추가.

---

## 3. 읽는 쪽 (챕터 5·6·9와의 접점) — 대조 대상

### 3-1 미션 단계가 고르는 원고

```
후보 = manuscript WHERE site_id = {사찰} AND verse_no = {구} AND kind = MISSION AND status = APPROVED
        ORDER BY variant_no
없으면 → 기본 원고(site_id NULL, 같은 구, MISSION, APPROVED)
선택   = 후보[ userId mod COUNT(후보) ]     ← 결정적. 같은 사용자·같은 사찰은 항상 같은 원고
```
- 선택된 `manuscript_id`는 **도장 세션에 저장**한다. 세션이 살아 있는 동안 원고가 퇴역·승인돼도 같은 원고를 보여 준다(사용자가 읽던 글이 중간에 바뀌지 않게).
- 도장 발행 시 `stamp.manuscript_id`에 세션의 것을 복사(이미 그렇게 돼 있으면 유지).
- 기본 원고마저 없으면 500이 아니라 **409 `MS-4093`** "이 사찰의 미션 원고가 준비되지 않았습니다" — 시더가 기본 원고 5편을 반드시 넣으므로 정상 운영에서는 나지 않는다. W·S 폴더의 기존 흐름이 깨지지 않도록 시더를 먼저 확인.

### 3-2 확장문구가 실리는 곳

- 도장 발행 응답·여권(챕터 4)·`GET /api/stamps/by-slot`에 `extPhrase{title, body, variantNo}` 필드. 없으면 `null`(present:false 원칙과 동일 — 필드는 항상 있음).
- 선택 규칙은 §3-1과 같고, 발행 시 `stamp.ext_manuscript_id`에 고정(컬럼 없으면 ALTER 후보). 이후 원고가 바뀌어도 그 도장의 문구는 그대로 — 전자책(챕터 9)이 그 시점의 문구를 그대로 실어야 한다.

### 3-3 퇴역 원고의 노출

퇴역·반려·DRAFT 원고는 **사용자 API 어디에도** 새로 나가지 않는다. 단 이미 참조된 도장의 응답에는 계속 실린다(그 시점의 문구). X 폴더 음성 테스트로 확인.

---

## 4. CSV 일괄 반입 (원고 140편 반입 통로)

`POST /api/admin/manuscripts/import` · `multipart/form-data` · opencsv 5.9 · UTF-8(BOM 허용).

| 열 | 규칙 |
|---|---|
| `site_name` | 사찰 이름 **완전 일치** + `sigungu` 열로 시군구 대조(동명이찰 규칙, 챕터 3). 둘 다 맞아야 매칭. 빈 값이면 기본 원고(`site_id NULL`) |
| `sigungu` | 위 |
| `verse_no` | 1~5 |
| `kind` | MISSION / EXT |
| `title`·`body` | §2-2 |

- 전부 **DRAFT**로 들어간다. `author_id` = 반입한 관리자. 심사는 별도.
- **행 단위 검증, 파일 단위 원자성**: 한 행이라도 실패하면 아무것도 넣지 않고 400 + `errors:[{row, field, reason}]` (최대 50건). 절반만 들어간 상태를 만들지 않는다.
- 같은 파일 재반입 → §2-2 중복 규칙(본문 동일)으로 409 목록. **드라이런** `?dryRun=true`는 검증만 하고 `{ok, wouldInsert, errors}` 반환.
- 상한(§2-3)은 파일 안의 행끼리도 합산해서 검사한다(같은 키 4행이면 4행째가 에러).
- 파일 크기 상한 2 MB, 행 상한 1,000.
- 정본 CSV 위치 `backend/docs/content/manuscripts.csv`(콘텐츠 트랙이 채움 — 이 챕터는 **양식과 예시 10행**만 만든다).

---

## 5. 에러코드 (`MS-*`, 명세 §7 대조 후 없으면 구현 누락으로 추가)

| 코드 | HTTP | 언제 |
|---|---|---|
| `MS-4001` | 400 | 검증(길이·구 범위·종류) — 공통 VALIDATION이 있으면 그것을 쓰고 이 코드는 만들지 않음 |
| `MS-4002` | 400 | 금칙(URL·전화·이메일·HTML) |
| `MS-4030` | 403 | 자기 원고 심사 |
| `MS-4040` | 404 | 없음/남의 원고 |
| `MS-4090` | 409 | 허용되지 않는 상태 전이 |
| `MS-4091` | 409 | 본문 동일 중복 |
| `MS-4092` | 409 | 변형 상한 / 기본 원고 2편째 |
| `MS-4093` | 409 | 미션 원고 미준비(기본 원고까지 없음) |

---

## 6. 엔드포인트 (신설 9개 — 이 표 외 신설 금지)

| # | 메서드 경로 | 인증 | 요약 |
|---|---|---|---|
| 1 | `POST /api/editor/manuscripts` | EDITOR+ | 등록(DRAFT), variantNo 서버 배정 |
| 2 | `PUT /api/editor/manuscripts/{id}` | 작성자 | DRAFT/REJECTED만, 제목·본문만 |
| 3 | `POST /api/editor/manuscripts/{id}/submit` | 작성자 | DRAFT → SUBMITTED |
| 4 | `GET /api/editor/manuscripts?siteId&verseNo&kind&status&page` | EDITOR+ | 본인 것만(ADMIN은 전체) |
| 5 | `GET /api/admin/manuscripts?status=SUBMITTED&siteId&page` | ADMIN | 심사 목록, 오래된 제출 먼저 |
| 6 | `POST /api/admin/manuscripts/{id}/approve` | ADMIN | 4-eyes |
| 7 | `POST /api/admin/manuscripts/{id}/reject` | ADMIN | 사유 필수, 4-eyes |
| 8 | `POST /api/admin/manuscripts/{id}/retire` | ADMIN | APPROVED만 |
| 9 | `POST /api/admin/manuscripts/import?dryRun=` | ADMIN | CSV |

- 사용자용 원고 목록 API는 **없다**(원고는 미션 단계와 도장 응답으로만 나간다).
- 목록 응답 DTO는 record, 페이징은 챕터 3 관리자 목록과 같은 형식.

---

## 7. 확인포인트 폴더 N · JUnit

### 7-1 폴더 N (newman) — cleanup → N → SQL 채점 → cleanup, 2회 동일

전제: 편집자 ED1·ED2, 관리자 ADM(원고 작성도 함), 데모 사찰 D, 기본 원고 5편(시더).

| N | 요청 | 기대 |
|---|---|---|
| N01 | ED1 등록(D, 구1, MISSION) | 201, `variantNo:1`, DRAFT |
| N02 | ED1 같은 키 등록 2회 | 201 v2 → 201 v3 |
| N03 | ED1 같은 키 4번째 | **409 MS-4092** |
| N04 | ED1 등록(본문에 `010-1234-5678`) | 400 MS-4002 |
| N05 | ED1 등록(본문 N01과 동일) | 409 MS-4091 |
| N06 | ED2가 N01 원고 수정 | 404 MS-4040 |
| N07 | ED1이 N01 수정(제목만) → 제출 | 200 → 200 SUBMITTED |
| N08 | ED1이 N01 다시 제출 | 409 MS-4090 |
| N09 | USER 토큰으로 #1 | 403 |
| N10 | ADM 등록 후 스스로 승인 | 201 → **403 MS-4030** |
| N11 | ADM이 N01 반려(사유 없이) → 사유 있음 | 400 → 200 REJECTED |
| N12 | ED1이 반려본 수정 → 상태 | 200, DRAFT, `rejectReason` 이력 유지 |
| N13 | ED1 제출 → ADM 승인 | 200 → 200 APPROVED |
| N14 | 미션 단계(챕터 5 S 흐름, 사용자 U1, 사찰 D 구1) | 응답 원고 = APPROVED 변형 중 `U1.id mod count`, 세션에 `manuscriptId` 저장(SQL) |
| N15 | 같은 세션에서 미션 재조회 → 그 사이 ADM이 그 원고 퇴역 → 재조회 | 같은 `manuscriptId` 유지 |
| N16 | 새 세션(U1, D, 구2 — 승인 원고 없음) | 기본 원고 반환, `siteId:null` |
| N17 | ED1 EXT 등록 → 제출 → ADM 승인 → U1 도장 발행 | 발행 응답 `extPhrase` 채움, `stamp.ext_manuscript_id` 고정(SQL) |
| N18 | ADM이 그 EXT 퇴역 → `GET /api/stamps/by-slot` | 옛 도장의 `extPhrase` **그대로** |
| N19 | ADM CSV dryRun(예시 10행) → 실반입 → 같은 파일 재반입 | `{ok:true, wouldInsert:10}` → 201 10건 DRAFT → 409 중복 10건 |
| N20 | ADM CSV(1행 사찰 이름 오타) | 400 `errors[0].row`, **삽입 0건**(SQL) |
| N21 | ADM CSV(같은 키 4행) | 400, 4행째 상한 에러, 삽입 0 |
| N22 | ADM 기본 원고 2편째 등록(site null, 구1, MISSION) → 승인 | 409 MS-4092 (기본 원고는 1편) |
| N23 | 사찰 D의 MISSION 승인본 전부 퇴역 + 기본 원고 있음 → 새 세션 | 기본 원고 |
| N24 | (JUnit로 대체) 기본 원고까지 없는 구 | 409 MS-4093 |
| N25 | X 폴더: USER 토큰으로 `/api/editor/**`·`/api/admin/manuscripts/**` 전수 | 401/403, DRAFT·REJECTED·RETIRED 본문이 사용자 응답에 새로 나가지 않음 |

### 7-2 JUnit (실제 MySQL)

| 클래스 | 반드시 |
|---|---|
| `ManuscriptServiceTest` | 상태 전이 표 전수(허용 7·불허 전부 4090) · 4-eyes · 금칙 4종 · 중복 · 상한 · **동시 등록 2스레드 번호 중복 0·상한 초과 0** · 기본 원고 1편 규칙 |
| `ManuscriptSelectTest` | `userId mod count` 결정성 · 세션 고정(퇴역 뒤에도 유지) · fallback · 기본 원고까지 없으면 MS-4093 |
| `ManuscriptImportTest` | 원자성(1행 실패 → 0 삽입) · dryRun 무삽입 · 파일 내 상한 합산 · 동명이찰(같은 이름 다른 시군구) 매칭 · BOM · 2 MB/1,000행 상한 |

### 7-3 테스트 데이터

- 시더: 기본 원고 MISSION 5 + EXT 5, 편집자 계정 1(ED1) — 시드 정본에 추가하고 `db-check.sh` H15(기본 원고 10편 존재·APPROVED)로 고정.
- `cleanup.sql`: 테스트가 만든 원고 삭제. **도장이 참조하는 원고는 도장 먼저**(RESTRICT 순서, 챕터 7 사례).
- 예시 CSV `backend/docs/verify/body/manuscripts-sample.csv` 10행 + 오류 CSV 2개(N20·N21용).

---

## 8. 함정 표

| # | 함정 | 대책 |
|---|---|---|
| 1 | NULL site_id는 유니크에서 빠짐 → 기본 원고가 여러 편 | 생성 컬럼 `site_key = IFNULL(site_id,0)` 유니크 |
| 2 | 변형 번호 MAX+1 무잠금 → 동시 등록 중복 | 키 잠금 + 유니크 |
| 3 | 사용자가 읽는 중 원고 퇴역 → 화면이 바뀜 | 세션에 manuscript_id 고정 |
| 4 | 퇴역 원고를 참조하는 도장의 문구가 사라짐 | stamp에 ext_manuscript_id 고정, 퇴역은 삭제 아님 |
| 5 | CSV 절반 삽입 | 파일 단위 원자성 + dryRun |
| 6 | 동명이찰 오매칭 | 이름 완전 일치 + 시군구 대조(챕터 3 규칙) |
| 7 | 자기 원고 자기 승인 | 4-eyes MS-4030 |
| 8 | 원고에 전화번호·URL | 금칙 MS-4002 |
| 9 | 기본 원고 없이 미션 진입 → 500 | MS-4093 + 시더 H15 |
| 10 | `/api/editor/**`를 보호 목록에 안 넣음 | S13~S14 갱신 + open-endpoint-probe 86개 실측 |
| 11 | 점검 결과 극단값 | 도구부터 의심 |

---

## 9. 챕터 닫힘 조건

- STEP 0(탈퇴 잔여 2건) 반영 확인 · 폴더 N 전부 통과 · newman 전체 2회 동일 · JUnit 3클래스 통과(동시성 포함) · endpoints.md **86** · open-endpoint-probe 86 실측 어긋남 0 · S1~S18(+editor 경로군) · H1~H15 · 런타임 5xx 0
- `docs/audit/ch8-manuscript.md` · `ch8-unused.md` · `ch8-replaced/` · `alter-ch8.sql`
- `정리.md` §1 · §4(편집자 워크플로·상태 기계·extPhrase 필드·CSV 양식) · §5-1 · §6(함정 1·3·5) · §7(역할 부여는 운영 SQL, 원고 140편 반입은 콘텐츠 트랙)
- 마지막 줄 `newman N/N, 기능 ✅ a · ⚠ 0 · ❌ 0 · — d`
