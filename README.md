# temple-stamp 백엔드

한국 사찰 순례 스탬프 앱의 서버. 챕터 0~10 완료 · API 91 · 표 36 · JUnit 279.

## 처음 띄우기 (5줄)

```bash
# 1) 준비물 — JDK 21 · MySQL 8 · Node(검증에 newman 을 npx 로 받는다)
java -version && mysql --version && node -v

# 2) DB
mysql -uroot -p -e "CREATE DATABASE temple_stamp_project DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 3) 프로젝트 루트에 .env.local 을 만든다 (저장소에 없다 — 아래 「설정 파일 둘」 참고)
#    DB_PASSWORD / JWT_SECRET / QR_SECRET  ← 뒤 둘은 Base64 32바이트 이상, 서로 달라야 한다

# 4) 첫 기동은 반드시 seed 프로파일로 — 사찰 110·코스 12가 이때 들어간다
SPRING_PROFILES_ACTIVE=seed ./gradlew bootRun

# 5) 그다음부터는 그냥
./gradlew bootRun
```

## 검증 한 줄

```bash
bash backend/docs/verify/all.sh
```

newman 2회(같은 결과) → DB H1~H18 → 보안 S1~S25 → 교착 재현을 순서대로 돌리고,
하나라도 깨지면 그 자리에서 멈춘다. 마지막 줄이 **초록**이면 통과다(약 6분).

---

## 설정 파일 둘

| 파일 | 커밋 | 담는 것 |
|---|---|---|
| `.env` | **된다** | 비밀 아닌 기본값 — DB URL·프런트 오리진·서버 바인딩 |
| `.env.local` | **안 된다** | 비밀값 — DB 비밀번호·JWT/QR 시크릿·카카오 키 |

```bash
# .env.local 예시
DB_PASSWORD=...
JWT_SECRET=$(openssl rand -base64 32)
QR_SECRET=$(openssl rand -base64 32)   # JWT_SECRET 과 반드시 다른 값
STORAGE_SECRET_KEY=local-storage-secret
KAKAO_REST_API_KEY=
```

**두 시크릿이 같으면 기동 자체가 막힌다.** QR 은 사찰 현장에 붙어 있어 사진 한 장으로도 새어 나가는데,
같은 키면 그 QR 하나로 로그인 토큰을 위조할 수 있다.

값이 없으면 `RequiredEnvCheck` 가 빈을 만들기 전에 "무엇이 어느 파일에서 와야 하는지" 를 찍고 멈춘다.
자주 겪는 함정은 **실행 구성의 작업 디렉터리가 프로젝트 루트가 아닌 것** — 그러면 `.env` 를 못 읽는다.

## 알아 두면 좋은 것

- **4번을 건너뛰면** 사찰 5곳만 들어가고 검증이 단언 30건 실패로 끝난다.
- **카카오 키가 비면** 시더가 CSV 로 물러나 좌표를 못 채운다 — 새로 세운 DB 는 좌표 없는 사찰이 106곳이 된다.
- **윈도우 Git Bash 에서는 요청 본문에 한글을 직접 넣지 말 것.** argv 가 ANSI 로 바뀌어 `400 COMMON-4004` 가 난다.
  `curl --data-binary @body/xxx.json` 처럼 파일로 넘긴다.

## 더 볼 곳

| 무엇 | 어디 |
|---|---|
| 기준 문서(가장 두껍다) | `backend/docs/정리.md` |
| 전체 지도 | `종합정리.md` |
| 꼭 남겨야 할 것만 | `꼭남겨야할내용.md` |
| 인수인계 한 장 | `backend/docs/audit/인수인계_temple-stamp_최종.md` |
| 프론트 전달 목록 | `backend/docs/audit/frontend-handoff.md` · API 타입 `backend/docs/frontend/api-types.d.ts` |
| 손으로 확인하는 10단계 | `postman/예성-손확인.md` |
