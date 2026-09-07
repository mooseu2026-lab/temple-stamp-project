#!/usr/bin/env bash
# backend/docs/verify/runtime-check.sh — 런타임 오류 스캔 (전체 점검 C §3)
#
#   bash backend/docs/verify/runtime-check.sh <bootRun 로그 파일>
#
# R1 기동 로그 · R2 전 요청 뒤 로그 · R6 매퍼 정합 · R7 미사용 코드.
# R3·R4 는 컬렉션 X 폴더, R5 는 JUnit + T 폴더 동시 실행이라 여기서 다루지 않는다.
cd "$(dirname "$0")/../../.."
LOG=${1:-app.log}
OUT=backend/docs/verify/runtime-check-result.txt
say() { echo "$@" | tee -a "$OUT"; }

: > "$OUT"
say "=================================================================="
say " 런타임 오류 스캔 — $(date '+%Y-%m-%d %H:%M:%S')"
say " 로그: $LOG"
say "=================================================================="

# ── 허용 목록 ───────────────────────────────────────────────────────────
# "이건 문제가 아니다" 를 여기 한곳에 모아 둔다. 늘어나면 그때마다 이유를 적는다.
#   · Sharing is only supported…   : JVM 이 CDS 아카이브를 못 쓸 때의 안내. 기능과 무관
#   · GlobalExceptionHandler       : 우리가 일부러 4xx 를 만들며 남긴 WARN(검증 세트가 오류를 일부러 낸다)
#   · SEED / no-coord              : 시더가 남기는 안내
#   · deprecated / Deprecation     : 빌드 경고
#   · 좌표 필드 반입 차단           : CoordinateFieldGuard 가 막은 것. 검증 세트가 일부러 좌표를 실어 보낸다(S4)
#   · No mapping for               : 없앤 경로를 일부러 부른 것(A11 /reissue · P09 progress · P10 passport · G05)
#   · 폐기된 리프레시 토큰 재사용    : 재사용 탐지가 동작한 것(A12). 이게 안 찍히면 오히려 문제다
#   · 세션 만료로 닫음              : 만료 기록(REQUIRES_NEW)이 동작한 것(T-08)
#   · 완주 취소 / 인증서 회수 / 보상 정리 / 회향 조건이 깨져 / 관리자 회수 : 챕터 7 의 취소 연쇄와
#                                    관리자 회수가 동작한 것(W16·W22·W25b). 회수는 되돌릴 수 없어 일부러 WARN 이다.
#                                    되돌리기 어려운 일이라 일부러 WARN 으로 남긴다 — 안 찍히면 오히려 문제다
# 늘릴 때는 "왜 문제가 아닌가" 를 한 줄로 함께 적는다. 이유 없는 허용은 경고를 지우는 것과 같다.
ALLOW='Sharing is only supported|GlobalExceptionHandler|SEED |no-coord|[Dd]eprecat|--enable-native-access|An illegal reflective|좌표 필드 반입 차단|No mapping for|폐기된 리프레시 토큰 재사용|세션 만료로 닫음|완주 취소|인증서 회수|보상 정리|회향 조건이 깨져|관리자 회수'

scan() {   # $1=라벨  $2=패턴
  local hits
  hits=$(grep -nE "$2" "$LOG" 2>/dev/null | grep -vE "$ALLOW" || true)
  if [ -z "$hits" ]; then
    say "  ✅ $1 — 0건"
  else
    say "  ⚠ $1 — $(echo "$hits" | wc -l)건"
    echo "$hits" | head -20 | sed 's/^/      /' | tee -a "$OUT"
  fi
}

say ""
say "── R1  기동 로그 ────────────────────────────────────────────────"
say "기대: 기동 구간(Started 까지)에 WARN·ERROR·Exception 0건. 허용 목록은 스크립트 상단."
STARTED=$(grep -n "Started TempleStampApplication" "$LOG" | head -1 | cut -d: -f1)
if [ -n "$STARTED" ]; then
  head -n "$STARTED" "$LOG" > /tmp/boot.$$
  say "  기동까지 $STARTED 줄"
  hits=$(grep -nE " WARN | ERROR |Exception" /tmp/boot.$$ | grep -vE "$ALLOW" || true)
  [ -z "$hits" ] && say "  ✅ 0건" || { say "  ⚠ $(echo "$hits" | wc -l)건"; echo "$hits" | head -20 | sed 's/^/      /' | tee -a "$OUT"; }
  rm -f /tmp/boot.$$
