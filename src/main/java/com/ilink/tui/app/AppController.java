package com.ilink.tui.app;

import com.ilink.tui.bot.BotGateway;
import com.ilink.tui.bot.IncomingTextMessage;
import com.ilink.tui.bot.LoginResult;
import com.ilink.tui.bot.LoginStatus;
import com.ilink.tui.model.AppPhase;
import com.ilink.tui.model.FocusPane;

import java.time.Instant;
import java.util.Objects;

/**
 * 应用控制器。
 * 负责承接 UI 动作和 Bot 回调，并将其统一转换为 {@link AppState} 的状态变化。
 */
public class AppController {

    private final AppState state;
    private final BotGateway botGateway;
    private Runnable refreshHook = () -> {
    };

    /**
     * 创建控制器并绑定 Bot 事件。
     *
     * @param state      应用状态容器
     * @param botGateway Bot 接入层
     */
    public AppController(AppState state, BotGateway botGateway) {
        this.state = Objects.requireNonNull(state, "state");
        this.botGateway = Objects.requireNonNull(botGateway, "botGateway");
        this.botGateway.registerMessageHandler(this::onIncomingMessage);
        this.botGateway.registerLoginStateHandler(this::applyLoginResult);
        this.botGateway.registerErrorHandler(this::onGatewayError);
    }

    /**
     * 绑定 UI 刷新回调。
     *
     * @param refreshHook 刷新回调
     */
    public void bindRefreshHook(Runnable refreshHook) {
        this.refreshHook = Objects.requireNonNull(refreshHook, "refreshHook");
    }

    /**
     * 初始化应用。
     * 优先尝试恢复凭证，否则拉起二维码登录流程。
     */
    public void bootstrap() {
        if (botGateway.tryRestoreSession()) {
            state.showChatReady(botGateway.currentAccountId());
            state.updateGlobalStatus("已复用本地登录凭证");
            botGateway.startPolling();
            refreshUi();
            return;
        }
        applyLoginResult(botGateway.beginLogin());
    }

    /**
     * 重新生成二维码并重试登录。
     */
    public void retryLogin() {
        applyLoginResult(botGateway.beginLogin());
    }

    /**
     * 选择下一个会话。
     */
    public void selectNextConversation() {
        state.selectNextConversation();
        refreshUi();
    }

    /**
     * 选择上一个会话。
     */
    public void selectPreviousConversation() {
        state.selectPreviousConversation();
        refreshUi();
    }

    /**
     * 在聊天页两大区域之间切换焦点。
     */
    public void focusNextPane() {
        AppState.Snapshot snapshot = state.snapshot();
        FocusPane nextPane = snapshot.focusPane() == FocusPane.CONVERSATIONS
                ? FocusPane.INPUT
                : FocusPane.CONVERSATIONS;
        state.setFocusPane(nextPane);
        refreshUi();
    }

    /**
     * 清空状态栏提示。
     */
    public void clearStatus() {
        state.clearGlobalStatus();
        refreshUi();
    }

    /**
     * 更新当前输入框草稿。
     *
     * @param draftInput 最新草稿内容
     */
    public void updateDraftInput(String draftInput) {
        state.updateDraftInput(draftInput);
    }

    /**
     * 将界面焦点状态与 UI 实际焦点同步。
     *
     * @param focusPane UI 当前实际焦点区域
     */
    public void syncFocusFromUi(FocusPane focusPane) {
        if (state.snapshot().focusPane() != focusPane) {
            state.setFocusPane(focusPane);
        }
    }

    /**
     * 发送当前输入框中的文本。
     */
    public void sendCurrentInput() {
        AppState.Snapshot snapshot = state.snapshot();
        String conversationId = snapshot.selectedConversationId();
        String draft = snapshot.draftInput();

        if (snapshot.phase() != AppPhase.CHAT_READY) {
            return;
        }
        if (conversationId == null || conversationId.isBlank()) {
            state.updateGlobalStatus("发送失败：当前没有可用会话");
            refreshUi();
            return;
        }
        if (draft == null || draft.isBlank()) {
            state.updateGlobalStatus("请输入要发送的文本");
            refreshUi();
            return;
        }

        try {
            botGateway.sendText(conversationId, draft);
            state.addOutgoingMessage(conversationId, draft, Instant.now());
            state.updateDraftInput("");
            state.updateGlobalStatus("消息已发送");
        } catch (RuntimeException ex) {
            state.updateGlobalStatus("发送失败：" + firstNonBlank(ex.getMessage(), ex.getClass().getSimpleName()));
        }
        refreshUi();
    }

    /**
     * 停止后台 Bot 任务。
     */
    public void shutdown() {
        botGateway.stop();
    }

    private void applyLoginResult(LoginResult result) {
        if (result == null) {
            return;
        }
        AppPhase previousPhase = state.snapshot().phase();
        if (result.status() == LoginStatus.RESTORED) {
            state.showChatReady(result.accountId());
            state.updateGlobalStatus(firstNonBlank(result.statusMessage(), "登录成功"));
            botGateway.startPolling();
            refreshUi();
            return;
        }

        if (result.status() == LoginStatus.QR_REQUIRED) {
            state.showAuthRequired(
                    result.qrContent(),
                    firstNonBlank(result.statusMessage(), "请使用微信扫码登录"),
                    previousPhase == AppPhase.CHAT_READY
            );
            refreshUi();
            return;
        }

        state.showAuthRequired("", firstNonBlank(result.statusMessage(), "登录失败"), false);
        refreshUi();
    }

    private void onIncomingMessage(IncomingTextMessage message) {
        if (message == null) {
            return;
        }
        state.addIncomingMessage(message.userId(), message.text(), message.timestamp());
        refreshUi();
    }

    private void onGatewayError(String message) {
        AppState.Snapshot snapshot = state.snapshot();
        if (snapshot.phase() == AppPhase.AUTH_REQUIRED) {
            state.updateAuthStatus(firstNonBlank(message, "登录流程出现异常"));
        } else {
            state.updateGlobalStatus(firstNonBlank(message, "消息同步出现异常"));
        }
        refreshUi();
    }

    private void refreshUi() {
        refreshHook.run();
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }
}
