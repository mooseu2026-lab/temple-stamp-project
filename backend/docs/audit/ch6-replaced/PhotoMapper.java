package com.templestamp.photo;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface PhotoMapper {

    /** 회원×사찰 한 장. 이미 있으면 파일 키를 갈아 끼운다. */
    int upsert(Photo photo);

    Optional<Photo> findByUserAndSite(@Param("userId") Long userId, @Param("siteId") Long siteId);

    List<Photo> findByUserId(@Param("userId") Long userId);

    int updatePrivacy(@Param("photoId") Long photoId, @Param("isPrivate") boolean isPrivate);
}
