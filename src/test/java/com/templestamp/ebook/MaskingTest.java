package com.templestamp.ebook;

import com.templestamp.certificate.dto.CertificateVerifyResponse;
import com.templestamp.ebook.dto.PrintOrderResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 마스킹은 한 번 잘못 나가면 되돌릴 수 없는 종류의 버그라 값별로 못 박아 둔다.
 */
class MaskingTest {

    @Nested
    @DisplayName("인쇄 신청(사용자용)")
    class PrintOrder {

        /**
         * 챕터 9 에서 마스킹을 <b>없앤 것이 아니라 마스킹할 대상이 사라졌다.</b>
         * 배송정보가 print_order_address 로 떨어져 나가면서, 목록 질의는 주소를 읽지도 않는다 —
         * 가려서 내보내는 것보다 <b>가져오지 않는 것</b>이 강한 보장이다.
         * 가리는 방식은 필드가 하나 늘 때 가리는 것을 잊으면 그대로 새어 나간다.
         */
        @Test
        @DisplayName("목록 응답에는 주소 계열 칸이 아예 없다 — 가리는 것이 아니라 없다")
        void listHasNoAddressComponent() {
            var names = java.util.Arrays.stream(PrintOrderResponse.class.getRecordComponents())
                    .map(java.lang.reflect.RecordComponent::getName)
                    .toList();
            assertThat(names).doesNotContain(
                    "recipientName", "recipientPhone", "postalCode", "address", "addressDetail", "shipping");
            assertThat(names).contains("printOrderId", "status", "quantity");
        }

        @Test
        @DisplayName("단건 조회에는 배송정보가 있다 — 본인이 보는 화면이라 가리지 않는다")
        void detailKeepsShipping() {
            var names = java.util.Arrays.stream(
                            com.templestamp.ebook.dto.PrintOrderDetailResponse.class.getRecordComponents())
                    .map(java.lang.reflect.RecordComponent::getName)
                    .toList();
            assertThat(names).contains("shipping");
        }
    }

    @Nested
    @DisplayName("인증서 공개 검증")
    class Certificate {

        @Test
        @DisplayName("별표는 닉네임 길이와 무관하게 항상 1개다 — 길이가 새어 나가지 않게")
        void nickname() {
            assertThat(CertificateVerifyResponse.maskNickname("순례자")).isEqualTo("순*자");
            assertThat(CertificateVerifyResponse.maskNickname("아주긴닉네임입니다")).isEqualTo("아*다");
            assertThat(CertificateVerifyResponse.maskNickname("민수")).isEqualTo("민*");
            assertThat(CertificateVerifyResponse.maskNickname("김")).isEqualTo("*");
            assertThat(CertificateVerifyResponse.maskNickname("")).isEqualTo("*");
            assertThat(CertificateVerifyResponse.maskNickname(null)).isEqualTo("*");
        }
    }
}
