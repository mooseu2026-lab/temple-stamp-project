# 중간 점검 A — 챕터 0~3 전체 API Postman 회귀 세트 + 실현 기능 목록 [예제 18]

> 작성일 2026-09-05 · 대상: 지금까지 만든 모든 엔드포인트(인증·회원·마스터 조회·가는 법·관리자·카카오·health)
> 위치: `backend/docs/textbook/verify-ch0-3.md` · 산출물: `postman/temple-stamp-all.postman_collection.json` + `backend/docs/audit/feature-status-ch0-3.md`
> 챕터 2-14 세트(9종)는 "규약 확인"이었다. 이 세트는 **"기능이 실제로 되는가"** 를 F-번호 단위로 묻는다. 두 세트는 역할이 다르므로 둘 다 유지한다.

---

## 1. 지금까지 실현된 기능 — 명세 F-번호 기준

"실현"의 기준은 셋 다 만족: ① 엔드포인트가 명세 §4 경로로 존재 ② 정상 경로가 실제 MySQL로 동작 ③ 주요 예외가 명세 에러코드로 나간다. Claude Code가 아래 **결과** 열을 채운다(✅ 셋 다 / ⚠ 일부 / ❌ 미실현 / — 범위 밖).

| # | 기능(명세) | 챕터 | 엔드포인트 | 점검 요청(§2 컬렉션) | 결과 |
|---|---|---|---|---|---|
| F-02 | 회원가입·로그인(BCrypt·실패횟수) | 1 | `POST /api/auth/signup` · `/login` | A01·A02·A03·A04·A05·A06 | |
| F-02' | 계정 잠금 5회→15분, 403 | 1 | `POST /api/auth/login` | A07(×5)·A08 | |
| F-03 | 토큰 재발급(쿠키 회전) | 1 | `POST /api/auth/refresh` | A09·A10·A11 | |
| F-03' | 재사용 탐지 → 전 기기 폐기 | 1 | `POST /api/auth/refresh` | A12·A13 | |
| — | 로그아웃(전 기기) | 1 | `POST /api/auth/logout` | A14·A15 | |
| F-22 | 내 정보·설정 변경(tier·locale·알림·닉네임) | 1 | `GET/PATCH /api/users/me` | U01·U02·U03·U04 | |
| — | 약관 동의·조회·철회 | 1 | `/api/users/me/agreements` | U05·U06·U07 | |
| F-04 | 권역 9 + 코스 수 + code | 2 | `GET /api/regions` | M01 | |
| F-04 | 코스 목록(선택 인증 → progress) | 2 | `GET /api/courses` | M02·M03 | |
| F-04 | 코스 상세(사찰 5곳 좌표 일괄) | 2 | `GET /api/courses/{id}` | M04·M05 | |
| — | 사찰 기본 정보 | 2 | `GET /api/sites/{id}` | M06·M07 | |
| F-05 | 싱글페이지 8블록 1회 조립 | 2 | `GET /api/sites/{id}/page` | M08·M09 | |
| F-06 | 확장문구 tier·구절 선택(미리보기 seen 미기록) | 2 | 〃 | M10·M11 + SQL | |
| F-07 | 미션 미리보기(null 허용) | 2 | 〃 | M08 | |
| F-20 | 다국어(site_i18n, 쿼리→헤더→users.locale→ko) | 2 | `/page?locale=` | M12·M13 | |
| — | 오관게 5구 | 2 | `GET /api/verses` · `/{n}` | M14·M15·M16 | |
| F-21 | 사찰 가는 법 7자리 | 2 | `GET /api/sites/{id}/guide` | M17 | |
| 흐름7 | 관리자 카카오 장소 검색 | 3 | `GET /api/admin/kakao/places` | K01·K02·K03 | |
| 흐름7 | 관리자 사찰 등록·수정(ko 필수·UPSERT) | 3 | `POST/PUT /api/admin/sites` | S01·S02·S03·S04 | |
| 흐름7 | 사찰 상태 전이(ACTIVE 조건) | 3 | `PATCH .../sites/{id}/status` | S05·S06 | |
| 흐름7 | 뷰포인트·뱃지 UPSERT | 3 | `POST .../viewpoints` · `/badges` | S07·S08 | |
| 흐름7 | 관리자 사찰 목록·검색 | 3 | `GET /api/admin/sites?q=` | S09 | |
| 흐름7 | 코스 등록(5곳·자리·구절·중복) | 3 | `POST/PUT /api/admin/courses` | C01·C02·C03·C04 | |
| 흐름7 | 코스 상태 전이 | 3 | `PATCH .../courses/{id}/status` | C05·C06 | |
| 흐름7 | 이동시간 일괄 UPSERT | 3 | `PUT /api/admin/site-distances` | C07 | |
| 규약 | 좌표 필드 차단 COMMON-4001 | 1 | 모든 non-admin 쓰기 | G01 | |
| 규약 | ADMIN 권한 분리(USER→403) | 1 | `/api/admin/**` | G02 | |
| 규약 | X-Request-Id 32자·timestamp +09:00·null 키 유지 | 1 | 전 응답 | 공통 Tests | |
| 배포 | `/health` UP, 나머지 actuator 401 | 2 | `GET /health` · `/env` | G03·G04 | |

**아직 범위 밖(❌가 아니라 —)**: F-01 인트로(서버 없음), F-08~F-11 스탬프 3단계, F-12 사진, F-13 여권, F-14·15 생각상자, F-16·17 명상, F-18·19 전자책·인증서, F-23 보상, QR 발급. → 챕터 4~9.

---

## 2. Postman 컬렉션 — 폴더 6개, 요청 52건

실행 순서가 곧 시나리오다. **폴더 순서를 바꾸면 깨진다** (A에서 만든 토큰·쿠키를 뒤에서 쓴다).
`newman`은 같은 실행 안에서 쿠키 항아리를 공유하므로 refresh 쿠키가 A09~A13에 자동으로 실린다.

공통 Tests(컬렉션 레벨, `/health`·`/env` 제외):
```javascript
pm.test('X-Request-Id 32자', () => pm.expect(pm.response.headers.get('X-Request-Id')).to.have.lengthOf(32));
pm.test('timestamp +09:00', () => pm.expect(pm.response.json().timestamp).to.match(/\+09:00$/));
pm.test('success 키 존재', () => pm.expect(pm.response.json()).to.have.property('success'));
```

**파일** `postman/temple-stamp-all.postman_collection.json`

