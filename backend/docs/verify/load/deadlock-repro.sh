#!/usr/bin/env bash
# 청소기(세션 만료) ↔ 미션 제출 교착 재현 시험 — 챕터 9 보강에서 상시화했다.
#
# 최종 점검 F 가 찾은 교착은 "만료 대상이 있을 때만" 난다. 평소 검증에는 만료 대상이 없어
# 일괄 UPDATE 가 아무 행도 잠그지 않고 지나가므로, 그냥 부하를 주면 영원히 보이지 않는다.
# 그래서 이 시험은 **만료 대상 3,000건을 일부러 만들어 두고** 청소기를 2초마다 돌린다.
#
#   통과 기준: 이번 실행 이후에 난 교착 0 · 5xx 0
#
# 쓰는 법:
#   bash backend/docs/verify/load/deadlock-repro.sh              # 개발 DB
#   DB_NAME=temple_stamp_verify_f bash .../deadlock-repro.sh     # 다른 스키마
#   LOAD_MS=60000 LOAD_WORKERS=20 ... 로 시간·동시수를 바꾼다
#
# ★ 앱이 예열된 뒤에 돌린다. 기동 직후에 때리면 커넥션 풀이 아직 차지 않아
#   교착이 아니라 커넥션 획득 실패로 500 이 난다(정리.md §5-6).
set -u
cd "$(dirname "$0")/../../../.."          # 저장소 뿌리

