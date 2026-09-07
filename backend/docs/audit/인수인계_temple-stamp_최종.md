# temple-stamp 백엔드 — 인수인계 (2026-09-07)

챕터 0~9 가 전부 닫히고 최종 점검 F 까지 끝난 시점의 한 장이다. 이 문서 하나로 "무엇이 있고, 어떻게 띄우고, 배포에 무엇이 필요하고, 무엇이 아직 안 됐는가" 를 알 수 있게 썼다. 상세는 `backend/docs/정리.md` 와 `backend/docs/audit/` 아래 챕터별 보고서에 있다.

---

## 1. 스택

| 무엇 | 버전 | 쓰는 곳 | 라이선스 |
|---|---|---|---|
| Java | 21 (toolchain) | | GPL+CE |
| Spring Boot | 3.5.16 | web · security · validation · actuator | Apache-2.0 |
| MyBatis Spring Boot Starter | 3.0.5 | 모든 DB 접근(XML 매퍼) | Apache-2.0 |
| MySQL Connector/J | Boot 관리 버전 | | GPL+FOSS 예외 |
| jjwt | 0.13.0 | 액세스·리프레시 토큰 | Apache-2.0 |
| zxing (core·javase) | 3.5.4 | QR 생성·인증서 QR | Apache-2.0 |
| OpenPDF | 2.0.3 | 전자책·인증서 PDF | LGPL/MPL |
| opencsv | 5.9 | 원고 CSV 반입 | Apache-2.0 |
| Lombok | Boot 관리 버전 | 컴파일 전용 | MIT |
| NotoSansKR-Regular.ttf | 6.1 MB | PDF 한글 임베드 | SIL OFL 1.1 — 원문 `src/main/resources/fonts/OFL.txt` 가 jar 에 함께 들어간다(OFL §2 가 사본 동봉을 요구한다) |

빌드 `./gradlew test` · 기동 `./gradlew bootRun` · 검증 `bash postman/run-all.sh`.

---

## 2. 표 36개

`cert_serial · certificate · course · course_site · ebook · expansion_phrase · gwan_verse · manuscript · meditation · meditation_i18n · meditation_log · mission · photo · phrase_seen · pilgrimage · print_order · print_order_address · refresh_token · region · reward_claim · reward_policy · shrine_element · site · site_badge · site_distance · site_element · site_i18n · site_viewpoint · slot_site · stamp · storage_orphan · task_seen · thinkbox · user_agreement · user_reward · users`

전부 InnoDB · utf8mb4 · `time_zone` 은 서버에 `+09:00` 을 명시한다. 생성 컬럼이 셋 있다 — `certificate.valid_pilgrimage_id`(회수본은 유니크에서 빠진다) · `manuscript.site_key`(`IFNULL(site_id,0)`, NULL 을 서로 다른 값으로 보는 MySQL 유니크를 피한다) · 하나 더는 `schema.sql` 참조.

---

## 3. API 91개

도메인별 분포와 인증 요구는 `docs/audit/endpoints.md` 가 정본이고, 개수는 `backend/docs/verify/expected-endpoints.txt` 한 줄(91)로 못 박혀 있다. 스캔·프로브·문서 셋이 이 수와 다르면 도구가 종료코드 1 로 멈춘다.

| 도메인 | 개수 | 도메인 | 개수 |
|---|---:|---|---:|
| 인증·회원 | 10 | 완주·인증서·보상 | 5 |
| 권역·코스·사찰 조회 | 6 | 관리자 완주·인증서·보상 | 5 |
| 구절(오관게) | 2 | 원고 — 편집자 | 4 |
| 카카오 | 1 | 원고 — 관리자 심사 | 5 |
| 관리자 사찰·코스·도장 심사 | 16 | 전자책·인쇄(사용자) | 8 |
| 순례·여권 | 2 | 전자책·인쇄(관리자) | 3 |
| 현장 인증·도장 | 6 | 관리자 사용자·청소기 | 2 |
| 기록(업로드·사진·생각상자·명상) | 12 | | |
| 콘텐츠(예절·i18n·확장문구·미션) | 4 | **합계** | **91** |

