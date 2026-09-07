-- =====================================================================
--  마스터 데이터. 전부 INSERT IGNORE 라 재기동마다 다시 실행돼도 안전하다.
--  마스터 테이블은 id 를 고정해 둔다 — 코스-사찰 연결처럼 id 를 참조하는 곳이 많아
--  auto_increment 에 맡기면 재실행 때 연결이 어긋난다.
--
--  ※ 아래 계정 3개는 로컬 확인용이다. 운영 프로파일은 sql.init.mode=never 라 실행되지 않지만,
--    공용 개발 서버에 올릴 때는 이 블록을 지우거나 비밀번호를 바꿀 것.
-- =====================================================================

-- ---------------------------------------------------------------- 계정
-- admin@templestamp.local / Admin1234!
-- rider@templestamp.local / Test1234!
-- guest@templestamp.local / Test1234!
INSERT IGNORE INTO users (user_id, email, password, nickname, role, tier, locale) VALUES
    (1, 'admin@templestamp.local',
        '$2a$10$xxfkr7kAVPzgKSh8wpDUbOqoPwBkShV69QsouncNh0xegYNy0yVY.',
        '관리자', 'ADMIN', 'AGE40', 'ko'),
    (2, 'rider@templestamp.local',
        '$2a$10$FVVkQmIpuwsYytWwqTLv7exlCfmLsSNovNp13Bo6m.DrG8cLgZj1a',
        '바람길', 'USER', 'RIDER', 'ko'),
    (3, 'guest@templestamp.local',
        '$2a$10$FVVkQmIpuwsYytWwqTLv7exlCfmLsSNovNp13Bo6m.DrG8cLgZj1a',
        'Wanderer', 'USER', 'FOREIGN', 'en');

-- ------------------------------------------------------- 원고 (챕터 8)
-- editor@templestamp.local / Test1234!
-- 편집자 계정. 역할 부여 API 는 만들지 않으므로(운영 SQL) 검증용 한 명을 시더가 넣는다.
INSERT IGNORE INTO users (user_id, email, password, nickname, role, tier, locale) VALUES
    (4, 'editor@templestamp.local',
        '$2a$10$FVVkQmIpuwsYytWwqTLv7exlCfmLsSNovNp13Bo6m.DrG8cLgZj1a',
        '편집자', 'EDITOR', 'AGE40', 'ko');

-- 기본 원고 10편(MISSION 5 · EXT 5). site_id 가 NULL 이라 어느 사찰에서든 마지막으로 쓰인다.
-- ★ 이것이 없으면 사찰 원고가 아직 없는 자리에서 미션 단계가 409 MS-4093 으로 막힌다.
--   db-check H15 가 "구 1~5 × 종류 2 가 전부 APPROVED 로 있는가" 를 지킨다.
INSERT IGNORE INTO manuscript
    (manuscript_id, site_id, verse_no, kind, variant_no, status, title, body, author_id, reviewer_id, reviewed_at) VALUES
    (1, NULL, 1, 'MISSION', 1, 'APPROVED', '이 앞에 서서',
        '일주문 앞에서 잠깐 멈춰 봅니다. 여기까지 온 길에 누구의 손이 닿았는지 하나만 떠올려 보세요. 그리고 그 사람의 이름을 마음속으로 한 번 불러 봅니다.',
        1, 1, NOW()),
    (2, NULL, 2, 'MISSION', 1, 'APPROVED', '받을 만한가',
        '이 자리를 받을 만한지 스스로에게 물어봅니다. 답이 바로 나오지 않아도 괜찮습니다. 묻는 일 자체가 오늘의 몫입니다.',
        1, 1, NOW()),
    (3, NULL, 3, 'MISSION', 1, 'APPROVED', '덜어내기',
        '가진 것 중에 오늘 하나를 덜어낸다면 무엇일지 생각해 봅니다. 크지 않아도 됩니다. 손에 쥔 것을 조금 느슨하게 하는 연습입니다.',
        1, 1, NOW()),
    (4, NULL, 4, 'MISSION', 1, 'APPROVED', '약으로 삼아',
        '지금 몸이 무거운 곳이 있는지 살펴봅니다. 이 걸음이 약이 된다면 어디에 먼저 닿기를 바라는지, 한 문장으로 적어 보세요.',
        1, 1, NOW()),
    (5, NULL, 5, 'MISSION', 1, 'APPROVED', '오늘의 다짐',
        '오늘 이 자리에서 세우는 다짐을 한 문장으로 적습니다. 지키기 쉬운 크기여야 합니다 — 내일도 할 수 있는 것으로.',
        1, 1, NOW()),
    (6, NULL, 1, 'EXT', 1, 'APPROVED', '온 길',
        '이 한 그릇이 여기 오기까지 지나온 손과 계절을 헤아려 봅니다.', 1, 1, NOW()),
    (7, NULL, 2, 'EXT', 1, 'APPROVED', '받는 자리',
        '받을 자격을 따지기보다, 받은 것을 어떻게 쓸지를 생각합니다.', 1, 1, NOW()),
    (8, NULL, 3, 'EXT', 1, 'APPROVED', '알맞게',
        '모자라지도 넘치지도 않게 — 탐하는 마음을 여기 내려놓습니다.', 1, 1, NOW()),
    (9, NULL, 4, 'EXT', 1, 'APPROVED', '몸을 고치는 일',
        '이 걸음이 몸과 마음을 고르게 하는 약이 되기를 바랍니다.', 1, 1, NOW()),
    (10, NULL, 5, 'EXT', 1, 'APPROVED', '이루려는 것',
        '무엇을 이루려 이 길에 섰는지, 오늘 다시 확인합니다.', 1, 1, NOW());

