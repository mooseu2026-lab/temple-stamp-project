/* 두 스키마를 information_schema 로 전수 비교한다.
   쓰는 법:  node backend/docs/verify/schema-diff.js <스키마A> <스키마B>
   종료코드 = 차이 건수(0 이면 통과).

   "개발 DB 에서만 통과하는 것은 통과가 아니다" 를 재는 자다. 최종 점검 F 에서 이 비교가
   ALTER 파일에 빠진 색인 하나를 찾아냈다 — 표·컬럼만 보면 통과였고 색인에서 갈렸다. */
const { execFileSync } = require('child_process');

const A = process.argv[2];
const B = process.argv[3];
if (!A || !B) {
  console.log('쓰는 법: node schema-diff.js <스키마A> <스키마B>');
  process.exit(2);
}
const USER = process.env.DB_USER || 'root';
const PASS = process.env.DB_PASS || '1234';
const BIN = process.env.MYSQL_BIN || 'mysql';

function q(sql) {
  const out = execFileSync(BIN,
    ['-u' + USER, '-p' + PASS, '-N', '-B', '--default-character-set=utf8mb4', '-e', sql],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] });
  return out.trim().split(/\r?\n/).filter(Boolean);
}

/* 비교 항목 일곱. 컬럼만 보면 색인·제약이 어긋난 것을 놓친다 — 그래서 일곱을 다 본다. */
const CHECKS = [
  ['표', db => `SELECT TABLE_NAME FROM information_schema.TABLES
      WHERE TABLE_SCHEMA='${db}' AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME`],
  ['컬럼', db => `SELECT CONCAT(TABLE_NAME,'.',COLUMN_NAME,' ',COLUMN_TYPE,' ',IS_NULLABLE,' ',
      IFNULL(COLUMN_DEFAULT,'-'),' ',IFNULL(EXTRA,'-')) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA='${db}' ORDER BY TABLE_NAME, COLUMN_NAME`],
  ['인덱스', db => `SELECT CONCAT(TABLE_NAME,'.',INDEX_NAME,'[',SEQ_IN_INDEX,']=',COLUMN_NAME,
      ' uniq=',IF(NON_UNIQUE=0,'Y','N')) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA='${db}' ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX`],
  ['외래키', db => `SELECT CONCAT(TABLE_NAME,'.',CONSTRAINT_NAME,' ',COLUMN_NAME,'->',
      REFERENCED_TABLE_NAME,'.',REFERENCED_COLUMN_NAME) FROM information_schema.KEY_COLUMN_USAGE
      WHERE TABLE_SCHEMA='${db}' AND REFERENCED_TABLE_NAME IS NOT NULL
      ORDER BY TABLE_NAME, CONSTRAINT_NAME, ORDINAL_POSITION`],
  ['CHECK 제약', db => `SELECT CONCAT(c.TABLE_NAME,'.',c.CONSTRAINT_NAME,' ',k.CHECK_CLAUSE)
      FROM information_schema.TABLE_CONSTRAINTS c
      JOIN information_schema.CHECK_CONSTRAINTS k
        ON k.CONSTRAINT_SCHEMA=c.CONSTRAINT_SCHEMA AND k.CONSTRAINT_NAME=c.CONSTRAINT_NAME
      WHERE c.TABLE_SCHEMA='${db}' AND c.CONSTRAINT_TYPE='CHECK'
      ORDER BY c.TABLE_NAME, c.CONSTRAINT_NAME`],
  ['생성 컬럼', db => `SELECT CONCAT(TABLE_NAME,'.',COLUMN_NAME,' ',GENERATION_EXPRESSION)
      FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='${db}' AND GENERATION_EXPRESSION <> ''
      ORDER BY TABLE_NAME, COLUMN_NAME`],
  ['문자셋·엔진', db => `SELECT CONCAT(TABLE_NAME,' ',ENGINE,' ',TABLE_COLLATION)
      FROM information_schema.TABLES WHERE TABLE_SCHEMA='${db}' AND TABLE_TYPE='BASE TABLE'
      ORDER BY TABLE_NAME`],
];

const gaps = [];
let total = 0;
console.log('| 무엇 | ' + A + ' | ' + B + ' | 차이 |');
console.log('|---|---:|---:|---:|');
for (const [name, sql] of CHECKS) {
  const a = q(sql(A));
  const b = q(sql(B));
  const sa = new Set(a);
  const sb = new Set(b);
  const onlyA = a.filter(x => !sb.has(x));
  const onlyB = b.filter(x => !sa.has(x));
  const diff = onlyA.length + onlyB.length;
  total += diff;
  console.log('| ' + name + ' | ' + a.length + ' | ' + b.length + ' | ' + diff + ' |');
  onlyA.forEach(x => gaps.push('  ' + A + ' 에만: [' + name + '] ' + x));
  onlyB.forEach(x => gaps.push('  ' + B + ' 에만: [' + name + '] ' + x));
}
console.log('| **합계 차이** | | | **' + total + '** |');
if (gaps.length) {
  console.log('');
  gaps.slice(0, 40).forEach(g => console.log(g));
  if (gaps.length > 40) console.log('  … 그 밖 ' + (gaps.length - 40) + '건');
}
process.exit(total === 0 ? 0 : 1);
