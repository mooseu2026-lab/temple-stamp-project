/*
 * backend/docs/verify/coord-backfill.js — 좌표 (0,0) 사찰 좌표 보강 (콘텐츠 트랙 1)
 *
 *   node backend/docs/verify/coord-backfill.js          # 조회만 (아무것도 바꾸지 않는다)
 *   node backend/docs/verify/coord-backfill.js --apply  # 찾은 좌표를 관리자 PUT 으로 반영
 *
 * 규칙
 *  - 검색 순서는 ① 도로명 주소 ② 이름 + 시군구 + 읍면동 ③ 이름 + 구분 ④ 이름만.
 *  - **카테고리에 "사찰" 이 있고, 지역까지 맞아야** 채운다.
 *    카테고리만 보면 안 된다 — 동명이찰이 많아서 "화암사" 로 검색하면 고성(강원) 대신 완주(전북)가,
 *    "백련사" 로 검색하면 가평(경기) 대신 부산이 잡힌다. 실제로 첫 시도에서 17곳 중 6곳 이상이
 *    다른 절이었다. 그래서 조사 자료의 주소와 **시·군·구가 일치하는지**까지 본다.
 *  - **본사 우선.** 같은 절 이름으로 암자·전각·주차장이 함께 잡힌다("다솔사 봉일암", "흥국사 주차장").
 *    이름 완전일치 > 시군구 일치 > 카테고리 순으로 골라 부속 건물이 대표 좌표가 되지 않게 한다.
 *    종단 접두어(대한불교조계종…)는 떼고 비교한다 — 카카오에 본사가 그 이름으로 올라와 있다.
 *  - 하나라도 어긋나면 채우지 않고 후보 3개만 적어 사람이 판단하게 둔다.
 *    틀린 좌표는 없는 좌표보다 나쁘다 — 반경 150m 안에서만 도장이 나가기 때문이다.
 *  - 저장은 관리자 PUT 을 통한다. DB 를 직접 고치면 updated_at·검증이 건너뛰어진다.
 */
const fs = require('fs');
const { execFileSync } = require('child_process');

const BASE = process.env.BASE_URL || 'http://localhost:8080';
const APPLY = process.argv.includes('--apply');
const DB = 'temple_stamp_project';
const MYSQL = process.env.MYSQL_BIN || 'mysql';
const DB_USER = process.env.DB_USER || 'root';
const DB_PASS = process.env.DB_PASS || '1234';
const CSV = 'backend/docs/content/sites-master.csv';

function sql(q) {
  // ★ 윈도우의 mysql 은 줄 끝에 CR(\r)을 붙인다. 그대로 두면 마지막 줄만 빼고 전부 보이지 않는 문자가 붙어
  //   CSV 대조가 조용히 빗나간다(처음 실행에서 18곳 중 17곳이 그렇게 빗나갔다).
  return execFileSync(MYSQL, ['-u' + DB_USER, '-p' + DB_PASS, '-N', '--default-character-set=utf8mb4', '-e', `USE ${DB}; ${q}`],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).replace(/\r/g, '').trim();
}
async function api(path, opts = {}) {
  const res = await fetch(BASE + path, opts);
  const text = await res.text();
  let json = null; try { json = JSON.parse(text); } catch (_) {}
  return { status: res.status, json, text };
}

/** CSV 를 따옴표까지 지켜 읽는다. road_address 에 쉼표가 들어 있는 행이 있다. */
function readCsv(path) {
  const raw = fs.readFileSync(path, 'utf8').replace(/^﻿/, '');
  const rows = []; let cur = ['']; let q = false;
  for (let i = 0; i < raw.length; i++) {
    const ch = raw[i];
    if (q) { if (ch === '"' && raw[i + 1] === '"') { cur[cur.length - 1] += '"'; i++; } else if (ch === '"') q = false; else cur[cur.length - 1] += ch; }
    else if (ch === '"') q = true;
    else if (ch === ',') cur.push('');
    else if (ch === '\n') { rows.push(cur); cur = ['']; }
    else if (ch !== '\r') cur[cur.length - 1] += ch;
  }
  if (cur.length > 1 || cur[0]) rows.push(cur);
  const head = rows.shift().map(h => h.trim());
  return rows.filter(r => r.length > 1).map(r => Object.fromEntries(head.map((h, i) => [h, (r[i] || '').trim()])));
}
const clean = v => (!v || v === '?' || v.startsWith('미확인')) ? null : v;

/**
 * 주소에서 행정구역 토큰을 뽑는다 — "전라남도 보성군 문덕면 …" → ['전라남도','보성군','문덕면'].
 * 시·도 이름은 표기가 갈려(전라남도/전남) 시·군·구만 비교한다.
 */
