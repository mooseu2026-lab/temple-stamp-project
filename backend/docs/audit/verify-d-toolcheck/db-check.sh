#!/usr/bin/env bash
# backend/docs/verify/db-check.sh — DB 연결 지속성 점검 (중간 점검 B §2, H1~H9)
#
#   bash backend/docs/verify/db-check.sh before   # H1·H3·H6·H7·H8·H9 (newman 앞)
#   bash backend/docs/verify/db-check.sh after    # H2·H4·H5           (newman 뒤)
#   bash backend/docs/verify/db-check.sh all      # 전부
#
# H3 은 실제로 10분을 기다린다. 유휴 뒤에 커넥션이 살아 있는지는 기다려 보지 않으면 알 수 없다.
# 결과는 화면과 docs/verify/db-check-result.txt 에 동시에 쌓인다(append — 전반/후반이 한 파일에 남는다).
cd "$(dirname "$0")"
OUT="db-check-result.txt"
BASE=${BASE_URL:-http://localhost:8080}
DB=temple_stamp_project
DB_USER=${DB_USER:-root}
DB_PASS=${DB_PASS:-1234}
MYSQL=${MYSQL_BIN:-mysql}
PHASE=${1:-all}

q() { "$MYSQL" -u"$DB_USER" -p"$DB_PASS" --default-character-set=utf8mb4 -t -e "USE $DB; $1" 2>/dev/null; }
# 숫자 하나만 받을 때. q() 는 -t 라 표 모양 문자열이 돌아온다 — 비교에 쓰면 늘 거짓이다.
# 윈도우 mysql 은 줄 끝에  을 붙이므로 함께 지운다(정리.md §6-10).
qn() { "$MYSQL" -u"$DB_USER" -p"$DB_PASS" --default-character-set=utf8mb4 -N -B -e "USE $DB; $1" 2>/dev/null | tr -d "" | head -1; }
say() { echo "$@" | tee -a "$OUT"; }
run() { echo "$1" | tee -a "$OUT"; shift; { "$@" 2>&1; } | tee -a "$OUT"; echo | tee -a "$OUT"; }

[ "$PHASE" = "before" ] || [ "$PHASE" = "all" ] && : > "$OUT"
say "=================================================================="
say " DB 연결 지속성 점검 — $(date '+%Y-%m-%d %H:%M:%S')  phase=$PHASE"
say "=================================================================="

# ─────────────────────────────────────────────────────────────── 전반
if [ "$PHASE" = "before" ] || [ "$PHASE" = "all" ]; then

say ""
say "── H1  기동 직후 연결 ────────────────────────────────────────────"
say "기대: /health 200 UP. components.db 는 노출하지 않는 것이 정상이다."
say "HTTP $(curl -s -o /tmp/h1.$$ -w '%{http_code}' "$BASE/health")  $(cat /tmp/h1.$$)"; rm -f /tmp/h1.$$
run "DB 왕복:" q "SELECT 1 AS ping;"

say ""
say "── H6  시간대 ───────────────────────────────────────────────────"
say "기대: Asia/Seoul 또는 +09:00. SYSTEM 이면 OS 시계를 따른다(정리.md §5-2 9번)."
run "" q "SELECT @@global.time_zone AS global_tz, @@session.time_zone AS session_tz, NOW() AS mysql_now, CURDATE() AS mysql_today;"
say "호스트 시각: $(date '+%Y-%m-%d %H:%M:%S %Z')"

say ""
say "── H7  외래키 무결성 ────────────────────────────────────────────"
say "기대: 아래 네 수치가 전부 0."
run "" q "SELECT
  (SELECT COUNT(*) FROM stamp s      LEFT JOIN site t ON t.site_id = s.site_id
     WHERE s.site_id IS NOT NULL AND t.site_id IS NULL)                       AS stamp_site_orphan,
  (SELECT COUNT(*) FROM slot_site ss LEFT JOIN site t ON t.site_id = ss.site_id
     WHERE t.site_id IS NULL)                                                 AS slot_site_orphan,
  (SELECT COUNT(*) FROM course_site cs LEFT JOIN site t ON t.site_id = cs.site_id
     WHERE t.site_id IS NULL)                                                 AS course_site_orphan,
  (SELECT COUNT(*) FROM stamp s      LEFT JOIN course_site cs ON cs.course_site_id = s.completed_course_site_id
     WHERE s.completed_course_site_id IS NOT NULL AND cs.course_site_id IS NULL) AS completed_slot_orphan;"

say ""
say "── H8  문자셋 ───────────────────────────────────────────────────"
say "기대: utf8mb4 / utf8mb4_unicode_ci, 한글 왕복이 그대로."
run "" q "SELECT DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME
          FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '$DB';"
run "한글 왕복(닉네임):" q "SELECT nickname FROM users WHERE email = 'admin@templestamp.local';"
run "한글 왕복(리터럴):" q "SELECT '확인포인트' AS roundtrip, CHAR_LENGTH('확인포인트') AS chars;"

say ""
say "── H9  sql.init 멱등 ────────────────────────────────────────────"
say "기대: bootRun 을 몇 번 켜도 표 31개, 시드 행 수가 같다."
say "      (schema.sql 은 CREATE TABLE IF NOT EXISTS, data.sql 은 INSERT IGNORE)"
run "" q "SELECT
  (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB') AS tables_,
  (SELECT COUNT(*) FROM site WHERE seed_key IS NOT NULL)                    AS site_seeded,
  (SELECT COUNT(*) FROM course WHERE name LIKE '%공양의 길')                 AS course_seeded,
  (SELECT COUNT(*) FROM slot_site)                                          AS slot_site,
  (SELECT COUNT(*) FROM site_element)                                       AS site_element,
  (SELECT COUNT(*) FROM region)                                             AS region_;"

say ""
say "── H3  장시간 유휴 후 재연결 (10분 실제 대기) ────────────────────"
say "기대: 10분 놀린 뒤에도 첫 요청이 200. HikariCP maxLifetime 이 MySQL wait_timeout 보다"
say "      짧아야 한다 — 반대면 서버가 먼저 끊은 커넥션을 풀이 건네주고 그 요청만 죽는다."
run "MySQL 타임아웃:" q "SELECT @@global.wait_timeout AS wait_timeout_s, @@global.interactive_timeout AS interactive_s;"
say "HikariCP maxLifetime: $(grep -i 'max-lifetime' ../../../src/main/resources/application*.yml 2>/dev/null || echo '설정 없음 → 기본 1800000ms(30분)')"
say "warm-up: HTTP $(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/regions")"
say "$(date '+%H:%M:%S') 부터 10분 대기…"
sleep 600
say "$(date '+%H:%M:%S') 대기 끝. 유휴 뒤 첫 요청:"
say "  /api/regions      HTTP $(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/regions")"
say "  /api/courses/1    HTTP $(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/courses/1")"
say "  /health           HTTP $(curl -s -o /dev/null -w '%{http_code}' "$BASE/health")"
run "유휴 뒤 서버 쪽 커넥션:" q "SHOW STATUS LIKE 'Threads_connected';"

