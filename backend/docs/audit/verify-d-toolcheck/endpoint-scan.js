const fs = require('fs'), path = require('path');
function walk(d, out = []) {
  for (const e of fs.readdirSync(d, { withFileTypes: true })) {
    const p = path.join(d, e.name);
    if (e.isDirectory()) walk(p, out); else if (e.name.endsWith('Controller.java')) out.push(p);
  }
  return out;
}

/* ── SecurityConfig 의 permitAll 목록을 읽는다 ── */
const sec = fs.readFileSync('C:/temple-stamp-project/src/main/java/com/templestamp/global/config/SecurityConfig.java', 'utf8');
const permitAny = [];   // 메서드 무관
const permitGet = [];
const permitOptions = [];   // preflight 전용. 여기에 "/**" 가 있는데 permitAny 로 섞으면 전부가 공개로 보인다
for (const m of sec.matchAll(/\.requestMatchers\(([^)]*)\)\s*\.permitAll\(\)/gs)) {
  const arg = m[1];
  const paths = [...arg.matchAll(/"([^"]+)"/g)].map(x => x[1]);
  const method = (arg.match(/HttpMethod\.(\w+)/) || [])[1];
  if (method === 'GET') permitGet.push(...paths);
  else if (method === 'OPTIONS') permitOptions.push(...paths);
  else if (!method) permitAny.push(...paths);
  else permitAny.push(...paths.map(x => method + ' ' + x));
}
const adminPrefix = /\.requestMatchers\("([^"]+)"\)\s*\.hasRole\("ADMIN"\)/.exec(sec);

function matches(pattern, url) {
  if (pattern.endsWith('/**')) return url.startsWith(pattern.slice(0, -3));
  return pattern === url;
}
function access(method, url) {
  if (adminPrefix && matches(adminPrefix[1], url)) return 'ROLE_ADMIN';
  if (permitAny.some(p => matches(p, url))) return 'permitAll';
  if (method === 'GET' && permitGet.some(p => matches(p, url))) return 'permitAll(GET)';
  return '로그인';
}

const rows = [];
for (const f of walk('C:/temple-stamp-project/src/main/java').sort()) {
  const s = fs.readFileSync(f, 'utf8');
  const cls = path.basename(f, '.java');
  const base = (s.match(/@RequestMapping\("([^"]+)"\)/) || [])[1] || '';
  const validated = /@Validated/.test(s);
  for (const m of s.matchAll(/@(Get|Post|Put|Patch|Delete)Mapping(?:\("([^"]*)"\))?/g)) {
    const method = m[1].toUpperCase();
    const url = base + (m[2] || '');
    rows.push({ cls, method, url, access: access(method, url), validated });
  }
}
rows.sort((a, b) => a.url.localeCompare(b.url) || a.method.localeCompare(b.method));

/* ── 컬렉션에서 실제로 호출하는 (메서드, 경로 모양) 을 모은다 ── */
// 컬렉션이 두 벌이다. 회향 시나리오는 준비 SQL 이 먼저 돌아야 해서 파일을 나눴다(챕터 7).
// 한쪽만 읽으면 그쪽에 없는 요청이 "미검증" 으로 잘못 뜬다.
const COLLECTIONS = [
  'C:/temple-stamp-project/postman/temple-stamp-all.postman_collection.json',
  'C:/temple-stamp-project/postman/w2-hoehyang.postman_collection.json',
];
// 회향 컬렉션은 폴더 없이 요청이 바로 들어 있다 — 폴더 하나로 감싸 같은 모양으로 맞춘다.
const col = { item: COLLECTIONS.flatMap(f => {
  const items = JSON.parse(fs.readFileSync(f, 'utf8')).item;
  const folders = items.filter(i => Array.isArray(i.item));
  const loose = items.filter(i => !Array.isArray(i.item));
  return loose.length ? folders.concat([{ name: 'W2 회향', item: loose }]) : folders;
}) };
const called = [];
for (const folder of col.item) {
  for (const it of folder.item || []) {
    const r = it.request; if (!r) continue;
    const raw = (r.url && r.url.raw ? r.url.raw : '').split('?')[0].replace('{{baseUrl}}', '');
    called.push({ method: r.method, raw, folder: folder.name.split(' ')[0], name: it.name });
  }
}
/**
 * 경로를 조각으로 나눠 견준다. 컨트롤러의 {siteId} 자리에는 컬렉션의 {{cs1}} 이든 숫자든
 * LOCATION_SERVICE 같은 문자열이든 무엇이 와도 된다 — 그 자리는 값이 들어가는 자리다.
 */
const seg = u => u.replace(/^\/|\/$/g, '').split('/');
function samePath(controllerUrl, calledUrl) {
  const a = seg(controllerUrl), b = seg(calledUrl);
  if (a.length !== b.length) return false;
  return a.every((x, i) => x.startsWith('{') || b[i].startsWith('{{') || x === b[i]);
}

const missing = [];
for (const r of rows) {
  const hit = called.filter(c => c.method === r.method && samePath(r.url, c.raw));
  r.covered = hit.length;
  r.by = hit.slice(0, 3).map(h => h.folder + ':' + h.name.split(' ')[0]).join(' · ');
  if (!hit.length) missing.push(r);
}

let md = '# 엔드포인트 전수 — 컨트롤러 스캔 ↔ 컬렉션 대조\n\n';
md += '자동 생성: `node docs/verify/endpoint-scan.js` (전체 점검 C · STEP 1)\n\n';
md += '```\n컨트롤러 ' + new Set(rows.map(r => r.cls)).size + '개 · 엔드포인트 ' + rows.length + '개\n';
md += '컬렉션에서 호출됨 ' + (rows.length - missing.length) + ' · 미검증 ' + missing.length + '\n```\n\n';
md += '접근 권한은 `SecurityConfig` 의 매처를 그대로 읽어 판정했다 — 손으로 적은 목록이 아니다.\n\n';
md += '| # | 메서드 | 경로 | 접근 | `@Validated` | 컬렉션 |\n|---:|---|---|---|---|---|\n';
rows.forEach((r, i) => {
  md += `| ${i + 1} | ${r.method} | \`${r.url}\` | ${r.access} | ${r.validated ? '✅' : '—'} | ${r.covered ? r.by : '**미검증**'} |\n`;
});
if (missing.length) {
  md += '\n## 미검증 엔드포인트\n\n| 메서드 | 경로 | 접근 | 왜 |\n|---|---|---|---|\n';
  // 왜 아직 안 부르는지 — 빈 칸으로 두면 다음 사람이 다시 조사해야 한다.
  const WHY = {
    'GET /api/print-orders/{printOrderId}': '인쇄 주문이 있어야 부를 수 있다 — 챕터 9 에서 검증',
    'DELETE /api/print-orders/{printOrderId}': '취소 조건(상태 전이)이 챕터 9 범위 — 그때 검증',
  };
  missing.forEach(r => { md += `| ${r.method} | \`${r.url}\` | ${r.access} | ${WHY[r.method + " " + r.url] || ""} |\n`; });
}
fs.writeFileSync('C:/temple-stamp-project/backend/docs/audit/endpoints.md', md, 'utf8');
console.log('엔드포인트 ' + rows.length + ' · 미검증 ' + missing.length);
missing.forEach(r => console.log('  ✗ ' + r.method + ' ' + r.url + '  [' + r.access + ']'));
console.log('@Validated 없는 컨트롤러:', [...new Set(rows.filter(r => !r.validated).map(r => r.cls))].join(', '));
