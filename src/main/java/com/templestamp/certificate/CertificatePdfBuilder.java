package com.templestamp.certificate;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import com.templestamp.certificate.dto.CertificateRow;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;

/**
 * 인증서 한 장을 PDF 로(챕터 9 §3).
 * <p>
 * 이 문서에는 <b>이름이 전부 들어간다</b> — 본인만 받을 수 있는 파일이기 때문이다.
 * 공개 진위 확인(<code>/verify</code>)의 마스킹과는 목적이 다르다. 그쪽은 제3자가 보는 화면이고
 * 이쪽은 본인이 내려받는 증서다. 둘을 같은 규칙으로 묶으면 어느 한쪽이 반드시 어색해진다.
 * <p>
 * QR 은 진위 확인 주소를 담는다. <b>파일이 정본이 아니라 QR 이 가리키는 곳이 정본이다</b> —
 * 회수된 뒤에도 옛 파일은 누군가의 컴퓨터에 남지만, QR 을 찍으면 REVOKED 가 나온다(함정 8).
 */
@Component
@RequiredArgsConstructor
public class CertificatePdfBuilder {

    private static final String FONT_PATH = "fonts/NotoSansKR-Regular.ttf";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy년 M월 d일");
    private static final int QR_PX = 220;

    public byte[] build(CertificateRow row, String holderName, String verifyUrl) {
        Document doc = new Document(PageSize.A4, 60, 60, 60, 60);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter.getInstance(doc, out);
        doc.open();

        BaseFont base = loadKoreanFont();
        Font serial = new Font(base, 28, Font.BOLD);
        Font title = new Font(base, 20, Font.BOLD);
        Font body = new Font(base, 12);
        Font small = new Font(base, 9, Font.NORMAL, new java.awt.Color(110, 110, 110));

        boolean hoehyang = Certificate.HOEHYANG.equals(row.getCertType());

        doc.add(gap(60));
        // 템플릿 두 종 — 문구만 다르다. 판형·배치를 나누면 고칠 곳이 두 배가 된다.
        doc.add(center(hoehyang ? "회향 인증서" : "순례 완주 인증서", title));
        doc.add(gap(30));
        doc.add(center(row.getSerialNo(), serial));
        doc.add(gap(30));
        doc.add(center(holderName + " 님", body));
        if (row.getCourseName() != null) {
            doc.add(center(row.getCourseName(), body));
        }
        doc.add(center(row.getIssuedAt().toLocalDate().format(DATE), body));
        doc.add(gap(24));
        doc.add(center(hoehyang
                ? "열두 길을 모두 걸어 마쳤음을 확인합니다."
                : "이 코스의 다섯 자리를 모두 채웠음을 확인합니다.", body));
        doc.add(gap(30));

        try {
            Image qr = Image.getInstance(qrPng(verifyUrl));
            qr.setAlignment(Element.ALIGN_CENTER);
            qr.scaleToFit(140, 140);
            doc.add(qr);
        } catch (Exception e) {
            throw new IllegalStateException("인증서 QR 을 만들지 못했다", e);
        }
        doc.add(gap(8));
        doc.add(center("진위 확인은 QR 또는 번호로", small));
        doc.add(center(verifyUrl, small));

        doc.close();
        return out.toByteArray();
    }

    /**
     * QR 을 PNG 로. <b>package-private 인 이유</b> — 테스트가 이 바이트를 그대로 디코드해
     * "그렸다" 가 아니라 "읽힌다" 를 확인한다. PDF 안에서 도로 꺼내는 길은 OpenPDF 에 없다.
     */
    byte[] qrPng(String text) throws Exception {
        BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, QR_PX, QR_PX);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", png);
        return png.toByteArray();
    }

    /** 폰트를 못 읽으면 멈춘다 — 한글이 □ 로 나간 증서는 되돌릴 수 없다(함정 1). */
    private BaseFont loadKoreanFont() {
        try (InputStream in = new ClassPathResource(FONT_PATH).getInputStream()) {
            return BaseFont.createFont(FONT_PATH, BaseFont.IDENTITY_H, BaseFont.EMBEDDED,
                    true, in.readAllBytes(), null);
        } catch (Exception e) {
            throw new IllegalStateException("한글 폰트를 읽지 못했다: " + FONT_PATH, e);
        }
    }

    private Paragraph center(String text, Font font) {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_CENTER);
        return p;
    }

    private Paragraph gap(float height) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(height);
        return p;
    }
}
