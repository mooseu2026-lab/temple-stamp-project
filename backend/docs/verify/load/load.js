/* 스모크 부하 — 최종 점검 F §3-5 에서 만들고 챕터 9 보강에서 상시화했다.
   동시 20 사용자 x (가입 → 로그인 → 코스 조회 → 미션 3단계) 를 3분 동안 돈다.
   보는 것: 5xx 0 · 교착 0 · 평균 응답 · 커넥션 풀 대기 0 · 누수 0.

   자리(course_site)는 워커마다 다른 것을 쓴다 — 같은 자리에 20명이 몰리면
   측정하는 것이 "우리 코드" 가 아니라 "한 행의 잠금 대기" 가 된다.
   그렇다고 자리를 나누면 잠금 경합을 못 보므로, 절반은 같은 자리에 몰아 둔다. */
const BASE = 'http://localhost:8080';
const WORKERS = Number(process.env.LOAD_WORKERS || 20);
const DURATION_MS = Number(process.env.LOAD_MS || 180_000);

const stat = { by: new Map(), fail: [], startedAt: Date.now() };
function record(name, ms, code, body) {
  let s = stat.by.get(name);
  if (!s) { s = { n: 0, sum: 0, max: 0, codes: new Map() }; stat.by.set(name, s); }
  s.n++; s.sum += ms; if (ms > s.max) s.max = ms;
  s.codes.set(code, (s.codes.get(code) || 0) + 1);
  if (code >= 500 || code === 0) stat.fail.push({ name, code, body: String(body).slice(0, 200) });
}

async function call(name, method, path, body, token) {
  const t0 = Date.now();
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = 'Bearer ' + token;
  try {
    const res = await fetch(BASE + path, {
      method, headers, body: body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await res.text();
    record(name, Date.now() - t0, res.status, text);
    let json = null;
    try { json = JSON.parse(text); } catch (e) { /* 본문이 JSON 이 아니면 그대로 둔다 */ }
    return { status: res.status, json, text };
  } catch (e) {
    record(name, Date.now() - t0, 0, e.message);
    return { status: 0, json: null, text: e.message };
  }
}

async function setup() {
  const login = await call('setup:admin', 'POST', '/api/auth/login',
    { email: 'admin@templestamp.local', password: 'Admin1234!' });
  const admin = login.json && login.json.data && login.json.data.accessToken;
  if (!admin) throw new Error('관리자 로그인 실패: ' + login.text.slice(0, 200));

  const course = await call('setup:course', 'GET', '/api/courses/1');
  const sites = course.json.data.sites;
  const slots = [];
  for (const s of sites) {
    const siteId = s.candidates && s.candidates.length ? s.candidates[0].siteId : s.siteId;
    const qr = await call('setup:qr', 'POST', '/api/admin/sites/' + siteId + '/qr', {}, admin);
    const token = qr.json && qr.json.data && (qr.json.data.qrToken || qr.json.data.token);
    if (!token) throw new Error('QR 발급 실패 site=' + siteId + ': ' + qr.text.slice(0, 200));
    slots.push({ courseSiteId: s.courseSiteId, siteId, qrToken: token });
  }
  return slots;
}

async function worker(id, slots, deadline) {
  // 워커 0~9 는 자리 1 에 몰아 잠금 경합을 만들고, 10~19 는 자리를 나눠 처리량을 본다.
  const slot = id < 10 ? slots[0] : slots[id % slots.length];
  let loops = 0;
  while (Date.now() < deadline) {
    // 이름을 reg- 로 시작하게 짓는 이유는 cleanup.sql 이 이미 그 이름을 지우기 때문이다.
    // 부하가 만든 수천 계정이 개발 DB 에 그대로 쌓이면 다음 검증의 숫자가 흔들린다.
    const email = 'reg-load-' + id + '-' + loops + '-' + Date.now() + '@test.com';
    const pw = 'Test1234!';
    const up = await call('① 가입', 'POST', '/api/auth/signup',
      { email, password: pw, nickname: '부하' + id });
    if (up.status !== 201) { loops++; continue; }

    const li = await call('② 로그인', 'POST', '/api/auth/login', { email, password: pw });
    const token = li.json && li.json.data && li.json.data.accessToken;
    if (!token) { loops++; continue; }

    // 위치기반서비스 동의가 없으면 GPS 인증이 403 USER-4031 로 막힌다 —
    // 앱에서도 첫 스탬프 전에 받는 동의라 시나리오에 넣는다. 빼면 부하가 3단계에 닿지 못한다.
    await call('③ 위치 동의', 'POST', '/api/users/me/agreements',
      [{ agreementType: 'LOCATION_SERVICE', version: 1 }], token);
    await call('④ 코스 조회', 'GET', '/api/courses/1');
    await call('⑤ 순례 시작', 'POST', '/api/pilgrimages', { courseId: 1 }, token);

    const gps = await call('⑥ GPS', 'POST', '/api/stamps/' + slot.courseSiteId + '/gps-check',
      { siteId: slot.siteId, withinRadius: true, accuracyGrade: 'HIGH' }, token);
    const stampId = gps.json && gps.json.data && gps.json.data.stampId;
    if (!stampId) { loops++; continue; }

    await call('⑦ QR', 'POST', '/api/stamps/' + stampId + '/qr', { qrToken: slot.qrToken }, token);
    await call('⑧ 미션', 'POST', '/api/stamps/' + stampId + '/mission',
      { sentence: '오늘 받은 것들의 이름을 하나씩 불러본다.' }, token);
    loops++;
  }
  return loops;
}

(async () => {
  const slots = await setup();
  console.log('자리 ' + slots.length + '개 준비 — QR 발급 완료');
  console.log('시작 ' + new Date().toISOString() + ' · 동시 ' + WORKERS + ' · ' + (DURATION_MS / 1000) + '초');
  const deadline = Date.now() + DURATION_MS;
  const loops = await Promise.all(
    Array.from({ length: WORKERS }, (_, i) => worker(i, slots, deadline)));
  const elapsed = (Date.now() - stat.startedAt) / 1000;

  const rows = [];
  let total = 0, sum5xx = 0, allSum = 0;
  for (const [name, s] of [...stat.by.entries()].sort()) {
    if (name.startsWith('setup:')) continue;
    const codes = [...s.codes.entries()].sort((a, b) => a[0] - b[0])
      .map(([c, n]) => c + ':' + n).join(' ');
    rows.push('| ' + name + ' | ' + s.n + ' | ' + Math.round(s.sum / s.n) + ' ms | '
      + s.max + ' ms | ' + codes + ' |');
    total += s.n; allSum += s.sum;
    for (const [c, n] of s.codes) if (c >= 500 || c === 0) sum5xx += n;
  }
  console.log('');
  console.log('| 단계 | 요청 | 평균 | 최대 | 상태코드 |');
  console.log('|---|---:|---:|---:|---|');
  console.log(rows.join('\n'));
  console.log('');
  console.log('총 요청 ' + total + ' · 완주 시나리오 ' + loops.reduce((a, b) => a + b, 0)
    + ' · 걸린 시간 ' + Math.round(elapsed) + '초 · 초당 ' + (total / elapsed).toFixed(1) + ' 요청');
  console.log('전체 평균 응답 ' + Math.round(allSum / total) + ' ms');
  console.log('5xx·연결실패 ' + sum5xx + '건');
  if (stat.fail.length) {
    console.log('실패 원문(최대 10건):');
    stat.fail.slice(0, 10).forEach(f => console.log('  ' + f.name + ' ' + f.code + ' ' + f.body));
  }
})();