-- 위치 동의가 없으면 GPS 인증이 막히므로 더미 계정은 미리 동의시켜 둔다.
INSERT IGNORE INTO user_agreement (user_agreement_id, user_id, agreement_type, agreement_version) VALUES
    (1, 1, 'LOCATION_SERVICE', '1'),
    (2, 1, 'EBOOK_PUBLIC',     '1'),
    (3, 2, 'LOCATION_SERVICE', '1'),
    (4, 2, 'EBOOK_PUBLIC',     '1'),
    (5, 3, 'LOCATION_SERVICE', '1');

-- ---------------------------------------------------------------- 9권역
INSERT IGNORE INTO region (region_id, code, name, sort_no) VALUES
    (1, 'SEOUL',      '서울',       10),
    (2, 'GYEONGGI',   '경기·인천',  20),
    (3, 'GANGWON',    '강원',       30),
    -- v4: 충청을 충남·세종 / 충북 둘로 나눈다. 후보 편성(slot-candidates.csv)이 그 단위로 되어 있고,
    --     셋이 공존하면 "충청" 이 코스 없는 빈 껍데기로 남는다. region_id 4 는 다시 쓰지 않는다.
    (10, 'CHUNGNAM_SEJONG', '충남·세종', 41),
    (11, 'CHUNGBUK',        '충북',      42),
    (5, 'JEONBUK',    '전북',       50),
    (6, 'JEONNAM',    '전남',       60),
    (7, 'GYEONGBUK',  '경북',       70),
    (8, 'GYEONGNAM',  '경남',       80),
    -- 제주는 v4 후보 편성에 아직 없다. 코스 0 으로 목록에만 나가고 화면에서는 "준비 중" 으로 표시한다.
    (9, 'JEJU',       '제주',       90);

-- ------------------------------------------------------------ 오관게 5구
-- 코스의 n번째 자리가 n번째 구절을 담당한다(course_site.verse_no = position).
INSERT IGNORE INTO gwan_verse (verse_no, hanja, text_ko, theme) VALUES
    (1, '計功多少 量彼來處', '이 음식이 어디서 왔는지 그 공을 헤아린다.', '감사'),
    (2, '忖己德行 全缺應供', '내 덕행이 이 공양을 받기에 온전한지 스스로 살핀다.', '자기 사랑'),
    (3, '防心離過 貪等爲宗', '마음을 지켜 허물을 여의되, 탐심을 내려놓는 것을 으뜸으로 삼는다.', '내려놓음'),
    (4, '正事良藥 爲療形枯', '좋은 약으로 삼아 야윈 몸을 돌본다.', '돌봄'),
    (5, '爲成道業 應受此食', '도업을 이루기 위해 이 공양을 받는다.', '과거·현재·미래');

