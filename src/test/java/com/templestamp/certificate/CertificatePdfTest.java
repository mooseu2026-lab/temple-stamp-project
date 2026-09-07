package com.templestamp.certificate;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.templestamp.certificate.dto.CertificateResponse;
import com.templestamp.certificate.dto.CertificateRow;
import com.templestamp.global.config.EbookProperties;
import com.templestamp.global.error.BusinessException;
import com.templestamp.global.error.ErrorCode;
import com.templestamp.upload.ObjectStorageClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 인증서 PDF(챕터 9 §3).
 * <p>
 * 이 문서에서 가장 중요한 것은 <b>QR 이 가리키는 곳</b>이다. 파일은 한 번 내려받으면
 * 누군가의 컴퓨터에 영원히 남고, 회수해도 그 파일이 사라지지는 않는다.
 * 그래서 <b>파일이 정본이 아니라 QR 이 가리키는 진위 확인이 정본</b>이다(함정 8).
 * 그 링크가 실제로 읽히는 QR 인지, 주소가 맞는지를 디코드해서 확인한다.
 */
@SpringBootTest
class CertificatePdfTest {

    private static final long COURSE_ID = 1L;

    @Autowired CertificateService certificateService;
    @Autowired CertificateMapper certificateMapper;
    @Autowired CertificatePdfBuilder pdfBuilder;
    @Autowired ObjectStorageClient storageClient;
    @Autowired EbookProperties ebookProperties;
    @Autowired JdbcTemplate jdbc;

