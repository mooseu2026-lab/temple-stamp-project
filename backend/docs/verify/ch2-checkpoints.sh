#!/usr/bin/env bash
# backend/docs/verify/ch2-checkpoints.sh — 기동 직후 30초 스모크. 실패하면 줄 번호를 찍고 종료(set -e)
set -euo pipefail
B=${BASE_URL:-http://localhost:8080}
# 요청 본문은 파일에서 읽는다. Windows Git Bash 는 네이티브 exe 에 argv 를 넘길 때 ANSI 코드페이지로
# 바꾸기 때문에, -d 안에 한글을 직접 쓰면 UTF-8 이 깨져 400 COMMON-4004 가 난다.
# 파일 본문은 그대로 전달되므로 바이트가 보존된다. 한글 서비스의 검증 세트가 한글을 피하면
# 정작 한글 경로에 문제가 생겼을 때 이 세트가 잡아 주지 못한다.
D="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
code() { curl -s -o /dev/null -w "%{http_code}" "$@"; }        # 상태코드만
expect() { [ "$1" == "$2" ] && echo "OK   $3" || { echo "FAIL $3 (got $1, want $2)"; exit 1; }; }

# 사전 — 기동 대기. actuator /health 대신 실제 API 로 기다린다(톰캣·시큐리티·MyBatis·MySQL 을 모두 지남)
for i in $(seq 1 60); do
  [ "$(code "$B/api/regions")" == "200" ] && break
  sleep 1
done

# 사전 — 로그인 (없으면 가입)
curl -s -X POST "$B/api/auth/signup" -H 'Content-Type: application/json' \
  --data-binary "@$D/body/signup.json" > /dev/null || true
TOKEN=$(curl -s -X POST "$B/api/auth/login" -H 'Content-Type: application/json' \
  --data-binary "@$D/body/login.json" | jq -r '.data.accessToken')
[ "${#TOKEN}" -gt 20 ] || { echo "FAIL login"; exit 1; }

expect "$(curl -s "$B/api/courses" | jq -r '.data.items[0].progress')"                     "null" "① 비로그인 progress null"
expect "$(code -H "Authorization: Bearer $TOKEN" "$B/api/sites/1/page")"                    "200"  "② 로그인 page 200"
expect "$(code "$B/api/courses?regionId=0")"                                                "400"  "③ regionId=0 → 400"
expect "$(curl -s -H 'Accept-Language: en-US' "$B/api/courses/1" | jq -r '.data.sites[0].siteName' | grep -c '^[A-Za-z]')" "1" "④ en siteName"
expect "$(curl -s "$B/api/sites/1/guide" | jq '.data.steps|length')"                        "7"    "⑤ guide 7칸"
expect "$(code "$B/api/verses/99")"                                                         "400"  "⑥-a verses/99 → 400"
expect "$(code "$B/api/sites/999")"                                                         "404"  "⑥-b sites/999 → 404"
expect "$(curl -s "$B/api/sites/1/page?target=AGE99" | jq -r '.error.code')"                "COMMON-4003" "⑥-c target=AGE99"
expect "$(curl -s -X POST "$B/api/pilgrimages" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
          -d '{"courseId":1,"latitude":37.5}' | jq -r '.error.code')"                        "COMMON-4001" "⑦ 좌표 차단"
expect "$(curl -sI "$B/api/regions" | grep -ci '^x-request-id')"                             "1"    "⑧ X-Request-Id"
expect "$(curl -s "$B/api/regions" | jq -r '.timestamp' | grep -c '+09:00$')"                "1"    "⑧ timestamp +09:00"
expect "$(curl -s "$B/health" | jq -r '.status')"                                            "UP"   "⑨ health UP"
echo "ALL 12 OK"

# 실측만 — 기대값 없음. SecurityConfig 를 "/api/regions" 정확 일치로 좁힌 뒤 trailing slash 가 어떻게 나가는지.
# 스프링 부트 3 은 trailing slash 매칭이 꺼져 있어 매처에 안 걸리고 anyRequest().authenticated() 로 떨어질 수 있다.
echo "MEASURED /api/regions/ -> $(code "$B/api/regions/")"
