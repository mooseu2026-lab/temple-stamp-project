package com.templestamp.course;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 권역. 전국 9권역으로 코스를 묶는다. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Region {

    private Long regionId;
    private String code;
    private String name;
    private Integer sortNo;
    private LocalDateTime createdAt;
}