-- ---------------------------------------------------------------- 사찰
-- ※ 좌표는 대표 지점 기준의 초기값이다. 운영 반영 전 현장·지도 확인이 필요하다.
INSERT IGNORE INTO site (site_id, name, latitude, longitude, verify_radius,
                         qr_location_hint, parking_info, access_info, meal_available, status) VALUES
    (1, '조계사',  37.5720000, 126.9816000, 150,
        '대웅전 앞 안내판 우측',
        '전용 주차장이 좁습니다. 인근 공영주차장을 권합니다. 이륜차는 일주문 밖 지정 구역에 세웁니다.',
        '지하철 안국역 6번 출구에서 도보 5분. 전 구간 평지·포장.', 'AVAILABLE', 'ACTIVE'),
    (2, '봉은사',  37.5150000, 127.0573000, 200,
        '일주문 지나 종무소 앞 게시판',
        '주차장이 넓으나 주말에는 일찍 찹니다. 이륜차 주차 구역 별도.',
        '지하철 봉은사역 1번 출구에서 도보 5분. 경내 완만한 오르막.', 'AVAILABLE', 'ACTIVE'),
    (3, '화계사',  37.6244000, 127.0106000, 200,
        '대적광전 계단 아래 안내판',
        '경내 주차 가능. 진입로가 좁아 교행이 어렵습니다.',
        '수유역에서 버스 환승. 일주문부터 오르막 300m.', 'LIMITED', 'ACTIVE'),
    (4, '도선사',  37.6455000, 127.0107000, 250,
        '청동관음보살상 앞 안내판',
        '산 아래 주차 후 셔틀 또는 도보. 이륜차는 경사가 급해 주의가 필요합니다.',
        '우이신설선 북한산우이역에서 도보 40분 또는 셔틀. 급경사 구간 있음.', 'AVAILABLE', 'ACTIVE'),
    (5, '진관사',  37.6357000, 126.9385000, 250,
        '일주문 안쪽 왼편 안내판',
        '주차장에서 경내까지 도보 10분. 이륜차 진입 제한 구간이 있습니다.',
        '구파발역에서 버스. 계곡을 따라 걷는 흙길 구간이 있습니다.', 'AVAILABLE', 'ACTIVE');

INSERT IGNORE INTO site_i18n (site_i18n_id, site_id, locale, name, description) VALUES
    (1,  1, 'ko', '조계사', '도심 한복판에 있는 절입니다. 대웅전 앞 회화나무가 오래 서 있습니다.'),
    (2,  1, 'en', 'Jogyesa', 'A temple in the middle of the city, with an old scholar tree before the main hall.'),
    (3,  2, 'ko', '봉은사', '고층 건물에 둘러싸인 채로 숲을 품고 있습니다.'),
    (4,  2, 'en', 'Bongeunsa', 'A wooded compound held inside a ring of high-rises.'),
    (5,  3, 'ko', '화계사', '북한산 자락으로 드는 길목에 있습니다.'),
    (6,  3, 'en', 'Hwagyesa', 'Set at the entrance to the Bukhansan foothills.'),
    (7,  4, 'ko', '도선사', '오르는 길이 가파릅니다. 올라선 자리에서 도시가 내려다보입니다.'),
    (8,  4, 'en', 'Doseonsa', 'A steep climb; the city opens up below once you arrive.'),
    (9,  5, 'ko', '진관사', '계곡을 따라 걸어 들어가는 동안 소리가 먼저 바뀝니다.'),
    (10, 5, 'en', 'Jinkwansa', 'Walking in along the valley, the sound changes before the view does.');

INSERT IGNORE INTO site_viewpoint (site_viewpoint_id, site_id, sort_no, location_desc, best_time, what_to_see) VALUES
    (1, 1, 1, '대웅전 앞 회화나무 아래', '이른 아침', '도심의 소리가 어디까지 들어오는지'),
    (2, 2, 1, '판전 뒤 언덕', '해질녘', '숲과 고층 건물이 만나는 경계선'),
    (3, 3, 1, '대적광전 옆 계단 위', '오전', '북한산 능선이 겹치는 자리'),
    (4, 4, 1, '청동관음보살상 앞 난간', '맑은 날 오후', '발아래로 펼쳐지는 도시'),
    (5, 5, 1, '계곡 건너 산책로', '비 온 다음 날', '물소리가 가장 크게 들리는 지점');

INSERT IGNORE INTO site_badge (site_badge_id, site_id, badge_type, description) VALUES
    (1, 2, 'FLOWER',   '가을 홍매와 은행나무가 유명합니다.'),
    (2, 5, 'FLOWER',   '봄철 계곡을 따라 진달래가 핍니다.'),
    (3, 4, 'GUARDIAN', '십일수호 사찰입니다.');

-- ---------------------------------------------------------------- 코스
INSERT IGNORE INTO course (course_id, region_id, name, description, status, sort_no) VALUES
    (1, 1, '서울 도심 다섯 절',
        '지하철로 닿는 도심의 절 다섯 곳을 걷습니다. 하루에 다 돌 수도, 다섯 번에 나눠 걸을 수도 있습니다.',
        'ACTIVE', 10);

-- position 이 곧 담당 구절 번호다.
INSERT IGNORE INTO course_site (course_site_id, course_id, site_id, position, verse_no) VALUES
    (1, 1, 1, 1, 1),
    (2, 1, 2, 2, 2),
    (3, 1, 3, 3, 3),
    (4, 1, 4, 4, 4),
    (5, 1, 5, 5, 5);