else
  say "  ❌ 기동 표시를 못 찾았다 — 로그 파일을 확인할 것"
fi

say ""
say "── R2  전 요청 뒤 로그 ──────────────────────────────────────────"
say "기대: ERROR·스택트레이스 0건. 5xx 는 하나라도 있으면 원인까지 적는다."
scan "ERROR"          " ERROR "
scan "스택트레이스"    "^	at com\.templestamp"
scan "500 응답"        "status=5[0-9][0-9]|HTTP 5[0-9][0-9]"
scan "처리되지 않은 예외" "처리되지 않은 예외"
say ""
WARN_ALL=$(grep -cE ' WARN ' "$LOG" 2>/dev/null || true)
# grep -vc 는 0건일 때 종료코드 1 이라 그냥 || echo 0 을 붙이면 0 이 두 줄 찍힌다.
WARN_LEFT=$(grep -E ' WARN ' "$LOG" 2>/dev/null | grep -vcE "$ALLOW" || true)
say "  WARN 총계(허용 목록 제외): ${WARN_ALL:-0} 중 ${WARN_LEFT:-0}"

say ""
say "── R6  매퍼 정합 ────────────────────────────────────────────────"
say "기대: 매퍼↔XML · #{} ↔ @Param · resultType 필드 미채움 전부 0."
node backend/docs/verify/mapper-check.js 2>&1 | tee -a "$OUT"

say ""
say "── R7  미사용 코드 (보고만) ─────────────────────────────────────"
say "기대: 목록만 남긴다. STAMP-4003 은 예약이라 허용."
node - <<'NODE' 2>&1 | tee -a "$OUT"
const fs = require('fs'), path = require('path');
function walk(d, f, out = []) {
  for (const e of fs.readdirSync(d, { withFileTypes: true })) {
    const p = path.join(d, e.name);
    if (e.isDirectory()) walk(p, f, out); else if (f(e.name)) out.push(p);
  }
  return out;
}
const src = walk('src/main/java', n => n.endsWith('.java')).map(f => fs.readFileSync(f, 'utf8')).join('\n');
const ec = fs.readFileSync('src/main/java/com/templestamp/global/error/ErrorCode.java', 'utf8');
const codes = [...ec.matchAll(/^\s{4}([A-Z][A-Z0-9_]+)\(/gm)].map(m => m[1]);
const unused = codes.filter(c => {
  const uses = src.split('ErrorCode.' + c).length - 1;
  return uses === 0;
});
console.log('  ErrorCode 상수 ' + codes.length + '개 중 참조 0건: ' + (unused.length ? unused.join(', ') : '없음'));
const reserved = ['STAMP_4003'];
const real = unused.filter(c => !reserved.includes(c));
console.log('  예약(STAMP_4003) 제외하면: ' + (real.length ? real.join(', ') : '없음'));

// 미참조 매퍼 메서드
const mapperFiles = walk('src/main/java', n => n.endsWith('Mapper.java'));
const dead = [];
for (const f of mapperFiles) {
  const s = fs.readFileSync(f, 'utf8');
  if (!/@Mapper/.test(s)) continue;
  const cls = path.basename(f, '.java');
  const body = s.replace(/\/\*[\s\S]*?\*\//g, '');
  for (const m of body.matchAll(/^\s{4}(?:[\w.<>,\[\]\s]+?)\s+(\w+)\s*\(/gm)) {
    const name = m[1];
    const uses = src.split('.' + name + '(').length - 1;
    if (uses === 0) dead.push(cls + '.' + name);   // 호출부가 하나도 없다 (선언은 ".name(" 이 아니라 걸리지 않는다)
  }
}
console.log('  미참조 매퍼 메서드 ' + dead.length + '개' + (dead.length ? ': ' + dead.join(', ') : ''));
NODE

say ""
say "=================================================================="
say " 끝 — 결과 파일: $OUT"
say "=================================================================="
