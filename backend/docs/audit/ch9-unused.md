# 챕터 9 — 미사용 코드 삭제 기록

기준: `runtime-check.sh` R7 · `node backend/docs/verify/mapper-check.js` · `rg` 로 변수명까지 붙여 재확인
최종 갱신 2026-09-07(챕터 9 STEP 8)
지운 것의 원본은 `backend/docs/audit/ch9-replaced/*.before-unused` 에 그대로 있다.

## 0. 이번에는 목록만 남기지 않고 지웠다

챕터 7·8 은 "참조 0 이지만 곧 쓰일 자리" 를 남겨 뒀다. 챕터 9 가 **마지막 코드 챕터**라
"곧" 이 더 이상 오지 않는다. 그래서 이번에 정리했다.

지우기 전에 **변수명까지 붙여 다시 셌다.** 단순히 `.findById(` 를 세면 이름이 겹치는 다른 매퍼의
호출까지 함께 잡혀 "쓰이는 중" 으로 보인다 — 챕터 7 에서 그렇게 한 번 속았다.

```
rg -n "ErrorCode\.<상수>\b" src        # 0 이면 지운다
rg -n "\.<메서드>\(" src               # 0 이면 지운다
```

## 1. 지운 것

### ErrorCode 12개

| 상수 | 원래 뜻 | 왜 참조가 0인가 |
|---|---|---|
| `COMMON-4290` | 요청이 너무 잦습니다 | 한도는 도메인별 코드(STAMP-4291·EBOOK-4290)가 쓴다 |
| `COMMON-5030` | 일시적으로 사용할 수 없습니다 | 외부 연동 실패는 KAKAO-5030 이 쓴다 |
| `AUTH-4001` | 인증 요청이 올바르지 않습니다 | 검증 실패는 COMMON-4000 이 받는다 |
| `COURSE-4042` | 코스 자리를 찾을 수 없습니다 | 자리 문제는 COURSE-4001 로 모였다(v4) |
| `UPLOAD-4000` · `UPLOAD-4130` | 업로드 검증·용량 | 실제로는 UPLOAD-4001 과 COMMON-4130 이 쓰인다 |
| `REWARD-4030` · `REWARD-4091` | 보상 권한·중복 | 챕터 7 에서 REWARD-4090·4092 로 정리됐다 |
| `CERT-4090` | 인증서 충돌 | 회수·재발행이 상태 전이로 정리되며 쓸 자리가 없어졌다 |
| `PRINT-4030` | 회향본 신청 자격 | **챕터 9 에서 "본인 READY 전자책만" 으로 바뀌며 뜻을 잃었다** |
| `ADMIN-4030` · `ADMIN-4040` | 관리자 권한·대상 없음 | 권한은 AUTH-4032, 대상 없음은 도메인별 4040 이 쓴다 |

`STAMP-4003` 은 예약이라 남긴다(허용 목록에 명시돼 있다).

### 매퍼 메서드 6개

| 메서드 | 왜 |
|---|---|
| `MeditationLogMapper.sumPlayedSec` | 전자책의 명상 요약은 `EbookMaterialMapper` 가 따로 센다 |
| `PhotoMapper.updatePrivacy` | 사진 공개 설정 API 가 없다(생각상자에만 있다) |
| `RewardPolicyMapper.findByCode` | 정책은 전량 조회 후 메모리에서 고른다 |
| `SiteDistanceMapper.findFrom` | 이동시간은 (from, to) 쌍으로만 본다 |
| `UserMapper.updatePassword` | 비밀번호 변경 API 가 없다. 탈퇴는 `anonymize` 가 비운다 |
| `ExpansionPhraseMapper.countReviewed` | 심사 집계 화면이 없다 |

Java 선언과 XML 문장을 **함께** 지웠다. 한쪽만 지우면 `mapper-check.js` 가 양방향 불일치로 잡는다.

### 도메인 메서드 1개

`Manuscript.isMission()` — 챕터 8 에서 "짝인 `isDefault()` 와 함께 남기고 챕터 9 에서도
안 쓰이면 지운다" 고 적어 둔 그 자리다. 챕터 9 에서도 쓰이지 않아 지웠다.
종류 판정이 필요한 곳은 도메인 객체가 없는 자리라 `Manuscript.MISSION.equals(kind)` 를 직접 쓴다.

## 2. 지우지 않은 것

| 대상 | 왜 남겼나 |
|---|---|
| `CertificateMapper.updateFileKey` | **챕터 9 §3 에서 쓰이게 됐다.** 챕터 7 부터 비워 둔 자리가 이제 채워졌다 |
| `ErrorCode.STAMP_4003` | 예약. 허용 목록에 이유와 함께 적혀 있다 |
| `ebook.retry_count` · `building_started_at` | 옛 대기열의 잔재. 컬럼이라 지우면 ALTER 가 하나 더 는다. 쓰지 않는다고 §1 에 적었다 |
| `Ebook.enqueue` 계열(완주가 부르는 대기열) | 챕터 7 의 완주 연쇄가 지금도 부른다 |

## 3. 결과

```
runtime-check R7 (챕터 9 STEP 8 뒤)
  ErrorCode 상수 79개 중 참조 0건: 없음
  미참조 매퍼 메서드 0개
mapper-check: 매퍼↔XML 0 · #{}↔@Param 0 · 별칭 미채움 0 (select 90개)
컴파일 경고 0 · JUnit 260/260 · newman 515/515
```

저장소의 미참조 코드가 **0** 이 됐다. 다음에 이 목록이 다시 생기면 그때가 또 정리할 때다.
