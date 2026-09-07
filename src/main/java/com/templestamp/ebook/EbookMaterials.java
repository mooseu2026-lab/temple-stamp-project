package com.templestamp.ebook;

import com.templestamp.ebook.dto.EbookCertRow;
import com.templestamp.ebook.dto.EbookStampRow;
import com.templestamp.ebook.dto.EbookThinkboxRow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;

/**
 * 전자책 한 권에 들어갈 재료 한 벌(챕터 9 §2-3).
 * <p>
 * 이 묶음이 존재하는 이유는 <b>같은 재료면 같은 책</b>이라는 규칙 하나 때문이다.
 * 재료를 모으는 곳과 해시를 내는 곳이 갈라져 있으면, 한쪽만 바뀌었을 때
 * "재료는 달라졌는데 해시는 같다" 가 조용히 생긴다. 그래서 모으기와 해싱을 한 자리에 둔다.
 *
 * @param nickname   표지에 찍히는 이름
 * @param stamps     완료된 도장(사찰명·발행일·그날의 확장문구·사진·문장)
 * @param thinkboxes 생각상자 — <b>비공개도 들어간다</b>(개인 소장본이므로)
 * @param certs      VALID 인증서만. 회수본은 싣지 않는다
 * @param medCount   명상 재생 횟수
 * @param medSeconds 명상 총 시간(초)
 */
public record EbookMaterials(
        String nickname,
        List<EbookStampRow> stamps,
        List<EbookThinkboxRow> thinkboxes,
        List<EbookCertRow> certs,
        int medCount,
        long medSeconds
) {

    public int stampCount() {
        return stamps.size();
    }

    public LocalDate firstDate() {
        return stamps.isEmpty() ? null : stamps.get(0).getCompletedAt().toLocalDate();
    }

    public LocalDate lastDate() {
        return stamps.isEmpty() ? null : stamps.get(stamps.size() - 1).getCompletedAt().toLocalDate();
    }

    public long courseCount() {
        return stamps.stream().map(EbookStampRow::getCourseId).distinct().count();
    }

    /**
     * 재료의 <b>정체성</b>만 모아 SHA-256. 본문 글자가 아니라 id·키를 쓰는 이유는,
     * 같은 도장에 붙은 원고가 나중에 퇴역해도 그 도장이 붙든 원고 id 는 그대로이기 때문이다
     * (챕터 8 §3-2). 내용으로 해싱하면 원고 수정 한 번에 모든 사람의 책이 새 책이 된다.
     * <p>
     * 명상은 횟수·시간까지 넣는다 — id 가 없어 그것 말고는 변화를 잡을 길이 없다.
     */
    public String snapshotHash() {
        StringBuilder sb = new StringBuilder();
        stamps.forEach(s -> sb.append("S").append(s.getStampId())
                .append(':').append(nullSafe(s.getPhotoKey()))
                .append(':').append(s.getExtManuscriptId() == null ? "-" : s.getExtManuscriptId())
                .append(':').append(nullSafe(s.getUserSentence()).length())
                .append('\n'));
        thinkboxes.forEach(t -> sb.append("T").append(t.getThinkboxId())
                .append(':').append(t.isEdited() ? 1 : 0).append('\n'));
        certs.forEach(c -> sb.append("C").append(c.getSerialNo()).append('\n'));
        sb.append("M").append(medCount).append(':').append(medSeconds).append('\n');

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("스냅샷 해시를 만들지 못했다", e);
        }
    }

    private static String nullSafe(String v) {
        return v == null ? "" : v;
    }
}
