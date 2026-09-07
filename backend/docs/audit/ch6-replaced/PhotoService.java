package com.templestamp.photo;

import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.global.type.UploadPurpose;
import com.templestamp.upload.ObjectStorageClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 사진 메타 등록. 별도 요청 DTO 없이 MissionSubmitRequest.photoKey 로만 접수한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PhotoService {

    private final PhotoMapper photoMapper;
    private final ObjectStorageClient storageClient;

    /**
     * presign 으로 발급한 키인지, 그 사용자 것인지 확인하고 메타를 남긴다.
     * 같은 사찰에 다시 올리면 기존 행의 키를 교체한다(회원당 사찰당 한 장).
     */
    @Transactional
    public Photo register(Long userId, Long siteId, String fileKey, boolean hasOtherFace) {
        storageClient.verifyOwnedKey(fileKey, UploadPurpose.PHOTO, userId);

        Photo photo = Photo.builder()
                .userId(userId)
                .siteId(siteId)
                .fileKey(fileKey)
                .hasOtherFace(hasOtherFace)
                .isPrivate(false)
                .build();
        photoMapper.upsert(photo);

        return photoMapper.findByUserAndSite(userId, siteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_5000));
    }

    public List<Photo> getMyPhotos(Long userId) {
        return photoMapper.findByUserId(userId);
    }

    public String urlOf(Photo photo) {
        return photo == null ? null : storageClient.presignGet(photo.getFileKey());
    }
}
