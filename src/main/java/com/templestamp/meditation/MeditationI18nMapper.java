package com.templestamp.meditation;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface MeditationI18nMapper {

    Optional<MeditationI18n> findByMeditationIdAndLocale(@Param("meditationId") Long meditationId,
                                                         @Param("locale") String locale);
}