인증 분포: ADMIN 34 · USER 36 · EDITOR+ADMIN 4 · 없음 17. 응답 봉투는 `success·data·error·timestamp` 넷으로 고정이고 시각은 전부 `+09:00`. 프론트가 알아야 할 것은 `frontend-handoff.md` 에 따로 뽑아 두었다.

---

## 4. 설정값 전부

`.env` 는 저장소에 있고 `.env.local` 은 사람마다 다르다(시크릿). `spring.config.import` 로 읽으며, **OS 환경변수가 항상 이긴다**.

### 4-1. 앱 설정

| 키 | 기본값 | 뜻 |
|---|---|---|
| `app.jwt.secret` | `${JWT_SECRET}` | 액세스·리프레시 서명. Base64 로 32바이트 이상이어야 기동한다 |
| `app.jwt.access-seconds` | 1800 | 액세스 30분 |
| `app.jwt.refresh-days` | **14** | 리프레시 14일(기획 명세대로 · 챕터 10 에서 7 → 14). 쿠키 `Max-Age` 와 `refresh_token.expires_at` 이 이 한 값을 쓴다 |
| `app.qr.secret` | `${QR_SECRET}` | QR 토큰 서명. **JWT 와 달라야 한다** |
| `app.qr.token-seconds` | 300 | QR 토큰 수명(회전으로 옛 토큰 무효) |
| `app.cookie.secure` | false / **prod true** | 리프레시 쿠키의 Secure |
| `cors.allowed-origins` | `${FRONTEND_URL:http://localhost:5173},http://localhost:3000` / prod 는 `${FRONTEND_URL}` | `allow-credentials` 가 true 라 `*` 불가 |

### 4-2. 도메인 설정

| 키 | 기본값 | 뜻 |
|---|---|---|
| `stamp.session-minutes` | 60 | GPS 인증 뒤 미션까지의 제한 시간 |
| (설정값 아님) `MissionScope` | SITE 50 · VERSE 30 · COMMON 20 | 행동과제 세 풀의 가중. 코드 상수라 배포로 바꾸지 않는다(챕터 10) |
| `stamp.default-travel-minutes` | 0 | 이동시간 기본값(자리 사이 거리로 계산) |
| `stamp.daily-limit` | 5 | 하루 도장 한도 |
| `stamp.evidence-daily-limit` | 2 | 하루 예외접수 한도 |
| `reward.audit-blocking` | false | 보상 심사 대기를 막을지 |
| `reward.review-threshold` | 60 | 사람이 봐야 하는 점수 기준 |
| `reward.min-gap-seconds` | 600 | 보상 최소 간격 |
| `reward.hoehyang-min-courses` | `${HOEHYANG_MIN_COURSES:12}` | **회향 하한** — 없으면 코스 하나만 끝내도 회향이 성립한다 |
| `manuscript.max-variants` | `${MANUSCRIPT_MAX_VARIANTS:3}` | 같은 자리 변형 상한 |
| `manuscript.import-max-rows` | 1000 | CSV 반입 행 상한 |
| `manuscript.import-max-bytes` | 2097152 | CSV 반입 크기 상한(2 MB) |
| `ebook.max-ready` | `${EBOOK_MAX_READY:5}` | 사용자당 남기는 READY 권수(주문 걸린 책 제외) |
| `ebook.daily-limit` | `${EBOOK_DAILY_LIMIT:3}` | 하루 개인 소장본 생성 상한 |
| `ebook.photo-max-px` | 1200 | 사진 긴 변 축소 |
| `ebook.jpeg-quality` | 0.8 | 재인코딩 품질 |
| `ebook.build-per-run` | 3 | 청소기 한 바퀴의 조판 건수 |
| `ebook.orphan-per-run` | 100 | 한 바퀴의 파일 삭제 건수 |
| `ebook.orphan-max-retry` | 5 | 넘으면 FAILED + `needs_review` |
| `ebook.token-per-run` | 1000 | 한 바퀴의 토큰 삭제 건수 |
| `ebook.token-grace-hours` | 24 | 만료 뒤 유예. **폐기됐어도 미만료면 남긴다** |
| `ebook.print-price` | `${PRINT_PRICE:15000}` | 부수당 가격(원). 결제 연동은 범위 밖 |
| `ebook.public-base-url` | `${PUBLIC_BASE_URL:http://localhost:8080}` | **인증서 QR 이 가리키는 주소.** prod 에서 localhost 가 들어 있으면 **기동이 막힌다**(챕터 9 보강) — 종이에 인쇄된 뒤에는 고칠 수 없어서다 |
| `storage.provider` | **local 만** (prod 도 local) | `provider` 를 읽는 코드가 없어 값이 갈래를 만들지 않는다. local 이 아니면 **기동이 막힌다**(챕터 9 보강). S3 는 배포 후 별도 결정 |
| `storage.bucket`·`endpoint` | 로컬 값 / prod `${STORAGE_BUCKET}`·`${STORAGE_ENDPOINT}` | presigned URL 을 만드는 데만 쓰인다 |
| `storage.secret-key` | `${STORAGE_SECRET_KEY:local-storage-secret}` | presigned 서명 |
| `storage.presign-seconds` | 300 | 서명 URL 수명 |
| `storage.max-upload-bytes` | 10485760 | 업로드 상한(10 MB) |
| `storage.local-dir` | `${STORAGE_LOCAL_DIR:}` | 비우면 임시폴더/`temple-stamp-storage` |
| `kakao.rest-key` | `${KAKAO_REST_API_KEY:}` | 없으면 관리자 장소 검색만 503 |
| `kakao.base-url`·`timeout-millis` | `https://dapi.kakao.com` · 3000 | |
| `spring.sql.init.mode` | `${SQL_INIT_MODE:always}` / **prod never** | 운영은 기동이 스키마를 건드리지 않는다 |
| `spring.datasource.hikari.maximum-pool-size` | 기본 10 / **prod 20** | 동시 20 이면 기본 10 은 늘 대기가 생긴다 |
| `spring.datasource.hikari.connection-timeout` | 기본 30초 / **prod 3초** | 예열 전 트래픽을 붙이면 3초 만에 500 이 된다 |
| `management.endpoints.web.exposure.include` | health | `base-path` 가 `/` 라 다른 것을 열면 루트에 열린다 |