-- v4: 자리마다의 후보. 데모 코스는 대표 한 곳씩만 둔다(MAIN sort 1).
-- 이 행이 없으면 GPS 인증이 "이 자리의 후보가 아닙니다"(COURSE-4001)로 막힌다.
INSERT IGNORE INTO slot_site (course_site_id, site_id, track, sort_no) VALUES
    (1, 1, 'MAIN', 1),
    (2, 2, 'MAIN', 1),
    (3, 3, 'MAIN', 1),
    (4, 4, 'MAIN', 1),
    (5, 5, 'MAIN', 1);

-- 사찰 간 최소 이동시간(분). 방향이 있는 쌍이라 코스당 5×4 = 20행.
-- 이보다 빨리 다음 도장을 찍으면 완료 대신 심사 보류로 간다.
INSERT IGNORE INTO site_distance (site_a_id, site_b_id, min_minutes) VALUES
    (1,2,40),(1,3,35),(1,4,55),(1,5,50),
    (2,1,40),(2,3,55),(2,4,70),(2,5,65),
    (3,1,35),(3,2,55),(3,4,25),(3,5,45),
    (4,1,55),(4,2,70),(4,3,25),(4,5,60),
    (5,1,50),(5,2,65),(5,3,45),(5,4,60);

-- ------------------------------------------------------------ 확장문구
-- 최종 규모는 5구 × 7계층 × 5버전 = 175편. 여기에는 계층마다 1버전(35편)만 넣는다.
-- 이 35편이 있어야 어느 계층 사용자든 화면이 비지 않는다. 나머지 140편은 편집팀 원고.
-- (구절·계층·버전) 이 같으면 덮어쓰므로 원고 파일을 이어서 밀어 넣으면 된다.
INSERT IGNORE INTO expansion_phrase (verse_no, tier, version_no, text_ko, review_status) VALUES
    -- 1구 · 감사
    (1,'AGE20',1,'지금 내 앞에 놓인 것들 중에 나 혼자 만든 것은 하나도 없다.','DRAFT'),
    (1,'AGE30',1,'바쁘게 지나치는 하루에도 누군가의 손이 여러 번 닿아 있다.','DRAFT'),
    (1,'AGE40',1,'내가 짊어졌다고 여긴 것들도 실은 여럿이 함께 든 것이었다.','DRAFT'),
    (1,'AGE50',1,'여기까지 온 길에 이름을 다 기억하지 못하는 손들이 있다.','DRAFT'),
    (1,'AGE60',1,'오래 살아온 만큼 빚진 것도 많다. 그 목록은 셀 수 없다.','DRAFT'),
    (1,'RIDER',1,'달려온 길은 누군가 먼저 닦아 놓은 길이다.','DRAFT'),
    (1,'FOREIGN',1,'먼 길을 왔다는 것은 그만큼 많은 사람의 도움을 받았다는 뜻이다.','DRAFT'),
    -- 2구 · 자기 사랑
    (2,'AGE20',1,'아직 이루지 못한 것으로 나를 재지 않는다.','DRAFT'),
    (2,'AGE30',1,'잘하고 있는지 묻기 전에, 오늘 버텨 낸 것을 먼저 세어 본다.','DRAFT'),
    (2,'AGE40',1,'부족한 자리를 들여다보되, 그 자리에 오래 서 있지는 않는다.','DRAFT'),
    (2,'AGE50',1,'지나온 선택들을 지금의 눈으로 함부로 깎아내리지 않는다.','DRAFT'),
    (2,'AGE60',1,'나를 아끼는 일이 남을 아끼는 일과 다르지 않다.','DRAFT'),
    (2,'RIDER',1,'속도를 늦추는 것도 실력이다.','DRAFT'),
    (2,'FOREIGN',1,'낯선 곳에서 서툰 것은 부족함이 아니라 당연함이다.','DRAFT'),
    -- 3구 · 내려놓음
    (3,'AGE20',1,'더 갖고 싶은 마음을 나쁘다 하지 않고, 그냥 알아차린다.','DRAFT'),
    (3,'AGE30',1,'쥐고 있는 것이 많아질수록 손이 무거워진다.','DRAFT'),
    (3,'AGE40',1,'놓아도 되는 것과 놓으면 안 되는 것을 구분하는 데 시간이 걸린다.','DRAFT'),
    (3,'AGE50',1,'가진 것을 덜어 내는 일에도 연습이 필요하다.','DRAFT'),
    (3,'AGE60',1,'남길 것을 고르는 일은 곧 내려놓을 것을 고르는 일이다.','DRAFT'),
    (3,'RIDER',1,'짐이 가벼울수록 멀리 간다.','DRAFT'),
    (3,'FOREIGN',1,'가방에 넣어 온 것 중 정말 필요했던 것은 몇 개였는지.','DRAFT'),
    -- 4구 · 돌봄
    (4,'AGE20',1,'몸이 보내는 신호를 나중으로 미루지 않는다.','DRAFT'),
    (4,'AGE30',1,'끼니를 거르는 습관은 언젠가 이자를 붙여 돌아온다.','DRAFT'),
    (4,'AGE40',1,'돌봐야 할 사람 목록에 내 이름도 넣는다.','DRAFT'),
    (4,'AGE50',1,'무리하지 않는 것이 게으른 것과 같지 않다.','DRAFT'),
    (4,'AGE60',1,'천천히 걷는 것도 걷는 것이다.','DRAFT'),
    (4,'RIDER',1,'장비를 살피듯 몸도 살핀다.','DRAFT'),
    (4,'FOREIGN',1,'낯선 음식과 낯선 시차 속에서 내 몸에 먼저 물어본다.','DRAFT'),
    -- 5구 · 과거·현재·미래
    (5,'AGE20',1,'지금 딛는 한 걸음이 나중의 길이 된다.','DRAFT'),
    (5,'AGE30',1,'어제의 나와 내일의 나 사이에 오늘의 내가 서 있다.','DRAFT'),
    (5,'AGE40',1,'무엇을 이루려 했는지 가끔 다시 확인한다.','DRAFT'),
    (5,'AGE50',1,'남은 시간을 세는 대신 오늘 할 일을 정한다.','DRAFT'),
    (5,'AGE60',1,'끝을 생각하는 일이 오늘을 흐리게 하지 않는다.','DRAFT'),
    (5,'RIDER',1,'다음 목적지는 지금 이 코너를 지나야 나온다.','DRAFT'),
    (5,'FOREIGN',1,'돌아가서도 이어질 무언가를 여기서 하나 가져간다.','DRAFT');

