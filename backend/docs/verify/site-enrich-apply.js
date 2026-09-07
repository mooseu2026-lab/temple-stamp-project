/* C1 STEP 4 — site-enrich.csv 의 확정·잠정 행을 <b>관리자 API 로만</b> 기존 DB 에 반영한다.
 *
 *   node backend/docs/verify/site-enrich-apply.js          # 무엇이 바뀌는지만 보여 준다
 *   node backend/docs/verify/site-enrich-apply.js --apply  # 실제로 보낸다
 *
 * DB 를 직접 UPDATE 하지 않는 이유는 챕터 4 이후 내내 같다 — 관리자 API 를 지나야
 * 검증·updated_at·상태 규칙이 함께 걸린다. SQL 로 넣으면 그 셋이 조용히 건너뛰어진다.
 *
 * ★ PUT /api/admin/sites/{id} 는 <b>전체 교체</b>다. 보내지 않은 칸은 지워진다.
 *   그래서 지금 값을 먼저 읽어 합친 뒤 보낸다(coord-backfill.js 와 같은 수법).
 *
 * ★ 비공식 등급은 <b>값을 비운다</b>. 출처가 없다는 뜻이고, 출처 없는 값이 화면에 나가면
 *   그것이 조사 결과인지 누군가의 짐작인지 나중에 아무도 구별하지 못한다.
 */
const fs = require('fs');
const { execFileSync } = require('child_process');

const BASE = process.env.BASE_URL || 'http://localhost:8080';
const APPLY = process.argv.includes('--apply');
const CSV = 'backend/docs/content/site-enrich.csv';
const DB = process.env.DB_NAME || 'temple_stamp_project';

function sql(q) {
  return execFileSync(process.env.MYSQL_BIN || 'mysql',
    ['-u' + (process.env.DB_USER || 'root'), '-p' + (process.env.DB_PASS || '1234'),
      '-N', '--default-character-set=utf8mb4', '-e', `USE ${DB}; ${q}`],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).replace(/\r/g, '').trim();
}
async function api(path, opts = {}) {
  const res = await fetch(BASE + path, opts);
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch { /* 본문이 JSON 이 아닐 수 있다 */ }
  return { status: res.status, json, text };
}
function readCsv(p) {
  const raw = fs.readFileSync(p, 'utf8').replace(/^﻿/, '');
  const rows = [];
  let cur = [''];
  let q = false;
  for (let i = 0; i < raw.length; i++) {
    const c = raw[i];
    if (q) {
      if (c === '"' && raw[i + 1] === '"') { cur[cur.length - 1] += '"'; i++; }
      else if (c === '"') q = false;
      else cur[cur.length - 1] += c;
    } else if (c === '"') q = true;
    else if (c === ',') cur.push('');
    else if (c === '\n') { rows.push(cur); cur = ['']; }
    else if (c !== '\r') cur[cur.length - 1] += c;
  }
  if (cur.length > 1 || cur[0] !== '') rows.push(cur);
  const head = rows[0].map((h) => h.trim());
  return rows.slice(1).filter((r) => (r[0] || '').trim())
    .map((r) => Object.fromEntries(head.map((h, i) => [h, (r[i] || '').trim()])));
}