```json
{
  "info": { "name": "temple-stamp — 챕터 0~3 전체 회귀", "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json" },
  "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
    "const p = pm.request.url.getPath();",
    "if (!p.endsWith('/health') && !p.endsWith('/env')) {",
    "  pm.test('X-Request-Id 32자', () => pm.expect(pm.response.headers.get('X-Request-Id')).to.have.lengthOf(32));",
    "  pm.test('timestamp +09:00', () => pm.expect(pm.response.json().timestamp).to.match(/\\+09:00$/));",
    "  pm.test('success 키 존재', () => pm.expect(pm.response.json()).to.have.property('success'));",
    "}"
  ]}}],
  "variable": [{ "key": "H", "value": "" }],
  "item": [
    { "name": "A 인증 (F-02·F-03)", "item": [
      { "name": "A01 가입 — 정상 (새 이메일, 매 실행 고유)", "event": [
        { "listen": "prerequest", "script": { "type": "text/javascript", "exec": [
          "const stamp = Date.now();",
          "pm.environment.set('email', `reg-${stamp}@test.com`);",
          "pm.environment.set('password', 'Check!2026');"
        ]}},
        { "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('201', () => pm.response.to.have.status(201));",
          "const u = pm.response.json().data;",
          "pm.test('password 계열 필드가 응답에 없다', () => pm.expect(JSON.stringify(u)).to.not.match(/password|loginFail|lockedUntil/));",
          "pm.test('tier 기본 AGE30', () => pm.expect(u.tier).to.eql('AGE30'));",
          "pm.environment.set('userId', u.userId);"
        ]}}],
        "request": { "method": "POST", "header": [{ "key": "Content-Type", "value": "application/json" }],
          "url": { "raw": "{{baseUrl}}/api/auth/signup", "host": ["{{baseUrl}}"], "path": ["api","auth","signup"] },
          "body": { "mode": "raw", "raw": "{\"email\":\"{{email}}\",\"password\":\"{{password}}\",\"nickname\":\"회귀검증\"}" } } },
      { "name": "A02 가입 — 중복 이메일 409 AUTH-4090", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('409 AUTH-4090', () => { pm.response.to.have.status(409); pm.expect(pm.response.json().error.code).to.eql('AUTH-4090'); });"
        ]}}],
        "request": { "method": "POST", "header": [{ "key": "Content-Type", "value": "application/json" }],
          "url": { "raw": "{{baseUrl}}/api/auth/signup", "host": ["{{baseUrl}}"], "path": ["api","auth","signup"] },
          "body": { "mode": "raw", "raw": "{\"email\":\"{{email}}\",\"password\":\"{{password}}\",\"nickname\":\"회귀검증\"}" } } },
      { "name": "A03 가입 — 비밀번호 규칙 위반 400 COMMON-4000 fields[password]", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('400 COMMON-4000', () => { pm.response.to.have.status(400); pm.expect(pm.response.json().error.code).to.eql('COMMON-4000'); });",
          "pm.test('fields 에 password', () => pm.expect(JSON.stringify(pm.response.json().error.fields)).to.include('password'));"
        ]}}],
        "request": { "method": "POST", "header": [{ "key": "Content-Type", "value": "application/json" }],
          "url": { "raw": "{{baseUrl}}/api/auth/signup", "host": ["{{baseUrl}}"], "path": ["api","auth","signup"] },
          "body": { "mode": "raw", "raw": "{\"email\":\"x-{{email}}\",\"password\":\"onlyletters\",\"nickname\":\"회귀검증\"}" } } },
      { "name": "A04 로그인 — 정상 (accessToken·expiresIn 1800·refresh 쿠키)", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('200', () => pm.response.to.have.status(200));",
          "const d = pm.response.json().data;",
          "pm.test('Bearer / 1800', () => { pm.expect(d.tokenType).to.eql('Bearer'); pm.expect(d.expiresIn).to.eql(1800); });",
          "pm.test('refresh 쿠키 HttpOnly + Path=/api/auth', () => { const c = pm.response.headers.get('Set-Cookie'); pm.expect(c).to.match(/HttpOnly/i); pm.expect(c).to.match(/Path=\\/api\\/auth/i); });",
          "pm.test('본문에 refresh 토큰 없음', () => pm.expect(JSON.stringify(d)).to.not.match(/refresh/i));",
          "pm.environment.set('accessToken', d.accessToken);"
        ]}}],
        "request": { "method": "POST", "header": [{ "key": "Content-Type", "value": "application/json" }],
          "url": { "raw": "{{baseUrl}}/api/auth/login", "host": ["{{baseUrl}}"], "path": ["api","auth","login"] },
          "body": { "mode": "raw", "raw": "{\"email\":\"{{email}}\",\"password\":\"{{password}}\"}" } } },
      { "name": "A05 로그인 — 비밀번호 틀림 401 AUTH-4011 (계정 존재 여부 안 새어야 함)", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('401 AUTH-4011', () => { pm.response.to.have.status(401); pm.expect(pm.response.json().error.code).to.eql('AUTH-4011'); });",
          "pm.environment.set('msgWrongPw', pm.response.json().error.message);"
        ]}}],
        "request": { "method": "POST", "header": [{ "key": "Content-Type", "value": "application/json" }],
          "url": { "raw": "{{baseUrl}}/api/auth/login", "host": ["{{baseUrl}}"], "path": ["api","auth","login"] },
          "body": { "mode": "raw", "raw": "{\"email\":\"{{email}}\",\"password\":\"Wrong!0000\"}" } } },
      { "name": "A06 로그인 — 없는 계정도 같은 401 AUTH-4011 같은 문구", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('401 AUTH-4011', () => { pm.response.to.have.status(401); pm.expect(pm.response.json().error.code).to.eql('AUTH-4011'); });",
          "pm.test('문구가 A05 와 동일 — 계정 존재 여부 비노출', () => pm.expect(pm.response.json().error.message).to.eql(pm.environment.get('msgWrongPw')));"
        ]}}],
        "request": { "method": "POST", "header": [{ "key": "Content-Type", "value": "application/json" }],
          "url": { "raw": "{{baseUrl}}/api/auth/login", "host": ["{{baseUrl}}"], "path": ["api","auth","login"] },
          "body": { "mode": "raw", "raw": "{\"email\":\"nobody-{{email}}\",\"password\":\"Wrong!0000\"}" } } },
      { "name": "A07 잠금용 계정 가입 + 실패 5회 루프", "event": [
        { "listen": "prerequest", "script": { "type": "text/javascript", "exec": [
          "if (!pm.environment.get('lockEmail')) { pm.environment.set('lockEmail', `lock-${Date.now()}@test.com`); pm.environment.set('lockN', 0); }"
        ]}},
        { "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('가입 201/409', () => pm.expect([201,409]).to.include(pm.response.code));"
        ]}}],
        "request": { "method": "POST", "header": [{ "key": "Content-Type", "value": "application/json" }],
          "url": { "raw": "{{baseUrl}}/api/auth/signup", "host": ["{{baseUrl}}"], "path": ["api","auth","signup"] },
          "body": { "mode": "raw", "raw": "{\"email\":\"{{lockEmail}}\",\"password\":\"{{password}}\",\"nickname\":\"잠금검증\"}" } } },
      { "name": "A07x 실패 로그인 (setNextRequest 로 5회 반복)", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "let n = Number(pm.environment.get('lockN')) + 1; pm.environment.set('lockN', n);",
          "if (n < 5) { pm.test(`실패 ${n}회: 401 AUTH-4011`, () => { pm.response.to.have.status(401); pm.expect(pm.response.json().error.code).to.eql('AUTH-4011'); }); pm.execution.setNextRequest('A07x 실패 로그인 (setNextRequest 로 5회 반복)'); }",
          "else { pm.test('5회째: 403 AUTH-4031 잠금 (4회에 걸리면 SET 순서 버그 재발)', () => { pm.response.to.have.status(403); pm.expect(pm.response.json().error.code).to.eql('AUTH-4031'); }); }"
        ]}}],
        "request": { "method": "POST", "header": [{ "key": "Content-Type", "value": "application/json" }],
          "url": { "raw": "{{baseUrl}}/api/auth/login", "host": ["{{baseUrl}}"], "path": ["api","auth","login"] },
          "body": { "mode": "raw", "raw": "{\"email\":\"{{lockEmail}}\",\"password\":\"Wrong!0000\"}" } } },
      { "name": "A08 잠긴 계정에 올바른 비밀번호 → 여전히 403 AUTH-4031, message 에 15분", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('403 AUTH-4031', () => { pm.response.to.have.status(403); pm.expect(pm.response.json().error.code).to.eql('AUTH-4031'); });",
          "pm.test('message 에 남은 시간', () => pm.expect(pm.response.json().error.message).to.match(/분/));"
        ]}}],
        "request": { "method": "POST", "header": [{ "key": "Content-Type", "value": "application/json" }],
          "url": { "raw": "{{baseUrl}}/api/auth/login", "host": ["{{baseUrl}}"], "path": ["api","auth","login"] },
          "body": { "mode": "raw", "raw": "{\"email\":\"{{lockEmail}}\",\"password\":\"{{password}}\"}" } } },
      { "name": "A09 refresh — 정상 회전 (새 accessToken, 새 쿠키)", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('200', () => pm.response.to.have.status(200));",
          "const d = pm.response.json().data;",
          "pm.test('새 accessToken', () => pm.expect(d.accessToken).to.not.eql(pm.environment.get('accessToken')));",
          "pm.test('새 refresh 쿠키 발급', () => pm.expect(pm.response.headers.get('Set-Cookie')).to.match(/HttpOnly/i));",
          "pm.environment.set('oldCookieHeader', pm.request.headers.get('Cookie') || '');",
          "pm.environment.set('accessToken', d.accessToken);"
        ]}}],
        "request": { "method": "POST", "header": [], "url": { "raw": "{{baseUrl}}/api/auth/refresh", "host": ["{{baseUrl}}"], "path": ["api","auth","refresh"] } } },
      { "name": "A10 refresh — 회전 후 새 쿠키로 다시 → 200 (연속 회전)", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('200', () => pm.response.to.have.status(200));",
          "pm.environment.set('accessToken', pm.response.json().data.accessToken);",
          "pm.environment.set('liveRefreshCookie', (pm.response.headers.get('Set-Cookie')||'').split(';')[0]);"
        ]}}],
        "request": { "method": "POST", "header": [], "url": { "raw": "{{baseUrl}}/api/auth/refresh", "host": ["{{baseUrl}}"], "path": ["api","auth","refresh"] } } },
      { "name": "A11 옛 경로 /reissue → 404 COMMON-4040 (별칭 없음 확정)", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('404 COMMON-4040', () => { pm.response.to.have.status(404); pm.expect(pm.response.json().error.code).to.eql('COMMON-4040'); });"
        ]}}],
        "request": { "method": "POST", "header": [], "url": { "raw": "{{baseUrl}}/api/auth/reissue", "host": ["{{baseUrl}}"], "path": ["api","auth","reissue"] } } },
      { "name": "A12 재사용 탐지 — A09 에서 이미 쓴 쿠키를 다시 보냄 → 401 AUTH-4015", "event": [
        { "listen": "prerequest", "script": { "type": "text/javascript", "exec": [
          "// 쿠키 항아리의 최신 쿠키 대신, A09 응답 이전에 쓰였던(폐기된) 값을 수동으로 싣는다",
          "pm.request.headers.upsert({ key: 'Cookie', value: pm.environment.get('usedRefreshCookie') || '' });"
        ]}},
        { "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('401 AUTH-4015 재사용 탐지', () => { pm.response.to.have.status(401); pm.expect(pm.response.json().error.code).to.eql('AUTH-4015'); });"
        ]}}],
        "request": { "method": "POST", "header": [], "url": { "raw": "{{baseUrl}}/api/auth/refresh", "host": ["{{baseUrl}}"], "path": ["api","auth","refresh"] } } },
      { "name": "A13 탐지 후 살아 있던 최신 쿠키도 죽어 있어야 함 → 401 (AUTH-4012 또는 4014)", "event": [
        { "listen": "prerequest", "script": { "type": "text/javascript", "exec": [
          "pm.request.headers.upsert({ key: 'Cookie', value: pm.environment.get('liveRefreshCookie') || '' });"
        ]}},
        { "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('401 — 전 기기 폐기 확인', () => { pm.response.to.have.status(401); pm.expect(['AUTH-4012','AUTH-4014','AUTH-4015']).to.include(pm.response.json().error.code); });"
        ]}}],
        "request": { "method": "POST", "header": [], "url": { "raw": "{{baseUrl}}/api/auth/refresh", "host": ["{{baseUrl}}"], "path": ["api","auth","refresh"] } } },
      { "name": "A14 재로그인 후 로그아웃 → 200, 쿠키 삭제(Max-Age=0)", "event": [
        { "listen": "prerequest", "script": { "type": "text/javascript", "exec": [
          "pm.sendRequest({ url: pm.environment.get('baseUrl') + '/api/auth/login', method: 'POST', header: { 'Content-Type': 'application/json' },",
          "  body: { mode: 'raw', raw: JSON.stringify({ email: pm.environment.get('email'), password: pm.environment.get('password') }) } },",
          "  (err, res) => { pm.environment.set('accessToken', res.json().data.accessToken); });"
        ]}},
        { "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('200', () => pm.response.to.have.status(200));",
          "pm.test('쿠키 삭제', () => pm.expect(pm.response.headers.get('Set-Cookie')).to.match(/Max-Age=0/i));"
        ]}}],
        "request": { "method": "POST", "header": [{ "key": "Authorization", "value": "Bearer {{accessToken}}" }], "url": { "raw": "{{baseUrl}}/api/auth/logout", "host": ["{{baseUrl}}"], "path": ["api","auth","logout"] } } },
      { "name": "A15 로그아웃 뒤 refresh → 401 (전 기기 폐기)", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('401', () => pm.response.to.have.status(401));",
          "// 뒤 폴더용 재로그인",
          "pm.sendRequest({ url: pm.environment.get('baseUrl') + '/api/auth/login', method: 'POST', header: { 'Content-Type': 'application/json' },",
          "  body: { mode: 'raw', raw: JSON.stringify({ email: pm.environment.get('email'), password: pm.environment.get('password') }) } },",
          "  (err, res) => { pm.environment.set('accessToken', res.json().data.accessToken); });"
        ]}}],
        "request": { "method": "POST", "header": [], "url": { "raw": "{{baseUrl}}/api/auth/refresh", "host": ["{{baseUrl}}"], "path": ["api","auth","refresh"] } } }
    ]},

    { "name": "U 회원 (F-22)", "item": [
      { "name": "U01 GET /users/me — 토큰 없음 401 AUTH-4013", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('401 AUTH-4013', () => { pm.response.to.have.status(401); pm.expect(pm.response.json().error.code).to.eql('AUTH-4013'); });"
        ]}}],
        "request": { "method": "GET", "header": [], "url": { "raw": "{{baseUrl}}/api/users/me", "host": ["{{baseUrl}}"], "path": ["api","users","me"] } } },
      { "name": "U02 GET /users/me — 정상, 내부 필드 비노출", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('200', () => pm.response.to.have.status(200));",
          "const u = pm.response.json().data;",
          "pm.test('userId 일치 — 토큰 sub 에서 나온 값', () => pm.expect(u.userId).to.eql(Number(pm.environment.get('userId'))));",
          "pm.test('password/loginFailCount/lockedUntil 없음', () => pm.expect(JSON.stringify(u)).to.not.match(/password|loginFail|locked/));"
        ]}}],
        "request": { "method": "GET", "header": [{ "key": "Authorization", "value": "Bearer {{accessToken}}" }], "url": { "raw": "{{baseUrl}}/api/users/me", "host": ["{{baseUrl}}"], "path": ["api","users","me"] } } },
      { "name": "U03 PATCH /users/me — tier RIDER + locale en (null=변경 없음)", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('200', () => pm.response.to.have.status(200));",
          "const u = pm.response.json().data;",
          "pm.test('tier RIDER, locale en, 닉네임 유지', () => { pm.expect(u.tier).to.eql('RIDER'); pm.expect(u.locale).to.eql('en'); pm.expect(u.nickname).to.eql('회귀검증'); });"
        ]}}],
        "request": { "method": "PATCH", "header": [{ "key": "Authorization", "value": "Bearer {{accessToken}}" }, { "key": "Content-Type", "value": "application/json" }],
          "url": { "raw": "{{baseUrl}}/api/users/me", "host": ["{{baseUrl}}"], "path": ["api","users","me"] },
          "body": { "mode": "raw", "raw": "{\"tier\":\"RIDER\",\"locale\":\"en\"}" } } },
      { "name": "U04 PATCH /users/me — 잘못된 tier 400 (USER-4001 또는 COMMON-4003)", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('400', () => pm.response.to.have.status(400));",
          "pm.test('code USER-4001 | COMMON-4003 | COMMON-4004', () => pm.expect(['USER-4001','COMMON-4003','COMMON-4004']).to.include(pm.response.json().error.code));"
        ]}}],
        "request": { "method": "PATCH", "header": [{ "key": "Authorization", "value": "Bearer {{accessToken}}" }, { "key": "Content-Type", "value": "application/json" }],
          "url": { "raw": "{{baseUrl}}/api/users/me", "host": ["{{baseUrl}}"], "path": ["api","users","me"] },
          "body": { "mode": "raw", "raw": "{\"tier\":\"AGE99\"}" } } },
      { "name": "U05 POST /users/me/agreements — LOCATION_SERVICE 동의", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('200/201', () => pm.expect([200,201]).to.include(pm.response.code));",
          "pm.test('agreementType 문자열', () => pm.expect(pm.response.json().data.agreementType).to.eql('LOCATION_SERVICE'));"
        ]}}],
        "request": { "method": "POST", "header": [{ "key": "Authorization", "value": "Bearer {{accessToken}}" }, { "key": "Content-Type", "value": "application/json" }],
          "url": { "raw": "{{baseUrl}}/api/users/me/agreements", "host": ["{{baseUrl}}"], "path": ["api","users","me","agreements"] },
          "body": { "mode": "raw", "raw": "{\"agreementType\":\"LOCATION_SERVICE\",\"version\":1}" } } },
      { "name": "U06 같은 버전 재동의 → 오류 아님(INSERT IGNORE), 최초 agreedAt 유지", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('200/201 — 멱등', () => pm.expect([200,201]).to.include(pm.response.code));"
        ]}}],
        "request": { "method": "POST", "header": [{ "key": "Authorization", "value": "Bearer {{accessToken}}" }, { "key": "Content-Type", "value": "application/json" }],
          "url": { "raw": "{{baseUrl}}/api/users/me/agreements", "host": ["{{baseUrl}}"], "path": ["api","users","me","agreements"] },
          "body": { "mode": "raw", "raw": "{\"agreementType\":\"LOCATION_SERVICE\",\"version\":1}" } } },
      { "name": "U07 GET /users/me/agreements — 1건", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('200, items 1건', () => { pm.response.to.have.status(200); pm.expect(pm.response.json().data.items.length).to.eql(1); });"
        ]}}],
        "request": { "method": "GET", "header": [{ "key": "Authorization", "value": "Bearer {{accessToken}}" }], "url": { "raw": "{{baseUrl}}/api/users/me/agreements", "host": ["{{baseUrl}}"], "path": ["api","users","me","agreements"] } } }
    ]},

    { "name": "M 마스터 조회 (F-04~F-07·F-20·F-21)", "item": [
      { "name": "M01 regions — 9건, code 존재", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "const it = pm.response.json().data.items;",
          "pm.test('9건', () => pm.expect(it.length).to.eql(9));",
          "pm.test('code 존재·대문자', () => it.forEach(r => pm.expect(r.code).to.match(/^[A-Z_]+$/)));",
          "pm.environment.set('emptyRegionId', (it.find(r => r.courseCount === 0) || it[8]).regionId);"
        ]}}],
        "request": { "method": "GET", "header": [], "url": { "raw": "{{baseUrl}}/api/regions", "host": ["{{baseUrl}}"], "path": ["api","regions"] } } },
      { "name": "M02 courses — 비로그인 progress null", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('progress 전부 null', () => pm.response.json().data.items.forEach(c => pm.expect(c.progress).to.be.null));"
        ]}}],
        "request": { "method": "GET", "header": [], "url": { "raw": "{{baseUrl}}/api/courses", "host": ["{{baseUrl}}"], "path": ["api","courses"] } } },
      { "name": "M03 courses?regionId=1 — 로그인, 미시작이라 progress null (순례 시작은 챕터 4)", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('200, regionId 1 만', () => { pm.response.to.have.status(200); pm.response.json().data.items.forEach(c => pm.expect(c.regionId).to.eql(1)); });"
        ]}}],
        "request": { "method": "GET", "header": [{ "key": "Authorization", "value": "Bearer {{accessToken}}" }], "url": { "raw": "{{baseUrl}}/api/courses?regionId=1", "host": ["{{baseUrl}}"], "path": ["api","courses"], "query": [{ "key": "regionId", "value": "1" }] } } },
      { "name": "M04 courses/1 — 사찰 5곳 좌표·반경·힌트 일괄", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "const d = pm.response.json().data;",
          "pm.test('sites 5, position 1~5 순', () => { pm.expect(d.sites.length).to.eql(5); d.sites.forEach((s,i) => pm.expect(s.position).to.eql(i+1)); });",
          "pm.test('좌표·반경 존재', () => d.sites.forEach(s => { pm.expect(s.latitude).to.be.a('number'); pm.expect(s.verifyRadius).to.be.above(0); }));"
        ]}}],
        "request": { "method": "GET", "header": [], "url": { "raw": "{{baseUrl}}/api/courses/1", "host": ["{{baseUrl}}"], "path": ["api","courses","1"] } } },
      { "name": "M05 courses/999 — 404 COURSE-4041", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('404 COURSE-4041', () => { pm.response.to.have.status(404); pm.expect(pm.response.json().error.code).to.eql('COURSE-4041'); });"
        ]}}],
        "request": { "method": "GET", "header": [], "url": { "raw": "{{baseUrl}}/api/courses/999", "host": ["{{baseUrl}}"], "path": ["api","courses","999"] } } },
      { "name": "M06 sites/1 — 기본 정보", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "const s = pm.response.json().data;",
          "pm.test('name·latitude·verifyRadius·qrLocationHint 키', () => ['name','latitude','longitude','verifyRadius','qrLocationHint'].forEach(k => pm.expect(s).to.have.property(k)));"
        ]}}],
        "request": { "method": "GET", "header": [], "url": { "raw": "{{baseUrl}}/api/sites/1", "host": ["{{baseUrl}}"], "path": ["api","sites","1"] } } },
      { "name": "M07 sites/999 — 404 SITE-4040", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('404 SITE-4040', () => { pm.response.to.have.status(404); pm.expect(pm.response.json().error.code).to.eql('SITE-4040'); });"
        ]}}],
        "request": { "method": "GET", "header": [], "url": { "raw": "{{baseUrl}}/api/sites/999", "host": ["{{baseUrl}}"], "path": ["api","sites","999"] } } },
      { "name": "M08 sites/1/page — 비로그인 8블록, verifyState null", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "const d = pm.response.json().data;",
          "pm.test('8블록', () => ['site','verse','expansionPhrase','mission','viewpoints','badges','riderInfo','verifyState'].forEach(k => pm.expect(d).to.have.property(k)));",
          "pm.test('verifyState null, guideAvailable 없음', () => { pm.expect(d.verifyState).to.be.null; pm.expect(d).to.not.have.property('guideAvailable'); });",
          "pm.test('verse.verseNo == site.position', () => pm.expect(d.verse.verseNo).to.eql(d.site.position));",
          "pm.environment.set('phraseAnon', d.expansionPhrase.expansionPhraseId);"
        ]}}],
        "request": { "method": "GET", "header": [], "url": { "raw": "{{baseUrl}}/api/sites/1/page", "host": ["{{baseUrl}}"], "path": ["api","sites","1","page"] } } },
      { "name": "M09 sites/1/page — 로그인(users.tier RIDER) → RIDER 문구", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('200', () => pm.response.to.have.status(200));",
          "pm.environment.set('phraseRider', pm.response.json().data.expansionPhrase.expansionPhraseId);"
        ]}}],
        "request": { "method": "GET", "header": [{ "key": "Authorization", "value": "Bearer {{accessToken}}" }], "url": { "raw": "{{baseUrl}}/api/sites/1/page", "host": ["{{baseUrl}}"], "path": ["api","sites","1","page"] } } },
      { "name": "M10 sites/1/page?target=AGE60 — 쿼리가 users.tier 를 덮음", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('AGE60 문구 ≠ RIDER 문구 (시드가 계층당 1편이므로 id 가 달라야 함)', () => pm.expect(pm.response.json().data.expansionPhrase.expansionPhraseId).to.not.eql(pm.environment.get('phraseRider')));"
        ]}}],
        "request": { "method": "GET", "header": [{ "key": "Authorization", "value": "Bearer {{accessToken}}" }], "url": { "raw": "{{baseUrl}}/api/sites/1/page?target=AGE60", "host": ["{{baseUrl}}"], "path": ["api","sites","1","page"], "query": [{ "key": "target", "value": "AGE60" }] } } },
      { "name": "M11 sites/1/page?target=AGE99 — 400 COMMON-4003", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('400 COMMON-4003', () => { pm.response.to.have.status(400); pm.expect(pm.response.json().error.code).to.eql('COMMON-4003'); });"
        ]}}],
        "request": { "method": "GET", "header": [], "url": { "raw": "{{baseUrl}}/api/sites/1/page?target=AGE99", "host": ["{{baseUrl}}"], "path": ["api","sites","1","page"], "query": [{ "key": "target", "value": "AGE99" }] } } },
      { "name": "M12 sites/1/page 로그인 + 힌트 없음 → users.locale(en) 적용, 이름 영문", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('site.name 영문 (users.locale=en)', () => pm.expect(pm.response.json().data.site.name).to.match(/^[A-Za-z]/));"
        ]}}],
        "request": { "method": "GET", "header": [{ "key": "Authorization", "value": "Bearer {{accessToken}}" }], "url": { "raw": "{{baseUrl}}/api/sites/1/page", "host": ["{{baseUrl}}"], "path": ["api","sites","1","page"] } } },
      { "name": "M13 sites/1/page?locale=ko + Accept-Language en — 쿼리가 이김, 한글", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('site.name 한글', () => pm.expect(pm.response.json().data.site.name).to.match(/[가-힣]/));"
        ]}}],
        "request": { "method": "GET", "header": [{ "key": "Accept-Language", "value": "en" }], "url": { "raw": "{{baseUrl}}/api/sites/1/page?locale=ko", "host": ["{{baseUrl}}"], "path": ["api","sites","1","page"], "query": [{ "key": "locale", "value": "ko" }] } } },
      { "name": "M14 verses — 5건, verseNo 1~5", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "const it = pm.response.json().data.items; pm.test('5건 순서', () => { pm.expect(it.length).to.eql(5); it.forEach((v,i) => pm.expect(v.verseNo).to.eql(i+1)); });"
        ]}}],
        "request": { "method": "GET", "header": [], "url": { "raw": "{{baseUrl}}/api/verses", "host": ["{{baseUrl}}"], "path": ["api","verses"] } } },
      { "name": "M15 verses/3 — hanja·textKo·theme", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "const v = pm.response.json().data; pm.test('필드', () => ['hanja','textKo','theme'].forEach(k => pm.expect(v[k]).to.be.a('string')));"
        ]}}],
        "request": { "method": "GET", "header": [], "url": { "raw": "{{baseUrl}}/api/verses/3", "host": ["{{baseUrl}}"], "path": ["api","verses","3"] } } },
      { "name": "M16 verses/6 — 400 COMMON-4000", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('400 COMMON-4000', () => { pm.response.to.have.status(400); pm.expect(pm.response.json().error.code).to.eql('COMMON-4000'); });"
        ]}}],
        "request": { "method": "GET", "header": [], "url": { "raw": "{{baseUrl}}/api/verses/6", "host": ["{{baseUrl}}"], "path": ["api","verses","6"] } } },
      { "name": "M17 sites/1/guide — 7칸, position 1~7, 없는 자리 passage", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "const s = pm.response.json().data.steps;",
          "pm.test('7칸 순서', () => { pm.expect(s.length).to.eql(7); s.forEach((x,i) => pm.expect(x.position).to.eql(i+1)); });",
          "pm.test('없는 자리 규칙', () => s.filter(x => !x.present).forEach(x => { pm.expect(x.etiquette).to.be.null; pm.expect(x.passage).to.be.a('string'); }));"
        ]}}],
        "request": { "method": "GET", "header": [], "url": { "raw": "{{baseUrl}}/api/sites/1/guide", "host": ["{{baseUrl}}"], "path": ["api","sites","1","guide"] } } }
    ]},

    { "name": "G 규약·보안", "item": [
      { "name": "G01 좌표 필드 포함 PATCH /users/me → 400 COMMON-4001", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('400 COMMON-4001', () => { pm.response.to.have.status(400); pm.expect(pm.response.json().error.code).to.eql('COMMON-4001'); });"
        ]}}],
        "request": { "method": "PATCH", "header": [{ "key": "Authorization", "value": "Bearer {{accessToken}}" }, { "key": "Content-Type", "value": "application/json" }],
          "url": { "raw": "{{baseUrl}}/api/users/me", "host": ["{{baseUrl}}"], "path": ["api","users","me"] },
          "body": { "mode": "raw", "raw": "{\"nickname\":\"회귀검증\",\"latitude\":37.5}" } } },
      { "name": "G02 USER 토큰으로 /api/admin/sites → 403 AUTH-4032", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('403 AUTH-4032', () => { pm.response.to.have.status(403); pm.expect(pm.response.json().error.code).to.eql('AUTH-4032'); });"
        ]}}],
        "request": { "method": "GET", "header": [{ "key": "Authorization", "value": "Bearer {{accessToken}}" }], "url": { "raw": "{{baseUrl}}/api/admin/sites", "host": ["{{baseUrl}}"], "path": ["api","admin","sites"] } } },
      { "name": "G03 /health → UP", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('UP', () => pm.expect(pm.response.json().status).to.eql('UP'));"
        ]}}],
        "request": { "method": "GET", "header": [], "url": { "raw": "{{baseUrl}}/health", "host": ["{{baseUrl}}"], "path": ["health"] } } },
      { "name": "G04 /env → 401 (노출 금지 확인)", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('401', () => pm.response.to.have.status(401));"
        ]}}],
        "request": { "method": "GET", "header": [], "url": { "raw": "{{baseUrl}}/env", "host": ["{{baseUrl}}"], "path": ["env"] } } }
    ]},

    { "name": "K 관리자 카카오 (챕터 3)", "item": [
      { "name": "K00 관리자 로그인", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('200', () => pm.response.to.have.status(200));",
          "pm.environment.set('adminToken', pm.response.json().data.accessToken);"
        ]}}],
        "request": { "method": "POST", "header": [{ "key": "Content-Type", "value": "application/json" }],
          "url": { "raw": "{{baseUrl}}/api/auth/login", "host": ["{{baseUrl}}"], "path": ["api","auth","login"] },
          "body": { "mode": "raw", "raw": "{\"email\":\"{{adminEmail}}\",\"password\":\"{{adminPassword}}\"}" } } },
      { "name": "K01 places — query 없음 400 COMMON-4000", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('400', () => pm.response.to.have.status(400));"
        ]}}],
        "request": { "method": "GET", "header": [{ "key": "Authorization", "value": "Bearer {{adminToken}}" }], "url": { "raw": "{{baseUrl}}/api/admin/kakao/places?query=", "host": ["{{baseUrl}}"], "path": ["api","admin","kakao","places"], "query": [{ "key": "query", "value": "" }] } } },
      { "name": "K02 places?query=통도사 — 200 places[0] 좌표 (키 없으면 503 KAKAO-5030 도 통과로 기록)", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "if (pm.response.code === 503) { pm.test('503 KAKAO-5030 — 키 미설정 경로 정상', () => pm.expect(pm.response.json().error.code).to.eql('KAKAO-5030')); }",
          "else { pm.test('200, latitude 35.4~35.6', () => { pm.response.to.have.status(200); const p = pm.response.json().data.places[0]; pm.expect(p.placeName).to.include('통도사'); pm.expect(p.latitude).to.be.within(35.4, 35.6); }); }"
        ]}}],
        "request": { "method": "GET", "header": [{ "key": "Authorization", "value": "Bearer {{adminToken}}" }], "url": { "raw": "{{baseUrl}}/api/admin/kakao/places?query=통도사&size=5", "host": ["{{baseUrl}}"], "path": ["api","admin","kakao","places"], "query": [{ "key": "query", "value": "통도사" }, { "key": "size", "value": "5" }] } } },
      { "name": "K03 places?size=16 — 400 (카카오 상한 15 를 우리가 먼저 막음)", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('400', () => pm.response.to.have.status(400));"
        ]}}],
        "request": { "method": "GET", "header": [{ "key": "Authorization", "value": "Bearer {{adminToken}}" }], "url": { "raw": "{{baseUrl}}/api/admin/kakao/places?query=통도사&size=16", "host": ["{{baseUrl}}"], "path": ["api","admin","kakao","places"], "query": [{ "key": "query", "value": "통도사" }, { "key": "size", "value": "16" }] } } }
    ]},

    { "name": "S·C 관리자 사찰·코스 (챕터 3)", "item": [
      { "name": "S01 사찰 생성 — en 만 → 400 COMMON-4000 fields[i18n]", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('400 COMMON-4000 i18n', () => { pm.response.to.have.status(400); pm.expect(pm.response.json().error.code).to.eql('COMMON-4000'); pm.expect(JSON.stringify(pm.response.json().error.fields)).to.include('i18n'); });"
        ]}}],
        "request": { "method": "POST", "header": [{ "key": "Authorization", "value": "Bearer {{adminToken}}" }, { "key": "Content-Type", "value": "application/json" }],
          "url": { "raw": "{{baseUrl}}/api/admin/sites", "host": ["{{baseUrl}}"], "path": ["api","admin","sites"] },
          "body": { "mode": "raw", "raw": "{\"latitude\":35.4879,\"longitude\":129.0645,\"verifyRadius\":150,\"qrLocationHint\":\"일주문 안내판\",\"i18n\":[{\"locale\":\"en\",\"name\":\"Verify Temple\"}]}" } } },
      { "name": "S02 사찰 생성 ×5 루프 (검증사찰1~5, ko+en) → 201", "event": [
        { "listen": "prerequest", "script": { "type": "text/javascript", "exec": [
          "let n = Number(pm.environment.get('siteN') || 0) + 1; pm.environment.set('siteN', n);",
          "pm.environment.set('siteName', `검증사찰${n}`);"
        ]}},
        { "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('201', () => pm.response.to.have.status(201));",
          "const id = pm.response.json().data.siteId;",
          "let ids = JSON.parse(pm.environment.get('siteIds') || '[]'); ids.push(id); pm.environment.set('siteIds', JSON.stringify(ids));",
          "if (ids.length < 5) pm.execution.setNextRequest('S02 사찰 생성 ×5 루프 (검증사찰1~5, ko+en) → 201');"
        ]}}],
        "request": { "method": "POST", "header": [{ "key": "Authorization", "value": "Bearer {{adminToken}}" }, { "key": "Content-Type", "value": "application/json" }],
          "url": { "raw": "{{baseUrl}}/api/admin/sites", "host": ["{{baseUrl}}"], "path": ["api","admin","sites"] },
          "body": { "mode": "raw", "raw": "{\"latitude\":35.4879,\"longitude\":129.0645,\"verifyRadius\":150,\"qrLocationHint\":\"일주문 안내판\",\"parkingInfo\":\"대형 주차장\",\"i18n\":[{\"locale\":\"ko\",\"name\":\"{{siteName}}\",\"description\":\"회귀 검증용\"},{\"locale\":\"en\",\"name\":\"Verify Temple {{siteN}}\"}]}" } } },
      { "name": "S03 사찰 수정 PUT — i18n 재전송 (UPSERT, 행 수 불변은 SQL 로)", "event": [
        { "listen": "prerequest", "script": { "type": "text/javascript", "exec": [ "pm.environment.set('siteId1', JSON.parse(pm.environment.get('siteIds'))[0]);" ]}},
        { "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('200, 반경 갱신', () => { pm.response.to.have.status(200); pm.expect(pm.response.json().data.verifyRadius).to.eql(200); });"
        ]}}],
        "request": { "method": "PUT", "header": [{ "key": "Authorization", "value": "Bearer {{adminToken}}" }, { "key": "Content-Type", "value": "application/json" }],
          "url": { "raw": "{{baseUrl}}/api/admin/sites/{{siteId1}}", "host": ["{{baseUrl}}"], "path": ["api","admin","sites","{{siteId1}}"] },
          "body": { "mode": "raw", "raw": "{\"latitude\":35.4879,\"longitude\":129.0645,\"verifyRadius\":200,\"qrLocationHint\":\"일주문 안내판\",\"i18n\":[{\"locale\":\"ko\",\"name\":\"검증사찰1\",\"description\":\"수정됨\"},{\"locale\":\"en\",\"name\":\"Verify Temple 1\"}]}" } } },
      { "name": "S04 사찰 수정 — 없는 id 404 SITE-4040", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('404 SITE-4040', () => { pm.response.to.have.status(404); pm.expect(pm.response.json().error.code).to.eql('SITE-4040'); });"
        ]}}],
        "request": { "method": "PUT", "header": [{ "key": "Authorization", "value": "Bearer {{adminToken}}" }, { "key": "Content-Type", "value": "application/json" }],
          "url": { "raw": "{{baseUrl}}/api/admin/sites/999999", "host": ["{{baseUrl}}"], "path": ["api","admin","sites","999999"] },
          "body": { "mode": "raw", "raw": "{\"latitude\":35.4879,\"longitude\":129.0645,\"verifyRadius\":200,\"qrLocationHint\":\"x\",\"i18n\":[{\"locale\":\"ko\",\"name\":\"x\"}]}" } } },
      { "name": "S05 사찰 ACTIVE ×5 루프 — 조건 충족(ko·힌트) → 200", "event": [
        { "listen": "prerequest", "script": { "type": "text/javascript", "exec": [
          "let k = Number(pm.environment.get('actN') || 0); pm.environment.set('curSite', JSON.parse(pm.environment.get('siteIds'))[k]);"
        ]}},
        { "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('200', () => pm.response.to.have.status(200));",
          "let k = Number(pm.environment.get('actN') || 0) + 1; pm.environment.set('actN', k);",
          "if (k < 5) pm.execution.setNextRequest('S05 사찰 ACTIVE ×5 루프 — 조건 충족(ko·힌트) → 200');"
        ]}}],
        "request": { "method": "PATCH", "header": [{ "key": "Authorization", "value": "Bearer {{adminToken}}" }, { "key": "Content-Type", "value": "application/json" }],
          "url": { "raw": "{{baseUrl}}/api/admin/sites/{{curSite}}/status", "host": ["{{baseUrl}}"], "path": ["api","admin","sites","{{curSite}}","status"] },
          "body": { "mode": "raw", "raw": "{\"status\":\"ACTIVE\"}" } } },
      { "name": "S06 힌트 없는 새 사찰 ACTIVE → 409 ADMIN-4092", "event": [
        { "listen": "prerequest", "script": { "type": "text/javascript", "exec": [
          "pm.sendRequest({ url: pm.environment.get('baseUrl') + '/api/admin/sites', method: 'POST', header: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + pm.environment.get('adminToken') },",
          "  body: { mode: 'raw', raw: JSON.stringify({ latitude: 35.1, longitude: 129.1, verifyRadius: 100, i18n: [{ locale: 'ko', name: '힌트없음' }] }) } },",
          "  (e, r) => pm.environment.set('noHintSite', r.json().data.siteId));"
        ]}},
        { "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('409 ADMIN-4092', () => { pm.response.to.have.status(409); pm.expect(pm.response.json().error.code).to.eql('ADMIN-4092'); });"
        ]}}],
        "request": { "method": "PATCH", "header": [{ "key": "Authorization", "value": "Bearer {{adminToken}}" }, { "key": "Content-Type", "value": "application/json" }],
          "url": { "raw": "{{baseUrl}}/api/admin/sites/{{noHintSite}}/status", "host": ["{{baseUrl}}"], "path": ["api","admin","sites","{{noHintSite}}","status"] },
          "body": { "mode": "raw", "raw": "{\"status\":\"ACTIVE\"}" } } },
      { "name": "S07 뷰포인트 UPSERT ×2 (같은 sortNo 두 번 → 행 1개, SQL 확인)", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('200', () => pm.response.to.have.status(200));",
          "let v = Number(pm.environment.get('vpN') || 0) + 1; pm.environment.set('vpN', v);",
          "if (v < 2) pm.execution.setNextRequest('S07 뷰포인트 UPSERT ×2 (같은 sortNo 두 번 → 행 1개, SQL 확인)');"
        ]}}],
        "request": { "method": "POST", "header": [{ "key": "Authorization", "value": "Bearer {{adminToken}}" }, { "key": "Content-Type", "value": "application/json" }],
          "url": { "raw": "{{baseUrl}}/api/admin/sites/{{siteId1}}/viewpoints", "host": ["{{baseUrl}}"], "path": ["api","admin","sites","{{siteId1}}","viewpoints"] },
          "body": { "mode": "raw", "raw": "{\"sortNo\":1,\"locationDesc\":\"대웅전 뒤 언덕\",\"bestTime\":\"해질녘\",\"whatToSee\":\"석양\"}" } } },
      { "name": "S08 뱃지 FLOWER → 200 / 잘못된 타입 400", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('200', () => pm.response.to.have.status(200));"
        ]}}],
        "request": { "method": "POST", "header": [{ "key": "Authorization", "value": "Bearer {{adminToken}}" }, { "key": "Content-Type", "value": "application/json" }],
          "url": { "raw": "{{baseUrl}}/api/admin/sites/{{siteId1}}/badges", "host": ["{{baseUrl}}"], "path": ["api","admin","sites","{{siteId1}}","badges"] },
          "body": { "mode": "raw", "raw": "{\"badgeType\":\"FLOWER\",\"description\":\"매화 3월\"}" } } },
      { "name": "S09 관리자 목록 ?q=검증사찰 — 5건 이상, DRAFT 포함", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('200, 5건 이상', () => { pm.response.to.have.status(200); pm.expect(pm.response.json().data.items.length).to.be.at.least(5); });"
        ]}}],
        "request": { "method": "GET", "header": [{ "key": "Authorization", "value": "Bearer {{adminToken}}" }], "url": { "raw": "{{baseUrl}}/api/admin/sites?q=검증사찰&size=50", "host": ["{{baseUrl}}"], "path": ["api","admin","sites"], "query": [{ "key": "q", "value": "검증사찰" }, { "key": "size", "value": "50" }] } } },
      { "name": "C01 코스 생성 — 4곳 → 400 COMMON-4000", "event": [
        { "listen": "prerequest", "script": { "type": "text/javascript", "exec": [
          "const ids = JSON.parse(pm.environment.get('siteIds'));",
          "const slots = n => ids.slice(0, n).map((id, i) => ({ siteId: id, position: i+1, verseNo: i+1 }));",
          "pm.environment.set('sites4', JSON.stringify(slots(4)));",
          "pm.environment.set('sites5', JSON.stringify(slots(5)));",
          "let bad = slots(5); bad[2].verseNo = 5; pm.environment.set('sitesBadVerse', JSON.stringify(bad));",
          "let dup = slots(5); dup[4].siteId = 1; pm.environment.set('sitesDup', JSON.stringify(dup));"
        ]}},
        { "listen": "test", "script": { "type": "text/javascript", "exec": [ "pm.test('400 COMMON-4000', () => { pm.response.to.have.status(400); pm.expect(pm.response.json().error.code).to.eql('COMMON-4000'); });" ]}}],
        "request": { "method": "POST", "header": [{ "key": "Authorization", "value": "Bearer {{adminToken}}" }, { "key": "Content-Type", "value": "application/json" }],
          "url": { "raw": "{{baseUrl}}/api/admin/courses", "host": ["{{baseUrl}}"], "path": ["api","admin","courses"] },
          "body": { "mode": "raw", "raw": "{\"regionId\":{{emptyRegionId}},\"name\":\"검증코스\",\"sites\":{{sites4}}}" } } },
      { "name": "C02 코스 생성 — verseNo≠position → 400 COURSE-4001", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('400 COURSE-4001', () => { pm.response.to.have.status(400); pm.expect(pm.response.json().error.code).to.eql('COURSE-4001'); });"
        ]}}],
        "request": { "method": "POST", "header": [{ "key": "Authorization", "value": "Bearer {{adminToken}}" }, { "key": "Content-Type", "value": "application/json" }],
          "url": { "raw": "{{baseUrl}}/api/admin/courses", "host": ["{{baseUrl}}"], "path": ["api","admin","courses"] },
          "body": { "mode": "raw", "raw": "{\"regionId\":{{emptyRegionId}},\"name\":\"검증코스\",\"sites\":{{sitesBadVerse}}}" } } },
      { "name": "C03 코스 생성 — site 1(코스1 소속) 포함 → 409 COURSE-4093", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('409 COURSE-4093', () => { pm.response.to.have.status(409); pm.expect(pm.response.json().error.code).to.eql('COURSE-4093'); });"
        ]}}],
        "request": { "method": "POST", "header": [{ "key": "Authorization", "value": "Bearer {{adminToken}}" }, { "key": "Content-Type", "value": "application/json" }],
          "url": { "raw": "{{baseUrl}}/api/admin/courses", "host": ["{{baseUrl}}"], "path": ["api","admin","courses"] },
          "body": { "mode": "raw", "raw": "{\"regionId\":{{emptyRegionId}},\"name\":\"검증코스\",\"sites\":{{sitesDup}}}" } } },
      { "name": "C04 코스 생성 — 정상 5곳 → 201, sites 5", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('201, sites 5', () => { pm.response.to.have.status(201); pm.expect(pm.response.json().data.sites.length).to.eql(5); });",
          "pm.environment.set('courseId', pm.response.json().data.courseId);"
        ]}}],
        "request": { "method": "POST", "header": [{ "key": "Authorization", "value": "Bearer {{adminToken}}" }, { "key": "Content-Type", "value": "application/json" }],
          "url": { "raw": "{{baseUrl}}/api/admin/courses", "host": ["{{baseUrl}}"], "path": ["api","admin","courses"] },
          "body": { "mode": "raw", "raw": "{\"regionId\":{{emptyRegionId}},\"name\":\"검증코스\",\"sites\":{{sites5}}}" } } },
      { "name": "C05 코스 ACTIVE → 200 (사찰 5곳 전부 ACTIVE) → 공개 GET /api/courses/{id} 200", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('200', () => pm.response.to.have.status(200));",
          "pm.sendRequest(pm.environment.get('baseUrl') + '/api/courses/' + pm.environment.get('courseId'), (e, r) => { pm.test('공개 조회 200, sites 5', () => { pm.expect(r.code).to.eql(200); pm.expect(r.json().data.sites.length).to.eql(5); }); });"
        ]}}],
        "request": { "method": "PATCH", "header": [{ "key": "Authorization", "value": "Bearer {{adminToken}}" }, { "key": "Content-Type", "value": "application/json" }],
          "url": { "raw": "{{baseUrl}}/api/admin/courses/{{courseId}}/status", "host": ["{{baseUrl}}"], "path": ["api","admin","courses","{{courseId}}","status"] },
          "body": { "mode": "raw", "raw": "{\"status\":\"ACTIVE\"}" } } },
      { "name": "C06 코스 INACTIVE (정리) → 200, 공개 조회는 404 COURSE-4041", "event": [{ "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('200', () => pm.response.to.have.status(200));",
          "pm.sendRequest(pm.environment.get('baseUrl') + '/api/courses/' + pm.environment.get('courseId'), (e, r) => { pm.test('비활성 코스 공개 404', () => pm.expect(r.code).to.eql(404)); });"
        ]}}],
        "request": { "method": "PATCH", "header": [{ "key": "Authorization", "value": "Bearer {{adminToken}}" }, { "key": "Content-Type", "value": "application/json" }],
          "url": { "raw": "{{baseUrl}}/api/admin/courses/{{courseId}}/status", "host": ["{{baseUrl}}"], "path": ["api","admin","courses","{{courseId}}","status"] },
          "body": { "mode": "raw", "raw": "{\"status\":\"INACTIVE\"}" } } },
      { "name": "C07 site-distances 20행 UPSERT → 200 (두 번 보내도 행 수 동일 — SQL)", "event": [
        { "listen": "prerequest", "script": { "type": "text/javascript", "exec": [
          "const ids = JSON.parse(pm.environment.get('siteIds')); const items = [];",
          "ids.forEach(a => ids.forEach(b => { if (a !== b) items.push({ siteAId: a, siteBId: b, minMinutes: 20 }); }));",
          "pm.environment.set('distItems', JSON.stringify(items));"
        ]}},
        { "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('200', () => pm.response.to.have.status(200));",
          "let d = Number(pm.environment.get('distN') || 0) + 1; pm.environment.set('distN', d);",
          "if (d < 2) pm.execution.setNextRequest('C07 site-distances 20행 UPSERT → 200 (두 번 보내도 행 수 동일 — SQL)');"
        ]}}],
        "request": { "method": "PUT", "header": [{ "key": "Authorization", "value": "Bearer {{adminToken}}" }, { "key": "Content-Type", "value": "application/json" }],
          "url": { "raw": "{{baseUrl}}/api/admin/site-distances", "host": ["{{baseUrl}}"], "path": ["api","admin","site-distances"] },
          "body": { "mode": "raw", "raw": "{\"items\":{{distItems}}}" } } },
      { "name": "C08 정리 — 검증 사찰 5곳 INACTIVE 루프 (코스가 INACTIVE 라 허용)", "event": [
        { "listen": "prerequest", "script": { "type": "text/javascript", "exec": [
          "let k = Number(pm.environment.get('deN') || 0); pm.environment.set('curSite', JSON.parse(pm.environment.get('siteIds'))[k]);"
        ]}},
        { "listen": "test", "script": { "type": "text/javascript", "exec": [
          "pm.test('200', () => pm.response.to.have.status(200));",
          "let k = Number(pm.environment.get('deN') || 0) + 1; pm.environment.set('deN', k);",
          "if (k < 5) pm.execution.setNextRequest('C08 정리 — 검증 사찰 5곳 INACTIVE 루프 (코스가 INACTIVE 라 허용)');",
          "else ['siteN','siteIds','actN','deN','vpN','distN','lockN','lockEmail'].forEach(k => pm.environment.unset(k));"
        ]}}],
        "request": { "method": "PATCH", "header": [{ "key": "Authorization", "value": "Bearer {{adminToken}}" }, { "key": "Content-Type", "value": "application/json" }],
          "url": { "raw": "{{baseUrl}}/api/admin/sites/{{curSite}}/status", "host": ["{{baseUrl}}"], "path": ["api","admin","sites","{{curSite}}","status"] },
          "body": { "mode": "raw", "raw": "{\"status\":\"INACTIVE\"}" } } }
    ]}
  ]
}
```

