package com.templestamp.photo;

import com.templestamp.course.CourseSiteMapper;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.global.error.ErrorResponse;
import com.templestamp.global.type.UploadPurpose;
import com.templestamp.photo.dto.PhotoResponse;
import com.templestamp.photo.dto.PhotoSaveRequest;
import com.templestamp.site.Site;
import com.templestamp.site.SiteMapper;
import com.templestamp.thinkbox.ThinkboxService;
import com.templestamp.upload.ObjectStorageClient;
import com.templestamp.upload.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 사찰당 사진 1장 + 문장 1개(개인 소장). <b>도장 여부를 묻지 않는다</b> — 도장을 못 받은 절도 남길 수 있다.
 * <p>
 * 사진은 {@code photo} 표에, 문장은 생각상자(DIRECT)에 들어가고 <b>둘은 한 트랜잭션</b>이다.
 * 하나가 실패하면 둘 다 없던 일이 된다 — 사진만 있고 이유가 없는 행이 남지 않게.
 * <p>
 * 도장의 사진({@code stamp.photo_key})과 이 표의 사진은 별개다. 도장 사진은 다짐 제출 때 붙는 것이고
 * 이 표는 개인 소장이다. 전자책은 둘 다 싣고 비공개·타인 얼굴은 뺀다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PhotoService {

    private final PhotoMapper photoMapper;
    private final SiteMapper siteMapper;
    private final CourseSiteMapper courseSiteMapper;
    private final ThinkboxService thinkboxService;
    private final ObjectStorageClient storageClient;

    /* ---------------- 개인 소장 (챕터 6) ---------------- */

    @Transactional
    public PhotoResponse save(Long userId, Long siteId, PhotoSaveRequest request) {
        // DTO 의 @Pattern 이 형식을 보고, 여기서 "내 키인가" 를 본다. 둘 다 있어야 남의 파일을 붙일 수 없다.
        if (!UploadService.ownsKey(userId, UploadPurpose.PHOTO, request.photoKey())) {
            throw new BusinessException(ErrorCode.COMMON_4000,
                    List.of(new ErrorResponse.FieldError("photoKey", "본인이 발급받은 사진 키가 아닙니다.")));
        }
        Site site = siteMapper.findActiveById(siteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SITE_4040));

        photoMapper.upsert(Photo.builder()
                .userId(userId).siteId(siteId).fileKey(request.photoKey())
                .hasOtherFace(request.hasOtherFace()).isPrivate(request.isPrivate())
                .build());

        // 사찰이 어느 코스의 자리인지 — 없을 수도 있다(후보에만 있는 절). 그때는 코스 없이 남긴다.
        Long courseId = courseSiteMapper.findCourseIdBySite(siteId).orElse(null);
        thinkboxService.upsertDirectForSite(userId, siteId, courseId, request.sentence(), request.isPrivate());

        return get(userId, siteId);
    }

    public PhotoResponse get(Long userId, Long siteId) {
        return photoMapper.findRow(userId, siteId)
                .map(PhotoResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_4040));
    }

    public List<PhotoResponse> listMine(Long userId) {
        return photoMapper.findAllRows(userId).stream().map(PhotoResponse::from).toList();
    }

    /* ---------------- 도장 경로 (챕터 5) ---------------- */

    /**
     * 다짐 제출에 사진이 딸려 왔을 때 메타만 남긴다. 여기는 문장을 만들지 않는다 —
     * 도장의 문장은 {@code stamp.user_sentence} 이고, 그 사본은 생각상자 MISSION 이 따로 갖는다.
     */
    @Transactional
    public Photo register(Long userId, Long siteId, String fileKey, boolean hasOtherFace) {
        storageClient.verifyOwnedKey(fileKey, UploadPurpose.PHOTO, userId);

        photoMapper.upsert(Photo.builder()
                .userId(userId).siteId(siteId).fileKey(fileKey)
                .hasOtherFace(hasOtherFace).isPrivate(false)
                .build());

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
