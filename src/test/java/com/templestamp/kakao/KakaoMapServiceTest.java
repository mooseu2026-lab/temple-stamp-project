package com.templestamp.kakao;

import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.kakao.dto.KakaoPlaceSearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 카카오 호출은 실제 네트워크를 타지 않는다. MockRestServiceServer 가 RestClient 뒤에서 응답을 흉내 낸다.
 * <p>
 * 여기서 지키려는 것 두 가지 —
 * 키가 없을 때 우리가 먼저 끊는가(카카오에 요청조차 보내지 않아야 한다), 그리고
 * 카카오의 x·y(문자열, x가 경도)를 우리 규격 latitude·longitude(BigDecimal)로 <b>뒤집지 않고</b> 옮기는가.
 * 이 뒤집힘은 지도에 찍어 보기 전에는 드러나지 않는다.
 */
class KakaoMapServiceTest {

    private static final String BASE_URL = "https://dapi.kakao.com";

    /** 통도사 실제 좌표대에 가까운 값. y=위도(35.4x), x=경도(129.0x) */
    private static final String BODY = """
            {
              "meta": { "total_count": 2, "pageable_count": 2, "is_end": true },
              "documents": [
                {
                  "id": "1",
                  "place_name": "통도사",
                  "category_name": "종교,불교 > 절",
                  "phone": "055-382-7182",
                  "address_name": "경남 양산시 하북면 지산리 583",
                  "road_address_name": "경남 양산시 하북면 통도사로 108",
                  "x": "129.0655000",
                  "y": "35.4879000",
                  "place_url": "http://place.map.kakao.com/1"
                }
              ]
            }
            """;

    @Test
    @DisplayName("키가 없으면 카카오를 부르지 않고 503 KAKAO-5030 으로 끊는다")
    void no_key_short_circuits_before_calling_kakao() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        // 기대 요청을 하나도 등록하지 않는다 — 호출이 나가면 verify 에서 실패한다.

        KakaoMapService service = new KakaoMapService(
                new KakaoProperties("   ", BASE_URL, 3000), builder.build());

        assertThatThrownBy(() -> service.search("통도사", 1, 15))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.KAKAO_5030);

        server.verify();   // 카카오로 나간 요청이 없어야 한다
    }

    @Test
    @DisplayName("카카오의 x·y 를 경도·위도로 바르게 옮긴다 (x=경도, y=위도)")
    void maps_x_to_longitude_and_y_to_latitude() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                        BASE_URL + "/v2/local/search/keyword.json")))
                // "통도사" 를 UTF-8 로 퍼센트 인코딩한 값. 한글이 다른 코드페이지로 바뀌면 여기서 잡힌다.
                .andExpect(queryParam("query", "%ED%86%B5%EB%8F%84%EC%82%AC"))
                .andExpect(queryParam("page", "1"))
                .andExpect(queryParam("size", "15"))
                .andExpect(header("Authorization", "KakaoAK test-key"))
                .andRespond(withSuccess(BODY, MediaType.APPLICATION_JSON));

        KakaoMapService service = new KakaoMapService(
                new KakaoProperties("test-key", BASE_URL, 3000),
                builder.defaultHeader("Authorization", "KakaoAK test-key").build());

        KakaoPlaceSearchResult result = service.search("통도사", 1, 15);

        assertThat(result.places()).hasSize(1);
        var place = result.places().get(0);
        assertThat(place.placeName()).isEqualTo("통도사");
        // ★ 뒤집히면 여기서 잡힌다. y(35.48) 가 위도, x(129.06) 가 경도다.
        assertThat(place.latitude()).isEqualByComparingTo(new BigDecimal("35.4879000"));
        assertThat(place.longitude()).isEqualByComparingTo(new BigDecimal("129.0655000"));
        assertThat(place.latitude()).isBetween(new BigDecimal("35.4"), new BigDecimal("35.5"));

        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(result.end()).isTrue();

        server.verify();
    }

    @Test
    @DisplayName("카카오가 5xx 를 주면 503 KAKAO-5031 로 바꿔 내보낸다")
    void kakao_failure_becomes_5031() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                BASE_URL + "/v2/local/search/keyword.json"))).andRespond(withServerError());

        KakaoMapService service = new KakaoMapService(
                new KakaoProperties("test-key", BASE_URL, 3000), builder.build());

        assertThatThrownBy(() -> service.search("통도사", 1, 15))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.KAKAO_5031);
    }
}
