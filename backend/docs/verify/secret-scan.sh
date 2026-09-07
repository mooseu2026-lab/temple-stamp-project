#!/usr/bin/env bash
# backend/docs/verify/secret-scan.sh — 저장소에 시크릿이 섞였는지 본다.
#
#   bash backend/docs/verify/secret-scan.sh
#
# 공개 저장소에 한 번 올라간 값은 지워도 이력에 남는다. 그래서 올리기 전에 한 번,
# 그리고 값이 바뀔 때마다 돌린다. 종료코드 1 이면 올리면 안 된다.
#
# ★ git 이 실제로 올릴 파일만 본다. .gitignore 로 빠지는 파일은 검사 대상이 아니다 —
#   .env.local 은 저장소에 있지만 커밋되지 않으므로 여기서 걸리면 오탐이다.
set -u
cd "$(dirname "$0")/../../.."

BAD=0
say() { echo "$@"; }
hit() { say "  ❌ $1"; BAD=$((BAD + 1)); }
ok()  { say "  ✅ $1"; }

# 검사 대상 = git 이 추적하거나 추적할 파일. git 이 없으면 전체에서 무시 목록만 뺀다.
if git rev-parse --git-dir > /dev/null 2>&1; then
  FILES=$(git ls-files --cached --others --exclude-standard)
else
  FILES=$(find . -type f \
    -not -path "./.git/*" -not -path "./build/*" -not -path "./.gradle/*" \
    -not -path "./node_modules/*" -not -name "*.log" -not -name ".env.local" \
    -not -path "./backend/docs/audit/verify-f-logs/*" \
    -not -path "./backend/docs/audit/ch9-hardening-logs/*")
fi
COUNT=$(echo "$FILES" | grep -c . )

say "=================================================================="
say " 시크릿 검사 — 대상 $COUNT 파일 ($(date '+%Y-%m-%d %H:%M'))"
say "=================================================================="
say ""

# 파일 목록을 한 번에 넘긴다. 파일마다 grep 을 새로 띄우면 760개에서 몇 분이 걸려
# 아무도 안 돌리는 검사가 된다 — 돌지 않는 검사는 없는 검사다.
FILE_LIST=$(mktemp)
echo "$FILES" | grep . > "$FILE_LIST"
trap 'rm -f "$FILE_LIST"' EXIT

# 사람이 보고 "비밀이 아니다" 라고 판단한 줄. 왜 괜찮은지가 그 파일에 함께 적혀 있다.
# 목록을 두는 이유는, 오탐을 그냥 두면 검사가 매번 빨간 줄을 내고 아무도 안 보게 되기 때문이다.
ALLOW=backend/docs/verify/secret-scan-allow.txt

allowed() {   # 표준입력에서 허용목록에 걸리는 줄을 뺀다
  if [ -f "$ALLOW" ]; then
    grep -vFf <(grep -vE '^\s*#|^\s*$' "$ALLOW")
  else
    cat
  fi
}

scan() {   # 1=설명  2=정규식  3=제외 정규식(선택)
  local desc="$1" re="$2" skip="${3:-^$}"
  local found
  found=$(xargs -a "$FILE_LIST" -d '\n' grep -InE --binary-files=without-match "$re" 2>/dev/null \
          | grep -vE "$skip" | allowed)
  if [ -z "$found" ]; then ok "$desc"; else
    hit "$desc"
    echo "$found" | head -8 | sed 's/^/     /'
  fi
}

say "① 값이 박힌 비밀 설정 (yml·properties)"
scan "yml 에 secret/password 값" \
  "^[[:space:]]*(secret|secret-key|password|rest-key|token)[[:space:]]*:[[:space:]]*[^\$[:space:]#]" \
  "example|<|여기|CHANGE"

say ""
say "② .env 계열에 비밀값"
# ★ 제외 규칙을 "=$" 로 두면 base64 패딩(==)으로 끝나는 <b>진짜 키</b>까지 함께 지워진다.
#   자가검증에서 심은 키가 정확히 그렇게 빠져나갔다 — 조용히 분모가 줄어드는 고장이다.
#   줄 전체를 앵커로 잡아 "값이 아예 비어 있는 줄" 만 뺀다.
scan ".env 에 SECRET/PASSWORD/KEY 값" \
  "^(JWT_SECRET|QR_SECRET|DB_PASSWORD|STORAGE_SECRET_KEY|KAKAO_REST_API_KEY)=.+" \
  ":[0-9]+:[A-Z_]+=[[:space:]]*$"

say ""
say "③ Base64·hex 로 보이는 긴 문자열 (32바이트 이상 키 모양)"
scan "Base64 32바이트 이상" \
  "(SECRET|secret|KEY|key|token|TOKEN)[\"'[:space:]:=]+[A-Za-z0-9+/]{40,}={0,2}" \
  "example\.com|dummy|sha256|SHA-256|placeholder|A-Za-z0-9|hotconv|makeotfexe"

say ""
say "④ 카카오 REST 키 모양 (32자 hex)"
scan "32자 hex 키" "[Kk]akao.{0,20}[0-9a-f]{32}" "dummy|not-real"

say ""
say "⑤ 접속 문자열에 비밀번호"
scan "URL 안의 비밀번호" "(mysql|jdbc|mongodb|redis)://[^:/[:space:]]+:[^@[:space:]]+@" "user:pass|<|비밀번호"

say ""
say "⑥ 개인키 파일"
scan "PEM/개인키" "BEGIN (RSA |EC |OPENSSH |)PRIVATE KEY"

say ""
say "⑦ .env.local 이 올라가는가"
if echo "$FILES" | grep -q "^\.env\.local$"; then
  hit ".env.local 이 커밋 대상이다 — .gitignore 를 확인할 것"
else
  ok ".env.local 은 커밋 대상이 아니다"
fi

say ""
say "=================================================================="
if [ "$BAD" -eq 0 ]; then
  say " 시크릿 0건 — 올려도 된다"
else
  say " ❌ $BAD 항목에서 걸렸다 — 값을 빼거나 .gitignore 에 넣고 다시 돌린다"
fi
say "=================================================================="
exit "$BAD"
