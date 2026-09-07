# 챕터 8 — 미참조 코드 정리

기준: `node backend/docs/verify/mapper-check.js` · `runtime-check.sh` R7 · `./gradlew compileJava compileTestJava` 경고
최종 갱신 2026-09-06(챕터 8 STEP 8)
교체·삭제한 원본은 `backend/docs/audit/ch8-replaced/` 에 있다.

## 0. 이번에도 도구가 먼저 틀렸다

R7 스캐너는 `.메서드이름(` 을 **한 줄 안에서** 센다. 그래서 이렇게 줄을 바꾼 호출을 못 본다.

```java
List<ManuscriptRow> rows = manuscriptMapper
        .findRows(authorFilter, siteId, verseNo, kind, status, page * size, size);
```

`ManuscriptMapper.findRows` 가 그 모양이라 "미참조" 로 보였다. 실제로는 목록 API 가 부른다.
그래서 이번 챕터가 더한 매퍼는 **선언 15개를 하나씩 되짚어** 셌다.

```
for m in countActive countRows ... ; do grep -rho "\.$m(" src/main/java | wc -l ; done
```

결과: **15개 전부 참조 있음.** 미참조 0.
`mapper-check.js` 는 매퍼↔XML 0 · `#{}`↔`@Param` 0 · 별칭 미채움 0(검사한 select 80개).
컴파일 경고 0건.

## 1. 이번 챕터가 더한 것 — 판정

| 대상 | 참조 | 판정 |
|---|---:|---|
| `ManuscriptMapper` 15개 | 전부 1회 이상 | ✅ 유지 |
| `ManuscriptSelector.findById` | 2 (`toStatus` 에서 미션·확장문구 둘) | ✅ 유지 |
| `ManuscriptTextResponse` · `ExtPhraseResponse` | 응답 조립 | ✅ 유지 |
| `ExtPhraseResponse.from(Manuscript)` | 도장 발행·조회 | ✅ 유지 |
| `ExtPhraseResponse.of(title, body, variantNo)` | 여권(조인 행에서 조립) | ✅ 유지 — 여권은 도메인이 아니라 평평한 행을 받는다 |
| `Manuscript.isMission()` | **0** | ⚠ 아래 |
| `StampMapper.fixExtManuscript` | 1 (심사 승인 경로) | ✅ 유지 |
| `SiteMapper.findIdsByNameAndSigungu` | 1 (CSV 반입) | ✅ 유지 |

### ⚠ `Manuscript.isMission()` — 참조 0

`isDefault()` 는 승인 시 기본 원고 교체 판정에 쓰이는데, `isMission()` 은 쓰이는 자리가 없다.
종류 판정이 필요한 곳(본문 길이 상한)은 `ManuscriptService.validateContent` 가
`Manuscript.MISSION.equals(kind)` 로 **문자열을 직접** 본다 — 그 자리에는 도메인 객체가 없기 때문이다.

**지우지 않고 남긴다.** 한 줄짜리 판정 메서드이고 `isDefault()` 와 짝이라, 둘 중 하나만 있으면
다음에 읽는 사람이 "왜 하나만 있지" 를 먼저 묻게 된다. 챕터 9(전자책)가 도장에서 원고를 꺼내
종류를 가르는 자리가 생기면 그때 쓰인다. **다음 챕터에서도 참조가 0이면 그때 지운다.**

## 2. 저장소 전체 미참조 (변동 없음 — 보고만)

`runtime-check.sh` R7 실측. 챕터 8 이 늘리거나 줄인 것은 없다.

```
ErrorCode 상수 87개 중 참조 0건:
  COMMON_4290, COMMON_5030, AUTH_4001, COURSE_4042, UPLOAD_4000, UPLOAD_4130,
  REWARD_4030, REWARD_4091, CERT_4090, ADMIN_4030, ADMIN_4040
  (예약된 STAMP_4003 은 허용 목록)

미참조 매퍼 메서드 7개:
  CertificateMapper.updateFileKey, MeditationLogMapper.sumPlayedSec, PhotoMapper.updatePrivacy,
  RewardPolicyMapper.findByCode, SiteDistanceMapper.findFrom, UserMapper.updatePassword,
  ExpansionPhraseMapper.countReviewed
```

MS-* 코드 일곱은 전부 참조가 있다(MS-4002·4030·4040·4090·4091·4092·4093).
**MS-4001 은 만들지 않았다** — 공통 `COMMON-4000` 이 그 자리를 쓴다(명세 §7 대조).

## 3. 교체한 파일 — `ch8-replaced/`

| 파일 | 왜 |
|---|---|
| `ManuscriptMapper.xml.gaplock` · `ManuscriptMapper.java.gaplock` · `ManuscriptService.java.gaplock` | 갭 잠금 방식(`MAX(...) FOR UPDATE`)을 행 잠금으로 바꾸기 전 상태. 6스레드 전부 교착났던 그 코드다 — `ch8-manuscript.md` §4 |
| `StampMapper.java.before` · `StampMapper.xml.before` · `Stamp.java.before` · `StampService.java.before` · `StampStatusResponse.java.before` · `MissionResultResponse.java.before` | 원고 두 칸을 넣기 전 |
| `cleanup.sql.before` · `all-checkpoints.sql.before` · `db-check.sh.before` · `security-check.sh.before` · `run-all.sh.before` | 점검 세트에 폴더 N 몫을 더하기 전 |
