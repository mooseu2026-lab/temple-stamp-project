/* 프론트 설계 대조 STEP 1 — 코드에서 API 계약을 뽑아 TypeScript 타입 초안을 만든다.
 *
 *   node backend/docs/frontend/generate-api-types.js
 *
 * 컨트롤러 시그니처와 record 선언을 읽는다. 손으로 옮겨 적으면 그 순간부터 낡는다.
 * 결과는 backend/docs/frontend/api-types.d.ts.
 */
const fs = require('fs');
const path = require('path');

function walk(d, out = []) {
  for (const f of fs.readdirSync(d)) {
    const p = path.join(d, f);
    if (fs.statSync(p).isDirectory()) walk(p, out);
    else if (f.endsWith('.java')) out.push(p);
  }
  return out;
}
const files = walk('src/main/java');

/* ── 애너테이션을 괄호 짝을 세어 지운다.
      @Pattern(regexp = "^[0-9-]{7,20}$") 처럼 인자 안에 괄호·중괄호가 있으면
      단순 정규식은 중간에서 끊겨 필드 이름이 깨진다 — 실제로 그렇게 나왔다. */
function stripAnnotations(src) {
  let out = '';
  for (let i = 0; i < src.length; i++) {
    if (src[i] !== '@') { out += src[i]; continue; }
    let j = i + 1;
    while (j < src.length && /[\w.]/.test(src[j])) j++;      // 이름
    while (j < src.length && /\s/.test(src[j])) j++;
    if (src[j] !== '(') { i = j - 1; continue; }             // 인자 없는 애너테이션
    let depth = 0, inStr = false;
    for (; j < src.length; j++) {
      const c = src[j];
      if (inStr) { if (c === '\\') j++; else if (c === '"') inStr = false; continue; }
      if (c === '"') { inStr = true; continue; }
      if (c === '(') depth++;
      else if (c === ')') { depth--; if (depth === 0) { j++; break; } }
    }
    i = j - 1;
  }
  return out;
}

const JAVA_TS = [
  [/^(Long|long|Integer|int|Short|short|Double|double|Float|float|BigDecimal)$/, 'number'],
  [/^(String|UUID|char|Character)$/, 'string'],
  [/^(Boolean|boolean)$/, 'boolean'],
  [/^(LocalDateTime|OffsetDateTime|LocalDate|LocalTime|Instant|ZonedDateTime)$/, 'string'],
  [/^Object$/, 'unknown'],
  [/^byte\[\]$/, 'string'],   // 바이트 배열은 base64 문자열로 오간다
];
const NUMBER_LIKE = /^(Long|Integer|Short|Double|Float|BigDecimal)$/;
const NULLABLE_BOX = /^(Long|Integer|Short|Double|Float|BigDecimal|Boolean|String|LocalDateTime|OffsetDateTime|LocalDate|LocalTime|Instant|UUID)$/;

function tsType(java) {
  const j = java.trim();
  let m = j.match(/^(?:List|Set|Collection)<(.+)>$/);
  if (m) return tsType(m[1]) + '[]';
  m = j.match(/^Map<[^,]+,\s*(.+)>$/);
  if (m) return 'Record<string, ' + tsType(m[1]) + '>';
  for (const [re, ts] of JAVA_TS) if (re.test(j)) return ts;
  if (/^[A-Z][A-Za-z0-9_]*$/.test(j)) return j;   // 다른 DTO·enum 이름은 그대로
  return 'unknown';
}

