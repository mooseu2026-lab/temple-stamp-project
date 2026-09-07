// src/main/java/com/templestamp/content/seed/SiteSeedImporter.java
package com.templestamp.content.seed;

import com.templestamp.admin.AdminSiteService;
import com.templestamp.admin.dto.AdminCourseSaveRequest;
import com.templestamp.admin.dto.AdminSiteSaveRequest;
import com.templestamp.admin.dto.SiteBadgeSaveRequest;
import com.templestamp.admin.dto.SiteViewpointSaveRequest;
import com.templestamp.admin.dto.StatusChangeRequest;
import com.templestamp.course.CourseMapper;
import com.templestamp.course.CourseSiteMapper;
import com.templestamp.course.Region;
import com.templestamp.course.RegionMapper;
import com.templestamp.course.SlotSite;
import com.templestamp.course.SlotSiteMapper;
import com.templestamp.course.SlotSiteService;
import com.templestamp.global.error.BusinessException;
import com.templestamp.kakao.KakaoMapService;
import com.templestamp.kakao.dto.KakaoPlaceResponse;
import com.templestamp.site.SiteElementMapper;
import com.templestamp.site.SiteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * v4 시드 적재기. {@code --spring.profiles.active=local,seed} 로 기동하면 한 번 돌고 끝난다
 * (운영 프로파일에는 절대 넣지 않는다).
 * <p>
 * 순서: 사찰(ko 필수·DRAFT) → 좌표(카카오) → 요소·뱃지 → 라인별 코스 1개 + 슬롯 5 + 후보 N.
 * <p>
 * 멱등: {@code site.seed_key} 로 재실행 시 기존 행을 쓰고, 요소·후보는 UPSERT 다.
 * 두 번 돌려서 행 수가 같아야 한다.
 * <p>
 * 넣지 않는 것
 * <ul>
 *   <li>7자리의 {@code ?}·"미확인" — {@link SeedCsv#clean} 이 null 로 만든다</li>
 *   <li>note 에 "시드 미투입" 이 있는 행(묘각사, Q2 확정) — 사찰도 후보도 건너뛴다</li>
 *   <li>좌표를 못 찾은 사찰의 좌표 — (0,0) 으로 두고 보고서에 남긴다.
 *       {@code qrLocationHint} 가 null 이라 어차피 ACTIVE 로 못 올라간다(의도).</li>
 * </ul>
 */
@Component
@Profile("seed")
@RequiredArgsConstructor
@Slf4j
public class SiteSeedImporter implements CommandLineRunner {

    /** 실사 전 임시 반경(m). 관리자 PUT 으로 교정한다. */
    private static final int DEFAULT_RADIUS = 150;

    /** 카카오 로컬 API 호출 간격(ms). 111회를 연달아 때리지 않는다. */
    private static final long KAKAO_INTERVAL_MS = 1000L;

    /** note 에 이 말이 있으면 사찰도 후보도 건너뛴다. */
    private static final String SKIP_MARK = "시드 미투입";

    /** note 에 이 말이 있으면 그 사찰의 후보 행에 {@code is_congested = 1}. */
    private static final String CONGESTED_MARK = "[Q3 확정]";

    /**
     * note 에 이 말이 있으면 사찰 행은 만들되 <b>INACTIVE 로 고정</b>한다.
     * 지우지 않는 이유는 조사 결과를 남겨 두기 위해서다 — 복원이 끝나면 상태만 올리면 된다.
     * (2026-09-07 예성 정정 §0-2 — 의성 고운사)
     */
    private static final String INACTIVE_MARK = "[DB 제외]";

    /**
     * CSV 의 {@code region_code} → DB {@code region.code}. <b>이름 기준 매핑이고 DB 코드는 바꾸지 않는다.</b>
     * 조사 CSV 는 광역시를 도에 붙여 적었고(부산/경남·대구/경북·전남/광주) DB 는 도 이름만 쓴다.
     * 같은 이름이면 그대로 통과시키므로 여기에는 어긋나는 셋만 적는다.
     */
    private static final Map<String, String> REGION_ALIAS = Map.of(
            "BUSAN_GYEONGNAM", "GYEONGNAM",
            "DAEGU_GYEONGBUK", "GYEONGBUK",
            "JEONNAM_GWANGJU", "JEONNAM");

    private final AdminSiteService adminSiteService;
    private final SiteMapper siteMapper;
    private final SiteElementMapper siteElementMapper;
    private final RegionMapper regionMapper;
    private final CourseMapper courseMapper;
    private final CourseSiteMapper courseSiteMapper;
    private final SlotSiteMapper slotSiteMapper;
    private final SlotSiteService slotSiteService;
    private final KakaoMapService kakao;

    @Override
    public void run(String... args) throws Exception {
        List<Map<String, String>> sites = SeedCsv.read("seed/sites-master.csv");
        List<Map<String, String>> slots = SeedCsv.read("seed/slot-candidates.csv");

        Map<String, Long> regionByCode = new HashMap<>();
        for (Region r : regionMapper.findAllRegions()) {
            regionByCode.put(r.getCode(), r.getRegionId());
        }
        Map<String, Long> elementBySort = new HashMap<>();
        siteElementMapper.findDictionary()
                .forEach(m -> elementBySort.put(String.valueOf(m.get("sortNo")), ((Number) m.get("elementId")).longValue()));

        // 같은 이름의 절이 여럿이면 화면에서 가를 수 없다 — "용문사" 가 셋이다.
        // 표시 이름에만 시군구를 붙이고 seed_key 는 <b>원래 이름 그대로</b> 둔다.
        // 키가 바뀌면 이미 올라간 DB 에서 같은 절이 하나 더 생긴다.
        Map<String, Integer> nameCount = new HashMap<>();
        sites.forEach(r -> nameCount.merge(r.get("site_name_ko"), 1, Integer::sum));

        Map<String, Long> siteIdBySeedKey = new LinkedHashMap<>();
        List<String> congestedKeys = new ArrayList<>();
        List<String> noCoord = new ArrayList<>();
        List<String> report = new ArrayList<>();
        int created = 0;

        // ── 1) 사찰 ──────────────────────────────────────────────────────────
        for (var r : sites) {
            String key = seedKey(r.get("region_code"), r.get("site_name_ko"), r.get("disambiguation"));
            if (skipped(r.get("note"))) {
                report.add("시드 미투입(Q2): " + key);
                continue;
            }

            Long siteId = siteMapper.findIdBySeedKey(key);
            if (siteId == null) {
                BigDecimal[] ll = resolveCoord(r);
                if (ll == null) {
                    ll = new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
                    noCoord.add(key);
                }
                var i18n = new ArrayList<AdminSiteSaveRequest.I18nBlock>();
                i18n.add(new AdminSiteSaveRequest.I18nBlock("ko", r.get("site_name_ko"), describe(r)));
                if (r.get("site_name_en") != null) {
                    i18n.add(new AdminSiteSaveRequest.I18nBlock("en", r.get("site_name_en"), null));
                }
                // qrLocationHint 는 현장에서 정한다 → null → ACTIVE 로 못 올라간다(의도).
                var req = new AdminSiteSaveRequest(ll[0], ll[1], DEFAULT_RADIUS, null,
                        r.get("parking_info"), r.get("access_info"), r.get("meal_available"), i18n);
                siteId = adminSiteService.create(req).siteId();
                siteMapper.updateSeedKey(siteId, key);
                created++;
            }
            siteIdBySeedKey.put(key, siteId);
            if (congested(r.get("note"))) {
                congestedKeys.add(key);
            }

            // 7자리 — Y 인 것만. 이름이 없어도 "있음" 은 기록한다(local_name null → 화면은 사전 이름으로).
            putElement(siteId, elementBySort.get("1"), r, "iljumun");
            putElement(siteId, elementBySort.get("2"), r, "geumgangmun");
            putElement(siteId, elementBySort.get("3"), r, "cheonwangmun");
            putElement(siteId, elementBySort.get("4"), r, "bulimun");
            putElement(siteId, elementBySort.get("5"), r, "pagoda");
            if (r.get("main_hall_name") != null) {   // 주불전은 모든 절에 있다 — Y/N 열이 따로 없다
                siteElementMapper.upsert(siteId, elementBySort.get("6"), r.get("main_hall_name"), null);
            }
            if (r.get("annex_halls") != null) {
                siteElementMapper.upsert(siteId, elementBySort.get("7"), r.get("annex_halls"), null);
            }
            if (r.get("flower_badge") != null) {
                adminSiteService.upsertBadge(siteId, new SiteBadgeSaveRequest("FLOWER", r.get("flower_badge")));
            }
            // 뷰포인트 — 조사에 한 덩이만 있다("어디서 무엇을 보는가"). 시기·볼거리 칸은 현장에서 채운다.
            // 없는 칸을 그럴듯한 문장으로 메우면 나중에 조사 결과인지 추측인지 아무도 모른다.
            if (r.get("viewpoint") != null) {
                adminSiteService.upsertViewpoint(siteId,
                        new SiteViewpointSaveRequest(1, trim255(r.get("viewpoint")), null, null));
            }
            // [DB 제외] — 행은 남기고 상태만 내린다(§0-2). ACTIVE 확장·코스 배정에서 저절로 빠진다.
            if (excluded(r.get("note"))) {
                adminSiteService.changeStatus(siteId, new StatusChangeRequest("INACTIVE"));
                report.add("DB 제외(INACTIVE 고정): " + key);
            }
        }

        // ── 2) 라인(line_code)별 코스 1개 + 슬롯 5 + 후보 ───────────────────────
        Map<String, List<Map<String, String>>> byLine = new LinkedHashMap<>();
        slots.forEach(s -> byLine.computeIfAbsent(s.get("line_code"), k -> new ArrayList<>()).add(s));

        int courses = 0;
        int candidates = 0;
        for (var e : byLine.entrySet()) {
            String csvRegion = e.getValue().get(0).get("region_code");
            Long regionId = regionByCode.get(dbRegionCode(csvRegion));
            if (regionId == null) {
                report.add("권역 코드 없음: " + csvRegion + " -> " + dbRegionCode(csvRegion));
                continue;
            }
            String courseName = e.getValue().get(0).get("line_name") + " 공양의 길";   // 예: "경기 남부 공양의 길"
            Long courseId = courseMapper.findIdByRegionAndName(regionId, courseName);
            if (courseId == null) {
                // 대표 후보 = 구별 MAIN 첫 행. 5구가 모두 있어야 AdminCourseService.validateSlots 를 통과한다.
                var reps = new ArrayList<AdminCourseSaveRequest.SiteSlot>();
                for (int v = 1; v <= 5; v++) {
                    Long repId = representative(e.getValue(), v, siteIdBySeedKey);
                    if (repId == null) {
                        report.add("대표 후보 없음: " + e.getKey() + " " + v + "구");
                        reps.clear();
                        break;
                    }
                    reps.add(new AdminCourseSaveRequest.SiteSlot(repId, v, v));
                }
                if (reps.isEmpty()) {
                    continue;
                }
                courseId = adminSiteService.createCourseForSeed(
                        new AdminCourseSaveRequest(regionId, courseName, null, null, null, reps));
                courses++;
            }

            Map<Integer, Map<String, Object>> slotByVerse = courseSiteMapper.findSlotIdsByCourse(courseId);
            for (var s : e.getValue()) {
                if (skipped(s.get("note"))) {
                    continue;   // 묘각사. 사찰 단계에서 이미 보고했다
                }
                String candidateKey = seedKey(s.get("region_code"), s.get("site_name"), s.get("disambiguation"));
                Long siteId = siteIdBySeedKey.get(candidateKey);
                if (siteId == null) {
                    report.add("후보 사찰 미등록: " + candidateKey);
                    continue;
                }
                Map<String, Object> slot = slotByVerse.get(Integer.parseInt(s.get("verse_no")));
                if (slot == null) {
                    report.add("슬롯 없음: " + e.getKey() + " " + s.get("verse_no") + "구");
                    continue;
                }
                SlotSite ss = new SlotSite();
                ss.setCourseSiteId(((Number) slot.get("courseSiteId")).longValue());
                ss.setSiteId(siteId);
                ss.setTrack(s.get("track"));
                ss.setSortNo(sortOf(e.getValue(), s));
                ss.setRouteNote(s.get("sunroad_route"));
                ss.setIsStar(SeedCsv.yes(s.get("sunroad_star")));
                ss.setServingNote(s.get("serving_note"));
                // 유니크를 코드에서 먼저 본다 — upsert 는 남의 행을 조용히 고칠 수 있다(챕터 11 항목 14).
                try {
                    slotSiteService.assign(ss);
                    candidates++;
                } catch (BusinessException ex) {
                    report.add("후보 중복(같은 트랙 다른 자리): " + candidateKey);
                }
            }
        }

        // ── 3) Q3 과포화 표시 — 후보 행을 만든 뒤라야 걸린다 ────────────────────
        int congestedRows = 0;
        for (String key : congestedKeys) {
            congestedRows += slotSiteMapper.markCongested(siteIdBySeedKey.get(key));
        }

        log.info("SEED DONE sites={} created={} courses={} candidates={} congestedRows={} noCoord={} report={}",
                siteIdBySeedKey.size(), created, courses, candidates, congestedRows, noCoord.size(), report.size());
        noCoord.forEach(k -> log.warn("SEED no-coord {}", k));
        report.forEach(m -> log.warn("SEED {}", m));
    }

    /* ---------------- 내부 ---------------- */

    /**
     * 카카오 → CSV → 없음. 카카오를 먼저 보는 이유는 CSV 좌표가 "미확인" 인 행이 많아서다.
     * 카테고리에 '사찰' 이 있는 첫 결과를 고른다 — 같은 이름의 식당·정류장이 먼저 나오는 일이 잦다.
     */
    private BigDecimal[] resolveCoord(Map<String, String> r) {
        try {
            String q = (r.get("site_name_ko") + " " + Objects.toString(r.get("disambiguation"), "")).trim();
            var res = kakao.search(q, 1, 3);
            Thread.sleep(KAKAO_INTERVAL_MS);
            for (KakaoPlaceResponse p : res.places()) {
                if (p.categoryName() != null && p.categoryName().contains("사찰") && p.latitude() != null) {
                    return new BigDecimal[]{p.latitude(), p.longitude()};
                }
            }
            if (!res.places().isEmpty() && res.places().get(0).latitude() != null) {
                return new BigDecimal[]{res.places().get(0).latitude(), res.places().get(0).longitude()};
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception ignore) {
            // 키 없음(503)·통신 실패 → CSV 로 물러난다
        }
        if (r.get("latitude") != null && r.get("longitude") != null) {
            return new BigDecimal[]{new BigDecimal(r.get("latitude")), new BigDecimal(r.get("longitude"))};
        }
        return null;
    }

    private void putElement(Long siteId, Long elementId, Map<String, String> r, String col) {
        if (SeedCsv.yes(r.get(col))) {
            siteElementMapper.upsert(siteId, elementId, r.get(col + "_name"), null);
        }
    }

    /** ko description — 교구·비고를 한 줄로. 둘 다 없으면 null 이다(빈 문자열을 넣지 않는다). */
    private static String describe(Map<String, String> r) {
        var sb = new StringBuilder();
        if (r.get("diocese") != null) {
            sb.append(r.get("diocese"));
        }
        if (r.get("note") != null) {
            sb.append(sb.length() > 0 ? " · " : "").append(r.get("note"));
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static String seedKey(String regionCode, String name, String disambiguation) {
        return regionCode + ":" + name + ":" + Objects.toString(disambiguation, "");
    }

    private static String dbRegionCode(String csvRegionCode) {
        return REGION_ALIAS.getOrDefault(csvRegionCode, csvRegionCode);
    }

    private static boolean skipped(String note) {
        return note != null && note.contains(SKIP_MARK);
    }

    private static boolean congested(String note) {
        return note != null && note.contains(CONGESTED_MARK);
    }

    private static boolean excluded(String note) {
        return note != null && note.contains(INACTIVE_MARK);
    }

    /**
     * 화면에 보일 이름. 같은 이름이 둘 이상일 때만 시군구를 괄호로 붙인다 — "용문사(예천)".
     * 구분(disambiguation)이 "예천 소백산" 처럼 [시군구] [산이름] 이라 앞 토막을 쓴다.
     */
    private static String displayName(Map<String, String> r, Map<String, Integer> nameCount) {
        String name = r.get("site_name_ko");
        if (nameCount.getOrDefault(name, 0) < 2) {
            return name;
        }
        String disamb = Objects.toString(r.get("disambiguation"), "").trim();
        String first = disamb.isEmpty() ? "" : disamb.split("\\s+")[0];
        return first.isEmpty() || first.contains("암자") ? name : name + "(" + first + ")";
    }

    /** site_viewpoint.location_desc 는 255자다. 넘치면 잘라 넣는다 — 통째로 버리는 것보다 낫다. */
    private static String trim255(String v) {
        return v.length() <= 255 ? v : v.substring(0, 255);
    }

    /** 그 구의 MAIN 첫 행. 건너뛴 행(묘각사)과 미등록 사찰은 대표가 될 수 없다. */
    private static Long representative(List<Map<String, String>> line, int verseNo, Map<String, Long> siteIdBySeedKey) {
        for (var s : line) {
            if (!"MAIN".equals(s.get("track")) || Integer.parseInt(s.get("verse_no")) != verseNo || skipped(s.get("note"))) {
                continue;
            }
            Long id = siteIdBySeedKey.get(seedKey(s.get("region_code"), s.get("site_name"), s.get("disambiguation")));
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    /** 같은 구·같은 track 안에서 CSV 등장 순. 대표(MAIN 첫 행)가 1 이 된다. */
    private static int sortOf(List<Map<String, String>> line, Map<String, String> row) {
        int n = 0;
        for (var x : line) {
            if (x.get("verse_no").equals(row.get("verse_no")) && x.get("track").equals(row.get("track"))) {
                n++;
                if (x == row) {
                    return n;
                }
            }
        }
        return n;
    }
}