    private long userId;
    private long pilgrimageId;
    private long certificateId;
    private String serialNo;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO users (email, password, nickname, role, tier, locale) "
                + "VALUES ('cert-pdf@test.com', 'x', '증서시험', 'USER', 'AGE40', 'ko')");
        userId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("INSERT INTO pilgrimage (user_id, course_id, status, completed_at) "
                + "VALUES (?, ?, 'COMPLETED', NOW())", userId, COURSE_ID);
        pilgrimageId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        serialNo = "PG-2026-99%04d".formatted((int) (userId % 10000));
        jdbc.update("INSERT INTO certificate (user_id, pilgrimage_id, cert_type, status, serial_no, issued_at) "
                + "VALUES (?, ?, 'PILGRIMAGE', 'VALID', ?, NOW())", userId, pilgrimageId, serialNo);
        certificateId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @AfterEach
    void tearDown() {
        try { storageClient.delete("CERT/%d/%d.pdf".formatted(userId, certificateId)); } catch (Exception ignored) { }
        jdbc.update("DELETE FROM certificate WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM pilgrimage WHERE pilgrimage_id = ?", pilgrimageId);
        jdbc.update("DELETE FROM users WHERE user_id = ?", userId);
    }

    @Test
    @DisplayName("① 첫 조회에 PDF 가 만들어지고 키가 적힌다 — 발행 때 미리 만들지 않는다")
    void createdOnFirstRead() {
        assertThat(certificateMapper.findById(certificateId).orElseThrow().getFileKey())
                .as("발행 직후에는 파일이 없다").isNull();

        CertificateResponse res = certificateService.getOne(userId, certificateId);
        assertThat(res.downloadUrl()).isNotBlank();

        String key = certificateMapper.findById(certificateId).orElseThrow().getFileKey();
        assertThat(key).isEqualTo("CERT/%d/%d.pdf".formatted(userId, certificateId));
        assertThat(storageClient.exists(key)).isTrue();
    }

    @Test
    @DisplayName("② 두 번째 조회는 있던 파일을 쓴다 — 키가 바뀌지 않는다")
    void reusedOnSecondRead() {
        certificateService.getOne(userId, certificateId);
        String first = certificateMapper.findById(certificateId).orElseThrow().getFileKey();

        certificateService.getOne(userId, certificateId);
        String second = certificateMapper.findById(certificateId).orElseThrow().getFileKey();
        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("③ 회수하면 링크가 끊긴다 — 그러나 파일은 지우지 않는다")
    void revokedHasNoLinkButKeepsFile() {
        certificateService.getOne(userId, certificateId);
        String key = certificateMapper.findById(certificateId).orElseThrow().getFileKey();

        jdbc.update("UPDATE certificate SET status = 'REVOKED', revoked_at = NOW(), revoke_reason = 'ADMIN' "
                + "WHERE certificate_id = ?", certificateId);

        CertificateResponse res = certificateService.getOne(userId, certificateId);
        assertThat(res.status()).isEqualTo("REVOKED");
        assertThat(res.downloadUrl()).as("링크는 끊는다").isNull();
        assertThat(storageClient.exists(key))
                .as("파일은 남긴다 — 이미 나간 파일은 어차피 못 거둔다. 진위 확인이 REVOKED 를 답한다")
                .isTrue();
    }

    @Test
    @DisplayName("④ 남의 인증서는 '없다' 고 답한다 — 번호를 넣어 보며 찾는 길을 막는다")
    void othersLookMissing() {
        assertThatThrownBy(() -> certificateService.getOne(userId + 999_999, certificateId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.CERT_4041);
    }

    @Test
    @DisplayName("⑤ QR 을 디코드하면 진위 확인 주소가 나온다 — 파일이 아니라 이 주소가 정본이다")
    void qrDecodesToVerifyUrl() throws Exception {
        // 만들어 넣는 그 바이트를 그대로 디코드한다. "그렸다" 가 아니라 "읽힌다" 를 봐야 한다 —
        // 크기를 잘못 주거나 여백이 없으면 그림은 멀쩡한데 스캐너가 못 읽는다.
        // (PDF 안에서 이미지를 도로 꺼내는 길은 OpenPDF 파서에 없어, 넣기 직전의 바이트를 본다.)
        byte[] png = pdfBuilder.qrPng(expectedVerifyUrl());
        BufferedImage qr = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(qr).isNotNull();

        String decoded = new MultiFormatReader().decode(
                new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(qr)))).getText();
        assertThat(decoded).isEqualTo(expectedVerifyUrl());
        assertThat(decoded).contains(serialNo);
        assertThat(decoded).as("앱 밖의 사람이 찍는 것이라 절대 주소여야 한다").startsWith("http");
    }

    @Test
    @DisplayName("⑤-b 그 QR 이 실제로 PDF 안에 그림으로 들어간다")
    void qrIsEmbedded() throws Exception {
        CertificateRow row = certificateMapper.findRowById(certificateId).orElseThrow();
        byte[] pdf = pdfBuilder.build(row, "증서시험", expectedVerifyUrl());

        PdfReader reader = new PdfReader(pdf);
        int images = 0;
        for (int i = 0; i < reader.getXrefSize(); i++) {
            com.lowagie.text.pdf.PdfObject obj = reader.getPdfObject(i);
            if (obj != null && obj.isStream()
                    && com.lowagie.text.pdf.PdfName.IMAGE.equals(
                            ((com.lowagie.text.pdf.PRStream) obj).get(com.lowagie.text.pdf.PdfName.SUBTYPE))) {
                images++;
            }
        }
        reader.close();
        assertThat(images).as("증서에는 그림이 QR 하나뿐이다").isEqualTo(1);
    }

    @Test
    @DisplayName("⑥ 증서에는 이름이 통째로 찍힌다 — 본인이 받는 파일이라 가리지 않는다")
    void holderNameIsNotMasked() throws Exception {
        CertificateRow row = certificateMapper.findRowById(certificateId).orElseThrow();
        byte[] pdf = pdfBuilder.build(row, "증서시험", expectedVerifyUrl());

        PdfReader reader = new PdfReader(pdf);
        String text = new PdfTextExtractor(reader).getTextFromPage(1);
        reader.close();

        assertThat(text).contains("순례 완주 인증서");
        assertThat(text).contains(serialNo);
        assertThat(text).as("마스킹 없이").contains("증서시험");
        assertThat(text).contains("진위 확인은 QR 또는 번호로");
    }

    @Test
    @DisplayName("⑦ 회향 인증서는 문구만 다르다 — 판형을 나누면 고칠 곳이 두 배가 된다")
    void hoehyangTemplate() throws Exception {
        jdbc.update("UPDATE certificate SET cert_type = 'HOEHYANG' WHERE certificate_id = ?", certificateId);
        CertificateRow row = certificateMapper.findRowById(certificateId).orElseThrow();
        byte[] pdf = pdfBuilder.build(row, "증서시험", expectedVerifyUrl());

        PdfReader reader = new PdfReader(pdf);
        String text = new PdfTextExtractor(reader).getTextFromPage(1);
        reader.close();

        assertThat(text).contains("회향 인증서");
        assertThat(text).contains("열두 길을 모두 걸어 마쳤음");
        assertThat(text).doesNotContain("순례 완주 인증서");
    }

    /* ---------------- 도구 ---------------- */

    private String expectedVerifyUrl() {
        String base = ebookProperties.publicBaseUrl();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base)
                + "/api/certificates/verify/" + serialNo;
    }

}
