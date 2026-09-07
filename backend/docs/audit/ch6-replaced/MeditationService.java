package com.templestamp.meditation;

import com.templestamp.global.web.LocaleUtil;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.meditation.dto.MeditationDetailResponse;
import com.templestamp.meditation.dto.MeditationListResponse;
import com.templestamp.meditation.dto.MeditationLogRequest;
import com.templestamp.meditation.dto.MeditationLogResponse;
import com.templestamp.meditation.dto.MeditationSummaryResponse;
import com.templestamp.upload.ObjectStorageClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 명상 108선. 목록·상세는 로그인 없이 볼 수 있고, 재생 기록만 로그인이 필요하다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeditationService {

    private final MeditationMapper meditationMapper;
    private final MeditationI18nMapper i18nMapper;
    private final MeditationLogMapper logMapper;
    private final ObjectStorageClient storageClient;

    public MeditationListResponse getList(String category) {
        List<MeditationSummaryResponse> items = meditationMapper.findAllActive(category).stream()
                .map(m -> new MeditationSummaryResponse(
                        m.getMeditationId(), m.getTitle(),
                        String.valueOf(m.getCategoryNo()), m.getDurationSec()))
                .toList();

        List<MeditationListResponse.CategoryCount> counts = meditationMapper.countByCategory().stream()
                .map(row -> new MeditationListResponse.CategoryCount(
                        String.valueOf(row.getCategoryNo()), row.getCount()))
                .toList();

        return new MeditationListResponse(items, counts);
    }

    /**
     * 요청 언어 번역이 없으면 ko 로, 그것도 없으면 원문으로 떨어뜨린다.
     * audioUrl 은 저장소 키를 임시 URL 로 바꾼 값이며 키가 없으면 null 이다.
     */
    public MeditationDetailResponse getDetail(Long meditationId, String acceptLanguage) {
        Meditation meditation = meditationMapper.findById(meditationId)
                .filter(Meditation::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDITATION_4040));

        String locale = LocaleUtil.resolve(acceptLanguage);
        MeditationI18n i18n = i18nMapper.findByMeditationIdAndLocale(meditationId, locale)
                .or(() -> i18nMapper.findByMeditationIdAndLocale(meditationId, LocaleUtil.DEFAULT_LOCALE))
                .orElse(null);

        String title = i18n != null ? i18n.getTitle() : meditation.getTitle();
        String script = i18n != null ? i18n.getScript() : meditation.getScript();
        String audioKey = i18n != null && i18n.getAudioKey() != null
                ? i18n.getAudioKey()
                : meditation.getAudioKey();

        return new MeditationDetailResponse(
                meditation.getMeditationId(),
                title,
                String.valueOf(meditation.getCategoryNo()),
                script,
                audioKey == null ? null : storageClient.presignGet(audioKey),
                meditation.getDurationSec());
    }

    @Transactional
    public MeditationLogResponse log(Long userId, MeditationLogRequest request) {
        Meditation meditation = meditationMapper.findById(request.meditationId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDITATION_4040));

        MeditationLog log = MeditationLog.builder()
                .userId(userId)
                .meditationId(request.meditationId())
                .playedSec(request.playedSeconds())
                .memo(request.memo())
                .createdAt(LocalDateTime.now())
                .build();
        logMapper.save(log);

        return new MeditationLogResponse(
                log.getMeditationLogId(), meditation.getMeditationId(), meditation.getTitle(),
                log.getPlayedSec(), log.getMemo(), log.getCreatedAt());
    }
}