/* ── ① record DTO ── */
const dtos = new Map();
const enums = new Map();
for (const f of files) {
  const raw = fs.readFileSync(f, 'utf8');
  const noComments = raw.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/[^\n]*/g, '');

  // enum 은 문자열 리터럴 합집합으로 낸다 — 화면이 분기하는 값이라 이름만으로는 부족하다.
  const em = noComments.match(/public enum (\w+)\s*\{([\s\S]*?)(?:;|\})/);
  if (em) {
    const vals = em[2].split(',').map(x => x.trim().split(/[({\s]/)[0]).filter(x => /^[A-Z][A-Z0-9_]*$/.test(x));
    if (vals.length) enums.set(em[1], vals);
  }

  const m = noComments.match(/public record (\w+)\s*\(([\s\S]*?)\)\s*\{/);
  if (!m) continue;
  const name = m[1];
  if (['ApiResponse', 'PageResponse', 'ItemsResponse'].includes(name)) continue;

  const body = stripAnnotations(m[2]);
  const fields = [];
  let depth = 0, cur = '';
  for (const ch of body) {
    if (ch === '<') depth++;
    if (ch === '>') depth--;
    if (ch === ',' && depth === 0) { fields.push(cur); cur = ''; continue; }
    cur += ch;
  }
  if (cur.trim()) fields.push(cur);

  const parsed = [];
  for (const one of fields) {
    const t = one.trim().replace(/\s+/g, ' ');
    if (!t) continue;
    const mm = t.match(/^(.+?)\s+([A-Za-z_$][A-Za-z0-9_$]*)$/);
    if (!mm) continue;                                  // 모양이 아니면 버린다(조용히 틀린 줄을 내지 않는다)
    parsed.push({ java: mm[1].trim(), name: mm[2], ts: tsType(mm[1]) });
  }
  if (parsed.length) dtos.set(name, { fields: parsed, file: f.split(path.sep).join('/') });
}

/* ── ② 엔드포인트 ── */
const eps = [];
for (const f of files.filter(x => /Controller\.java$/.test(x))) {
  const src = fs.readFileSync(f, 'utf8');
  const base = (src.match(/@RequestMapping\("([^"]*)"\)/) || [])[1] || '';
  const L = src.split(/\r?\n/);
  for (let i = 0; i < L.length; i++) {
    const mm = L[i].match(/@(Get|Post|Put|Patch|Delete)Mapping(?:\("([^"]*)"\))?/);
    if (!mm) continue;
    let sig = '', j = i + 1, status = null;
    for (; j < Math.min(i + 20, L.length); j++) {
      const st = L[j].match(/@ResponseStatus\(HttpStatus\.(\w+)\)/);
      if (st) status = st[1];
      if (/public\s+/.test(L[j])) { sig = L[j]; break; }
    }
    let k = j;
    while (k < L.length && !/\)\s*\{?\s*$/.test(L[k]) && k < j + 12) { k++; sig += ' ' + L[k]; }
    const ret = (sig.match(/public\s+([A-Za-z0-9_<>,\[\] ]+?)\s+\w+\s*\(/) || [])[1] || 'void';
    const bodyDto = (sig.match(/@(?:Valid\s+)?RequestBody\s+(?:@Valid\s+)?(\w+)/) || [])[1] || null;
    eps.push({
      method: mm[1].toUpperCase(),
      path: (base + (mm[2] || '')).replace(/\/$/, '') || '/',
      ret: ret.trim(), body: bodyDto, status,
    });
  }
}

/* ── ③ 파일 ── */
const out = [];
out.push('/* temple-stamp — API 타입 초안 (자동 생성 · ' + new Date().toISOString().slice(0, 10) + ')');
out.push(' *');
out.push(' * 손으로 고치지 말 것. `node backend/docs/frontend/generate-api-types.js` 가');
out.push(' * 컨트롤러와 record 선언에서 다시 뽑는다 — 옮겨 적는 순간부터 낡기 때문이다.');
out.push(' *');
out.push(' * 규약');
out.push(' *   성공: { success: true,  data: T,    error: null, timestamp }');
out.push(' *   실패: { success: false, data: null, error: { code, message, fields }, timestamp }');
out.push(' *   날짜는 전부 ISO + 09:00 문자열. "없음" 은 404 가 아니라 200 + null.');
out.push(' *   DELETE 다섯은 204 · 본문 없음 — res.json() 을 부르면 안 된다.');
out.push(' *   요청 본문에 latitude·longitude 를 넣으면 400 COMMON-4001 이다.');
out.push(' */');
out.push('');
out.push('export interface ApiResponse<T> {');
out.push('  success: boolean;');
out.push('  data: T | null;');
out.push('  error: ApiError | null;');
out.push('  /** ISO 8601 + 09:00 */');
out.push('  timestamp: string;');
out.push('}');
out.push('');
out.push('export interface ApiError {');
out.push('  /** {도메인}-{HTTP 3자리}{일련 1자리} — 예 "STAMP-4091" */');
out.push('  code: string;');
out.push('  message: string;');
out.push('  /** 검증 오류일 때만 채워진다 */');
out.push('  fields: Record<string, string> | null;');
out.push('}');
out.push('');
out.push('/** 페이지가 없는 목록 */');
out.push('export interface ItemsResponse<T> { items: T[]; }');
out.push('');
out.push('/** 페이지 목록. page 는 0부터 */');
out.push('export interface PageResponse<T> {');
out.push('  items: T[];');
out.push('  page: number;');
out.push('  size: number;');
out.push('  totalCount: number;');
out.push('  hasNext: boolean;');
out.push('}');
out.push('');

if (enums.size) {
  out.push('// ── enum — 화면이 이 값으로 분기한다 ──');
  out.push('');
  for (const [name, vals] of [...enums.entries()].sort()) {
    out.push('export type ' + name + " = " + vals.map(v => "'" + v + "'").join(' | ') + ';');
  }
  out.push('');
}

out.push('// ── DTO — record 선언에서 뽑았다 ──');
out.push('');
for (const [name, d] of [...dtos.entries()].sort()) {
  out.push('/** ' + d.file.replace('src/main/java/com/templestamp/', '') + ' */');
  out.push('export interface ' + name + ' {');
  for (const f of d.fields) {
    const opt = NULLABLE_BOX.test(f.java);
    out.push('  ' + f.name + (opt ? '?' : '') + ': ' + f.ts + (opt ? ' | null' : '') + ';');
  }
  out.push('}');
  out.push('');
}

out.push('// ── 엔드포인트 91 ──');
out.push('');
out.push('export interface Endpoint {');
out.push('  method: string; path: string; request: string | null; response: string; status: number;');
out.push('}');
out.push('');
out.push('export const ENDPOINTS: readonly Endpoint[] = [');
for (const e of eps.sort((a, b) => a.path.localeCompare(b.path) || a.method.localeCompare(b.method))) {
  const unwrap = e.ret
    .replace(/^ResponseEntity<(.*)>$/, '$1')
    .replace(/^ApiResponse<(.*)>$/, '$1')
    .replace(/^(Void|void)$/, 'null') || 'null';
  const st = e.status === 'CREATED' ? 201 : e.status === 'NO_CONTENT' ? 204
    : e.status === 'ACCEPTED' ? 202 : 200;
  out.push("  { method: '" + e.method + "', path: '" + e.path + "', request: "
    + (e.body ? "'" + e.body + "'" : 'null') + ", response: '" + unwrap + "', status: " + st + ' },');
}
out.push('];');
out.push('');

const text = out.join('\n');
fs.writeFileSync('backend/docs/frontend/api-types.d.ts', text, 'utf8');

/* ── ④ 스스로 검사한다. 조용히 깨진 타입을 내보내지 않는다. ── */
const code = text.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/[^\n]*/g, '');
const balance = (code.match(/\{/g) || []).length - (code.match(/\}/g) || []).length;
const unknowns = text.split('\n').filter(l => /: unknown( \| null)?;/.test(l));
// 자기 검사 — interface 본문의 필드 줄만 본다.
//   처음에는 파일 전체를 훑어 주석과 ENDPOINTS 줄까지 "이상한 줄" 로 셌다(95건 오탐).
//   도구가 스스로 오탐을 내면 진짜 고장을 그 안에 덮는다.
const bodyLines = [];
{
  let inIface = false;
  for (const l of text.split(String.fromCharCode(10))) {
    if (/^export interface /.test(l)) { inIface = true; continue; }
    if (inIface && l === String.fromCharCode(125)) { inIface = false; continue; }
    if (inIface) bodyLines.push(l);
  }
}
const FIELD_LINE = /^\s+[A-Za-z_$][A-Za-z0-9_$]*\??:/;
const DOC_LINE = /^\s*\/\*/;
const weird = bodyLines.filter(l => l.trim() && !DOC_LINE.test(l) && !FIELD_LINE.test(l));

console.log('DTO ' + dtos.size + ' · enum ' + enums.size + ' · 엔드포인트 ' + eps.length
  + ' → backend/docs/frontend/api-types.d.ts');
console.log('중괄호 균형 ' + balance + ' (0이어야 한다)');
console.log('타입을 못 정한 필드 ' + unknowns.length + (unknowns.length ? ' → ' + unknowns.map(x => x.trim()).join(' · ') : ''));
console.log('모양이 이상한 줄 ' + weird.length + (weird.length ? ' → ' + weird.slice(0, 5).map(x => x.trim()).join(' · ') : ''));
if (balance !== 0 || weird.length) { console.log('❌ 생성물이 깨졌다 — 파서를 고칠 것'); process.exit(1); }
