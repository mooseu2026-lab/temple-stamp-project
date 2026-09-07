package com.templestamp.verse;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface GwanVerseMapper {

    Optional<GwanVerse> findByVerseNo(@Param("verseNo") Integer verseNo);

    List<GwanVerse> findAll();
}
