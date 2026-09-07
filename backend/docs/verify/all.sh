#!/usr/bin/env bash
# backend/docs/verify/all.sh — 검증 한 벌을 순서대로 돌린다. 하나라도 실패하면 거기서 멈춘다.
#
#   run-all.sh 1회차 → 2회차(2회 동일 확인) → db-check → security-check → 교착 재현
#
# 배포 전에 사람이 보는 문은 이것 하나다(정리.md §5). 초록이면 배포 절차로 넘어간다.
#
#   bash backend/docs/verify/all.sh
#   DB_NAME=<스키마> bash backend/docs/verify/all.sh     # 다른 스키마로
#
# ★ 앱이 떠 있어야 한다. 그리고 기동 직후가 아니라 <b>예열된 뒤</b>에 돌린다 —
#   커넥션 풀이 아직 차지 않은 상태에서 부하를 주면 교착이 아니라 커넥션 획득 실패로 500 이 난다(§5-6).
set -u
cd "$(dirname "$0")/../../.."          # 저장소 뿌리

BASE=${BASE_URL:-http://localhost:8080}
DB_NAME=${DB_NAME:-temple_stamp_project}
export DB_NAME
OUT=backend/docs/verify/all-result.txt
APP_LOG=${APP_LOG:-app.log}
STARTED=$(date '+%Y-%m-%d %H:%M:%S')
T0=$(date +%s)

: > "$OUT"
say() { echo "$@" | tee -a "$OUT"; }

say "=================================================================="
say " 검증 한 벌 — $STARTED · 스키마 $DB_NAME"
say "=================================================================="

if [ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$BASE/health")" != "200" ]; then
  say "❌ 앱이 떠 있지 않다($BASE/health). ./gradlew bootRun 을 먼저."
  exit 1
fi

# 단계마다 결과를 담아 둔다. 실패하면 그 자리에서 멈추는 것이 요점이다 —
# 앞 단계가 깨진 채로 뒤를 돌리면 뒤의 숫자가 무엇을 말하는지 알 수 없다.
R_NEWMAN1="—"; R_NEWMAN2="—"; R_SAME="—"; R_DB="—"; R_SEC="—"; R_LOCK="—"
FAILED=""

stop() {   # 1=단계 이름  2=한 줄 이유
  say ""
  say "❌ $1 에서 멈췄다 — $2"
  say "   앞 단계가 깨진 채로 뒤를 돌리면 뒤의 숫자를 믿을 수 없다. 여기서 끝낸다."
  summary
  exit 1
}

summary() {
  local T1 ELAPSED
  T1=$(date +%s); ELAPSED=$((T1 - T0))
  say ""
  say "=================================================================="
  say " ① newman 1회차   : $R_NEWMAN1"
  say " ② newman 2회차   : $R_NEWMAN2"
  say " ③ 2회 동일       : $R_SAME"
  say " ④ DB H1~H18      : $R_DB"
  say " ⑤ 보안 S1~S25    : $R_SEC"
  say " ⑥ 교착 재현      : $R_LOCK"
  say "=================================================================="
  say " ${FAILED:-초록 — 전부 통과}   ($((ELAPSED / 60))분 $((ELAPSED % 60))초 · 결과 $OUT)"
  say "=================================================================="
}

# ── ① 1회차 ─────────────────────────────────────────────────────────
say ""
say "① run-all.sh 1회차"
if ! bash postman/run-all.sh >> "$OUT" 2>&1; then
  R_NEWMAN1="실패"; FAILED="실패: newman 1회차"
  stop "newman 1회차" "요청이나 단언이 깨졌다. 위 원문에서 실패한 요청 이름을 본다"
fi
cp backend/docs/verify/all-sql-result.txt backend/docs/verify/all-sql-result.1.txt
R_NEWMAN1=$(grep -cE "^\s+[0-9]+\.\s" "$OUT" > /dev/null; echo "통과")
say "   → 통과"

# ── ② 2회차 ─────────────────────────────────────────────────────────
say ""
say "② run-all.sh 2회차 (같은 결과가 나와야 한다)"
if ! bash postman/run-all.sh >> "$OUT" 2>&1; then
  R_NEWMAN2="실패"; FAILED="실패: newman 2회차"
  stop "newman 2회차" "1회차는 통과했는데 2회차가 깨졌다 — 정리가 덜 됐거나 검사가 상태를 남긴다"
fi
R_NEWMAN2="통과"
say "   → 통과"

# ── ③ 2회 동일 ──────────────────────────────────────────────────────
say ""
say "③ 보조 SQL 채점이 두 번 같은가"
if diff -q backend/docs/verify/all-sql-result.1.txt backend/docs/verify/all-sql-result.txt > /dev/null; then
  R_SAME="바이트 동일"
  say "   → 바이트까지 같다"
else
  R_SAME="다르다"; FAILED="실패: 2회 채점 불일치"
  diff backend/docs/verify/all-sql-result.1.txt backend/docs/verify/all-sql-result.txt >> "$OUT" 2>&1
  stop "2회 동일" "채점 결과가 실행마다 다르다 — 위 diff 를 본다"
fi
rm -f backend/docs/verify/all-sql-result.1.txt

# ── ④ DB ────────────────────────────────────────────────────────────
say ""
say "④ db-check.sh after (H1~H18 · H17 은 임시 스키마 둘을 세워 비교한다)"
bash backend/docs/verify/db-check.sh after >> "$OUT" 2>&1
DB_BAD=$(grep -c "  ❌" backend/docs/verify/db-check-result.txt)
if [ "$DB_BAD" -eq 0 ]; then
  R_DB="어긋남 0"
  say "   → 어긋남 0"
else
  R_DB="❌ $DB_BAD"; FAILED="실패: DB 검사 $DB_BAD 건"
  grep "  ❌" backend/docs/verify/db-check-result.txt >> "$OUT"
  stop "db-check" "$DB_BAD 건이 어긋났다"
fi

# ── ⑤ 보안 ──────────────────────────────────────────────────────────
say ""
say "⑤ security-check.sh (S1~S25)"
bash backend/docs/verify/security-check.sh "$APP_LOG" >> "$OUT" 2>&1
SEC_LINE=$(grep -oE "통과 [0-9]+ · 실패 [0-9]+" backend/docs/verify/security-check-result.txt | tail -1)
SEC_BAD=$(echo "$SEC_LINE" | grep -oE "실패 [0-9]+" | grep -oE "[0-9]+")
if [ "${SEC_BAD:-1}" -eq 0 ]; then
  R_SEC="$SEC_LINE"
  say "   → $SEC_LINE"
else
  R_SEC="❌ $SEC_LINE"; FAILED="실패: 보안 검사"
  stop "security-check" "$SEC_LINE"
fi

# ── ⑥ 교착 재현 ─────────────────────────────────────────────────────
say ""
say "⑥ 교착 재현 시험 (만료 대상을 만들어 두고 청소기와 부하를 겹친다)"
if bash backend/docs/verify/load/deadlock-repro.sh >> "$OUT" 2>&1; then
  R_LOCK="교착 0 · 5xx 0"
  say "   → 교착 0 · 5xx 0"
else
  R_LOCK="❌ 교착 또는 5xx"; FAILED="실패: 교착 재현"
  stop "교착 재현" "이번 실행에서 교착이 났거나 5xx 가 나왔다 — deadlock-repro-result.txt 를 본다"
fi

summary
exit 0
