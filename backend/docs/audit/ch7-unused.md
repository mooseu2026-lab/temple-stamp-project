# 챕터 7 — 미참조 코드 정리

기준: `node backend/docs/verify/mapper-check.js` · `runtime-check.sh` R7 · `./gradlew compileJava compileTestJava` 경고
최종 갱신 2026-09-06(챕터 7 보강 STEP 5 반영)
삭제한 것의 원본은 `backend/docs/audit/ch7-replaced/` 에 챕터 시작 시점 그대로 들어 있다.

## 0. 도구부터 의심했다 (정리.md §6-11)

R7 스캐너는 **메서드 이름이 겹치면 못 잡는다.** `.findById(` 를 소스 전체에서 세기 때문에,
`UserRewardMapper.findById` 를 안 쓰게 된 뒤에도 다른 매퍼의 `findById` 호출이 있어 "쓰이는 중" 으로 보였다.

```
grep -rhno "userRewardMapper\.\w*" src/main/java | sort | uniq -c     # 변수 이름까지 붙여 다시 셌다
```

그래서 이번 챕터가 건드린 두 매퍼는 **변수명까지 포함해** 다시 셌다. 아래 표의 근거가 그 결과다.
컴파일 경고는 0건, `mapper-check.js` 는 매퍼↔XML 0 · `#{}`↔`@Param` 0 · 별칭 미채움 0(select 75개).

## 1. 판정

| 항목 | 판정 | 근거 |
|---|---|---|
| `UserRewardMapper.findById` (+ XML `findById`) | **삭제** | 수령 신청이 소유자·종류·상태를 한 번에 봐야 해서 `findRowById`(정책 조인)로 바뀌었다. 호출부 0 — 변수명까지 붙여 확인 |
| `CertificateService.countByUser` · `CertificateMapper.countByUserId` (+ XML) | **삭제** | 호출부 0. 인증서 장수로 무엇을 판정하는 곳이 없다(회향본 자격은 `PRINT-4030` 쪽에서 따로 본다). 필요해지면 세 줄이다 |
| `CertificateMapper.updateFileKey` | **유지** | 교재 §3-5 — 챕터 9 에서 PDF 를 만든 뒤 그 키를 적는다. 미참조 상태로 남기고 정리.md §7 에 적었다 |
| `ErrorCode.REWARD_4091` "이미 청구한 보상입니다" | **유지** | 명세 §7 코드다. 이번에 `REWARD-4092`(교재 §4-3 이 지정)로 신청 중복을 던지면서 참조가 0이 됐다. **번호는 바꾸지 않는다**는 규칙이라 지우지 않고 남긴다 — 삭제·재사용은 명세 쪽 결정 |
| `ErrorCode.REWARD_4030` "이상 활동이 감지되어…" | **유지** | 교재 §4-4 는 "관리자 아님 403" 자리에 이 코드를 놓았지만, 저장소는 관리자 권한을 `SecurityConfig` 의 `ROLE_ADMIN` 매처로 막고 스프링이 403 을 낸다 — 서비스가 던질 자리가 없다. 게다가 이 코드의 문구는 권한이 아니라 **감사 보류**다. 명세 코드라 유지하고 차이를 보고서 §3 에 적었다 |
| `ErrorCode.CERT_4041` vs 교재의 `CERT-4040` | **유지(삭제할 것이 없다)** | 보강 STEP 5 로 다시 본 결과, 저장소에 `CERT-4040` 은 **존재한 적이 없다.** 404 코드는 `CERT-4041` 하나뿐이고 컬렉션(R11·W06)·DTO 주석이 그 번호를 쓴다. 교재 §3-3 의 표기를 `CERT-4041` 로 읽는다 — 권고와 근거는 `ch7-hardening.md` §6 |
| `ErrorCode.CERT_4091` "이미 회수된 인증서입니다" | **추가·참조됨** | 보강에서 관리자 회수 엔드포인트가 생기며 던질 자리가 생겼다. 연쇄(자동) 회수는 0행으로 조용히 넘어가고, 사람이 누른 회수만 이 코드를 받는다 |
| 중간본(`INTERIM`) 인증서 종류 | **제외 확정** | 이 저장소의 중간본은 인증서가 아니라 **전자책**이다(`cert_type CHECK` = PILGRIMAGE·HOEHYANG). 보강 A §3-1 이 "구현이 없으면 제외" 로 정정했다 — 코드·W 채점 어디에도 넣지 않았다 |
| `ErrorCode.CERT_4090` "이미 발급된 인증서입니다" | **유지** | 교재 §3-3 은 "같은 근거로 이미 VALID 인데 또 발행" 에 이 코드를 쓰라고 한다. 저장소는 그 상황에서 예외를 던지지 않고 **있던 인증서를 그대로 돌려준다**(멱등). 재집계가 몇 번이고 도는 구조라 여기서 예외가 나면 완주 처리가 통째로 롤백된다 — 설계를 바꾸지 않고 코드만 남긴다 |
| `ErrorCode.STAMP_4003` | **유지(예약)** | 현장 QR 은 만료가 없다(정리.md §3). 주석에 "미사용(예약)" 이 이미 있다 |
| `COMMON_4290` `COMMON_5030` `AUTH_4001` `COURSE_4042` `UPLOAD_4000` `UPLOAD_4130` `ADMIN_4030` `ADMIN_4040` | **유지** | 전부 명세 §7 코드. 챕터 8·9 와 운영 상황(대량 요청·저장소 장애)에서 쓰인다. `COURSE-4042` 는 "자리 없음" 본뜻으로만 쓰기로 이미 정리됐다(정리.md §7-3) |
| `MeditationLogMapper.sumPlayedSec` · `PhotoMapper.updatePrivacy` · `RewardPolicyMapper.findByCode` · `SiteDistanceMapper.findFrom` · `UserMapper.updatePassword` · `ExpansionPhraseMapper.countReviewed` | **유지(챕터 7 범위 밖)** | 챕터 6 이전에 만들어진 것들이라 이번 챕터가 판정할 자리가 아니다. 전체 점검 C 보고서 §3 R7 에 이미 목록으로 올라가 있다 |
| Mapper XML 에 있는데 Java 인터페이스에 없는 SQL | **없음(0건)** — 단, 도구를 먼저 고쳤다 | 0건이 도구 탓인지 보려고 없는 id 를 XML 에 심어 봤더니 **스크립트가 못 잡았다.** ① 이 Java→XML 한 방향만 보고 있었다. 양방향으로 고친 뒤 다시 심었더니 잡혔고, 되돌린 실제 저장소는 0건이다 |

## 2. 삭제하지 않은 이유가 하나로 모이는 것

`ErrorCode` 는 **명세가 정본이고 번호는 우리 것이 아니다.** 미참조라는 이유로 지우면
다음 사람이 같은 번호를 다른 뜻으로 다시 만들 수 있다 — 그때 이미 나간 클라이언트는
같은 코드에 다른 화면을 띄운다. 지금 참조가 0인 것은 "아직 그 자리를 안 만들었다" 는 뜻이지
"필요 없다" 는 뜻이 아니다. 그래서 이 표의 판정은 대부분 **유지 + 보고**다.
