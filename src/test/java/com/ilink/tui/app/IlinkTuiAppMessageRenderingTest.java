package com.ilink.tui.app;

import com.ilink.tui.bot.BotGateway;
import com.ilink.tui.model.ChatMessage;
import dev.tamboui.toolkit.element.ContainerElement;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.Column;
import dev.tamboui.toolkit.elements.Spacer;
import dev.tamboui.toolkit.elements.TextElement;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IlinkTuiAppMessageRenderingTest {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @Test
    void inboundMessageRendersContentBlockOnLeft() throws Exception {
        IlinkTuiApp app = new IlinkTuiApp(new AppState(), new AppController(new AppState(), new SilentBotGateway()));
        ChatMessage message = new ChatMessage(
                "conv-1",
                "bot-user",
                "hello from bot",
                false,
                Instant.parse("2026-04-17T10:15:30Z")
        );

        StyledElement<?> item = app.renderMessageItem(message);
        List<Element> rowChildren = childrenOf(item);

        assertEquals(2, rowChildren.size());
        assertInstanceOf(Column.class, rowChildren.get(0));
        assertInstanceOf(Spacer.class, rowChildren.get(1));

        List<Element> messageBlockChildren = childrenOf(rowChildren.get(0));
        TextElement meta = assertInstanceOf(TextElement.class, messageBlockChildren.get(0));
        TextElement body = assertInstanceOf(TextElement.class, messageBlockChildren.get(1));
        assertTrue(meta.content().contains("bot-user"));
        assertTrue(meta.content().contains(TIME_FORMATTER.format(message.timestamp())));
        assertEquals("hello from bot", body.content());
    }

    @Test
    void outboundMessageRendersContentBlockOnRight() throws Exception {
        IlinkTuiApp app = new IlinkTuiApp(new AppState(), new AppController(new AppState(), new SilentBotGateway()));
        ChatMessage message = new ChatMessage(
                "conv-1",
                "Me",
                "hello from me",
                true,
                Instant.parse("2026-04-17T10:15:30Z")
        );

        StyledElement<?> item = app.renderMessageItem(message);
        List<Element> rowChildren = childrenOf(item);

        assertEquals(2, rowChildren.size());
        assertInstanceOf(Spacer.class, rowChildren.get(0));
        assertInstanceOf(Column.class, rowChildren.get(1));

        List<Element> messageBlockChildren = childrenOf(rowChildren.get(1));
        TextElement meta = assertInstanceOf(TextElement.class, messageBlockChildren.get(0));
        TextElement body = assertInstanceOf(TextElement.class, messageBlockChildren.get(1));
        assertTrue(meta.content().contains("Me"));
        assertTrue(meta.content().contains(TIME_FORMATTER.format(message.timestamp())));
        assertEquals("hello from me", body.content());
    }

    @SuppressWarnings("unchecked")
    private static List<Element> childrenOf(Object element) throws Exception {
        Field field = ContainerElement.class.getDeclaredField("children");
        field.setAccessible(true);
        return (List<Element>) field.get(element);
    }

    private static final class SilentBotGateway extends BotGateway {
    }
}
