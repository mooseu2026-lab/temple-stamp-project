/*
 * backend/docs/verify/open-endpoint-probe.js — 비로그인 실측 (전체 점검 D §3·S13)
 *
 *   node backend/docs/verify/open-endpoint-probe.js
 *
 * SecurityConfig 에서 뽑은 "비로그인 허용 목록" 과 실제 응답이 같은지 본다.
 * 문서로만 맞춰 두면 매처 한 줄이 어긋난 날 아무도 모른다 — 그래서 표에 있는 것을 전부 토큰 없이 두드린다(86개).
 *
 *   허용 목록에 있는 경로 → 401 이 아니어야 한다(400·404·200 은 상관없다. 인증을 요구하지 않으면 통과)
 *   그 밖의 경로        → 반드시 401
 *
 * 쓰기 요청도 토큰 없이 부른다. 401 로 막히는 것이 핵심이라 몸통은 비운다 —
 * 혹시 막히지 않는다면 그것이 바로 찾으려던 결함이다.
 */
const fs = require('fs');
const { execFileSync } = require('child_process');

const BASE = process.env.BASE_URL || 'http://localhost:8080';
const MD = 'C:/temple-stamp-project/backend/docs/audit/endpoints.md';

/* endpoints.md 의 본문 표를 읽는다 — 스캐너가 방금 쓴 것이 정본이다. */
const lines = fs.readFileSync(MD, 'utf8').split('\n');
const rows = [];
for (const l of lines) {
  const m = l.match(/^\| *\d+ \| (\w+) `([^`]+)` \| (없음|USER|ADMIN|EDITOR[+]ADMIN) \|/);
  if (m) rows.push({ method: m[1], url: m[2], auth: m[3] });
}
if (!rows.length) { console.log('❌ endpoints.md 에서 표를 읽지 못했다 — 스캐너를 먼저 돌릴 것'); process.exit(1); }

/* ── 기대 개수 단언 (전체 점검 E §7-1) ───────────────────────────────
   이 도구가 조용히 틀린 방식은 "어긋남 0" 을 유지한 채 <b>분모가 줄어드는</b> 것이었다 —
   권한 칸에 EDITOR+ADMIN 이 생기자 정규식이 그 네 줄을 표에서 통째로 빼먹었고,
   86개를 두드린다면서 82개만 두드렸다. 실패가 아니라 침묵으로 나타난다.
   그래서 (기대 = endpoints.md 요약 = 읽어 들인 행 수)를 먼저 못 박고 시작한다. */
const expected = parseInt(fs.readFileSync(
  'C:/temple-stamp-project/backend/docs/verify/expected-endpoints.txt', 'utf8').trim(), 10);
const summary = parseInt((lines.join('\n').match(/엔드포인트 (\d+) /) || [])[1], 10);
const pre = [];
if (rows.length !== expected) pre.push('읽어 들인 행 ' + rows.length + ' ≠ 기대 ' + expected);
if (summary !== expected) pre.push('endpoints.md 요약 ' + summary + ' ≠ 기대 ' + expected);
if (pre.length) {
  console.log('❌ 개수 단언 실패 — ' + pre.join(' · '));
  console.log('   표를 못 읽고 있을 수 있다(권한 칸 모양·행 형식). 두드린 개수부터 의심할 것.');
  process.exit(1);
}

/* 경로 변수 자리는 1 로 채운다. 무엇이 들어가든 인증은 그 앞에서 판정된다. */
const fill = u => u.replace(/\{[^}]+\}/g, '1');

function call(method, url) {
  const args = ['-s', '-w', '\n%{http_code}', '-X', method, BASE + fill(url)];
  if (method !== 'GET' && method !== 'DELETE') {
    args.push('-H', 'Content-Type: application/json', '--data-binary', '{}');
  }
  const out = execFileSync('curl', args, { encoding: 'utf8' });
  const nl = out.lastIndexOf('\n');
  const body = out.slice(0, nl);
  let code = null;
  try { code = (JSON.parse(body).error || {}).code || null; } catch (e) { /* 본문이 JSON 이 아닐 수 있다 */ }
  return { status: out.slice(nl + 1).trim(), errorCode: code };
}

/* 필터가 막은 401 만 "인증을 요구했다" 로 센다.
   열린 경로도 제 나름의 401 을 낼 수 있다 — 예컨대 refresh 는 쿠키가 없으면 AUTH-4014 를 돌려준다.
   그것은 요청이 컨트롤러까지 들어갔다는 뜻이라 오히려 열려 있다는 증거다. */
const FILTER_401 = 'AUTH-4013';

let open = 0, protectedOk = 0;
const bad = [];
for (const r of rows) {
  const res = call(r.method, r.url);
  const blockedByFilter = res.status === '401' && res.errorCode === FILTER_401;
  if (r.auth === '없음') {
    if (blockedByFilter) bad.push(`${r.method} ${r.url} — 허용 목록인데 필터가 막았다(${res.errorCode})`);
    else open++;
  } else {
    if (!blockedByFilter) bad.push(`${r.method} ${r.url} — 보호 경로인데 ${res.status} ${res.errorCode || ''}`);
    else protectedOk++;
  }
}

console.log(`  비로그인 실측 ${rows.length}개 — 허용 ${open} · 보호(401) ${protectedOk} · 어긋남 ${bad.length}`);
// 허용 + 보호 + 어긋남 이 총 개수와 맞아야 한다. 어느 하나가 조용히 빠지면 여기서 걸린다.
if (open + protectedOk + bad.length !== expected) {
  console.log('❌ 개수 단언 실패 — 허용 ' + open + ' + 보호 ' + protectedOk + ' + 어긋남 ' + bad.length +
              ' ≠ 기대 ' + expected);
  process.exit(1);
}
console.log('  개수 단언 ✅ 기대 = endpoints.md 요약 = 두드린 수 = 허용+보호+어긋남 = ' + expected);
bad.forEach(b => console.log('    ❌ ' + b));
process.exit(bad.length ? 1 : 0);
