package com.ilink.tui.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

/**
 * 将登录链接渲染为终端可读的块状二维码。
 */
public final class QrCodeRenderer {

    private static final int QR_SIZE = 24;
    private static final int QUIET_ZONE = 2;
    private static final char FULL_BLOCK = '█';
    private static final char UPPER_HALF_BLOCK = '▀';
    private static final char LOWER_HALF_BLOCK = '▄';
    private static final char EMPTY = ' ';

    private QrCodeRenderer() {
    }

    /**
     * 将文本渲染为 ASCII 风格二维码。
     * 渲染失败时回退为原始文本。
     *
     * @param content 登录链接或二维码原始内容
     * @return 终端可显示的二维码文本
     */
    public static String render(String content) {
        if (content == null || content.isBlank()) {
            return "二维码生成中...";
        }
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);
            return toTerminalBlocks(withQuietZone(matrix, QUIET_ZONE));
        } catch (WriterException ex) {
            return content;
        }
    }

    private static String toTerminalBlocks(BitMatrix matrix) {
        StringBuilder builder = new StringBuilder();
        for (int y = 0; y < matrix.getHeight(); y += 2) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                boolean upper = matrix.get(x, y);
                boolean lower = y + 1 < matrix.getHeight() && matrix.get(x, y + 1);
                builder.append(resolveBlock(upper, lower));
            }
            if (y + 2 < matrix.getHeight()) {
                builder.append(System.lineSeparator());
            }
        }
        return builder.toString();
    }

    private static BitMatrix withQuietZone(BitMatrix source, int quietZone) {
        int width = source.getWidth() + quietZone * 2;
        int height = source.getHeight() + quietZone * 2;
        BitMatrix padded = new BitMatrix(width, height);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                if (source.get(x, y)) {
                    padded.set(x + quietZone, y + quietZone);
                }
            }
        }
        return padded;
    }

    private static char resolveBlock(boolean upper, boolean lower) {
        if (upper && lower) {
            return FULL_BLOCK;
        }
        if (upper) {
            return UPPER_HALF_BLOCK;
        }
        if (lower) {
            return LOWER_HALF_BLOCK;
        }
        return EMPTY;
    }
}
