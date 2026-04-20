package com.ilink.tui.app;

import com.ilink.tui.model.AppPhase;
import com.ilink.tui.model.ChatMessage;
import com.ilink.tui.model.ConversationSummary;
import com.ilink.tui.model.FocusPane;
import com.ilink.tui.qr.QrCodeRenderer;
import dev.tamboui.layout.Constraint;
import dev.tamboui.style.Color;
import dev.tamboui.style.Overflow;
import dev.tamboui.style.Style;
import dev.tamboui.toolkit.app.ToolkitApp;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.common.ScrollBarPolicy;
import dev.tamboui.widgets.input.TextInputState;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static dev.tamboui.toolkit.Toolkit.column;
import static dev.tamboui.toolkit.Toolkit.list;
import static dev.tamboui.toolkit.Toolkit.panel;
import static dev.tamboui.toolkit.Toolkit.richTextArea;
import static dev.tamboui.toolkit.Toolkit.row;
import static dev.tamboui.toolkit.Toolkit.spacer;
import static dev.tamboui.toolkit.Toolkit.text;
import static dev.tamboui.toolkit.Toolkit.textInput;

/**
 * 基于 TamboUI 的 iLink 终端聊天应用。
 */
public class IlinkTuiApp extends ToolkitApp {

    private static final String AUTH_ROOT_ID = "auth-root";
    private static final String CONVERSATION_LIST_ID = "conversation-list";
    private static final String INPUT_ID = "chat-input";
    private static final int MESSAGE_WIDTH_PERCENT = 72;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final AppState state;
    private final AppController controller;
    private final TextInputState inputState = new TextInputState();

    /**
     * 创建 TUI 应用。
     *
     * @param state      应用状态容器
     * @param controller 控制器
     */
    public IlinkTuiApp(AppState state, AppController controller) {
        this.state = state;
        this.controller = controller;
    }

    @Override
    protected void onStart() {
        controller.bindRefreshHook(this::requestRender);
        runAsync("ilink-bootstrap", controller::bootstrap);
    }

    @Override
    protected void onStop() {
        controller.shutdown();
    }

    @Override
    protected Element render() {
        syncFocusState();
        syncDraftState();

        AppState.Snapshot snapshot = state.snapshot();
        syncInputState(snapshot);
        return snapshot.phase() == AppPhase.AUTH_REQUIRED
                ? renderAuthPage(snapshot)
                : renderChatPage(snapshot);
    }

    private Element renderAuthPage(AppState.Snapshot snapshot) {
        String qrView = QrCodeRenderer.render(snapshot.qrContent());
        return panel(
                "iLink Login",
                column(
                        text("未检测到可复用登录态，请使用微信扫码登录。").bold().cyan(),
                        panel(
                                "QR Code",
                                richTextArea(qrView)
                                        .constraint(Constraint.fill())
                        ).rounded().borderColor(Color.CYAN).constraint(Constraint.length(qrPanelHeight(qrView))),
                        panel(
                                "Login Link",
                                richTextArea(snapshot.qrContent().isBlank() ? "等待生成登录链接..." : snapshot.qrContent())
                                        .scrollbar(ScrollBarPolicy.AS_NEEDED)
                                        .constraint(Constraint.length(4))
                        ).rounded().borderColor(Color.GRAY).constraint(Constraint.length(6)),
                        panel(
                                "Status",
                                text(snapshot.authStatusText().isBlank() ? "等待扫码" : snapshot.authStatusText())
                                        .yellow()
                        ).rounded().borderColor(Color.YELLOW).constraint(Constraint.length(3)),
                        text("快捷键：r 重新生成二维码，q 退出。").gray()
                ).spacing(1)
        ).rounded()
                .borderColor(Color.CYAN)
                .focusable()
                .id(AUTH_ROOT_ID)
                .onKeyEvent(this::handleAuthKeys);
    }

    private Element renderChatPage(AppState.Snapshot snapshot) {
        return row(
                renderConversationList(snapshot),
                renderConversationBody(snapshot)
        ).spacing(1)
                .fill()
                .onKeyEvent(this::handleGlobalKeys);
    }

