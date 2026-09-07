package com.templestamp.course;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 순례 코스. 사찰 5곳으로 이루어진다. ACTIVE 인 코스만 사용자에게 보인다. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Course {

    public static final String DRAFT = "DRAFT";
    public static final String ACTIVE = "ACTIVE";
    public static final String INACTIVE = "INACTIVE";

    private Long courseId;
    private Long regionId;
    private String name;
    private String description;
    private String status;
    private Integer sortNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isActive() {
        return ACTIVE.equals(status);
    }
}
