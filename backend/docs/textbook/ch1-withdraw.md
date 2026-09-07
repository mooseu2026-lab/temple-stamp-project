# 챕터 1 보강 — 회원 탈퇴 (정본)

작성 2026-09-06 · 전체 점검 D에서 F04(회원 탈퇴) 미구현 확인 → 챕터 8 진입 전 보강. 엔드포인트 1개 신설 허용(76 → 77).

## 1. 왜 지금

실제 서비스 수준이면 탈퇴는 선택이 아니다(개인정보 처리 요구 시 지체 없이 삭제·파기). 챕터 7까지 인증서·보상·사진이 사용자에 묶였으므로 연쇄를 지금 정하지 않으면 챕터 8·9가 또 늘린다.

## 2. 엔드포인트

`DELETE /api/users/me` · USER · body `{ "password": "…" }` (비밀번호 재확인 필수, 틀리면 챕터 1 공통 인증 실패 코드, 5회 잠금 규칙 동일 적용). 기존 `withdraw`(약관 철회)와 이름이 겹치지 않게 서비스 메서드는 `deleteAccount`.

응답 204. 이후 같은 access·refresh 토큰은 전부 401.

## 3. 방식 — 소프트 삭제 + 익명화

`user` 행은 지우지 않는다(인증서 번호·통계·FK 보존). 같은 트랜잭션에서:

| 순서 | 대상 | 처리 |
|---|---|---|
| 1 | user | `status=DELETED`, `deleted_at=NOW()`, `email`→`deleted-{userId}@invalid`, `name`→`탈퇴회원`, 전화·주소·프로필 등 개인정보 컬럼 NULL, 비밀번호 해시 NULL |
| 2 | refresh 토큰·세션 | 전부 무효(챕터 1 재사용 탐지와 같은 경로) |
| 3 | 동의 기록 | 유지(법정 보존), 철회 시각만 기록 |
| 4 | 진행 중 도장 세션 | EXPIRED |
| 5 | 인증서 VALID | `REVOKED`, 사유 `USER_WITHDRAWN`(ch7 §3-2에 이미 정의) — verify는 200+REVOKED 유지, `holderMasked`는 `탈퇴회원` |
| 6 | 보상 | GRANTED→REVOKED, 나머지는 `needs_review`(ch7 §2-4와 동일) |
| 7 | reward_claim | 행 삭제(배송 개인정보) |
| 8 | 사진·문장·생각상자·명상 | 행 삭제, 사진 파일 키는 `storage_orphan` 큐에 적재(표 없으면 이번에 신설 — 챕터 9가 소비) |
| 9 | 완주 기록 | 유지(집계용, 개인정보 없음) |

탈퇴 사용자로 로그인·재가입: 같은 이메일 재가입 **허용**(원 행은 익명화됐으므로 유니크 충돌 없음).

## 4. 금지

- 물리 DELETE로 user 행을 지우지 않는다(FK 연쇄로 인증서·완주까지 사라짐).
- 관리자가 대신 탈퇴시키는 API는 만들지 않는다(운영 절차).
- 탈퇴 사용자 데이터를 응답에 노출하는 관리자 목록이 있으면 `status=DELETED` 필터로 기본 제외.

## 5. 확인포인트 폴더 U 추가

| U | 요청 | 기대 |
|---|---|---|
| U-D1 | 비번 틀리게 DELETE | 401/400(공통 코드), 실패 횟수 +1 |
| U-D2 | 정상 DELETE | 204 |
| U-D3 | 옛 access로 `GET /api/users/me` / 옛 refresh로 갱신 | 401 / 401 |
| U-D4 | SQL 채점 | user.status=DELETED·email 익명화·인증서 REVOKED(USER_WITHDRAWN)·GRANTED 보상 0·reward_claim 0·사진 행 0·storage_orphan에 키 n건·completion 유지 |
| U-D5 | `GET /api/certificates/verify/{그 번호}` | 200, REVOKED, holderMasked=`탈퇴회원` |
| U-D6 | 같은 이메일 재가입 | 201 |

JUnit `UserDeleteServiceTest`: 연쇄 9단계 각 1건 + 트랜잭션 중간 실패 시 전부 롤백 1건.

## 6. 닫힘

endpoints.md 77 · U-D1~D6 통과 · newman 전체 2회 동일 · security-check S13(보호 목록 +1) · cleanup.sql에 storage_orphan 추가 · 정리.md §1·§4(탈퇴 응답·재가입 규칙)·§5-1(ALTER: user 컬럼·storage_orphan)·§7(관리자 대행 탈퇴는 운영 절차). 마지막 줄 형식 동일.