    private Element renderConversationList(AppState.Snapshot snapshot) {
        return list()
                .data(snapshot.conversations(), this::renderConversationItem)
                .selected(selectedConversationIndex(snapshot))
                .title("Conversations")
                .rounded()
                .highlightColor(Color.CYAN)
                .constraint(Constraint.length(28))
                .borderColor(snapshot.focusPane() == FocusPane.CONVERSATIONS ? Color.CYAN : Color.GRAY)
                .id(CONVERSATION_LIST_ID)
                .focusable()
                .onKeyEvent(this::handleConversationKeys);
    }

    private Element renderConversationBody(AppState.Snapshot snapshot) {
        return column(
                renderMessagesPane(snapshot),
                textInput(inputState)
                        .title("Input")
                        .placeholder(snapshot.selectedConversationId().isBlank()
                                ? "等待会话出现后再发送消息"
                                : "输入消息并按 Enter 发送")
                        .rounded()
                        .borderColor(Color.GRAY)
                        .focusedBorderColor(Color.CYAN)
                        .id(INPUT_ID)
                        .focusable()
                        .onKeyEvent(this::handleInputKeys)
                        .onSubmit(this::submitCurrentDraft)
                        .constraint(Constraint.length(3)),
                panel(
                        "Status",
                        text(snapshot.globalStatusText().isBlank()
                                ? defaultChatStatus(snapshot)
                                : snapshot.globalStatusText())
                                .yellow()
                ).rounded().borderColor(Color.YELLOW).constraint(Constraint.length(3))
        ).spacing(1).fill();
    }

    private StyledElement<?> renderConversationItem(ConversationSummary summary) {
        return column(
                text(summary.title()).bold(),
                text("最后活跃：" + TIME_FORMATTER.format(summary.lastActivity())).dim()
        );
    }

    StyledElement<?> renderMessageItem(ChatMessage message) {
        StyledElement<?> messageBlock = buildMessageBlock(message);
        return message.outbound()
                ? row(spacer(), messageBlock).spacing(1)
                : row(messageBlock, spacer()).spacing(1);
    }

    private EventResult handleAuthKeys(KeyEvent event) {
        return handleGlobalShortcut(event, true);
    }