-- ---------------------------------------------------------------- 미션
-- 미션은 (구절·계층) 조합으로 뽑히고 대체 경로가 없다. 35개 조합을 모두 채워 둔다.
INSERT IGNORE INTO mission (verse_no, tier, scope, variant_no, body, review_status) VALUES
    (1,'AGE20','VERSE',1,'오늘 여기까지 오는 데 도움이 된 사람이나 것을 하나 떠올려 한 문장으로 적어 주세요.','DRAFT'),
    (1,'AGE30','VERSE',1,'최근 일주일 중 누군가의 수고 덕분에 편했던 순간을 한 문장으로 적어 주세요.','DRAFT'),
    (1,'AGE40','VERSE',1,'혼자 해냈다고 생각했던 일에 실은 누가 있었는지 한 문장으로 적어 주세요.','DRAFT'),
    (1,'AGE50','VERSE',1,'이름을 기억하지 못하지만 고마운 사람 한 명을 떠올려 한 문장으로 적어 주세요.','DRAFT'),
    (1,'AGE60','VERSE',1,'살아오며 받은 것 중 아직 갚지 못한 것 하나를 한 문장으로 적어 주세요.','DRAFT'),
    (1,'RIDER','VERSE',1,'오늘 달려온 길에서 고마웠던 것을 한 문장으로 적어 주세요.','DRAFT'),
    (1,'FOREIGN','VERSE',1,'이 여정에서 도움을 준 사람을 떠올려 한 문장으로 적어 주세요.','DRAFT'),

    (2,'AGE20','VERSE',1,'오늘의 나에게 해 주고 싶은 말을 한 문장으로 적어 주세요.','DRAFT'),
    (2,'AGE30','VERSE',1,'요즘 스스로를 몰아세운 말이 있다면, 그 말을 바꿔 한 문장으로 적어 주세요.','DRAFT'),
    (2,'AGE40','VERSE',1,'내가 잘해 온 것 하나를 인정하는 문장을 적어 주세요.','DRAFT'),
    (2,'AGE50','VERSE',1,'지난 선택 중 후회했던 하나를 지금의 눈으로 다시 보고 한 문장으로 적어 주세요.','DRAFT'),
    (2,'AGE60','VERSE',1,'스스로에게 조금 너그러워지기 위해 오늘 놓아줄 것을 한 문장으로 적어 주세요.','DRAFT'),
    (2,'RIDER','VERSE',1,'오늘 속도를 줄여도 괜찮았던 순간을 한 문장으로 적어 주세요.','DRAFT'),
    (2,'FOREIGN','VERSE',1,'낯선 곳에서 서툴렀던 순간을 받아들이는 문장을 적어 주세요.','DRAFT'),

    (3,'AGE20','VERSE',1,'지금 가장 갖고 싶은 것을 적고, 그것이 없어도 괜찮은 이유를 한 문장으로 적어 주세요.','DRAFT'),
    (3,'AGE30','VERSE',1,'손에 쥐고 있는 것 중 하나를 골라, 왜 놓기 어려운지 한 문장으로 적어 주세요.','DRAFT'),
    (3,'AGE40','VERSE',1,'오늘 내려놓기로 한 것 하나를 한 문장으로 적어 주세요.','DRAFT'),
    (3,'AGE50','VERSE',1,'덜어 내고 싶은 일이나 관계를 하나 골라 한 문장으로 적어 주세요.','DRAFT'),
    (3,'AGE60','VERSE',1,'남기고 싶은 것 하나와 놓아줄 것 하나를 한 문장에 적어 주세요.','DRAFT'),
    (3,'RIDER','VERSE',1,'다음 주행에서 덜어 낼 짐 하나를 한 문장으로 적어 주세요.','DRAFT'),
    (3,'FOREIGN','VERSE',1,'가져온 짐 중 필요 없었던 것을 떠올려 한 문장으로 적어 주세요.','DRAFT'),

    (4,'AGE20','VERSE',1,'요즘 몸이 보내는 신호 하나를 알아차려 한 문장으로 적어 주세요.','DRAFT'),
    (4,'AGE30','VERSE',1,'오늘 나를 위해 챙긴 것 하나를 한 문장으로 적어 주세요.','DRAFT'),
    (4,'AGE40','VERSE',1,'돌봐야 할 사람 목록에 내 이름을 넣는다면 무엇을 하겠는지 적어 주세요.','DRAFT'),
    (4,'AGE50','VERSE',1,'무리하지 않기 위해 오늘 조정한 것을 한 문장으로 적어 주세요.','DRAFT'),
    (4,'AGE60','VERSE',1,'오늘 내 걸음의 속도에 대해 한 문장으로 적어 주세요.','DRAFT'),
    (4,'RIDER','VERSE',1,'오늘 장비와 몸을 각각 어떻게 살폈는지 한 문장으로 적어 주세요.','DRAFT'),
    (4,'FOREIGN','VERSE',1,'여행 중 내 몸을 돌보기 위해 한 일을 한 문장으로 적어 주세요.','DRAFT'),

    (5,'AGE20','VERSE',1,'오늘의 한 걸음이 무엇으로 이어졌으면 하는지 한 문장으로 적어 주세요.','DRAFT'),
    (5,'AGE30','VERSE',1,'어제의 나에게, 그리고 내일의 나에게 각각 한 마디를 한 문장에 담아 주세요.','DRAFT'),
    (5,'AGE40','VERSE',1,'지금 하고 있는 일의 이유를 다시 한 문장으로 적어 주세요.','DRAFT'),
    (5,'AGE50','VERSE',1,'남은 시간에 꼭 하고 싶은 일 하나를 한 문장으로 적어 주세요.','DRAFT'),
    (5,'AGE60','VERSE',1,'오늘 하루를 어떻게 마무리하고 싶은지 한 문장으로 적어 주세요.','DRAFT'),
    (5,'RIDER','VERSE',1,'다음 목적지와 그곳에 가려는 이유를 한 문장으로 적어 주세요.','DRAFT'),
    (5,'FOREIGN','VERSE',1,'돌아가서도 이어 가고 싶은 것을 한 문장으로 적어 주세요.','DRAFT');