### 4-3. 환경변수

| 이름 | 필수 | 기본값 | 어디서 막히나 |
|---|---|---|---|
| `DB_URL` | ✅ 항상 | 없음 | `RequiredEnvCheck` 가 기동을 막는다(`.env`) |
| `DB_USERNAME` | ✅ 항상 | 없음 | 같음(`.env`) |
| `DB_PASSWORD` | ✅ 항상 | 없음 | 같음(`.env.local`) |
| `JWT_SECRET` | ✅ 항상 | 없음 | 같음. Base64 32바이트 이상 |
| `QR_SECRET` | ✅ 항상 | 없음 | 같음 |
| `STORAGE_BUCKET` | ✅ prod | 로컬 값 | prod 프로파일에서만 기동을 막는다 |
| `STORAGE_ENDPOINT` | ✅ prod | 로컬 값 | 같음 |
| `FRONTEND_URL` | prod 사실상 필수 | 로컬 도메인 | 없으면 CORS 가 로컬 도메인으로 남는다 |
| `PUBLIC_BASE_URL` | **✅ prod** | localhost:8080 | localhost 가 들어 있으면 `RequiredEnvCheck` 가 기동을 막는다 |
| `STORAGE_SECRET_KEY` | 권장 | `local-storage-secret` | 서명 키가 로컬 값으로 남는다 |
| `KAKAO_REST_API_KEY` | 선택 | 빈 값 | 관리자 장소 검색만 503 |
| `SQL_INIT_MODE` | 선택 | always(prod 는 yml 이 never) | |
| `HOEHYANG_MIN_COURSES` | 선택 | 12 | |
| `EBOOK_MAX_READY`·`EBOOK_DAILY_LIMIT`·`PRINT_PRICE`·`MANUSCRIPT_MAX_VARIANTS` | 선택 | 5·3·15000·3 | |
| `STORAGE_LOCAL_DIR` | 선택 | 임시폴더 | |
| `SERVER_ADDRESS` | 선택 | 0.0.0.0 | |