say ""
say "── H2  커넥션 수 (newman 전) ─────────────────────────────────────"
run "" q "SHOW STATUS LIKE 'Threads_connected';"
say "위 값을 newman 뒤(after)와 비교한다. 차이가 풀 크기(기본 10)를 넘으면 누수다."

fi

# ─────────────────────────────────────────────────────────────── 후반
if [ "$PHASE" = "after" ] || [ "$PHASE" = "all" ]; then

say ""
say "── H11  챕터 6 표 정합 ──────────────────────────────────────────"
say "기대: 아래 셋이 전부 0. photo 와 thinkbox DIRECT 는 같은 (사용자, 사찰) 을 가리켜야 한다."
run "" q "SELECT
  (SELECT COUNT(*) FROM photo p LEFT JOIN site s ON s.site_id = p.site_id WHERE s.site_id IS NULL)            AS photo_site_orphan,
  (SELECT COUNT(*) FROM thinkbox t LEFT JOIN site s ON s.site_id = t.site_id
    WHERE t.site_id IS NOT NULL AND s.site_id IS NULL)                                                        AS thinkbox_site_orphan,
  (SELECT COUNT(*) FROM meditation_log ml LEFT JOIN meditation m ON m.meditation_id = ml.meditation_id
    WHERE m.meditation_id IS NULL)                                                                            AS medlog_orphan;"
run "사찰별 DIRECT 문장이 둘 이상인 경우(0행이어야 한다):" q "SELECT user_id, site_id, COUNT(*) AS n FROM thinkbox
  WHERE source = \"DIRECT\" AND site_id IS NOT NULL GROUP BY user_id, site_id HAVING COUNT(*) > 1;"