-- COMMON 과제 — 구절도 사찰도 가리지 않는다. 세 풀 중 가중 20 을 받는 자리다(챕터 10).
--   기본 계층(AGE30)으로만 넣는다. 다른 계층의 COMMON 은 콘텐츠 트랙이 채운다 —
--   비어 있으면 그 계층은 SITE·VERSE 두 풀로만 가중되고, 그것도 규칙대로 도는 상태다.
INSERT IGNORE INTO mission (verse_no, tier, scope, variant_no, body, review_status) VALUES
    (NULL,'AGE30','COMMON',1,'지금 서 있는 자리에서 들리는 소리를 하나만 골라 한 문장으로 적어 주세요.','DRAFT'),
    (NULL,'AGE30','COMMON',2,'오늘 가장 오래 바라본 것이 무엇이었는지 한 문장으로 적어 주세요.','DRAFT'),
    (NULL,'AGE30','COMMON',3,'여기까지 오는 길에 스친 생각 하나를 한 문장으로 적어 주세요.','DRAFT'),
    (NULL,'AGE30','COMMON',4,'돌아가서 오늘을 떠올릴 때 남기고 싶은 한 문장을 적어 주세요.','DRAFT'),
    (NULL,'AGE30','COMMON',5,'지금 마음의 속도를 한 문장으로 적어 주세요.','DRAFT');

