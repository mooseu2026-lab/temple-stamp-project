## 13. 기능 F01~F58 판정표

✅는 newman 요청이 실제 응답을 받고 보조 SQL 채점까지 맞은 것만 붙였다. 파일이 걸린 항목은 저장소에서 바이트를 읽어 본 것만 ✅다. 근거 열의 앞글자는 폴더, 뒤는 요청 id다.

| F | 기능 | 판정 | 근거(폴더·요청 id) | 비고 |
|---|---|---|---|---|
| F01 | 회원가입·이메일/비번 검증·동의 | ✅ | A01·A02·A03 · U01 | 중복 409 · 비번 규칙 400 fields |
| F02 | 로그인 JWT 30분·refresh 회전·재사용 탐지 | ✅ | A04·A09·A10·A12·A13 · S5·S18 | 재사용 시 전 세션 무효 |
| F03 | 5회 실패 잠금 403·해제 | ✅ | A07·A07x·A08 · 채점 ① | 잠금 메시지에 15분 |
| F04 | 회원 탈퇴(소프트 삭제·익명화·연쇄·사진키/문장 비움) | ✅ | U-D0~U-D6 · W2 U-D5·U-D5b · 채점 ㉔㉕㉖ · S19 | 도장 행은 남고 두 칸만 비운다 |
| F05 | 권역 10행·코스 목록 ACTIVE만 | ✅ | M01·M02·M03 | 표 10행, 목록 1건(빈 DB는 데모 코스만 ACTIVE) |
| F06 | 코스 상세·자리 5·후보 사찰·congested | ✅ | M04·M18 · T00c | 자리마다 candidates |
| F07 | 사찰 싱글페이지 8블록·가는 법 7칸 | ✅ | M08~M13·M17 | 계층·로케일 우선순위 |
| F08 | 관리자 사찰 UPSERT·status 필터·좌표 화이트리스트 | ✅ | S01~S12 · C01~C09 · G01 | 본문 status 금지 400 |
| F09 | 카카오 장소 연동 | ✅ | K01·K02·K03 | |
| F10 | 순례 시작·여권·진행률 | ✅ | P02~P08 | 재요청 created:false |
| F11 | 1단계 GPS(서버는 좌표 미수신) | ✅ | T02·T03·T04b·T04 | LOW 400 · 반경 밖 400 · 후보 아님 400 |
| F12 | 2단계 QR(만료 없음·회전 무효화) | ✅ | T06·T07·T08 · T14a~T14 | 다른 사찰 QR 400 |
| F13 | 3단계 미션·다짐 제출 → 도장 | ✅ | T09·T10 | 10자 미만 400 |
| F14 | 세션 60분 만료 | ✅ | JUnit StampFlow(T-08) · db-check H18 sessionExpired | newman으로는 60분을 기다릴 수 없어 JUnit 실측을 인정(점검 D 결정) |
| F15 | 하루 한도 5·예외접수 2·관리자 승인 한도 무관 | ✅ | T16a·T16b·T16 | 3건째 429 |
| F16 | 자리 1도장·409 + by-slot | ✅ | T11·T11b | 409 메시지에 사찰명·발행일 |
| F17 | 관리자 도장 심사(COMPLETED↔REJECTED) | ✅ | T15a·T15·T15b · W2 W16·W19 | 회수·재승인 양방향 |
| F18 | presign(jpeg/png·키 소유 검증) | ✅ | V01·V02·V03·V03b · X48~X50 | |
| F19 | 사진 사찰당 1 + 문장 1 UPSERT | ✅ | V04·V05·V05a·V08 · 채점 ⑪ | 행 수 불변 |
| F20 | 생각상자(isPrivate)·flashback 200+null | ✅ | V09~V15 · R01~R04 | 없으면 404가 아니라 200 + null |
| F21 | 명상 기록 | ✅ | V16~V19 · R05~R07 | 로케일 폴백 |
| F22 | 완주 판정·재집계 멱등 | ✅ | W01~W04·W09 · 채점 | 4/5는 완주 아님 |
| F23 | 관리자 재집계 1명·배치 | ✅ | W23·W23b·W23c·W23d·W24 | 세 번 돌려도 같은 결과 |
| F24 | 완주 취소 연쇄(인증서 회수·보상 GRANTED만 REVOKED·needs_review) | ✅ | W2 W16·W17·W17b·W17c·W22~W22d | 이미 움직인 실물은 사람이 본다 |
| F25 | 인증서 자동 발행·시퀀스 번호·중복 0 | ✅ | W04 · 채점 duplicated_serials 0 | JUnit 10장 동시 발행도 중복 0 |
| F26 | 인증서 관리자 회수·재발행 새 번호 | ✅ | W2 W25·W25b·W25c·W19b | 같은 것 재회수 409 |
| F27 | 공개 진위 확인(비로그인·마스킹·회수 200+REVOKED) | ✅ | W05·W06 · W2 W18·W25d·U-D5b · JUnit Masking(Certificate) | 없는 번호만 404 |
| F28 | 보상 적립 멱등·claimable 서버 계산 | ✅ | W08 · R08 · W2 W21b | |
| F29 | 수령 신청(reward_claim 분리) | ✅ | W10 · W2 W11·W12·W13·W26·W26b | 목록에 배송 정보 없음 |
| F30 | 관리자 보상 심사(CLAIMED→PAID·REJECTED) | ✅ | W2 W14·W20·W20b · R20·R21 | 이미 처리된 것 재승인 409 |
| F31 | 회향 하한 12·HH 인증서 | ✅ | W15 · W2 W21·W21b | ACTIVE 1개면 성립 안 함 |
| F32 | 완주 재성립 시 보상 같은 행 복귀 | ✅ | W2 W27 | 같은 id가 한 번만 |
| F33 | 공통: ErrorCode 형식·@Validated 400·404 존재 미노출 | ✅ | X01~X26 · G05 | 없는 보호 URL도 401 |
| F34 | 원고 등록(DRAFT)·variantNo 서버 배정 | ✅ | N01·N02a·N02b | 요청에 없던 번호가 응답에 |
| F35 | 변형 상한 3(설정값)·기본 원고 1편 | ✅ | N03·N22 · 채점 over_cap 0 · default_over_one 0 | |
| F36 | 금칙(URL·전화·이메일·HTML) 400 MS-4002 | ✅ | N04 · S21 넷 | |
| F37 | 본문 동일 중복 409 MS-4091 | ✅ | N05 | |
| F38 | 작성자 수정(DRAFT/REJECTED)·남의 원고 404 | ✅ | N06·N07a·N12 | 403이 아니라 404 |
| F39 | 제출·상태 전이(표 밖 MS-4090) | ✅ | N07b·N07c·N08 · JUnit ② | 닫힌 길 전부 같은 답 |
| F40 | 심사 4-eyes(자기 원고 403 MS-4030) | ✅ | N10a·N10b·N10c | 관리자도 걸린다 |
| F41 | 반려 사유 필수·REJECTED→수정 시 DRAFT | ✅ | N11a·N11b·N11c·N12 | 사유는 지우지 않는다 |
| F42 | 승인·기본 원고 승인 시 이전 기본 RETIRED | ✅ | N13a~N13d · JUnit ① · 채점 default_approved 10 | |
| F43 | 퇴역(삭제 없음) | ✅ | N15b·N18a·N23a·N23a2 | 삭제 API 자체가 없다 |
| F44 | 미션 원고 선택 userId mod count·세션 고정 | ✅ | N14·N15a·N15 · 채점 mission_fixed 3 | 퇴역 뒤에도 같은 원고 |
| F45 | 기본 원고 fallback·전부 없으면 MS-4093 | ✅ | N16·N23 · JUnit Select ⑥ | siteId null로 구분 |
| F46 | 확장문구 발행 시 stamp 고정·퇴역 뒤 옛 도장 유지 | ✅ | N17·N18 · 채점 ext_fixed 1 | 여권·by-slot에도 실린다 |
| F47 | CSV 반입(원자성·dryRun·시군구 대조·상한 합산·BOM·2MB/1,000행) | ✅ | N19a~N19d·N20·N21 · JUnit Import ①~⑩ · 채점 imported_draft 11 | 한 행이라도 틀리면 0건 |
| F48 | EDITOR 역할·/api/editor/** 보호 | ✅ | N09·N25a~N25g · S14 | 편집자는 심사 문 403 |
| F49 | 잠금 규칙(있는 행 잠금) — 동시 등록 6스레드 교착 0 | ✅ | JUnit ManuscriptService ⑨ · H5 | 갭 잠금을 행 잠금으로 바꾼 결과 |
| F50 | 전자책 요청 — 스냅샷 해시 멱등·REQUESTED 1건·하루 3권 | ✅ | E01·E02·E03·E03b·E07·E08a·E08b · JUnit EbookService ①~⑥ · 채점 snapshot_dup 0 · requested_left 0 | 재료가 그대로면 같은 id, 한 줄만 달라도 새 id |
| F51 | 전자책 생성 — 비동기·READY/FAILED·쪽 구성·사진 리사이즈·한글 폰트 임베드 | ✅ | E04·E08 · JUnit EbookService ⑦⑧⑨⑩ · Housekeeping ⑥ · StorageReality ② | PDF에서 한글을 도로 뽑아 확인 |
| F52 | 전자책 다운로드 — presigned 10분·남의 것 404·READY 5권 상한·옛 파일 orphan | ✅ | E05·E06·E09·E10 · JUnit EbookService ⑪⑫ · StorageReality ⑤ | E06은 URL 모양까지만(newman 한계), 바이트는 JUnit |
| F53 | 인증서 1장 조회 + PDF 첫 조회 생성·QR = verify URL·REVOKED면 링크 null | ✅ | E11a·E11·E12·E13·E14a·E14 · JUnit CertificatePdf ①~⑦ · StorageReality ③ · 채점 cert_with_file 1 · revoked_file_kept 1 | QR을 디코드해 주소까지 대조 |
| F54 | 인쇄주문 — READY 책만·상태 6·사용자 취소 REQUESTED만·배송정보 별도 표 | ✅ | E15~E22c · JUnit PrintOrderService ①~⑪ · Masking(PrintOrder) · 채점 address_rows·orphan_address 0 | 목록에는 주소 칸이 아예 없다 |
| F55 | 청소기 — orphan·토큰 만료+24h·전자책·세션·작업별 트랜잭션·건수 로그 | ✅ | E04·E09·E23·E25c · JUnit Housekeeping ①~⑥ · **db-check H18 대상=처리** · 채점 orphan_failed 0 · token_overdue 0 | 돌았는가가 아니라 몇 건을 했는가로 채점 |
| F56 | 관리자 사용자 목록 — DELETED 기본 제외·개인정보 최소 | ✅ | E24·E24b·E24c · S25 | 목록 응답에 민감 키 0 |
| F57 | 탈퇴 연쇄 보강 — ebook 삭제+orphan·REQUESTED 주문 취소 후 삭제·CONFIRMED 이후 needs_review | ✅ | E25·E25b·E25c · JUnit PrintOrderService ⑪ · 채점 needs_review·orphan_address | FK 순서(주문 먼저, 전자책 나중) |
| F58 | 저장소 실재 — 사진·전자책·인증서 PDF가 바이트로 읽힘 | ✅ | JUnit StorageRealityTest ①~⑤ | JPEG `FF D8 FF`·PNG 머리글·`%PDF`·한글·사찰명·인증서 번호, 지운 키는 읽히지 않음 |

집계: ✅ 58 · ⚠ 0 · ❌ 0.