(async () => {
  const login = await api('/api/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: 'admin@templestamp.local', password: 'Admin1234!' }),
  });
  if (login.status !== 200) { console.error('관리자 로그인 실패', login.status); process.exit(1); }
  const auth = { Authorization: 'Bearer ' + login.json.data.accessToken, 'Content-Type': 'application/json' };

  const rows = readCsv(CSV).filter((r) => r.siteId);
  const fail = [];
  let put = 0; let vp = 0; let badge = 0; let renamed = 0; let inactive = 0;

  for (const r of rows) {
    const id = Number(r.siteId);
    const official = r.grade === '확정' || r.grade === '잠정';
    const suffix = r.grade === '잠정' ? ' (2026-09 잠정)' : '';

    const cur = JSON.parse(sql(`SELECT JSON_OBJECT(
        'verifyRadius', verify_radius, 'qrLocationHint', qr_location_hint,
        'parkingInfo', parking_info, 'accessInfo', access_info,
        'mealAvailable', meal_available, 'name', name, 'status', status)
      FROM site WHERE site_id = ${id};`) || '{}');
    if (!cur.name) { fail.push({ id, name: r.name, why: 'site 행이 없다' }); continue; }

    const i18n = JSON.parse(sql(`SELECT IFNULL(JSON_ARRAYAGG(JSON_OBJECT(
        'locale', locale, 'name', name, 'description', description)), JSON_ARRAY())
      FROM site_i18n WHERE site_id = ${id};`))
      .filter((b) => ['ko', 'en', 'ja', 'zh'].includes(b.locale) && b.name);
    const ko = i18n.find((b) => b.locale === 'ko');
    if (!ko) { fail.push({ id, name: r.name, why: 'ko 언어 행이 없어 PUT 이 거절한다' }); continue; }

    // 동명이찰 표시 이름은 <b>이 도구가 바꾸지 않는다</b>(C1 STEP 2 — 목록만 만든다).
    // 이름을 바꾸면 원고 CSV 반입의 사찰 매칭·시더 정합 검사·newman N 폴더가 함께 깨진다.
    // 바꾸는 회차는 그 넷을 같이 고치는 회차여야 한다.
    if (ko.name !== r.name) { renamed++; }   // 셈만 한다

    // 사람이 읽는 두 칸에만 잠정 표시를 붙인다. 공양은 코드값이라 붙이면 CHECK 가 막는다.
    const withSuffix = (v) => (v && suffix && !v.endsWith(suffix) ? v + suffix : v);
    const body = {
      latitude: undefined, longitude: undefined,   // 좌표는 이 도구가 건드리지 않는다(coord-backfill 의 일)
      verifyRadius: cur.verifyRadius || 150,
      qrLocationHint: cur.qrLocationHint,
      parkingInfo: official ? (withSuffix(r.parkingInfo) || cur.parkingInfo) : null,
      accessInfo: official ? (withSuffix(r.accessInfo) || cur.accessInfo) : null,
      mealAvailable: official ? (r.mealAvailable || null) : null,
      i18n,
    };
    const cs = JSON.parse(sql(`SELECT JSON_OBJECT('lat', latitude, 'lng', longitude) FROM site WHERE site_id = ${id};`));
    body.latitude = Number(cs.lat);
    body.longitude = Number(cs.lng);

    if (!APPLY) {
      const diff = [];
      if (ko.name !== cur.name) diff.push(`이름 ${cur.name} → ${ko.name}`);
      if ((body.mealAvailable || '') !== (cur.mealAvailable || '')) diff.push(`공양 ${cur.mealAvailable || '(빈)'} → ${body.mealAvailable || '(빈)'}`);
      if (diff.length) console.log(`  ${id} ${r.name}  ${diff.join(' · ')}`);
      continue;
    }

    const res = await api('/api/admin/sites/' + id, { method: 'PUT', headers: auth, body: JSON.stringify(body) });
    if (res.status !== 200 && res.status !== 201) {
      fail.push({ id, name: r.name, why: `PUT ${res.status} ${(res.json && res.json.error && res.json.error.code) || res.text.slice(0, 80)}` });
      continue;
    }
    put++;

    // 뷰포인트 — "장소|시기|볼거리" 세 토막. 조사에는 장소만 있어 나머지는 비운다.
    for (let i = 1; i <= 3; i++) {
      const raw = r['viewpoint' + i];
      if (!raw || !official) continue;
      const [loc, time, see] = raw.split('|');
      if (!loc) continue;
      const v = await api(`/api/admin/sites/${id}/viewpoints`, {
        method: 'POST', headers: auth,
        body: JSON.stringify({ sortNo: i, locationDesc: loc.slice(0, 255), bestTime: time || null, whatToSee: see || null }),
      });
      if (v.status === 200 || v.status === 201) vp++;
      else fail.push({ id, name: r.name, why: `viewpoint ${v.status}` });
    }

    // 꽃절 뱃지는 등급을 가리지 않는다 — 이미 DB 에 들어가 있던 조사 사실이고,
    // 빼면 시더 정합 검사(SeedConsistencyTest)가 CSV 와 DB 사이에서 깨진다.
    if (r.badgeFlower === 'Y') {
      const m = (r.note || '').match(/꽃절=([^·]+)/);
      const b = await api(`/api/admin/sites/${id}/badges`, {
        method: 'POST', headers: auth,
        body: JSON.stringify({ badgeType: 'FLOWER', description: (m ? m[1].trim() : '꽃절').slice(0, 500) }),
      });
      if (b.status === 200 || b.status === 201) badge++;
      else fail.push({ id, name: r.name, why: `badge ${b.status}` });
    }

    // §0-2 — [DB 제외] 인 사찰은 INACTIVE 로 내린다.
    if ((r.note || '').includes('§0-2') && cur.status !== 'INACTIVE') {
      const st = await api(`/api/admin/sites/${id}/status`, {
        method: 'PATCH', headers: auth, body: JSON.stringify({ status: 'INACTIVE' }),
      });
      if (st.status === 200) inactive++;
      else fail.push({ id, name: r.name, why: `status ${st.status}` });
    }
  }

  console.log(`\n${APPLY ? '반영' : '미리보기'} — PUT ${put} · 뷰포인트 ${vp} · 뱃지 ${badge} · 이름변경 ${renamed} · INACTIVE ${inactive}`);
  if (fail.length) {
    console.log(`\n실패 ${fail.length}건`);
    console.log('| siteId | 사찰 | 사유 |');
    console.log('|---:|---|---|');
    fail.forEach((f) => console.log(`| ${f.id} | ${f.name} | ${f.why} |`));
  } else {
    console.log('실패 0건');
  }
  process.exit(fail.length ? 1 : 0);
})();
