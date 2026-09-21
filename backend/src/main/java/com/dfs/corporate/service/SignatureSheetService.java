package com.dfs.corporate.service;

import com.dfs.corporate.web.error.ApiException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Builds a printable A4 sheet with the same signature image tiled in 4 slots (2×2).
 */
@Service
public class SignatureSheetService {

    /** A4 at ~150 DPI for a crisp printable raster. */
    private static final int PAGE_W = 1240;
    private static final int PAGE_H = 1754;
    private static final int MARGIN = 48;
    private static final int GAP = 24;
    private static final Color PAGE_BG = Color.WHITE;
    private static final Color SLOT_BORDER = new Color(210, 210, 210);

    public record Sheets(byte[] png, byte[] jpeg, byte[] pdf) {}

    public Sheets createFromImageBytes(byte[] imageBytes) {
        BufferedImage source = readImage(imageBytes);
        BufferedImage sheet = composeFourUp(source);
        try {
            byte[] png = encode(sheet, "png");
            byte[] jpeg = encodeJpeg(sheet);
            byte[] pdf = encodePdf(png);
            return new Sheets(png, jpeg, pdf);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to build signature sheet");
        }
    }

    private BufferedImage readImage(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "signature file is required");
        }
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (img == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Unsupported or corrupt signature image (use JPEG or PNG)");
            }
            return img;
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Could not read signature image");
        }
    }

    private BufferedImage composeFourUp(BufferedImage source) {
        BufferedImage page = new BufferedImage(PAGE_W, PAGE_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = page.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(PAGE_BG);
            g.fillRect(0, 0, PAGE_W, PAGE_H);

            int cellW = (PAGE_W - 2 * MARGIN - GAP) / 2;
            int cellH = (PAGE_H - 2 * MARGIN - GAP) / 2;
            int[][] origins = {
                    {MARGIN, MARGIN},
                    {MARGIN + cellW + GAP, MARGIN},
                    {MARGIN, MARGIN + cellH + GAP},
                    {MARGIN + cellW + GAP, MARGIN + cellH + GAP}
            };
            for (int[] origin : origins) {
                drawSlot(g, source, origin[0], origin[1], cellW, cellH);
            }
        } finally {
            g.dispose();
        }
        return page;
    }

    private void drawSlot(Graphics2D g, BufferedImage source, int x, int y, int cellW, int cellH) {
        g.setColor(PAGE_BG);
        g.fillRect(x, y, cellW, cellH);
        g.setColor(SLOT_BORDER);
        g.drawRect(x, y, cellW - 1, cellH - 1);

        int pad = 16;
        int boxW = cellW - 2 * pad;
        int boxH = cellH - 2 * pad;
        double scale = Math.min((double) boxW / source.getWidth(), (double) boxH / source.getHeight());
        int drawW = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int drawH = Math.max(1, (int) Math.round(source.getHeight() * scale));
        int dx = x + pad + (boxW - drawW) / 2;
        int dy = y + pad + (boxH - drawH) / 2;
        g.drawImage(source, dx, dy, drawW, drawH, null);
    }

    private byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, format, out)) {
            throw new IOException("No ImageIO writer for " + format);
        }
        return out.toByteArray();
    }

    private byte[] encodeJpeg(BufferedImage image) throws IOException {
        BufferedImage rgb = image;
        if (image.getType() != BufferedImage.TYPE_INT_RGB) {
            rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(image, 0, 0, null);
            g.dispose();
        }
        return encode(rgb, "jpg");
    }

    private byte[] encodePdf(byte[] pngBytes) throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDImageXObject image = PDImageXObject.createFromByteArray(doc, pngBytes, "signature-sheet");
            float pageW = page.getMediaBox().getWidth();
            float pageH = page.getMediaBox().getHeight();
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(image, 0, 0, pageW, pageH);
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
