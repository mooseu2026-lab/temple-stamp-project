/* ErrorCode.java → backend/docs/frontend/error-codes.json
 *
 *   node backend/docs/verify/error-codes.js
 *
 * 프론트가 코드마다 화면을 정해야 하는데, 목록을 손으로 옮겨 적으면 그 순간부터 낡는다.
 * 여기서 뽑아 두면 코드가 하나 늘 때 이 파일을 다시 돌리는 것으로 끝난다.
 *
 * ★ ErrorCode.java 는 <b>읽기만</b> 한다. 번호와 생성자 순서는 고정이라(절대 규칙),
 *   이 도구가 그것을 건드릴 이유가 없다.
 */
const fs = require('fs');
const path = require('path');

const SRC = 'src/main/java/com/templestamp/global/error/ErrorCode.java';
const OUT = 'backend/docs/frontend/error-codes.json';
const EXPECTED = 79;

const src = fs.readFileSync(SRC, 'utf8');

/* enum 상수 본문만 자른다. 뒤의 필드·생성자에도 괄호와 문자열이 있어서 통째로 훑으면 가짜가 섞인다.
   ★ 첫 '{' 나 첫 ';' 를 쓰면 안 된다 — 클래스 주석 안에 {도메인}_{상태} 같은 중괄호가 있고,
     첫 세미콜론은 package 선언의 것이다. 실제로 그렇게 잘라서 0개가 나왔다. */
const HEAD = 'public enum ErrorCode {';
const start = src.indexOf(HEAD);
if (start < 0) { console.log('enum 선언을 찾지 못했다: ' + SRC); process.exit(1); }
const end = src.indexOf('private final int status;', start);
if (end < 0) { console.log('상수 목록의 끝(필드 선언)을 찾지 못했다'); process.exit(1); }
const body = src.slice(start + HEAD.length, end);

/* NAME(status, "CODE", "메시지") — 메시지 안의 이스케이프된 따옴표까지 받는다. */
const RE = /^\s*([A-Z][A-Z0-9_]*)\s*\(\s*(\d{3})\s*,\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*\)/gm;

const rows = [];
let m;
while ((m = RE.exec(body)) !== null) {
  rows.push({
    name: m[1],
    status: Number(m[2]),
    code: m[3],
    message: m[4].replace(/\\"/g, '"').replace(/\\n/g, '\n'),
  });
}

/* ── 자가 검증 — 틀린 자로 잰 숫자는 숫자가 아니다(정리.md §6-11) ── */
let bad = 0;
const seen = new Set();
for (const r of rows) {
  if (seen.has(r.code)) { console.log('중복 코드: ' + r.code); bad++; }
  seen.add(r.code);
  if (!/^[A-Z]+-\d{4}$/.test(r.code)) { console.log('코드 모양이 이상하다: ' + r.code); bad++; }
  if (r.status < 400 || r.status > 599) { console.log('상태코드가 4xx·5xx 가 아니다: ' + r.code + ' ' + r.status); bad++; }
  // 코드의 끝 세 자리가 상태코드와 맞는지 — REWARD-4092 는 409 다.
  const head = Number(r.code.slice(-4, -1));
  if (head !== r.status) { console.log('코드와 상태가 어긋난다: ' + r.code + ' vs ' + r.status); bad++; }
}

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, JSON.stringify(rows, null, 2) + '\n', 'utf8');

const byStatus = rows.reduce((acc, r) => { acc[r.status] = (acc[r.status] || 0) + 1; return acc; }, {});
console.log('에러코드 ' + rows.length + '개 → ' + OUT);
console.log('상태코드별: ' + Object.entries(byStatus).sort().map(([k, v]) => k + ':' + v).join(' · '));
console.log('어긋남 ' + bad + ' (0이어야 한다)');
console.log(rows.length === EXPECTED
  ? '개수 단언 ✅ ' + EXPECTED
  : '개수 단언 ❌ 기대 ' + EXPECTED + ' · 실제 ' + rows.length + ' — 코드가 늘었으면 이 파일의 EXPECTED 를 함께 고친다');

process.exit(bad === 0 && rows.length === EXPECTED ? 0 : 1);