function districts(addr) {
  if (!addr) return [];
  return (addr.match(/[가-힣]+(?:시|군|구)/g) || []).filter(t => !/^(광역시|특별시)$/.test(t));
}
/** 카카오 장소명 앞에 붙는 종단 이름. 본사가 "대한불교조계종다솔사" 로 올라와 있는 경우가 있다. */
const ORDER_PREFIX = /^(대한불교조계종|한국불교태고종|대한불교태고종|대한불교천태종|대한불교진각종|대한불교관음종|대한불교법화종|사단법인)\s*/;
/** 절의 부속 시설. 이런 이름이 대표 좌표가 되면 안 된다. */
const SUB_FACILITY = /(암|전|각|문|탑|비|대방|괘불|홍교|주차장|템플스테이|휴게소|식당|광장|매표소|공원|박물관|성보관|매점|안내소)$/;

/**
 * 장소명이 그 절의 <b>본사</b>를 가리키는 정도. 낮을수록 좋다.
 *   0 종단 접두어를 뗀 이름이 정확히 같다        (다솔사 ← 대한불교조계종다솔사)
 *   1 이름으로 시작하고 뒤가 부속 시설이 아니다  (학림사 오등선원)
 *   2 이름으로 시작하지만 뒤가 부속 시설이다     (다솔사 봉일암 · 흥국사 주차장)
 *   3 이름을 품고만 있다                          (금강산 화암사)
 *   4 관계 없음
 */
function nameRank(placeName, siteName) {
  const name = String(placeName || '').replace(ORDER_PREFIX, '').trim();
  const site = String(siteName || '').trim();
  if (!site) return 4;
  if (name === site) return 0;
  if (name.startsWith(site)) {
    const rest = name.slice(site.length).trim();
    return !rest ? 0 : (SUB_FACILITY.test(rest) ? 2 : 1);
  }
  return name.includes(site) ? 3 : 4;
}

/** 조사 자료의 주소·구분과 카카오 결과의 주소가 같은 고장을 가리키는가. */
function sameArea(expectAddr, disamb, place) {
  const got = districts(place.roadAddressName || place.addressName || '');
  if (!got.length) return false;
  const want = new Set([...districts(expectAddr), ...districts(disamb)]);
  // 구분(disambiguation)은 "고성 설악산 신선봉" 처럼 시·군 이름만 있는 경우가 많다 — 접미사 없이도 본다.
  for (const w of String(disamb || '').split(/[\s·]+/).filter(Boolean)) {
    got.forEach(g => { if (g.startsWith(w)) want.add(g); });
  }
  if (!want.size) return false;
  return got.some(g => want.has(g));
}