    private EventResult handleConversationKeys(KeyEvent event) {
        EventResult shortcut = handleGlobalShortcut(event, false);
        if (shortcut.isHandled()) {
            return shortcut;
        }
        if (shouldRedirectTypingToInput(event)) {
            focusInputAndPrimeDraft(event.character());
            return EventResult.HANDLED;
        }
        if (event.isUp()) {
            controller.selectPreviousConversation();
            return EventResult.HANDLED;
        }
        if (event.isDown()) {
            controller.selectNextConversation();
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    private EventResult handleInputKeys(KeyEvent event) {
        EventResult shortcut = handleGlobalShortcut(event, false);
        if (shortcut.isHandled()) {
            return shortcut;
        }
        return EventResult.UNHANDLED;
    }

    private EventResult handleGlobalKeys(KeyEvent event) {
        return handleGlobalShortcut(event, false);
    }

    private EventResult handleGlobalShortcut(KeyEvent event, boolean allowRetry) {
        if (event.isQuit() || event.isCtrlC() || event.isCharIgnoreCase('q')) {
            quit();
            return EventResult.HANDLED;
        }
        if (event.isCancel()) {
            controller.clearStatus();
            return EventResult.HANDLED;
        }
        if (allowRetry && event.isCharIgnoreCase('r')) {
            runAsync("ilink-retry-login", controller::retryLogin);
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    private void submitCurrentDraft() {
        controller.updateDraftInput(inputState.text());
        controller.sendCurrentInput();
        inputState.clear();
    }

    private void syncDraftState() {
        if (state.snapshot().phase() != AppPhase.CHAT_READY) {
            return;
        }
        String focusedId = runner() != null ? runner().focusManager().focusedId() : null;
        if (INPUT_ID.equals(focusedId)) {
            String draft = state.snapshot().draftInput();
            if (!inputState.text().equals(draft)) {
                controller.updateDraftInput(inputState.text());
            }
        }
    }

    private void syncInputState(AppState.Snapshot snapshot) {
        if (snapshot.phase() != AppPhase.CHAT_READY) {
            if (!inputState.text().isEmpty()) {
                inputState.clear();
            }
            return;
        }
        if (!inputState.text().equals(snapshot.draftInput())) {
            inputState.setText(snapshot.draftInput());
            inputState.moveCursorToEnd();
        }
    }

    private void syncFocusState() {
        if (runner() == null) {
            return;
        }
        String focusedId = runner().focusManager().focusedId();
        if (INPUT_ID.equals(focusedId)) {
            controller.syncFocusFromUi(FocusPane.INPUT);
        } else if (CONVERSATION_LIST_ID.equals(focusedId)) {
            controller.syncFocusFromUi(FocusPane.CONVERSATIONS);
        }
    }

    private void requestRender() {
        if (runner() != null && runner().isRunning()) {
            runner().runOnRenderThread(() -> {
            });
        }
    }

    private void runAsync(String threadName, Runnable action) {
        Thread thread = new Thread(action, threadName);
        thread.setDaemon(true);
        thread.start();
    }

    private int selectedConversationIndex(AppState.Snapshot snapshot) {
        List<ConversationSummary> conversations = snapshot.conversations();
        for (int index = 0; index < conversations.size(); index++) {
            if (conversations.get(index).conversationId().equals(snapshot.selectedConversationId())) {
                return index;
            }
        }
        return 0;
    }

    private Element renderMessagesPane(AppState.Snapshot snapshot) {
        if (snapshot.selectedConversationId().isBlank()) {
            return panel(
                    messagePanelTitle(snapshot),
                    text("暂无会话，等待新消息...").gray()
            ).rounded().borderColor(Color.GRAY).constraint(Constraint.fill());
        }
        List<ChatMessage> messages = snapshot.messagesByConversation().get(snapshot.selectedConversationId());
        if (messages == null || messages.isEmpty()) {
            return panel(
                    messagePanelTitle(snapshot),
                    text("该会话暂无消息。").gray()
            ).rounded().borderColor(Color.GRAY).constraint(Constraint.fill());
        }
        return list()
                .data(messages, this::renderMessageItem)
                .title(messagePanelTitle(snapshot))
                .rounded()
                .borderColor(Color.GRAY)
                .highlightStyle(Style.EMPTY)
                .highlightSymbol("")
                .scrollbar(ScrollBarPolicy.AS_NEEDED)
                .scrollToEnd()
                .constraint(Constraint.fill());
    }

    private String messagePanelTitle(AppState.Snapshot snapshot) {
        if (snapshot.selectedConversationId().isBlank()) {
            return "Messages";
        }
        return "Messages · " + snapshot.selectedConversationId();
    }

    private String defaultChatStatus(AppState.Snapshot snapshot) {
        if (snapshot.currentAccountId().isBlank()) {
            return "已进入聊天界面";
        }
        return "已登录：" + snapshot.currentAccountId();
    }

    private boolean shouldRedirectTypingToInput(KeyEvent event) {
        AppState.Snapshot snapshot = state.snapshot();
        return snapshot.phase() == AppPhase.CHAT_READY
                && !snapshot.selectedConversationId().isBlank()
                && event.code() == dev.tamboui.tui.event.KeyCode.CHAR
                && !event.hasCtrl()
                && !event.hasAlt()
                && !event.isCharIgnoreCase('q');
    }

    private void focusInputAndPrimeDraft(char typedChar) {
        if (runner() != null) {
            runner().focusManager().setFocus(INPUT_ID);
        }
        inputState.insert(typedChar);
        controller.updateDraftInput(inputState.text());
        requestRender();
    }

    private StyledElement<?> buildMessageBlock(ChatMessage message) {
        boolean outbound = message.outbound();
        return column(
                formatMessageMetaText(message, outbound),
                formatMessageBodyText(message, outbound)
        ).constraint(Constraint.percentage(MESSAGE_WIDTH_PERCENT));
    }

    private String buildMessageMeta(ChatMessage message) {
        return "[" + TIME_FORMATTER.format(message.timestamp()) + "] " + message.senderLabel();
    }

    static int qrPanelHeight(String qrView) {
        return qrContentLineCount(qrView) + 2;
    }

    private static int qrContentLineCount(String qrView) {
        if (qrView == null || qrView.isEmpty()) {
            return 1;
        }
        return (int) qrView.lines().count();
    }

    private StyledElement<?> formatMessageMetaText(ChatMessage message, boolean outbound) {
        var metaText = text(buildMessageMeta(message)).dim();
        return outbound ? metaText.right() : metaText;
    }

    private StyledElement<?> formatMessageBodyText(ChatMessage message, boolean outbound) {
        var bodyText = text(message.text()).overflow(Overflow.WRAP_WORD);
        return outbound ? bodyText.right() : bodyText;
    }
}