**파일** `postman/all.postman_environment.json`
```json
{ "name": "temple-stamp all", "values": [
  { "key": "baseUrl", "value": "http://localhost:8080", "enabled": true },
  { "key": "adminEmail", "value": "admin@templestamp.local", "enabled": true },
  { "key": "adminPassword", "value": "Admin1234!", "enabled": true },
  { "key": "usedRefreshCookie", "value": "", "enabled": true }
]}
```

**A12(재사용 탐지)의 함정 하나**: newman 쿠키 항아리는 A09 응답의 새 쿠키로 자동 교체되므로 "이미 쓴 쿠키"를 다시 보내려면 A09 **요청 직전**의 쿠키 값을 잡아 둬야 한다. A04 Tests 마지막 줄에 다음을 추가한다(Claude Code STEP 2에서 반영):
```javascript
pm.environment.set('usedRefreshCookie', (pm.response.headers.get('Set-Cookie')||'').split(';')[0]);
```
A04의 쿠키 = A09에서 쓰여 폐기된 쿠키. A12가 이것을 보내면 서버는 "폐기된 토큰 재사용"으로 보고 `AUTH-4015` + 전 기기 폐기. A13은 그 결과 최신 쿠키까지 죽었음을 확인한다.

**파일** `postman/run-all.sh`
```bash
#!/usr/bin/env bash
cd "$(dirname "$0")"
npx --yes newman run temple-stamp-all.postman_collection.json -e all.postman_environment.json \
  --reporters cli,json --reporter-json-export ../backend/docs/verify/all-newman-result.json
```

