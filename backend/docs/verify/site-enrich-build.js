/* C1 STEP 1 — 사찰 조사 자료에 등급을 매겨 backend/docs/content/site-enrich.csv 를 만든다.
 *
 *   node backend/docs/verify/site-enrich-build.js
 *
 * 입력
 *   src/main/resources/seed/sites-master.csv   조사 원본(시더가 읽는 그 파일)
 *   backend/docs/content/사찰조사_2026-09-07.md §0 예성 정정 — 아래 CORRECTIONS 에 옮겨 적었다
 *   DB site 표                                  siteId 매칭(이름 + 시군구)
 *
 * ★ 이 도구는 <b>아무것도 지어내지 않는다.</b> 원본에 없는 칸은 빈칸으로 남긴다.
 *   coordPoint·description·qrLocationHint 가 통째로 비는 이유가 그것이다 — 조사 원본에 그 열이 없다.
 *   빈칸을 "미상" 이나 그럴듯한 문장으로 채우면, 나중에 그것이 조사 결과인지 추측인지 아무도 모른다.
 *
 * ★ 등급은 <b>출처의 종류와 개수</b>로만 매긴다(정본 C1 STEP 1).
 *   공식 2곳 이상 = 확정 · 1곳 = 잠정 · 그 밖 = 비공식.
 *   위키·나무위키는 공식이 아니다 — 누구나 고칠 수 있는 곳이라 "두 곳" 으로 세면 숫자가 거짓이 된다.
 */
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const SRC = 'src/main/resources/seed/sites-master.csv';
const OUT = 'backend/docs/content/site-enrich.csv';

/* ── 예성 정정 (사찰조사_2026-09-07.md §0). 원문보다 우선한다. ── */
const CORRECTIONS = {
  // §0-1 — 서산 서광사 도로명주소 확정(예성 제공)
  'CHUNGNAM_SEJONG:서광사:서산 부춘산': { roadAddress: '충남 서산시 부춘산1로 44 (읍내동)', addressGrade: '확정' },
  // §0-2 — 의성 고운사는 DB 대상에서 제외. 행은 남기되 제외 사유를 적는다.
  'DAEGU_GYEONGBUK:고운사:의성 등운산': { excluded: '§0-2 DB 대상 제외 — status INACTIVE 고정' },
};

/* ── 출처 분류 ──
   공식: 정부·공공기관, 한국학중앙연구원 계열, 한국관광공사, 사찰·종단 공식 홈페이지.
   그 밖(위키·나무위키·일반 매체·여행 사이트)은 참고일 뿐 공식으로 세지 않는다. */
const OFFICIAL_HOST = [
  'heritage.go.kr', 'cha.go.kr', 'korean.visitkorea.or.kr', 'encykorea.aks.ac.kr',
  'grandculture.net', 'haeinsa.or.kr', 'munsuam.org', 'daeheungsa.co.kr', 'daewonsa.or.kr',
  'hwagyesa.org', 'doseonsa.org', 'seoncenter.or.kr', 'lotuslantern.net',
];
const OFFICIAL_WORD = [
  '국가유산포털', '국가유산청', '국가유산디지털서비스', '한국민족문화대백과', '문화대전',
  '공식홈페이지', '공식 홈페이지', '공식',
];
const IGNORE_WORD = ['미확인', '현장실사'];

function classify(one) {
  const t = one.trim();
  if (!t || IGNORE_WORD.some((w) => t.includes(w))) return 'none';
  const m = t.match(/^https?:\/\/([^/]+)/);
  if (m) {
    const host = m[1].replace(/^www\./, '');
    return OFFICIAL_HOST.some((h) => host === h || host.endsWith('.' + h)) ? 'official' : 'other';
  }
  return OFFICIAL_WORD.some((w) => t.includes(w)) ? 'official' : 'other';
}

/** 출처 문자열 하나를 조각으로 나눈다 — 구분자가 '·' 와 ';' 두 가지로 섞여 있다. */
function splitSources(raw) {
  return (raw || '').split(/[·;]/).map((s) => s.trim()).filter(Boolean);
}

