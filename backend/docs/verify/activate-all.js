/* 11-A STEP 6 — 사찰·코스를 <b>관리자 API 로만</b> ACTIVE 로 올린다.
 *
 *   node backend/docs/verify/activate-all.js          # 무엇이 바뀌는지만
 *   node backend/docs/verify/activate-all.js --apply
 *
 * 순서가 정해져 있다. 코스는 자리 5곳이 <b>전부 ACTIVE</b> 여야 열린다
 * (AdminCourseService.changeStatus → ADMIN-4092). 그래서 사찰을 먼저 올린다.
 *
 * 빼는 것 둘
 *   · 좌표가 없거나 한국 밖인 사찰 — 반경 150m 안에서만 도장이 나가므로 좌표 없이 열면 아무도 못 찍는다
 *   · 의성 고운사 — 예성 정정 §0-2. INACTIVE 고정이고 ACTIVE 확장에서 제외한다
 */
const { execFileSync } = require('child_process');

const BASE = process.env.BASE_URL || 'http://localhost:8080';
const APPLY = process.argv.includes('--apply');
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
  try { json = JSON.parse(text); } catch { /* 평문일 수 있다 */ }
  return { status: res.status, json, text };
}
const code = (r) => (r.json && r.json.error && r.json.error.code) || String(r.status);

(async () => {
  const login = await api('/api/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: 'admin@templestamp.local', password: 'Admin1234!' }),
  });
  if (login.status !== 200) { console.error('관리자 로그인 실패', login.status); process.exit(1); }
  const auth = { Authorization: 'Bearer ' + login.json.data.accessToken, 'Content-Type': 'application/json' };

  /* ── ① 사찰 ── */
  const sites = sql(`SELECT site_id, name FROM site
     WHERE status <> 'ACTIVE'
       AND latitude BETWEEN 33 AND 39 AND longitude BETWEEN 124 AND 132
       AND status <> 'INACTIVE'
     ORDER BY site_id;`).split('\n').filter(Boolean)
    .map((l) => { const [id, name] = l.split('\t'); return { id: Number(id), name }; });

  const skipped = sql(`SELECT site_id, name, status,
      CASE WHEN status = 'INACTIVE' THEN '§0-2 DB 제외'
           ELSE '좌표 없음 또는 한국 밖' END
    FROM site
   WHERE status <> 'ACTIVE'
     AND (status = 'INACTIVE'
          OR latitude IS NULL OR longitude IS NULL
          OR latitude NOT BETWEEN 33 AND 39 OR longitude NOT BETWEEN 124 AND 132)
   ORDER BY site_id;`).split('\n').filter(Boolean)
    .map((l) => { const [id, name, status, why] = l.split('\t'); return { id: Number(id), name, status, why }; });

  console.log(`사찰 — 올릴 것 ${sites.length} · 뺄 것 ${skipped.length}`);
  if (skipped.length) {
    console.log('| siteId | 사찰 | 지금 상태 | 뺀 이유 |');
    console.log('|---:|---|---|---|');
    skipped.forEach((s) => console.log(`| ${s.id} | ${s.name} | ${s.status} | ${s.why} |`));
  }

  const siteFail = [];
  let siteOk = 0;
  if (APPLY) {
    for (const s of sites) {
      const r = await api(`/api/admin/sites/${s.id}/status`, {
        method: 'PATCH', headers: auth, body: JSON.stringify({ status: 'ACTIVE' }),
      });
      if (r.status === 200) siteOk++;
      else siteFail.push({ ...s, why: code(r), msg: (r.json && r.json.error && r.json.error.message) || '' });
    }
    console.log(`\n사찰 ACTIVE — 성공 ${siteOk} · 실패 ${siteFail.length}`);
    if (siteFail.length) {
      console.log('| siteId | 사찰 | 코드 | 메시지 |');
      console.log('|---:|---|---|---|');
      siteFail.forEach((f) => console.log(`| ${f.id} | ${f.name} | ${f.why} | ${f.msg} |`));
    }
  }

  /* ── ② 코스 ── */
  const courses = sql(`SELECT course_id, name, status FROM course ORDER BY course_id;`)
    .split('\n').filter(Boolean)
    .map((l) => { const [id, name, status] = l.split('\t'); return { id: Number(id), name, status }; });

  console.log(`\n코스 ${courses.length}개`);
  if (!APPLY) { console.log('(미리보기 — --apply 로 실제 보낸다)'); return; }

  const courseFail = [];
  let courseOk = 0;
  for (const c of courses) {
    if (c.status === 'ACTIVE') { courseOk++; continue; }
    const r = await api(`/api/admin/courses/${c.id}/status`, {
      method: 'PATCH', headers: auth, body: JSON.stringify({ status: 'ACTIVE' }),
    });
    if (r.status === 200) courseOk++;
    else courseFail.push({ ...c, code: code(r), msg: (r.json && r.json.error && r.json.error.message) || '' });
  }
  console.log(`코스 ACTIVE — ${courseOk}/${courses.length}`);
  if (courseFail.length) {
    console.log('| courseId | 코스 | 코드 | 사유 |');
    console.log('|---:|---|---|---|');
    courseFail.forEach((f) => console.log(`| ${f.id} | ${f.name} | ${f.code} | ${f.msg} |`));
  }

  /* ── ③ 공개 응답 실측 — 코스마다 자리 5·좌표 5 ── */
  const pub = await api('/api/courses');
  const items = (pub.json && pub.json.data && pub.json.data.items) || [];
  console.log(`\n공개 GET /api/courses — ACTIVE ${items.length}/${courses.length}`);

  const bad = [];
  for (const c of items) {
    const d = await api('/api/courses/' + c.courseId);
    const sitesOf = (d.json && d.json.data && d.json.data.sites) || [];
    const withCoord = sitesOf.filter((s) => s.latitude != null && s.longitude != null).length;
    const line = `${c.courseId} ${c.name}: 자리 ${sitesOf.length} · 좌표 ${withCoord}`;
    if (sitesOf.length !== 5) bad.push(line + '  ← 자리가 5가 아니다');
    else console.log('  ' + line);
  }
  if (bad.length) { console.log('\n어긋남'); bad.forEach((b) => console.log('  ' + b)); }
})();
