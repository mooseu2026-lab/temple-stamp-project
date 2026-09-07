// src/main/java/com/templestamp/content/seed/SiteSeedRow.java
package com.templestamp.content.seed;

/**
 * {@code sites-master.csv} 한 줄. 비어 있거나 {@code ?}·"미확인" 인 칸은 {@link SeedCsv#clean} 이 null 로 만든다.
 * <p>
 * 시더는 Map 을 직접 다루지만, 이 record 가 있어야 "CSV 열이 무엇무엇인지" 가 코드 한 곳에 남는다.
 * 열 이름이 바뀌면 여기와 {@link SiteSeedImporter} 를 함께 고친다.
 */
public record SiteSeedRow(String regionCode, String nameKo, String disambiguation, String nameEn, String roadAddress,
                          Double latitude, Double longitude, String diocese, String researchStatus,
                          String iljumun, String iljumunName, String geumgangmun, String geumgangmunName,
                          String cheonwangmun, String cheonwangmunName, String bulimun, String bulimunName,
                          String pagoda, String pagodaName, String mainHallName, String annexHalls,
                          String parkingInfo, String accessInfo, String mealAvailable, String flowerBadge,
                          String sources, String note) {

    /** 재실행해도 같은 사찰을 다시 만들지 않게 하는 열쇠. 동명이찰은 disambiguation 으로 갈린다. */
    public String seedKey() {
        return regionCode + ":" + nameKo + ":" + (disambiguation == null ? "" : disambiguation);
    }
}