BASE=${BASE:-http://localhost:8080}
DB_NAME=${DB_NAME:-temple_stamp_project}
DB_USER=${DB_USER:-root}
DB_PASS=${DB_PASS:-1234}
MYSQL=${MYSQL_BIN:-mysql}
TARGETS=${TARGETS:-3000}
export LOAD_MS=${LOAD_MS:-60000}
export LOAD_WORKERS=${LOAD_WORKERS:-20}
OUT=backend/docs/verify/load/deadlock-repro-result.txt

q() { "$MYSQL" -u"$DB_USER" -p"$DB_PASS" -N -B --default-character-set=utf8mb4 -D "$DB_NAME" -e "$1" 2>/dev/null; }
qs() { "$MYSQL" -u"$DB_USER" -p"$DB_PASS" -e "$1" 2>/dev/null; }

say() { echo "$@" | tee -a "$OUT"; }
: > "$OUT"

say "=================================================================="
say " 교착 재현 시험 — $(date '+%Y-%m-%d %H:%M:%S') · 스키마 $DB_NAME"
say "=================================================================="

if [ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$BASE/health")" != "200" ]; then
  say "❌ 앱이 떠 있지 않다($BASE/health). bootRun 을 먼저 띄운다."
  exit 1
fi

# ── ① 만료 대상 만들기 — 이미 끝난 도장을 잠시 되돌려 "60분 넘게 열려 있는 세션" 으로 만든다.
#     새로 만들지 않는 이유는 도장에 딸린 것(순례·사용자·자리)을 함께 만들어야 하기 때문이다.
BEFORE_DONE=$(q "SELECT COUNT(*) FROM stamp WHERE verify_status = 'COMPLETED';")

# 되돌릴 도장이 모자라면 먼저 만든다. 대상이 없으면 일괄 UPDATE 가 아무 행도 잠그지 않고 지나가
# "교착 0" 이 나오는데, 그것은 통과가 아니라 <b>재지 못한 것</b>이다 — 이 시험이 조용히 무의미해지는 길이다.
for TRY in 1 2 3 4; do
  [ "${BEFORE_DONE:-0}" -ge "$TARGETS" ] && break
  say ""
  say "  도장이 $BEFORE_DONE 건이라 30초 부하로 더 만든다 (목표 $TARGETS · $TRY/4회)"
  LOAD_MS=30000 node backend/docs/verify/load/load.js > /dev/null 2>&1
  BEFORE_DONE=$(q "SELECT COUNT(*) FROM stamp WHERE verify_status = 'COMPLETED';")
done
q "UPDATE stamp SET verify_status='QR_DONE', mission_verified_at=NULL,
     gps_verified_at = NOW() - INTERVAL 90 MINUTE
   WHERE verify_status='COMPLETED' ORDER BY stamp_id DESC LIMIT $TARGETS;" > /dev/null
STALE=$(q "SELECT COUNT(*) FROM stamp WHERE verify_status IN ('GPS_DONE','QR_DONE')
             AND gps_verified_at < NOW() - INTERVAL 60 MINUTE;")
say ""
say "① 만료 대상 $STALE 건 준비 (COMPLETED 였던 $BEFORE_DONE 건 중 최대 $TARGETS 건을 되돌렸다)"
if [ "${STALE:-0}" -lt 100 ]; then
  say "⚠ 대상이 $STALE 건뿐이다 — 이 시험은 대상이 많아야 의미가 있다."
  say "  run-all.sh 를 한 번 돌려 도장을 쌓은 뒤에 다시 하거나, 부하를 먼저 준다."
fi

# ── ② 기준 시각. 교착은 있음/없음이 아니라 **시각**으로 판정한다 —
#     InnoDB 는 가장 최근 하나만 남기므로 옛 기록이 서버를 다시 켤 때까지 떠 있다(정리.md §6-18 덧).
MARK_EPOCH=$(date '+%s')
MARK=$(date '+%Y-%m-%d %H:%M:%S')
say "② 기준 시각 $MARK — 이 시각 이후에 난 교착만 센다"

# ── ③ 청소기를 2초마다 돌리면서 부하를 준다.
ADM=$(curl -s -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
  --data-binary @backend/docs/verify/body/admin-login.json \
  | node -e "let d='';process.stdin.on('data',c=>d+=c).on('end',()=>{try{console.log(JSON.parse(d).data.accessToken)}catch(e){console.log('')}})")
if [ -z "$ADM" ]; then say "❌ 관리자 로그인 실패 — 시드가 올라가 있는지 본다"; exit 1; fi

ROUNDS=$(( LOAD_MS / 2000 ))
( for _ in $(seq 1 "$ROUNDS"); do
    curl -s -o /dev/null -X POST "$BASE/api/admin/housekeeping/run" -H "Authorization: Bearer $ADM"
    sleep 2
  done ) &
CLEANER=$!

say "③ 동시 $LOAD_WORKERS × $((LOAD_MS / 1000))초 부하 + 청소기 2초마다 ($ROUNDS 회)"
node backend/docs/verify/load/load.js > backend/docs/verify/load/deadlock-repro-load.txt 2>&1
wait "$CLEANER" 2>/dev/null

FIVEXX=$(grep -oE "5xx·연결실패 [0-9]+" backend/docs/verify/load/deadlock-repro-load.txt | grep -oE "[0-9]+$")
say ""
sed -n '/| 단계/,/5xx·연결실패/p' backend/docs/verify/load/deadlock-repro-load.txt | tee -a "$OUT"

# ── ④ 교착 판정
DL_LINE=$(qs "SHOW ENGINE INNODB STATUS\G" | sed -n '/LATEST DETECTED DEADLOCK/,+2p' | tail -1)
DL_TIME=$(echo "$DL_LINE" | grep -oE "^[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}")
say ""
say "④ 마지막 교착 기록: ${DL_TIME:-없음}"

BAD=0
if [ -n "$DL_TIME" ]; then
  DL_EPOCH=$(date -d "$DL_TIME" '+%s' 2>/dev/null || echo 0)
  if [ "$DL_EPOCH" -ge "$MARK_EPOCH" ]; then
    say "  ❌ 이번 실행에서 교착이 났다 — 기준 $MARK 이후"
    qs "SHOW ENGINE INNODB STATUS\G" | sed -n '/LATEST DETECTED DEADLOCK/,/WE ROLL BACK/p' | head -60 >> "$OUT"
    BAD=$((BAD + 1))
  else
    say "  ✅ 이번 실행 이후 교착 없음 (남은 기록은 $DL_TIME 의 옛것)"
  fi
else
  say "  ✅ 교착 기록 자체가 없다"
fi

if [ "${FIVEXX:-0}" = "0" ]; then
  say "  ✅ 5xx·연결실패 0"
else
  say "  ❌ 5xx·연결실패 ${FIVEXX}건"
  grep -A6 "실패 원문" backend/docs/verify/load/deadlock-repro-load.txt >> "$OUT"
  BAD=$((BAD + 1))
fi

# ── ⑤ 치우기. 부하가 만든 계정과 그 흔적을 지운다(cleanup.sql 이 reg-% 를 이미 안다).
#     남겨 두면 다음 검증의 숫자가 실행 횟수만큼 커진다.
"$MYSQL" -u"$DB_USER" -p"$DB_PASS" --default-character-set=utf8mb4 -D "$DB_NAME" \
  < backend/docs/verify/cleanup.sql > /dev/null 2>&1
LEFT=$(q "SELECT COUNT(*) FROM users WHERE email LIKE 'reg-load-%@test.com';")
say ""
say "⑤ 정리 — 남은 부하 계정 ${LEFT:-?}건"

say ""
say "=================================================================="
if [ "$BAD" -eq 0 ]; then
  say " 통과 — 교착 0 · 5xx 0   결과 파일: $OUT"
else
  say " 실패 $BAD 항목   결과 파일: $OUT"
fi
say "=================================================================="
exit "$BAD"
