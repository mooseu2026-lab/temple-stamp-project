/*
 * backend/docs/verify/mapper-check.js — 매퍼 정합 전수 검사 (전체 점검 C · R6)
 *
 * 셋을 본다. 전부 컴파일러가 잡아 주지 않고, 호출하거나 응답을 볼 때에야 드러나는 것들이다.
 *   ① 매퍼 인터페이스 메서드 ↔ XML 문장 id (양방향) — 없으면 호출 시점 500, 남으면 죽은 SQL
 *   ② XML 의 #{param}       ↔ @Param 이름        — 없으면 호출 시점 500
 *   ③ resultType 클래스 필드 ↔ SELECT 별칭       — 어긋나면 조용히 null (가장 나쁘다)
 */
const fs = require('fs'), path = require('path');
function walk(d, f, out = []) {
  for (const e of fs.readdirSync(d, { withFileTypes: true })) {
    const p = path.join(d, e.name);
    if (e.isDirectory()) walk(p, f, out); else if (f(e.name)) out.push(p);
  }
  return out;
}
const JAVA = 'src/main/java', MAP = 'src/main/resources/mapper';

const xmls = {};
for (const x of walk(MAP, n => n.endsWith('.xml'))) {
  const s = fs.readFileSync(x, 'utf8');
  xmls[(s.match(/namespace="([^"]+)"/) || [])[1]] = { file: x, src: s,
    ids: new Set([...s.matchAll(/<(?:select|insert|update|delete)\s[^>]*id="([^"]+)"/g)].map(m => m[1])) };
}
const classes = {};
for (const j of walk(JAVA, n => n.endsWith('.java'))) {
  const s = fs.readFileSync(j, 'utf8');
  classes[(s.match(/package ([\w.]+);/) || [])[1] + '.' + path.basename(j, '.java')] = s;
}
function fieldsOf(fq) {
  const s = classes[fq]; if (!s) return null;
  const b = s.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');
  const r = b.match(/public record \w+\s*\(([\s\S]*?)\)\s*\{/);
  if (r) return [...r[1].matchAll(/(?:@\w+(?:\([^)]*\))?\s*)*[\w.<>,\[\]]+\s+(\w+)\s*(?:,|$)/g)].map(m => m[1]);
  return [...b.matchAll(/^\s*private\s+(?:final\s+)?[\w.<>,\[\]]+\s+(\w+)\s*;/gm)].map(m => m[1]);
}
const camel = s => s.replace(/_([a-z])/g, (_, ch) => ch.toUpperCase());

let bad1 = 0, bad2 = 0, bad3 = 0, selects = 0;

/* ① 인터페이스 ↔ XML */
for (const j of walk(JAVA, n => n.endsWith('Mapper.java'))) {
  const s = fs.readFileSync(j, 'utf8');
  if (!/@Mapper/.test(s)) continue;
  const fq = (s.match(/package ([\w.]+);/) || [])[1] + '.' + path.basename(j, '.java');
  const x = xmls[fq];
  const body = s.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');
  const methods = [...body.matchAll(/^\s{4}(?:[\w.<>,\[\]\s]+?)\s+(\w+)\s*\(/gm)].map(m => m[1]);
  const annotated = [...s.matchAll(/@(?:Select|Insert|Update|Delete)\b[\s\S]{0,400}?\s(\w+)\s*\(/g)].map(m => m[1]);
  if (!x) { console.log('  ❌ XML 없음: ' + fq); bad1++; continue; }
  for (const m of methods) if (!annotated.includes(m) && !x.ids.has(m)) { console.log('  ❌ ' + fq.split('.').pop() + '.' + m + '() → XML 문장 없음'); bad1++; }

  // 반대 방향도 본다. XML 에만 남은 문장은 컴파일도 되고 테스트도 통과하지만,
  // "이 SQL 이 아직 쓰인다" 는 착각을 남긴다. 한쪽만 보면 지운 흔적이 조용히 쌓인다.
  for (const id of x.ids) {
    if (!methods.includes(id)) { console.log('  ❌ ' + x.file + ' 의 <' + id + '> → 인터페이스에 메서드 없음'); bad1++; }
  }
}

/* ② #{param} ↔ @Param */
const iface = {};
for (const j of walk(JAVA, n => n.endsWith('Mapper.java'))) {
  const s = fs.readFileSync(j, 'utf8');
  const fq = (s.match(/package ([\w.]+);/) || [])[1] + '.' + path.basename(j, '.java');
  const body = s.replace(/\/\*[\s\S]*?\*\//g, '');
  const map = {};
  for (const m of body.matchAll(/\s(\w+)\s*\(([^;]*?)\);/g)) {
    const params = [...m[2].matchAll(/@Param\("(\w+)"\)/g)].map(p => p[1]);
    const bean = /\b[A-Z]\w+\s+\w+\s*[,)]/.test(m[2]) && !params.length;
    map[m[1]] = { params, bean };
  }
  iface[fq] = map;
}
for (const [ns, x] of Object.entries(xmls)) {
  const m = iface[ns]; if (!m) continue;
  for (const st of x.src.matchAll(/<(select|insert|update|delete)\s[^>]*id="([^"]+)"[^>]*>([\s\S]*?)<\/\1>/g)) {
    const sig = m[st[2]]; if (!sig || sig.bean || !sig.params.length) continue;
    const foreachItems = [...st[3].matchAll(/item="(\w+)"/g)].map(f => f[1]);
    for (const u of new Set([...st[3].matchAll(/[#$]\{(\w+)/g)].map(a => a[1]))) {
      if (!sig.params.includes(u) && !foreachItems.includes(u)) {
        console.log('  ❌ ' + ns.split('.').pop() + '.' + st[2] + ' → #{' + u + '} 가 @Param 에 없다 [' + sig.params.join(',') + ']');
        bad2++;
      }
    }
  }
}

/* ③ resultType 필드 ↔ SELECT 별칭 (미채움) */
for (const [, x] of Object.entries(xmls)) {
  const frags = {};
  for (const f of x.src.matchAll(/<sql\s+id="(\w+)">([\s\S]*?)<\/sql>/g)) frags[f[1]] = f[2];
  for (const st of x.src.matchAll(/<select\s[^>]*id="([^"]+)"[^>]*resultType="(com\.templestamp\.[\w.]+)"[^>]*>([\s\S]*?)<\/select>/g)) {
    const fields = fieldsOf(st[2]); if (!fields) continue;
    selects++;
    let sql = st[3].replace(/<include refid="(\w+)"\s*\/>/g, (_, r) => frags[r] || '').replace(/<[^>]+>/g, ' ');
    const sel = sql.match(/SELECT([\s\S]*?)\bFROM\b/i);
    if (!sel || /\*/.test(sel[1])) continue;
    const produced = new Set();
    const cols = []; let depth = 0, cur = '';
    for (const ch of sel[1]) { if (ch === '(') depth++; else if (ch === ')') depth--; if (ch === ',' && depth === 0) { cols.push(cur); cur = ''; } else cur += ch; }
    cols.push(cur);
    for (let col of cols) {
      col = col.trim().replace(/\s+/g, ' '); if (!col) continue;
      const as = col.match(/\bAS\s+([`"]?)(\w+)\1\s*$/i);
      const name = as ? as[2] : (col.match(/([\w.]+)\s*$/) || [])[1];
      if (name) produced.add(camel(name.split('.').pop()));
    }
    const miss = fields.filter(f => !produced.has(f));
    if (miss.length) { console.log('  ❌ ' + path.basename(x.file) + '::' + st[1] + ' → ' + st[2].split('.').pop() + ' 미채움: ' + miss.join(', ')); bad3++; }
  }
}

console.log('  ① 매퍼↔XML 불일치 ' + bad1 + '건');
console.log('  ② #{}↔@Param 불일치 ' + bad2 + '건');
console.log('  ③ 별칭 미채움 ' + bad3 + '건 (검사한 select ' + selects + '개)');
process.exitCode = (bad1 + bad2 + bad3) ? 1 : 0;