---

## 5. 운영 절차

### 5-1. 편집자 역할 부여·회수

API 를 만들지 않았다. 관리자 계정 하나가 뚫리면 콘텐츠 전체를 바꾸는 문이 되기 때문이다.

```sql
UPDATE users SET role = 'EDITOR' WHERE email = '…';   -- 부여
UPDATE users SET role = 'USER'   WHERE email = '…';   -- 회수
```

### 5-2. `needs_review` 처리

"이미 사람이나 물건이 움직인 뒤에 되돌린 것" 에 켜진다 — 보상(`user_reward.needs_review`)과 인쇄주문(`print_order.needs_review`). 자동으로 꺼지지 않는다. 담당자가 실물을 확인한 뒤 손으로 내린다.

```sql
SELECT * FROM user_reward  WHERE needs_review = 1;
SELECT * FROM print_order  WHERE needs_review = 1;
```

### 5-3. 청소기 FAILED 처리

`storage_orphan` 은 다섯 번 실패하면 `FAILED` + `needs_review` 로 접는다. 5분마다 영원히 재시도하지 않게 하려는 것이다. 원인(대개 저장소 권한·경로)을 고친 뒤 되돌린다.

```sql
SELECT file_key, retry_count, last_error FROM storage_orphan WHERE status = 'FAILED';
UPDATE storage_orphan SET status = 'PENDING', retry_count = 0 WHERE storage_orphan_id = ?;
```

수동 실행은 `POST /api/admin/housekeeping/run`(ADMIN). 응답과 로그에 작업별 처리 건수가 같이 실린다.

### 5-4. 원고 CSV 반입

편집자 계정으로 `POST /api/editor/manuscripts/import`. 양식과 예시 10행은 `backend/docs/verify/body/` 에 있다. 상한은 1,000행·2 MB, **한 행이라도 틀리면 0건**이 들어간다(dryRun 으로 먼저 본다). 시군구는 사찰 주소와 대조한다.

### 5-5. QR 회전

`POST /api/admin/sites/{siteId}/qr`. 발급하면 그 사찰의 옛 QR 은 즉시 무효다. 현장 안내판을 바꾼 뒤에 돌린다.

### 5-6. 백업·복구

```bash
mysqldump -u<user> -p --single-transaction --routines --triggers --events \
  --set-gtid-purged=OFF --default-character-set=utf8mb4 <db> > backup.sql
mysql -u<user> -p --default-character-set=utf8mb4 <새 스키마> < backup.sql
```

리허설 실측: 덤프 0.17 MB·1초 미만, 복원 1.4초, 복원본으로 `run-all.sh` 를 돌려 원본과 요청·단언·채점이 모두 같았다.

**파일 저장소(전자책·인증서 PDF·사진)는 이 덤프에 없다.** 별도 백업을 **DB 덤프와 같은 시각에 하루 한 번**,
**30일 보관**한다. 시각이 어긋나면 DB 에는 있는 키의 파일이 백업에 없다. 복구 리허설에는 파일을 포함해
인증서 한 장의 `downloadUrl` 을 열어 `%PDF` 가 나오는 것까지 본다 — DB 만 되돌리고 "복구 성공" 이라고 적지 않는다.

---

## 6. 배포 체크리스트

`정리.md` §5 가 정본이다. 요점만 옮긴다.

