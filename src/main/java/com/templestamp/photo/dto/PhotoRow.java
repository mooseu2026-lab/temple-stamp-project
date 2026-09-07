// src/main/java/com/templestamp/photo/dto/PhotoRow.java
package com.templestamp.photo.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** photo JOIN site(+i18n) LEFT JOIN thinkbox(DIRECT). PhotoMapper.findRow 의 resultType */
@Getter @Setter @NoArgsConstructor
public class PhotoRow {
    private Long siteId;
    private String siteName;
    private String photoKey;
    private String sentence;          // 생각상자 DIRECT 본문. 없으면 null
    private Boolean hasOtherFace;
    private Boolean isPrivate;
    private LocalDateTime updatedAt;
}