---

## 3. SQL 보조 확인 (Postman이 못 보는 것)

**파일** `backend/docs/verify/all-checkpoints.sql`
```sql
USE temple_stamp_project;
-- A07: 잠금 계정 — login_fail_count 5, locked_until 미래                                 기대: 5, > NOW()
SELECT login_fail_count, locked_until > NOW() AS locked FROM users WHERE email LIKE 'lock-%@test.com' ORDER BY user_id DESC LIMIT 1;
-- A12: 재사용 탐지 후 그 사용자의 refresh_token 전부 revoked                            기대: 0
SELECT COUNT(*) AS alive FROM refresh_token rt JOIN users u ON u.user_id = rt.user_id
 WHERE u.email LIKE 'reg-%@test.com' AND rt.revoked_at IS NULL AND rt.expires_at > NOW()
   AND rt.created_at < (SELECT MAX(created_at) FROM refresh_token WHERE user_id = rt.user_id);
-- M09~M12: 미리보기 seen 미기록                                                          기대: 0
SELECT COUNT(*) AS preview_seen FROM phrase_seen ps JOIN users u ON u.user_id = ps.user_id WHERE u.email LIKE 'reg-%@test.com';
-- U06: 같은 버전 재동의 1행                                                              기대: 1
SELECT COUNT(*) FROM user_agreement ua JOIN users u ON u.user_id = ua.user_id WHERE u.email LIKE 'reg-%@test.com' AND agreement_type='LOCATION_SERVICE';
-- S03: i18n UPSERT — 검증사찰1 의 행 수                                                  기대: 2 (ko, en)
SELECT COUNT(*) FROM site_i18n WHERE site_id = (SELECT site_id FROM site WHERE name='검증사찰1' ORDER BY site_id DESC LIMIT 1);
-- S07: 뷰포인트 두 번 → 1행                                                              기대: 1
SELECT COUNT(*) FROM site_viewpoint WHERE site_id = (SELECT site_id FROM site WHERE name='검증사찰1' ORDER BY site_id DESC LIMIT 1) AND sort_no=1;
-- C07: 이동시간 두 번 → 20행                                                             기대: 20
SELECT COUNT(*) FROM site_distance WHERE site_a_id IN (SELECT site_id FROM site WHERE name LIKE '검증사찰%');
-- C08 정리 확인: 검증 사찰·코스 INACTIVE                                                  기대: 모두 INACTIVE
SELECT name, status FROM site WHERE name LIKE '검증사찰%' UNION ALL SELECT name, status FROM course WHERE name='검증코스';
```