| # | 항목 | 빠뜨리면 |
|---|---|---|
| 1 | **운영 DB 에 ALTER 네 파일**(alter-ch7 → ch1-withdraw → ch8 → ch9). 챕터 9 보강에서 빠졌던 `cert_serial` 표와 `idx_storage_orphan_pending` 색인이 파일에 들어갔다 | 표는 있는데 색인이 옛것이거나, `cert_serial` 이 없어 첫 인증서 발행에서 터진다. `db-check.sh` H17 이 사슬과 `schema.sql` 을 대조한다 |
| 2 | 운영용 JWT·QR 시크릿 별도 발급(서로 다르게) | 로컬 값이 운영에 남는다 |
| 3 | actuator 노출이 `health` 하나인지 — **파일 두 곳** | `/env` 가 루트에 열리고 거기 시크릿이 있다 |
| 4 | `app.cookie.secure: true` | Secure 없는 쿠키가 나간다 |
| 5 | `cors.allowed-origins` 를 배포 도메인으로 | 로컬 기본값이 남는다 |
| 6 | `PUBLIC_BASE_URL` 운영 도메인 | 인증서 QR 이 localhost 를 가리킨다 |
| 7 | `STORAGE_BUCKET`·`STORAGE_ENDPOINT` | prod 기동이 막힌다(막히는 것이 맞다) |
| 8 | MySQL 서버 `time_zone = '+09:00'` 명시 | 하루 한도·회상 날짜가 밀린다 |
| 9 | 한글 폰트가 jar 에 들어갔는지 | PDF 한글이 □ 로 나간다 — 예외도 경고도 없이 |
| 10 | 청소기는 단일 인스턴스 전제 | 여러 대로 늘리려면 분산 잠금이 먼저다 |
| 11 | 배포 직후 예열 요청 뒤 트래픽 개방 | 커넥션 획득 3초를 넘겨 500 이 난다(F §12) |
| 12 | **파일 저장소 백업 — DB 덤프와 같은 시각 일 1회 · 보관 30일 · 복구 리허설에 파일 포함** | DB 만 되돌리면 실물이 없다. 시각이 어긋나면 DB 에는 있는 키의 파일이 백업에 없다 |

배포 후 확인: `GET /health` → `{"status":"UP"}` · `GET /env` → 401.

---

## 7. 미결

| 무엇 | 상태 | 메모 |
|---|---|---|
| 청소기 세션 만료 UPDATE 와 미션 제출의 교착 | **닫힘(2026-09-07 보강)** | PK 순 200건 배치로 바꿨다. 재현 시험 `verify/load/deadlock-repro.sh` 가 상시로 교착 0·5xx 0 을 확인한다 |
| ALTER 파일에 빠졌던 색인·표 | **닫힘(2026-09-07 보강)** | 색인 두 줄은 `alter-ch9.sql`, `cert_serial` 표는 `alter-ch7.sql`. H17 이 상시 대조 |
| S3 구현 | **배포 후 별도 결정** | `provider` 는 local 만 받고 다른 값이면 기동이 막힌다 — 설정과 실제가 갈라지지 않는다. 붙일 때 `ObjectStorageClient` 의 넷만 바꾸고 그 검사를 푼다 |
| 결제 연동 | 범위 밖 | 붙으면 `CONFIRMED` 앞에 `PAID` 가 하나 더 필요하다 |
| ShedLock(분산 잠금) | 인스턴스를 늘릴 때 | 지금 넣으면 쓰지 않는 표가 는다 |
| 전자책 중간본(INTERIM) | 미구현 | 타입만 있고 만드는 길이 없다 |
| 탈퇴 유예 기간 | **없음(2026-09-07 결정 완료)** | 즉시 익명화. 되돌리고 싶은 사람은 새로 가입한다 — 도장·완주는 돌아오지 않는다 |
| SITE 행동과제 콘텐츠 | **비어 있음** | 규칙(챕터 10)은 돌고 내용이 없다. 사찰별 과제가 들어오기 전에는 VERSE·COMMON 두 풀로만 가중된다 |
| AGE30 외 계층의 COMMON 과제 | **비어 있음** | 시드는 AGE30 에 5편만 넣었다. 다른 계층은 콘텐츠 트랙이 채운다 |
| 미션 목록 API | 없음 | 등록(`POST /api/admin/contents/missions`)만 있다. 신설은 챕터 10 범위 밖이었다 |
| 마지막 로그인 칸 | 만들지 않음 | 별도 표(`login_history`)가 맞다 |
| 원고 140편 반입 | 콘텐츠 트랙 | 양식과 예시 10행까지 되어 있다 |
| 사찰 110곳 QR 위치 힌트·봉인사 좌표 | 답사 필요 | 좌표 미확보 1곳 |
| 시드 12코스 ACTIVE 전환 시점 | 운영 결정 | 지금은 전부 DRAFT, 데모 1개만 ACTIVE |