say ""
say "── H12  유니크 제약 실측 ────────────────────────────────────────"
say "기대: 셋 다 중복 INSERT 가 오류로 막힌다. 정의만 보지 않고 실제로 넣어 본다."
# $1=라벨  $2=중복 INSERT SQL  $3=원본이 있는지 세는 SQL  $4=제약 이름
#
# 복제할 원본이 없으면 INSERT ... SELECT 는 0행을 넣고 조용히 성공한다.
# 그것을 "중복이 통과했다" 로 읽으면 정리 직후에는 늘 ❌ 가 뜬다 — 검사가 아니라 착시다.
# 그래서 원본 수를 먼저 세고, 없으면 정의만 확인했다고 분명히 적는다.
uniq() {
  local out rows defined
  rows=$(qn "$3")
  if [ "${rows:-0}" = "0" ]; then
    defined=$(qn "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = '$DB' AND INDEX_NAME = '$4' AND NON_UNIQUE = 0;")
    if [ "${defined:-0}" -gt 0 ]; then
      say "  ➖ $1 — 복제할 행이 없어 실측은 건너뛰고 제약 정의만 확인했다(있음)"
    else
      say "  ❌ $1 — 제약 자체가 없다"
    fi
    return
  fi
  out=$("$MYSQL" -u"$DB_USER" -p"$DB_PASS" --default-character-set=utf8mb4 -e "USE $DB; $2" 2>&1 \
        | grep -iE 'duplicate|ERROR' | head -1)
  if [ -n "$out" ]; then
    say "  ✅ $1 — 막혔다: ${out:0:120}"
  else
    say "  ❌ $1 — 중복이 들어갔다"
  fi
}
uniq "photo(user_id, site_id)" \
  "INSERT INTO photo (user_id, site_id, file_key, has_other_face, is_private)
   SELECT user_id, site_id, CONCAT(file_key,'x'), 0, 0 FROM photo LIMIT 1;" \
  "SELECT COUNT(*) FROM photo;" "uk_photo_user_site"
uniq "site(seed_key)" \
  "INSERT INTO site (name, latitude, longitude, verify_radius, seed_key)
   SELECT CONCAT(name,'-dup'), latitude, longitude, verify_radius, seed_key
   FROM site WHERE seed_key IS NOT NULL LIMIT 1;" \
  "SELECT COUNT(*) FROM site WHERE seed_key IS NOT NULL;" "uk_site_seed_key"
uniq "stamp(pilgrimage_id, completed_course_site_id)" \
  "INSERT INTO stamp (pilgrimage_id, course_site_id, verify_status, verify_method, mission_verified_at)
   SELECT pilgrimage_id, course_site_id, 'COMPLETED', 'GPS_QR', NOW()
   FROM stamp WHERE verify_status = 'COMPLETED' LIMIT 1;" \
  "SELECT COUNT(*) FROM stamp WHERE verify_status = 'COMPLETED';" "uk_stamp_completed"
say "  (혹시 들어갔다면 흔적을 지운다)"
"$MYSQL" -u"$DB_USER" -p"$DB_PASS" --default-character-set=utf8mb4 \
  -e "USE $DB; DELETE FROM site WHERE name LIKE '%-dup';" 2>/dev/null

say ""
say "── H2  커넥션 누수 (newman 후) ───────────────────────────────────"
say "기대: 전(前) 값과의 차이가 HikariCP 풀 크기(기본 10) 이내."
run "" q "SHOW STATUS LIKE 'Threads_connected';"
run "풀이 붙어 있는 접속:" q "SELECT COUNT(*) AS app_conns FROM information_schema.PROCESSLIST WHERE db = '$DB';"

say ""
say "── H4  트랜잭션 잔류 ────────────────────────────────────────────"
say "기대: 0행. 남아 있으면 어딘가 커밋도 롤백도 안 하고 붙잡고 있는 것이다."
run "" q "SELECT COUNT(*) AS open_trx FROM information_schema.INNODB_TRX;"
run "(있으면 상세)" q "SELECT trx_id, trx_state, trx_started, trx_query FROM information_schema.INNODB_TRX;"

say ""
say "── H5  잠금 대기·교착 ───────────────────────────────────────────"
say "기대: LATEST DETECTED DEADLOCK 없음."
DEADLOCK=$("$MYSQL" -u"$DB_USER" -p"$DB_PASS" -e "SHOW ENGINE INNODB STATUS\G" 2>/dev/null \
  | sed -n '/LATEST DETECTED DEADLOCK/,/^---/p' | head -40)
if [ -z "$DEADLOCK" ]; then
  say "  교착 기록 없음 ✅"
else
  say "  ⚠ 교착 기록 있음:"
  echo "$DEADLOCK" | tee -a "$OUT"
fi
run "현재 잠금 대기:" q "SELECT COUNT(*) AS lock_waits FROM performance_schema.data_lock_waits;"

fi

say ""
say "=================================================================="
say " 끝 — $(date '+%Y-%m-%d %H:%M:%S')   결과 파일: docs/verify/$OUT"
say "=================================================================="