function gradeOf(raw) {
  const parts = splitSources(raw);
  const official = parts.filter((p) => classify(p) === 'official').length;
  if (official >= 2) return '확정';
  if (official === 1) return '잠정';
  return '비공식';
}

/* ── 공양 — 서술을 값 4종으로 접는다 ──
   ★ "미확인" 은 NONE 이 아니다. "없다" 와 "모른다" 는 다른 사실이라 빈칸으로 둔다.
     NONE 을 넣으면 확인해 보지도 않고 "공양 없음" 을 화면에 띄우게 된다. */
function mealOf(raw) {
  const t = (raw || '').trim();
  if (!t || t.includes('미확인')) return '';
  if (/없음|불가/.test(t)) return 'NONE';
  if (/인근|주변\s*식당/.test(t)) return 'NEARBY';
  if (/사찰음식|템플스테이/.test(t)) return 'TEMPLE_MEAL';
  if (/공양간|대중공양/.test(t)) return 'PUBLIC_MEAL';
  return '';
}

function clean(v) {
  const t = (v || '').trim();
  if (!t || t === '?' || t.includes('미확인')) return '';
  return t;
}

/* ── CSV 읽기·쓰기 (따옴표 안의 쉼표를 지킨다) ── */
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
  return rows.slice(1)
    .filter((r) => (r[1] || '').trim())
    .map((r) => Object.fromEntries(head.map((h, i) => [h, (r[i] || '').trim()])));
}
const q = (v) => (/[",\n]/.test(v) ? '"' + v.replace(/"/g, '""') + '"' : v);

/* ── DB 에서 siteId 를 가져온다. seed_key 가 REGION:이름:구분 이라 정확히 맞출 수 있다. ── */
function loadSiteIds() {
  const MYSQL = process.env.MYSQL_BIN || 'mysql';
  const user = process.env.DB_USER || 'root';
  const pass = process.env.DB_PASS || '1234';
  const db = process.env.DB_NAME || 'temple_stamp_project';
  try {
    const out = execFileSync(MYSQL, ['-u' + user, '-p' + pass, '-N', '--default-character-set=utf8mb4',
      '-e', `USE ${db}; SELECT site_id, seed_key, name FROM site WHERE seed_key IS NOT NULL;`],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).replace(/\r/g, '');
    const map = new Map();
    for (const line of out.trim().split('\n')) {
      if (!line) continue;
      const [id, key] = line.split('\t');
      map.set(key, Number(id));
    }
    return map;
  } catch (e) {
    console.log('⚠ DB 를 읽지 못해 siteId 를 비운다 — 매칭은 STEP 2 가 다시 한다');
    return new Map();
  }
}

/* ── 동명이찰: 이름 뒤에 시군구를 붙인다. "용문사" 셋을 화면에서 가를 수 있어야 한다. ── */
function sigunguOf(row) {
  // disambiguation 이 "예천 소백산"·"서산 부춘산" 처럼 [시군구] [산이름] 이다. 앞 토막을 쓴다.
  const dis = (row.disambiguation || '').trim();
  const first = dis.split(/\s+/)[0] || '';
  if (first && !/암자|산내/.test(first)) return first;
  // 없으면 주소에서 뽑는다 — "경상북도 의성군 단촌면 …" 의 둘째 토막.
  const addr = (row.road_address || '').trim();
  const m = addr.match(/^\S+\s+(\S+[시군구])/);
  return m ? m[1].replace(/(시|군|구)$/, '') : '';
}

const rows = readCsv(SRC);
const idBySeedKey = loadSiteIds();

/* 이름이 겹치는지 먼저 센다 — 겹칠 때만 접미를 붙인다. */
const nameCount = {};
rows.forEach((r) => { nameCount[r.site_name_ko] = (nameCount[r.site_name_ko] || 0) + 1; });

const HEAD = ['siteId', 'name', 'roadAddress', 'coordPoint', 'qrLocationHint', 'parkingInfo', 'accessInfo',
  'mealAvailable', 'description', 'descriptionEn', 'viewpoint1', 'viewpoint2', 'viewpoint3',
  'badgeFlower', 'grade', 'source', 'checkedAt', 'note'];

const out = [HEAD.join(',')];
const stat = { 확정: 0, 잠정: 0, 비공식: 0, id있음: 0, id없음: 0, 제외: 0 };

for (const r of rows) {
  const seedKey = `${r.region_code}:${r.site_name_ko}:${r.disambiguation}`;
  const fix = CORRECTIONS[seedKey] || {};
  const sigungu = sigunguOf(r);
  const name = nameCount[r.site_name_ko] > 1 && sigungu
    ? `${r.site_name_ko}(${sigungu})`
    : r.site_name_ko;

  const siteId = idBySeedKey.get(seedKey);
  if (siteId) stat.id있음++; else stat.id없음++;

  const grade = fix.addressGrade === '확정' && !fix.excluded ? gradeOf(r.sources) : gradeOf(r.sources);
  stat[grade]++;
  if (fix.excluded) stat.제외++;

  // 뷰포인트: 원본은 "장소(설명)" 한 덩이다. 시기·볼거리 칸은 조사에 없으므로 비운다.
  const vp = clean(r.viewpoint);
  const viewpoints = vp ? [vp + '||'] : [];

  /* note 는 <b>버리는 칸이 아니라 남기는 칸</b>이다.
     조사 원본의 heritage·walk_path·verse1_asset·flower_badge 는 위 열 어디에도 대응이 없다.
     대응이 없다고 지우면 조사한 사람의 손이 사라진다 — 키=값 꼴로 붙여 두면
     STEP 3 이 필요한 것만 골라 쓰고, 아직 쓸 곳이 없는 것도 자리에 남는다. */
  const note = [
    fix.excluded || '',
    clean(r.note),
    r.research_status ? `조사단계=${r.research_status}` : '',
    clean(r.meal_available) ? `공양원문=${clean(r.meal_available)}` : '',
    clean(r.flower_badge) ? `꽃절=${clean(r.flower_badge)}` : '',
    clean(r.heritage) ? `국가유산=${clean(r.heritage)}` : '',
    clean(r.walk_path) ? `산책로=${clean(r.walk_path)}` : '',
    clean(r.verse1_asset) ? `1구자산=${clean(r.verse1_asset)}` : '',
    clean(r.main_hall_name) ? `주불전=${clean(r.main_hall_name)}` : '',
    clean(r.site_name_en) ? `영문명=${clean(r.site_name_en)}` : '',
  ].filter(Boolean).join(' · ');

  out.push([
    siteId || '',
    name,
    fix.roadAddress || clean(r.road_address),
    '',                                   // coordPoint — 조사 원본에 없다. 현장 실사에서 채운다
    '',                                   // qrLocationHint — 같은 이유
    clean(r.parking_info),
    clean(r.access_info),
    mealOf(r.meal_available),
    '',                                   // description — 조사 원본에 소개 문장 열이 없다
    '',                                   // descriptionEn — 같은 이유(site_name_en 은 이름이지 소개가 아니다)
    viewpoints[0] || '', viewpoints[1] || '', viewpoints[2] || '',
    clean(r.flower_badge) ? 'Y' : 'N',
    grade,
    clean(r.sources),
    '',                                   // checkedAt — §0-3: 전화 확인 전까지 비운다
    note,
  ].map((v) => q(String(v))).join(','));
}

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, out.join('\n') + '\n', 'utf8');

console.log(`site-enrich.csv ${rows.length}행 → ${OUT}`);
console.log(`등급  확정 ${stat.확정} · 잠정 ${stat.잠정} · 비공식 ${stat.비공식}`);
console.log(`siteId 매칭  있음 ${stat.id있음} · 없음 ${stat.id없음}`);
console.log(`§0 제외  ${stat.제외}`);
