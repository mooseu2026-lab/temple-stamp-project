package com.templestamp.user;

import com.templestamp.user.dto.StorageOrphanRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 지워야 할 저장소 키 큐. 탈퇴·사진 교체·전자책 정리가 적재하고 <b>챕터 9 청소기가 소비</b>한다.
 * <p>
 * 파일 삭제를 그 자리에서 하지 않는 이유가 하나다 — 저장소 호출이 실패하면 그 트랜잭션 전체가
 * 롤백된다. 사용자가 "지워 달라" 고 했는데 저장소가 잠깐 아프다는 이유로 아무 일도
 * 일어나지 않는 것이 가장 나쁘다. 그래서 DB 에는 지금 적고, 파일은 나중에 지운다.
 */
@Mapper
public interface StorageOrphanMapper {

    int enqueue(@Param("fileKey") String fileKey, @Param("reason") String reason);

    /** 채점·테스트용. 아직 지워지지 않은 키 수. */
    int countPending(@Param("reason") String reason);

    /* ---------------- 챕터 9 청소기 ---------------- */

    /** 오래된 것부터. FAILED 로 접힌 것은 다시 집지 않는다 — 사람이 볼 몫이다. */
    List<StorageOrphanRow> findPending(@Param("limit") int limit);

    int markDeleted(@Param("id") Long id);

    /**
     * 실패 한 번을 적는다. {@code maxRetry} 를 넘기면 FAILED 로 접고 {@code needs_review} 를 켠다 —
     * 못 지우는 키가 5분마다 영원히 도는 것을 막는다.
     */
    int markRetry(@Param("id") Long id,
                  @Param("maxRetry") int maxRetry,
                  @Param("lastError") String lastError);
}
