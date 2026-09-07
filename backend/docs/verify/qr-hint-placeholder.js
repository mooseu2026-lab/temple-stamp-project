/* 11-A STEP 6 보조 — QR 위치 안내가 비어 있는 사찰에 <b>미정 표시</b>를 넣는다.
 *
 *   node backend/docs/verify/qr-hint-placeholder.js          # 미리보기
 *   node backend/docs/verify/qr-hint-placeholder.js --apply
 *
 * 왜 이런 것이 필요한가
 *   사찰을 ACTIVE 로 올리려면 qr_location_hint 가 있어야 한다(AdminSiteService.changeStatus → ADMIN-4092).
 *   시더는 그 칸을 <b>일부러</b> 비워 둔다 — "QR 을 어디에 붙일지" 는 절마다 사람이 가서 정하는 값이라,
 *   비어 있는 것이 곧 "아직 안 정했다" 는 표시였다.
 *
 * 왜 그럴듯한 문구를 지어내지 않는가
 *   "대웅전 앞 안내판 우측" 같은 말을 110곳에 넣으면 그것이 <b>현장 실사 결과인 척</b>한다.
 *   나중에 누구도 그것이 조사된 값인지 도구가 채운 값인지 구별하지 못한다.
 *   그래서 넣는 문구는 <b>미정임을 그대로 말하는 한 줄</b>이다. 화면에 나가도 거짓이 아니다.
 *
 * 실사가 끝나면 PUT /api/admin/sites/{id} 로 진짜 위치로 <b>교체</b>한다.
 * 이 문구가 남아 있는 사찰 수가 곧 "아직 실사하지 않은 절" 의 수다.
 */
const { execFileSync } = require('child_process');

const BASE = process.env.BASE_URL || 'http://localhost:8080';
const APPLY = process.argv.includes('--apply');
const DB = process.env.DB_NAME || 'temple_stamp_project';
const PLACEHOLDER = 'QR 위치 미정 — 종무소에 문의 (현장 실사 전, 2026-09)';

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
  try { json = JSON.parse(text); } catch { /* 평문일 수 있다 */ }
  return { status: res.status, json, text };
}

(async () => {
  const login = await api('/api/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: 'admin@templestamp.local', password: 'Admin1234!' }),
  });
  if (login.status !== 200) { console.error('관리자 로그인 실패', login.status); process.exit(1); }
  const auth = { Authorization: 'Bearer ' + login.json.data.accessToken, 'Content-Type': 'application/json' };

  const targets = sql(`SELECT site_id, name FROM site
     WHERE (qr_location_hint IS NULL OR qr_location_hint = '')
       AND status <> 'INACTIVE'
       AND latitude BETWEEN 33 AND 39 AND longitude BETWEEN 124 AND 132
     ORDER BY site_id;`).split('\n').filter(Boolean)
    .map((l) => { const [id, name] = l.split('\t'); return { id: Number(id), name }; });

  console.log(`QR 위치 안내가 빈 사찰 ${targets.length}곳${APPLY ? ' — 미정 표시를 넣는다' : ' (미리보기)'}`);
  console.log(`넣을 문구: "${PLACEHOLDER}"`);
  if (!APPLY) return;

  const fail = [];
  let ok = 0;
  for (const t of targets) {
    // PUT 은 전체 교체다 — 지금 값을 읽어 합친 뒤 보낸다.
    const cur = JSON.parse(sql(`SELECT JSON_OBJECT(
        'lat', latitude, 'lng', longitude, 'r', verify_radius,
        'p', parking_info, 'a', access_info, 'm', meal_available)
      FROM site WHERE site_id = ${t.id};`));
    const i18n = JSON.parse(sql(`SELECT IFNULL(JSON_ARRAYAGG(JSON_OBJECT(
        'locale', locale, 'name', name, 'description', description)), JSON_ARRAY())
      FROM site_i18n WHERE site_id = ${t.id};`))
      .filter((b) => ['ko', 'en', 'ja', 'zh'].includes(b.locale) && b.name);
    if (!i18n.some((b) => b.locale === 'ko')) { fail.push({ ...t, why: 'ko 언어 행 없음' }); continue; }

    const r = await api('/api/admin/sites/' + t.id, {
      method: 'PUT', headers: auth,
      body: JSON.stringify({
        latitude: Number(cur.lat), longitude: Number(cur.lng), verifyRadius: cur.r || 150,
        qrLocationHint: PLACEHOLDER, parkingInfo: cur.p, accessInfo: cur.a,
        mealAvailable: cur.m, i18n,
      }),
    });
    if (r.status === 200 || r.status === 201) ok++;
    else fail.push({ ...t, why: (r.json && r.json.error && r.json.error.code) || r.status });
  }
  console.log(`\n채움 ${ok} · 실패 ${fail.length}`);
  if (fail.length) {
    console.log('| siteId | 사찰 | 사유 |');
    console.log('|---:|---|---|');
    fail.forEach((f) => console.log(`| ${f.id} | ${f.name} | ${f.why} |`));
  }
})();
