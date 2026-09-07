#!/usr/bin/env bash
# backend/docs/verify/ch3-checkpoints.sh — 챕터 3 확인포인트 12종 (ch3.md §5)
# 관리자 토큰이 필요하다. data.sql 의 admin@templestamp.local / Admin1234! 로 로그인한다.
#
# 요청 본문은 body/*.json(UTF-8) 에서 읽는다 — Windows Git Bash 는 네이티브 exe 에 argv 를 넘길 때
# ANSI 코드페이지로 바꾸기 때문에, -d 안에 한글을 직접 쓰면 400 COMMON-4004 가 난다.
set -uo pipefail
B=${BASE_URL:-http://localhost:8080}
D="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

PASS=0; FAIL=0
code() { curl -s -o /dev/null -w "%{http_code}" "$@"; }
expect() {
  if [ "$1" == "$2" ]; then echo "OK   $3"; PASS=$((PASS+1));
  else echo "FAIL $3 (got $1, want $2)"; FAIL=$((FAIL+1)); fi
}

# 사전 — 기동 대기
for i in $(seq 1 60); do
  [ "$(code "$B/api/regions")" == "200" ] && break
  sleep 1
done

# 사전 — 관리자 로그인
ADMIN=$(curl -s -X POST "$B/api/auth/login" -H 'Content-Type: application/json' \
  --data-binary "@$D/body/admin-login.json" | jq -r '.data.accessToken')
[ "${#ADMIN}" -gt 20 ] || { echo "FAIL admin login"; exit 1; }
AH="Authorization: Bearer $ADMIN"

# 사전 — 일반(USER) 토큰. 챕터 2 확인포인트 계정을 재사용한다
curl -s -X POST "$B/api/auth/signup" -H 'Content-Type: application/json' \
  --data-binary "@$D/body/signup.json" > /dev/null || true
USER=$(curl -s -X POST "$B/api/auth/login" -H 'Content-Type: application/json' \
  --data-binary "@$D/body/login.json" | jq -r '.data.accessToken')
UH="Authorization: Bearer $USER"

# ── ① USER 토큰으로 관리자 경로 → 403 AUTH-4032
expect "$(curl -s -H "$UH" "$B/api/admin/kakao/places?query=%ED%86%B5%EB%8F%84%EC%82%AC" | jq -r '.error.code')" \
       "AUTH-4032" "① USER 토큰 → 403 AUTH-4032"

# ── ②③ 카카오. 키가 없으면 ② 만, 있으면 ③ 만 의미가 있다
KAKAO_CODE=$(curl -s -H "$AH" "$B/api/admin/kakao/places?query=%ED%86%B5%EB%8F%84%EC%82%AC" | jq -r '.error.code')
if [ "$KAKAO_CODE" == "KAKAO-5030" ]; then
  expect "$KAKAO_CODE" "KAKAO-5030" "② ADMIN·키 없음 → 503 KAKAO-5030"
  echo "SKIP ③ 실제 카카오 호출 (키 없음)"
else
  echo "SKIP ② 키 미설정 경로 (키가 설정돼 있음)"
  LAT=$(curl -s -H "$AH" "$B/api/admin/kakao/places?query=%ED%86%B5%EB%8F%84%EC%82%AC" | jq -r '.data.places[0].latitude')
  expect "$(awk -v v="$LAT" 'BEGIN{print (v>=35.4 && v<=35.5) ? "1" : "0"}')" "1" \
         "③ ADMIN·키 있음 → places[0].latitude 35.4~35.5 (측정 $LAT)"
fi

# ── ④ i18n 에 en 만 → 400 COMMON-4000, fields[0].field == "i18n"
RESP=$(curl -s -X POST "$B/api/admin/sites" -H "$AH" -H 'Content-Type: application/json' \
  --data-binary "@$D/body/site-en-only.json")
expect "$(echo "$RESP" | jq -r '.error.code')"            "COMMON-4000" "④-a en 만 → COMMON-4000"
expect "$(echo "$RESP" | jq -r '.error.fields[0].field')" "i18n"        "④-b fields[0].field == i18n"

# ── ⑤ ko 추가 → 201, data.siteId
SITE_ID=$(curl -s -X POST "$B/api/admin/sites" -H "$AH" -H 'Content-Type: application/json' \
  --data-binary "@$D/body/site-with-ko.json" | jq -r '.data.siteId')
expect "$(awk -v v="$SITE_ID" 'BEGIN{print (v ~ /^[0-9]+$/) ? "1" : "0"}')" "1" "⑤ ko 포함 → 201 siteId=$SITE_ID"

# ── ⑥ qrLocationHint 없이 ACTIVE → 409 ADMIN-4092
expect "$(curl -s -X PATCH "$B/api/admin/sites/$SITE_ID/status" -H "$AH" -H 'Content-Type: application/json' \
          --data-binary "@$D/body/status-active.json" | jq -r '.error.code')" \
       "ADMIN-4092" "⑥ qr 힌트 없이 ACTIVE → 409 ADMIN-4092"

# ── ⑦ 코스 사찰 4곳 → 400 COMMON-4000
expect "$(curl -s -X POST "$B/api/admin/courses" -H "$AH" -H 'Content-Type: application/json' \
          -d '{"regionId":1,"name":"CH3-4sites","sites":[{"siteId":1,"position":1,"verseNo":1},{"siteId":2,"position":2,"verseNo":2},{"siteId":3,"position":3,"verseNo":3},{"siteId":4,"position":4,"verseNo":4}]}' \
          | jq -r '.error.code')" "COMMON-4000" "⑦ 사찰 4곳 → 400 COMMON-4000"

# ── ⑧ 이미 코스 1 소속인 사찰 포함 → 409 COURSE-4093
expect "$(curl -s -X POST "$B/api/admin/courses" -H "$AH" -H 'Content-Type: application/json' \
          -d '{"regionId":1,"name":"CH3-dup","sites":[{"siteId":1,"position":1,"verseNo":1},{"siteId":2,"position":2,"verseNo":2},{"siteId":3,"position":3,"verseNo":3},{"siteId":4,"position":4,"verseNo":4},{"siteId":5,"position":5,"verseNo":5}]}' \
          | jq -r '.error.code')" "COURSE-4093" "⑧ 사찰 중복 배정 → 409 COURSE-4093"

# ── ⑨ verseNo != position → 400 COURSE-4001
expect "$(curl -s -X POST "$B/api/admin/courses" -H "$AH" -H 'Content-Type: application/json' \
          -d "{\"regionId\":1,\"name\":\"CH3-verse\",\"sites\":[{\"siteId\":$SITE_ID,\"position\":1,\"verseNo\":2},{\"siteId\":2,\"position\":2,\"verseNo\":2},{\"siteId\":3,\"position\":3,\"verseNo\":3},{\"siteId\":4,\"position\":4,\"verseNo\":4},{\"siteId\":5,\"position\":5,\"verseNo\":5}]}" \
          | jq -r '.error.code')" "COURSE-4001" "⑨ verseNo≠position → 400 COURSE-4001"

# ── ⑩ site-distances UPSERT — 두 번 보내도 행 수가 같다
curl -s -X PUT "$B/api/admin/site-distances" -H "$AH" -H 'Content-Type: application/json' \
  -d '{"items":[{"siteAId":1,"siteBId":2,"minMinutes":40},{"siteAId":2,"siteBId":1,"minMinutes":41}]}' > /dev/null
N1=$(curl -s -X PUT "$B/api/admin/site-distances" -H "$AH" -H 'Content-Type: application/json' \
  -d '{"items":[{"siteAId":1,"siteBId":2,"minMinutes":42},{"siteAId":2,"siteBId":1,"minMinutes":43}]}' | jq -r '.success')
expect "$N1" "true" "⑩ site-distances UPSERT 200 (행 수 불변은 SQL 로 확인)"

# ── ⑪ 옛 공개 검색 제거 + 관리자 목록 동작
echo "MEASURED GET /api/sites (옛 공개 검색) -> $(code "$B/api/sites")"
expect "$(code -H "$AH" "$B/api/admin/sites?q=CH3")" "200" "⑪ ADMIN GET /api/admin/sites?q= → 200"

# ── ⑫ regions 에 code
expect "$(curl -s "$B/api/regions" | jq -r '.data.items[0].code' | grep -c '^[A-Z]')" "1" "⑫ regions items[0].code 존재"

echo "----"
echo "PASS $PASS / FAIL $FAIL"
[ "$FAIL" -eq 0 ] && echo "ALL CH3 OK"
