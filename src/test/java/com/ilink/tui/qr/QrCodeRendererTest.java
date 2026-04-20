package com.ilink.tui.qr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QrCodeRendererTest {

    @Test
    void renderUsesBlockCharactersInsteadOfOriginalLink() {
        String content = "https://example.com/login?q=wechat";

        String rendered = QrCodeRenderer.render(content);

        assertNotEquals(content, rendered);
        assertTrue(rendered.contains("█") || rendered.contains("▀") || rendered.contains("▄"));
    }

    @Test
    void renderProducesCompactMultiLineOutput() {
        String content = "https://example.com/login?q=wechat";

        String rendered = QrCodeRenderer.render(content);
        String[] lines = rendered.split(System.lineSeparator());

        assertTrue(lines.length > 0);
        assertTrue(lines.length < 24, "半块字符压缩后行数应明显小于原始矩阵高度");
    }

    @Test
    void renderOnlyUsesExpectedTerminalCharacters() {
        String content = "https://example.com/login?q=wechat";

        String rendered = QrCodeRenderer.render(content);

        for (char c : rendered.toCharArray()) {
            assertTrue(
                    c == '█' || c == '▀' || c == '▄' || c == ' ' || c == '\n' || c == '\r',
                    "unexpected char: " + c
            );
        }
    }

    @Test
    void renderReturnsLoadingMessageWhenContentIsBlank() {
        String rendered = QrCodeRenderer.render("   ");

        assertEquals("二维码生成中...", rendered);
    }

    private static void assertEquals(String expected, String actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
