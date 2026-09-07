# Render 배포 가이드(한글) · 예성이 정해야 할 것 · GitHub 업로드 · 학원 이동 (2026-09-07)

## 1. Render 조사 요약 (2026년 기준, 출처: Render 문서·Aiven 문서·Cloudflare R2 문서)

| 항목 | 사실 | 우리에게 뜻하는 것 |
|---|---|---|
| Java 지원 | Render는 Java 네이티브 런타임이 **없다** → **Dockerfile 필수** | Dockerfile(빌드 `./gradlew bootJar`, 실행 `eclipse-temurin:21-jre`) 하나를 배포 트랙에서 만든다 |
| 무료 티어 | 512 MB RAM · 15분 무요청이면 **잠들고** 깨는 데 약 1분 · 월 750 인스턴스시간 | 명세 E-13 "콜드스타트 60초 + 재시도"가 이걸 예상한 것. 시연용은 되지만 실사용은 부적합 |
| 파일 시스템 | **모든 티어가 기본 휘발성** — 재배포·재시작·잠들기마다 로컬 파일 삭제. **영구 디스크는 유료 서비스에서만**, $0.25/GB/월 | 지금 저장소 구현은 `local`(서버 디스크)뿐 → **무료 티어면 사진·PDF가 사라진다** |
| 유료 | Starter **$7/월**부터, 잠들지 않음 | 실사용 전에는 Starter로 |
| MySQL | Render는 **관리형 MySQL 없음**(PostgreSQL만) | 외부 MySQL 필요 |
| 외부 MySQL | **Aiven 무료**: 1 GB 저장·1 GB RAM·**최대 연결 76**·TLS 필수·지역은 "Asia Pacific" 묶음만(서울 지정 불가)·오래 안 쓰면 꺼짐 / PlanetScale 무료 없음($39~) / TiDB Cloud Starter 무료(싱가포르·도쿄, MySQL 호환이지만 100%는 아님) / Railway 유료 | **Aiven 무료** 권장(예성이 이미 다른 프로젝트에서 씀). Hikari 풀을 운영 20 → **10**으로 |
| 오브젝트 스토리지 | **Cloudflare R2** 무료 10 GB·읽기 1천만/월·**전송료 0** / Backblaze B2 10 GB / AWS S3 5 GB(12개월) | R2가 최선. 단 **우리 코드에 S3 구현이 없다**(챕터 9에서 보류) |
| 지역 | Render **싱가포르** 리전 있음 | 한국에서 가장 가까움 |
| 도메인·TLS | 커스텀 도메인 무료, TLS 자동 | CNAME 하나 |
| 고정 IP | 기본은 공용 풀(2025-10 변경됨). 고정 IP는 유료 부가 | Aiven 무료는 IP 허용목록이 없어 **불필요** |
| 메모리 | 512 MB에서 `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=70` | 배포 트랙에 고정 |
| 헬스체크 | Render Health Check Path 설정 | 우리는 `/health` |

## 2. 예성이 정할 것 — 두 갈래

| | (a) 무료 조합 | (b) 유료 최소 |
|---|---|---|
| Render | 무료(잠듦) | **Starter $7** + 영구 디스크 5 GB($1.25) |
| MySQL | Aiven 무료 | Aiven 무료 |
| 파일 | Cloudflare R2 → **S3 구현 필요(챕터 11, 약 8시간)** | 서버 디스크(`local`) → **코드 변경 없음** |
| 월 비용 | 0원 | 약 $8.3 |
| 단점 | 콜드스타트 1분+ · S3 코드 추가 | 돈 |
| 권고 | 시연·심사용 | **실사용 개시용** |

**제 권고: (b)로 1차 개시, R2는 사용자 늘면 챕터 11로.** 이유: 지금 코드가 그대로 돌고(local 저장소 + 단일 인스턴스 청소기 전제와도 맞음), 콜드스타트 없이 QR 현장 인증이 된다. 무료로 가려면 (a)인데 S3 구현이 먼저다.

## 3. "정해지면 알려 주세요"의 정확한 목록

아래 8줄을 채워서 보내 주시면 배포 트랙 정본을 그 값으로 씁니다. **비밀번호·시크릿은 절대 보내지 마세요**(호스트·이름만).

| # | 항목 | 예시 |
|---|---|---|
| 1 | Render 요금제 | (b) Starter + 디스크 5 GB |
| 2 | MySQL | Aiven 무료 — 만든 뒤 **호스트·포트·DB이름·사용자명**(비번 제외). 예 `xxx.aivencloud.com:12345 / defaultdb / avnadmin`, 지역 묶음 Asia Pacific |
| 3 | 백엔드 도메인 | 없으면 `temple-stamp.onrender.com` 그대로. 있으면 도메인명(예 `api.templestamp.kr`) |
| 4 | 프론트 도메인(FRONTEND_URL) | 프론트를 어디 올릴지(Netlify?) + 주소. CORS에 들어감 |
| 5 | PUBLIC_BASE_URL | 인증서 QR에 찍히는 주소 = 보통 3번과 같음. **종이로 나가면 못 바꿈** |
| 6 | 파일 저장소 | (b)면 "디스크", (a)면 "R2 버킷 이름" |
| 7 | 카카오 REST API 키 | 발급했는지 여부만(키 자체는 Render 환경변수에 직접 입력) |
| 8 | GitHub 저장소 | 이름·private 여부(예 `yesung/temple-stamp-project`, private) |

