package com.templestamp.pilgrimage;

import com.templestamp.course.CourseService;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.pilgrimage.dto.PilgrimageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PilgrimageService {

    private final PilgrimageMapper pilgrimageMapper;
    private final CourseService courseService;

    /**
     * 순례 시작. 같은 코스를 이미 시작했으면 그 기록을 그대로 돌려준다.
     * 코스 탭을 연타해도, 앱을 지웠다 깔아도 진행이 이어지도록 시작을 멱등하게 둔다.
     */
    @Transactional
    public Pilgrimage ensureStarted(Long userId, Long courseId) {
        return pilgrimageMapper.findByUserAndCourse(userId, courseId)
                .orElseGet(() -> create(userId, courseId));
    }

    /**
     * 신규 생성이면 created=true, 기존 재사용이면 false. 둘 다 200 이다 —
     * 연타가 오류인 상황이 아니어서 409 로 만들 실익이 없다.
     */
    @Transactional
    public PilgrimageResponse start(Long userId, Long courseId) {
        courseService.getCourse(courseId);

        boolean existed = pilgrimageMapper.findByUserAndCourse(userId, courseId).isPresent();
        Pilgrimage pilgrimage = ensureStarted(userId, courseId);

        return new PilgrimageResponse(
                pilgrimage.getPilgrimageId(),
                pilgrimage.getCourseId(),
                pilgrimage.getStatus(),
                pilgrimage.getStartedAt(),
                !existed);
    }

    private Pilgrimage create(Long userId, Long courseId) {
        Pilgrimage pilgrimage = Pilgrimage.builder()
                .userId(userId)
                .courseId(courseId)
                .status(Pilgrimage.IN_PROGRESS)
                .build();
        try {
            pilgrimageMapper.save(pilgrimage);
            return pilgrimageMapper.findById(pilgrimage.getPilgrimageId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PILGRIMAGE_4040));
        } catch (DuplicateKeyException e) {
            // 같은 사용자가 동시에 두 번 눌렀을 때. 먼저 들어간 행을 쓴다.
            return pilgrimageMapper.findByUserAndCourse(userId, courseId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PILGRIMAGE_4040));
        }
    }

    public List<PilgrimageResponse> getMine(Long userId) {
        return pilgrimageMapper.findByUserId(userId).stream()
                .map(p -> new PilgrimageResponse(
                        p.getPilgrimageId(), p.getCourseId(), p.getStatus(), p.getStartedAt(), false))
                .toList();
    }

    public Pilgrimage getById(Long pilgrimageId) {
        return pilgrimageMapper.findById(pilgrimageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PILGRIMAGE_4040));
    }

    public Pilgrimage getOwned(Long userId, Long pilgrimageId) {
        Pilgrimage pilgrimage = getById(pilgrimageId);
        if (!pilgrimage.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PILGRIMAGE_4030);
        }
        return pilgrimage;
    }
}