**정리 SQL**(선택 — 검증 데이터를 지우고 싶을 때. FK 순서 주의):
```sql
DELETE FROM site_distance WHERE site_a_id IN (SELECT site_id FROM site WHERE name LIKE '검증사찰%');
DELETE FROM course_site WHERE course_id IN (SELECT course_id FROM course WHERE name='검증코스');
DELETE FROM course WHERE name='검증코스';
DELETE FROM site WHERE name LIKE '검증사찰%' OR name='힌트없음';   -- i18n·viewpoint·badge 는 CASCADE
DELETE FROM users WHERE email LIKE 'reg-%@test.com' OR email LIKE 'lock-%@test.com';   -- refresh_token CASCADE, user_agreement 는 RESTRICT → 먼저 삭제
```

---

## 4. Claude Code 지시문 — 점검·보고

```
루트 src/ 작업. git 없음. 원문 backend/docs/textbook/verify-ch0-3.md. 목적은 코드 수정이 아니라 **점검과 보고**다 — 고치는 것은 컬렉션·환경 파일과 "명백한 필드명 불일치"뿐이고, 동작이 다르면 고치지 말고 표에 적는다.

STEP 0 — 전제 확인: 챕터 3 STEP 1~7 이 끝났는지(ch3-admin.md). 안 끝났으면 K·S·C 폴더는 "미실행(챕터 3 미완)" 으로 표에 적고 A·U·M·G 폴더만 돌린다.

STEP 1 — 파일 생성: postman/temple-stamp-all.postman_collection.json · postman/all.postman_environment.json · postman/run-all.sh(chmod +x) · backend/docs/verify/all-checkpoints.sql — §2·§3 원문 그대로. A04 Tests 마지막 줄에 §2 아래의 usedRefreshCookie 한 줄 추가.

STEP 2 — 필드명 대조: 컬렉션 본문의 필드명(nickname·tier·locale·agreementType·version·i18n·sites·position·verseNo·items·siteAId·siteBId·minMinutes·query·page·size)이 저장소 DTO 와 같은지 확인. 다르면 **컬렉션을 저장소에 맞추고** 보고서 "필드명 차이" 표에 적는다. DTO 는 고치지 않는다.

STEP 3 — 실행: ./gradlew test 통과 확인 → bootRun(/api/regions 200 대기) → bash postman/run-all.sh → mysql < backend/docs/verify/all-checkpoints.sql > backend/docs/verify/all-sql-result.txt. newman 이 네트워크로 막히면 같은 순서를 curl 로 재현한 docs/verify/all-checkpoints.sh 를 만들어 대신 돌린다(요청 이름을 echo 로 남길 것).
  잠금(A07x)은 실제로 15분 잠기므로 lock-*@test.com 계정만 쓰고 다른 계정으로 번지지 않게 한다. 실행이 끝나면 UPDATE users SET login_fail_count=0, locked_until=NULL WHERE email LIKE 'lock-%@test.com' 로 원복.

STEP 4 — 기능 상태표 작성: backend/docs/audit/feature-status-ch0-3.md. §1 표를 그대로 옮기고 결과 열을 채운다: ✅(명세 경로·정상·예외 셋 다) / ⚠(일부 — 무엇이 다른지 한 줄) / ❌(미실현 — 어디서 막히는지) / —(범위 밖). 각 행에 근거 요청 이름과 응답 코드를 적는다. 표 아래에 ① newman 요약(단언 통과/실패 수) ② 실패 단언 전부 원문 ③ SQL 대조표 ④ 필드명 차이 표 ⑤ [질문] — "명세와 구현이 다른데 어느 쪽이 맞는지" 만 모은다.

STEP 5 — 원복: §3 정리 SQL 실행(검증 데이터 삭제), 잠금 원복 확인, bootRun 종료. 보고서 마지막 줄에 "newman N/N, 기능 ✅ a · ⚠ b · ❌ c · — d" 한 줄 요약.
```

**예성 직접**: 이 파일을 `backend/docs/textbook/verify-ch0-3.md`로 저장 → §4 붙여넣기 → `feature-status-ch0-3.md` 의 마지막 줄 한 줄만 읽기. ⚠·❌가 있으면 그 파일을 올려주면 다음 챕터 지시문에 수정으로 넣는다.