시크릿 4개(`DB_PASSWORD`·`JWT_SECRET`·`QR_SECRET`·`STORAGE_SECRET_KEY`)와 카카오 키는 **Render 대시보드 환경변수에 예성이 직접** 넣습니다. Claude Code에게도 안 보여 줍니다.

## 4. GitHub 업로드 — 챕터 10·콘솔이 끝난 뒤 Claude Code에 붙여 넣을 지시문

```
[GitHub 업로드] 저장소에 git이 없다. 아래 순서로 올려라. 시크릿이 하나라도 올라가면 실패다.
STEP 1: .gitignore 작성 — .env.local · build/ · .gradle/ · *.log · logs/ · 로컬 저장소 디렉터리(STORAGE_LOCAL_DIR 기본 경로·temple-stamp-storage/) · .idea/ · node_modules/ · 덤프 *.sql.gz · docs/audit/verify-f-logs/·ch9-hardening-logs/ 안의 원문 로그(요약 md는 남긴다) · postman 결과 json.
STEP 2: 시크릿 검사 — 전체 파일에서 비밀번호·JWT·QR·저장소 키·카카오 키 형태 문자열 grep(.env는 값이 아니라 변수명만 있는지 확인). 발견되면 그 파일을 ignore에 넣거나 값을 지우고 다시 검사. 결과 0건을 보고.
STEP 3: git init → 기본 브랜치 main → 첫 커밋 "temple-stamp backend ch0~10 + dev console + docs". 커밋 전 git status에 .env.local·build/가 없는지 확인.
STEP 4: 원격은 예성이 만든 private 저장소 URL(예성이 알려 줌) → git remote add origin → git push -u origin main. 예성이 GitHub 로그인/토큰 입력을 직접 한다(Claude Code는 토큰을 요구하지 않는다).
STEP 5: README.md 맨 위에 "처음 띄우기 5줄"(JDK 21·MySQL 8·Node 18+newman·.env.local 만들기·SPRING_PROFILES_ACTIVE=seed ./gradlew bootRun)과 "검증 한 줄"(bash backend/docs/verify/all.sh)이 있는지 확인. 없으면 추가하고 두 번째 커밋.
STEP 6: 다른 폴더에 git clone → README대로 첫 기동 → all.sh 초록까지 시간 기록(감사 G 리허설과 같은 방식). 이것이 "학원에서 이어서 할 수 있다"의 증명이다.
마지막 줄: github: 커밋 N · 시크릿 0 · 클론 첫기동 N분 · all.sh 통과
```

## 5. 학원 PC로 가져갈 것 (GitHub에 **없는** 것만)

| # | 무엇 | 왜 | 방법 |
|---|---|---|---|
| 1 | `.env.local` | 시크릿(DB 비번·JWT·QR·저장소 키·카카오 키). gitignore라 저장소에 없음 | USB 또는 비밀번호 관리자. 학원 PC의 MySQL 비번이 다르면 DB_PASSWORD만 바꾼다 |
| 2 | 인수인계 문서 3개 | 새 대화창이 읽을 것 | `인수인계_새창용_2026-09-07.md` · `backend/docs/정리.md` · `backend/docs/audit/종합정리.md` — 셋 다 저장소에 있지만 **첫 대화에 첨부**해야 하므로 위치만 기억 |
| 3 | 기획 명세·이미지 | 명세 md는 저장소에 있음. 흐름도 이미지는 노션에만 | 노션 접근 가능하면 불필요 |
| 4 | 설치 목록 | 학원 PC에 없을 수 있음 | JDK 21 · MySQL 8(`time_zone=+09:00`) · Node 18+ 와 `npm i -g newman` · Git · Claude Code · (프론트용) Node 20 |
| 5 | 로컬 DB 덤프 | **불필요** — `seed` 프로파일로 4~7분에 재현됨. 시드 밖 데이터(콘솔 계정 등)는 버려도 됨 | — |
| 6 | 카카오 키 | REST 키(백엔드)·JavaScript 키(프론트) | 카카오 개발자 콘솔에서 학원 PC 도메인(`localhost:5173`) 추가 |

학원에서 첫 30분: `git clone` → `.env.local` 복사 → README 5줄 → `all.sh` 초록 → 새 대화창에 인수인계 첨부 → "FE-1 시작".
