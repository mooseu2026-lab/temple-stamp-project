// src/main/java/com/templestamp/content/seed/SlotSeedRow.java
package com.templestamp.content.seed;

/** {@code slot-candidates.csv} 한 줄. 한 라인(line_code)의 한 구(verse_no)에 들어갈 후보 사찰 하나. */
public record SlotSeedRow(String regionCode, String regionName, String lineCode, String lineName,
                          int verseNo, String siteName, String disambiguation,
                          String track, String servingNote, String sunroadRoute, boolean star, String note) {

    /** {@link SiteSeedRow#seedKey()} 와 같은 규칙이어야 후보가 사찰에 붙는다. */
    public String siteSeedKey() {
        return regionCode + ":" + siteName + ":" + (disambiguation == null ? "" : disambiguation);
    }
}