(async () => {
  const login = await api('/api/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: 'admin@templestamp.local', password: 'Admin1234!' }),
  });
  if (login.status !== 200) { console.error('관리자 로그인 실패', login.status, login.text.slice(0, 200)); process.exit(1); }
  const token = login.json.data.accessToken;
  const auth = { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' };

  const csv = readCsv(CSV);
  const byKey = new Map();
  for (const r of csv) byKey.set(`${r.region_code}:${r.site_name_ko}:${r.disambiguation || ''}`, r);

  // --recheck 를 주면 이미 채운 곳까지 다시 본다(규칙이 바뀌었을 때 되짚기 위해).
  const where = process.argv.includes('--recheck') ? '1 = 1' : 'latitude = 0';
  const zero = sql(`SELECT site_id, name, seed_key FROM site WHERE seed_key IS NOT NULL AND ${where} ORDER BY seed_key;`)
    .split('\n').filter(Boolean).map(l => { const [id, name, key] = l.split('\t'); return { id: Number(id), name, key }; });

  console.log(`좌표 0 인 사찰 ${zero.length}곳${APPLY ? ' — --apply (반영)' : ' — 조회만'}\n`);
  const filled = [], unresolved = [];

  for (const s of zero) {
    const row = byKey.get(s.key) || {};
    const road = clean(row.road_address);
    const disamb = clean(row.disambiguation) || '';
    // 조사 주소에서 시·군·구와 읍·면·동을 뽑아 "흥국사 고양 덕양구" 같은 검색어를 만든다.
    //   주소 전체로는 0건인데 이 조합으로는 잡히는 절이 있다(흥국사·학림사가 그랬다).
    const adminTokens = [
      ...(String(road || '').match(/[가-힣]+(?:시|군|구)/g) || []),
      ...(String(road || '').match(/[가-힣]+(?:읍|면|동)/g) || []),
    ].filter((v, i, a) => a.indexOf(v) === i);

    const queries = [];
    if (road) queries.push({ label: '주소', q: road });
    if (adminTokens.length) queries.push({ label: '이름+행정구역', q: (s.name + ' ' + adminTokens.join(' ')).trim() });
    queries.push({ label: '이름+구분', q: (s.name + ' ' + disamb).trim() });
    // 3순위 — 이름만. 동명이찰이 많아 위험하지만 지역 검증(sameArea)이 뒤에서 막는다.
    //   '서광사 서산 부춘산' 처럼 구분까지 넣으면 카카오가 0건을 주는 경우가 있어 마지막에 한 번 더 본다.
    if (disamb) queries.push({ label: '이름만', q: s.name });

    let hit = null, tried = [];
    for (const { label, q } of queries) {
      const r = await api('/api/admin/kakao/places?query=' + encodeURIComponent(q) + '&page=1&size=5', { headers: auth });
      await new Promise(r2 => setTimeout(r2, 1000));   // 카카오 호출 간격
      if (r.status !== 200) { tried.push(`${label}="${q}" → HTTP ${r.status}`); continue; }
      const places = r.json.data.places || [];
      tried.push({ label, q, places: places.slice(0, 3).map(p => `${p.placeName} [${p.categoryName || '-'}] ${p.roadAddressName || p.addressName || ''}${sameArea(road, disamb, p) ? '' : '  ← 지역 불일치'}`) });
      // 지역 일치는 필수. 그 안에서 이름 순위(본사 우선) → 카테고리 순으로 고른다.
      const ranked = places
        .filter(p => p.latitude && sameArea(road, disamb, p))
        .map(p => ({ p, nr: nameRank(p.placeName, s.name), cat: (p.categoryName || '').includes('사찰') ? 0 : 1 }))
        // 이름이 관계없거나(4), 부속 시설인데 카테고리도 사찰이 아니면 버린다.
        .filter(x => x.nr <= 3 && (x.nr <= 1 || x.cat === 0))
        .sort((a, b) => a.nr - b.nr || a.cat - b.cat);
      if (ranked.length) {
        const best = ranked[0].p;
        hit = { ...best, via: label, q, nameRank: ranked[0].nr,
                matchedAddr: best.roadAddressName || best.addressName };
        break;
      }
    }

    if (!hit) {
      unresolved.push({ ...s, road, disamb, tried });
      console.log(`  ✗ ${s.name} (${s.key})`);
      console.log(`      기대: 주소=${road || "(미확인)"} · 구분=${disamb || "(없음)"}`);
      for (const t of tried) if (t.places) t.places.forEach(p => console.log(`      · [${t.label}] ${p}`));
      continue;
    }

    console.log(`  ✓ ${s.name} ← ${hit.via} "${hit.q}"  [${hit.placeName}] rank=${hit.nameRank}  ${hit.latitude}, ${hit.longitude}  ${hit.matchedAddr}`);
    filled.push({ ...s, expectAddr: road, disamb, lat: hit.latitude, lng: hit.longitude,
                  via: hit.via, source: hit.placeName, nameRank: hit.nameRank,
                  matchedAddr: hit.matchedAddr, category: hit.categoryName });

    if (!APPLY) continue;

    // 기존 값을 그대로 다시 보낸다 — PUT 은 전체 교체라 빠뜨리면 지워진다.
    // 탭·줄바꿈으로 이어 붙이면 소개문 안의 줄바꿈에서 깨진다. JSON 으로 통째로 받는다.
    const cur = JSON.parse(sql(`SELECT JSON_OBJECT(
        'verifyRadius', verify_radius, 'qrLocationHint', qr_location_hint,
        'parkingInfo', parking_info, 'accessInfo', access_info, 'mealAvailable', meal_available)
      FROM site WHERE site_id = ${s.id};`));
    const i18n = JSON.parse(sql(`SELECT IFNULL(JSON_ARRAYAGG(JSON_OBJECT(
        'locale', locale, 'name', name, 'description', description)), JSON_ARRAY())
      FROM site_i18n WHERE site_id = ${s.id};`))
      // 저장된 locale 이 ko·en·ja·zh 가 아니면 PUT 이 거절한다. 그런 행은 손대지 않고 그대로 둔다.
      .filter(b => ['ko', 'en', 'ja', 'zh'].includes(b.locale) && b.name);

    if (!i18n.some(b => b.locale === 'ko')) {
      console.log('      ⚠ ko 언어 행이 없어 건너뛴다 (PUT 이 거절한다)');
      filled[filled.length - 1].applied = false;
      continue;
    }

    const put = await api('/api/admin/sites/' + s.id, {
      method: 'PUT', headers: auth,
      body: JSON.stringify({
        latitude: Number(hit.latitude), longitude: Number(hit.longitude),
        verifyRadius: cur.verifyRadius || 150,
        qrLocationHint: cur.qrLocationHint, parkingInfo: cur.parkingInfo,
        accessInfo: cur.accessInfo, mealAvailable: cur.mealAvailable, i18n,
      }),
    });
    if (put.status !== 200 && put.status !== 201) {
      console.log(`      ⚠ PUT 실패 ${put.status} ${put.text.slice(0, 160)}`);
      filled[filled.length - 1].applied = false;
    } else {
      filled[filled.length - 1].applied = true;
    }
  }

  console.log(`\n확보 ${filled.length}곳 / 미해결 ${unresolved.length}곳`);
  fs.writeFileSync('backend/docs/verify/coord-backfill-result.json',
    JSON.stringify({ ranAt: new Date().toISOString(), apply: APPLY, filled, unresolved }, null, 1), 'utf8');
  console.log('결과: backend/docs/verify/coord-backfill-result.json');
})();
