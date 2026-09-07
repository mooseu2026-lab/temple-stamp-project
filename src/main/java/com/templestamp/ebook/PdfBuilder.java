package com.templestamp.ebook;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import com.templestamp.ebook.dto.EbookCertRow;
import com.templestamp.ebook.dto.EbookPhotoRow;
import com.templestamp.ebook.dto.EbookStampRow;
import com.templestamp.ebook.dto.EbookThinkboxRow;
import com.templestamp.global.config.EbookProperties;
import com.templestamp.upload.ObjectStorageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 전자책 PDF 조판(챕터 9 §2-3).
 * <p>
 * <b>한글 폰트를 임베드하지 않으면 글자가 전부 □ 로 나온다.</b> 기본 폰트에 한글 글리프가 없어서다.
 * 그런데 그 사고는 조용하다 — 예외도 경고도 없이 파일은 잘 만들어지고, 열어 봐야 안다.
 * 그래서 JUnit 이 만들어진 PDF 에서 <b>텍스트를 도로 뽑아</b> 한글이 들어 있는지 본다(함정 1).
 * <p>
 * 사진은 긴 변 {@code photo-max-px} 로 줄이고 JPEG 로 다시 압축해 넣는다.
 * 원본을 그대로 넣으면 도장 60장짜리 책이 수백 MB 가 된다(함정 2).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdfBuilder {

    private static final String FONT_PATH = "fonts/NotoSansKR-Regular.ttf";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy년 M월 d일");

    private final EbookProperties ebookProperties;
    private final ObjectStorageClient storageClient;

    /** 만들어진 책 한 권. 쪽 수와 바이트 수는 응답에 실린다. */
    public record PdfResult(byte[] bytes, int pageCount) {
    }

    /** 종류를 적지 않은 옛 호출. 표지는 개인 소장본 표지다. */
    public PdfResult build(EbookMaterials m) {
        return build(m, null, null);
    }

    /**
     * 종류에 맞는 표지로 한 권. 전자일기장(INTERIM)은 표지에 몇 코스까지의 기록인지 적는다 —
     * 3·6·9·12 가 모두 "나의 순례 기록" 이면 서가에서 어느 것이 어느 것인지 알 수 없다.
     */
    public PdfResult build(EbookMaterials m, String ebookType, Integer milestone) {
        Document doc = new Document(PageSize.A4, 50, 50, 50, 50);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        doc.open();

        BaseFont base = loadKoreanFont();
        Font title = new Font(base, 24, Font.BOLD);
        Font head = new Font(base, 16, Font.BOLD);
        Font body = new Font(base, 11);
        Font small = new Font(base, 9, Font.NORMAL, new java.awt.Color(110, 110, 110));

        cover(doc, m, title, body, ebookType, milestone);
        courses(doc, m, head, body);
        stamps(doc, m, head, body, small);
        photos(doc, m, head, small);
        thinkboxes(doc, m, head, body, small);
        meditations(doc, m, head, body);
        certificates(doc, m, head, body);
        colophon(doc, m, head, small);

        doc.close();
        return new PdfResult(out.toByteArray(), writer.getPageNumber());
    }

    /* ---------------- 페이지 ---------------- */

    private void cover(Document doc, EbookMaterials m, Font title, Font body,
                       String ebookType, Integer milestone) {
        boolean diary = Ebook.INTERIM.equals(ebookType);
        doc.add(gap(120));
        doc.add(center(diary ? "전자일기장" : "나의 순례 기록", title));
        if (diary && milestone != null) {
            doc.add(gap(8));
            doc.add(center("%d코스까지의 기록".formatted(milestone), body));
        }
        doc.add(gap(24));
        doc.add(center(m.nickname(), body));
        LocalDate from = m.firstDate();
        LocalDate to = m.lastDate();
        if (from != null) {
            doc.add(center(from.format(DATE) + " ~ " + to.format(DATE), body));
        }
        doc.add(gap(12));
        doc.add(center("코스 %d · 도장 %d".formatted(m.courseCount(), m.stampCount()), body));
        doc.newPage();
    }

    private void courses(Document doc, EbookMaterials m, Font head, Font body) {
        doc.add(new Paragraph("걸어온 길", head));
        doc.add(gap(10));
        // 코스마다 몇 칸을 채웠는지. 같은 코스의 도장을 세면 진행률이 그대로 나온다.
        Map<String, Integer> byCourse = new LinkedHashMap<>();
        Map<String, String> region = new LinkedHashMap<>();
        for (EbookStampRow s : m.stamps()) {
            byCourse.merge(s.getCourseName(), 1, Integer::sum);
            region.putIfAbsent(s.getCourseName(), s.getRegionName());
        }
        byCourse.forEach((name, count) ->
                doc.add(new Paragraph("· %s (%s) — %d칸".formatted(name, region.get(name), count), body)));
        doc.newPage();
    }

    private void stamps(Document doc, EbookMaterials m, Font head, Font body, Font small) {
        for (EbookStampRow s : m.stamps()) {
            doc.add(new Paragraph(s.getSiteName() == null ? "사찰 미상" : s.getSiteName(), head));
            doc.add(new Paragraph(s.getCompletedAt().toLocalDate().format(DATE), small));
            doc.add(gap(10));

            if (s.getExtBody() != null) {
                // 그날 그 사람이 본 문구다. 원고가 뒤에 퇴역·수정돼도 이 책의 글은 바뀌지 않는다.
                doc.add(new Paragraph(s.getExtTitle(), body));
                doc.add(new Paragraph(s.getExtBody(), body));
                doc.add(gap(10));
            }
            addPhoto(doc, s.getPhotoKey(), small);
            if (s.getUserSentence() != null && !s.getUserSentence().isBlank()) {
                doc.add(gap(8));
                doc.add(new Paragraph("“" + s.getUserSentence() + "”", body));
            }
            doc.newPage();
        }
    }

    /**
     * 사진이 저장소에 없으면 "사진 없음" 을 적고 넘어간다 — <b>실패가 아니다</b>.
     * 청소기가 이미 지운 키일 수 있고, 그것 때문에 책 한 권이 통째로 실패하면 안 된다.
     */
    private void addPhoto(Document doc, String photoKey, Font small) {
        if (photoKey == null || photoKey.isBlank()) {
            return;
        }
        try {
            if (!storageClient.exists(photoKey)) {
                doc.add(new Paragraph("(사진 없음)", small));
                return;
            }
            byte[] shrunk = shrink(storageClient.getBytes(photoKey));
            Image img = Image.getInstance(shrunk);
            img.scaleToFit(400, 400);
            img.setAlignment(Element.ALIGN_LEFT);
            doc.add(img);
        } catch (Exception e) {
            log.warn("전자책 사진 삽입 실패 — 빈 자리로 둔다. key={}", photoKey, e);
            doc.add(new Paragraph("(사진 없음)", small));
        }
    }

    /**
     * 도장에 붙지 않은 사진. 전자일기장은 도장이 없어도 만들어지므로 이 페이지가 비어 있을 수도,
     * 이 페이지만 있을 수도 있다 — 없으면 통째로 건너뛴다(빈 제목만 남은 페이지를 만들지 않는다).
     */
    private void photos(Document doc, EbookMaterials m, Font head, Font small) {
        if (m.photos().isEmpty()) {
            return;
        }
        doc.add(new Paragraph("사진", head));
        doc.add(gap(10));
        for (EbookPhotoRow ph : m.photos()) {
            String where = ph.getSiteName() == null ? "" : ph.getSiteName() + " · ";
            doc.add(new Paragraph(where + ph.getCreatedAt().toLocalDate().format(DATE), small));
            addPhoto(doc, ph.getFileKey(), small);
            doc.add(gap(12));
        }
        doc.newPage();
    }

    private void thinkboxes(Document doc, EbookMaterials m, Font head, Font body, Font small) {
        if (m.thinkboxes().isEmpty()) {
            return;
        }
        doc.add(new Paragraph("생각상자", head));
        doc.add(gap(10));
        for (EbookThinkboxRow t : m.thinkboxes()) {
            String where = t.getSiteName() == null ? "" : " · " + t.getSiteName();
            doc.add(new Paragraph(t.getCreatedAt().toLocalDate().format(DATE) + where, small));
            doc.add(new Paragraph(t.getContent(), body));
            doc.add(gap(8));
        }
        doc.newPage();
    }

    private void meditations(Document doc, EbookMaterials m, Font head, Font body) {
        doc.add(new Paragraph("명상", head));
        doc.add(gap(10));
        doc.add(new Paragraph("들은 횟수 %d회 · 모두 %d분".formatted(m.medCount(), m.medSeconds() / 60), body));
        doc.newPage();
    }

    private void certificates(Document doc, EbookMaterials m, Font head, Font body) {
        doc.add(new Paragraph("인증서", head));
        doc.add(gap(10));
        if (m.certs().isEmpty()) {
            doc.add(new Paragraph("아직 없습니다.", body));
        }
        for (EbookCertRow c : m.certs()) {
            String what = c.getCourseName() == null ? "회향" : c.getCourseName();
            doc.add(new Paragraph("%s · %s · %s".formatted(
                    c.getSerialNo(), what, c.getIssuedAt().toLocalDate().format(DATE)), body));
        }
        doc.newPage();
    }

    private void colophon(Document doc, EbookMaterials m, Font head, Font small) {
        doc.add(new Paragraph("판권", head));
        doc.add(gap(10));
        doc.add(new Paragraph("만든 날 " + LocalDate.now().format(DATE), small));
        doc.add(new Paragraph("스냅샷 " + m.snapshotHash().substring(0, 8), small));
        doc.add(new Paragraph("개인 소장용", small));
    }

    /* ---------------- 도구 ---------------- */

    /**
     * 폰트를 못 읽으면 <b>여기서 멈춘다.</b> 기본 폰트로 넘어가면 한글이 □ 로 나간 책이
     * 사용자 손에 들어가고, 그것은 되돌릴 수 없다. 만들지 못한 책은 다시 만들면 된다.
     */
    private BaseFont loadKoreanFont() {
        try (InputStream in = new ClassPathResource(FONT_PATH).getInputStream()) {
            return BaseFont.createFont(FONT_PATH, BaseFont.IDENTITY_H, BaseFont.EMBEDDED,
                    true, in.readAllBytes(), null);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "한글 폰트를 읽지 못했다: " + FONT_PATH + " — 임베드 없이 만들면 한글이 □ 로 나온다", e);
        }
    }

    /** 긴 변을 설정값으로 줄이고 JPEG 로 다시 압축한다. 이미 작으면 원본 그대로 둔다. */
    private byte[] shrink(byte[] original) throws Exception {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(original));
        if (src == null) {
            return original;   // 읽지 못하는 형식이면 손대지 않는다 — Image.getInstance 가 판단한다
        }
        int max = ebookProperties.photoMaxPx();
        int longSide = Math.max(src.getWidth(), src.getHeight());
        BufferedImage target = src;
        if (longSide > max) {
            double scale = (double) max / longSide;
            int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
            int h = Math.max(1, (int) Math.round(src.getHeight() * scale));
            target = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = target.createGraphics();
            g.drawImage(src.getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH), 0, 0, null);
            g.dispose();
        } else if (target.getType() != BufferedImage.TYPE_INT_RGB) {
            BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = rgb.createGraphics();
            g.drawImage(src, 0, 0, null);
            g.dispose();
            target = rgb;
        }
        return toJpeg(target);
    }

    private byte[] toJpeg(BufferedImage image) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(ebookProperties.jpegQuality());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream mos = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(mos);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
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

    /** 목록 조립에만 쓰는 도구 — 테스트가 페이지 구성을 확인할 때 함께 본다. */
    public List<String> sections() {
        return List.of("표지", "걸어온 길", "도장", "사진", "생각상자", "명상", "인증서", "판권");
    }
}
