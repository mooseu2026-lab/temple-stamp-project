package com.templestamp.meditation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeditationI18n {

    private Long meditationI18nId;
    private Long meditationId;
    private String locale;
    private String title;
    private String script;
    private String audioKey;
}