-- ---------------------------------------------------------------- 명상
INSERT IGNORE INTO meditation (meditation_id, category_no, title, script, duration_sec, sort_no, status) VALUES
    (1, 1, '다섯 호흡',
        '앉은 자리에서 다섯 번만 천천히 숨을 쉽니다. 세는 것을 잊어도 괜찮습니다. 잊었다는 것을 알아차린 그 순간이 이미 알아차림입니다.',
        180, 10, 'ACTIVE'),
    (2, 2, '걷기 명상',
        '발바닥이 땅에 닿는 감각만 따라갑니다. 속도는 지금 그대로 둡니다. 생각이 앞서가면 발로 돌아옵니다.',
        300, 20, 'ACTIVE'),
    (3, 3, '소리 듣기',
        '가장 먼 소리부터 가장 가까운 소리까지 차례로 옮겨 갑니다. 마지막에는 내 숨소리에 머뭅니다.',
        240, 30, 'ACTIVE'),
    (4, 4, '앉아서 쉬기',
        '아무것도 하지 않는 시간을 삼 분만 둡니다. 지루하면 지루한 채로 둡니다.',
        180, 40, 'ACTIVE'),
    (5, 5, '내려놓기',
        '오늘 붙들고 있던 것을 하나 떠올립니다. 밀어내지 않고, 그저 손에서 힘을 뺍니다.',
        240, 50, 'ACTIVE');

INSERT IGNORE INTO meditation_i18n (meditation_i18n_id, meditation_id, locale, title, script) VALUES
    (1, 1, 'en', 'Five Breaths',
        'Take five slow breaths where you are. Losing count is fine — noticing that you lost it is already the practice.'),
    (2, 2, 'en', 'Walking Meditation',
        'Follow only the feel of your soles meeting the ground. Keep your current pace.');

-- ---------------------------------------------------------------- 보상
INSERT IGNORE INTO reward_policy (reward_policy_id, code, reward_type, trigger_type, name, description) VALUES
    (1, 'RW-STAMP',     'STAMP',    'STAMP_COMPLETED',
        '순례 도장', '사찰 한 곳을 인증할 때마다 여권에 도장이 찍힙니다.'),
    (2, 'RW-COURSE',    'COUPON',   'COURSE_COMPLETED',
        '코스 완주 쿠폰', '코스 하나를 완주하면 드리는 쿠폰입니다.'),
    (3, 'RW-INTERIM',   'PHYSICAL', 'THREE_COURSES_COMPLETED',
        '중간 편집본', '세 코스를 마치면 중간 편집본을 보내 드립니다.'),
    (4, 'RW-HOEHYANG',  'PHYSICAL', 'ALL_COMPLETED',
        '회향 기념품', '모든 코스를 마친 분께 드리는 회향 기념품입니다.');

-- ------------------------------------------------------- 사찰 가는 법 (2)
-- 참배 순서 사전 7행. 60곳이 공유하는 유일한 순서 기준이다.
-- meaning 은 그 절에 그 요소가 없어도 보여준다 — "없음" 을 빈칸이 아니라 설명으로 채우기 위해서다.
-- passage_meaning 은 "직전 자리에서 이 자리로 오는 길" 이다. 다음 행의 것을 이어 붙이면
-- 없는 자리의 대체 서술(② + ③)이 된다.
INSERT IGNORE INTO shrine_element (element_id, code, name, sort_no, meaning, etiquette, passage_meaning) VALUES
    (1, 'ILJUMUN', '일주문', 1,
        '기둥이 한 줄로 선 절의 정문. 속세와 절의 경계이며, 한 마음(一心)으로 들어서라는 뜻이다. 조계문·산문 등 이름이 다른 절도 있다.',
        '문 앞에서 합장 반배하고 지난다. 문지방은 밟지 않는다.',
        '주차 후 30초 서서 숨을 고른다. 여기서부터는 걷는 속도가 곧 마음의 속도다.'),
    (2, 'GEUMGANGMUN', '금강문', 2,
        '금강역사 두 분이 지키는 문. 불법을 수호하는 힘을 상징한다. 문의 개수가 다른 것은 결함이 아니라 그 절의 사정이다.',
        '역사의 얼굴을 한 번 보고 합장 반배한다.',
        '일주문을 지나면 숲길이 이어진다. 문과 문 사이의 이 길이 마음을 고르는 자리다.'),
    (3, 'CHEONWANGMUN', '천왕문', 3,
        '사천왕이 불법을 지키는 문. 동서남북을 지키는 네 왕이 악을 밟고 서 있다. 무서운 얼굴은 나를 향한 것이 아니라 내 안의 나쁜 마음을 향한 것이다.',
        '사천왕을 하나씩 올려다본다. 발밑에 밟힌 것이 무엇인지 본다. 합장 반배.',
        '문을 하나 지날 때마다 한 생각을 내려놓는다. 여기까지 온 것만으로 이미 반은 온 것이다.'),
    (4, 'BURIMUN', '불이문', 4,
        '둘이 아님(不二)을 뜻하는 문. 나와 부처, 속세와 절이 둘이 아니라는 가르침이며 해탈문이라고도 한다. 이 문을 지나면 부처의 마당이다.',
        '걸음을 늦춘다. 문 안쪽에서 뒤를 한 번 돌아본다.',
        '천왕문에서 이 자리까지는 오르막인 절이 많다. 숨이 찬 것도 참배의 일부다.'),
    (5, 'PAGODA', '탑', 5,
        '부처의 진신사리를 모신 곳. 법당보다 먼저 세워진 예배 대상이며, 탑이 곧 부처다. 석탑의 층수와 양식으로 절의 나이를 짐작할 수 있다.',
        '시계 방향으로 세 바퀴 돈다. 돌면서 오늘 내려놓을 것 하나를 정한다.',
        '마당이 열리고 탑이 먼저 보인다. 법당으로 곧장 가지 말고 탑 앞에 선다.'),
    (6, 'MAIN_HALL', '주불전', 6,
        '본존불을 모신 중심 법당. 석가모니불이면 대웅전, 비로자나불이면 대적광전, 아미타불이면 무량수전·극락전 — 이름이 곧 그 절이 모신 부처를 말해준다.',
        '신발을 벗고 옆문으로 들어가 삼배한다. 가운데 문은 스님의 문이다.',
        '탑을 돌고 난 뒤 법당 앞에 선다. 편액의 이름을 읽고 어느 부처를 뵙는지 안 뒤에 들어간다.'),
    (7, 'ANNEX_HALL', '부속전각', 7,
        '산신각·삼성각·명부전·나한전 등 큰법당 주변의 작은 전각들. 산신과 칠성은 토속 신앙이 절 안으로 들어온 흔적이며, 절마다 구성이 가장 다른 곳이다.',
        '큰법당을 먼저 참배한 뒤 들른다. 작은 전각일수록 조용히.',
        '큰법당에서 나와 뒤편으로 돈다. 가장 높은 곳의 작은 집이 산신각이다.');

-- 사찰별 보유 요소.
-- ※ 아래 보유 여부와 전각 이름은 형식 검증용 임시값이다. 좌표와 마찬가지로
--   운영 반영 전 현장·협회 확인을 거쳐 교체해야 한다. 틀린 값이 들어가도 화면은 정상으로 보이므로
--   "화면이 잘 나온다" 는 검수 근거가 되지 않는다.
-- 일부러 절마다 빠진 자리를 다르게 두었다 — 없는 자리를 대체 서술로 채우는 규칙(R3)의 검증용이다.
INSERT IGNORE INTO site_element (site_id, element_id, local_name, note) VALUES
    -- 1 조계사 — 도심 절. 산문 세 곳과 탑 자리가 비어 대체 서술이 가장 많이 나온다
    (1, 6, '대웅전', NULL),
    (1, 7, NULL,     NULL),
    -- 2 봉은사
    (2, 1, NULL,     NULL),
    (2, 3, NULL,     NULL),
    (2, 6, '대웅전', NULL),
    (2, 7, NULL,     NULL),
    -- 3 화계사
    (3, 1, NULL,         NULL),
    (3, 6, '대적광전', '비로자나불을 모신 법당이라 이름이 대웅전이 아니다'),
    (3, 7, NULL,         NULL),
    -- 4 도선사
    (4, 1, NULL,     NULL),
    (4, 5, NULL,     NULL),
    (4, 6, '대웅전', NULL),
    (4, 7, NULL,     NULL),
    -- 5 진관사 — 가장 많은 자리가 채워진 절
    (5, 1, NULL,     NULL),
    (5, 3, NULL,     NULL),
    (5, 4, NULL,     NULL),
    (5, 6, '대웅전', NULL),
    (5, 7, NULL,     NULL);
