package com.ilink.tui.bot;

import io.github.pigmesh.ai.ilink.Credentials;
import io.github.pigmesh.ai.ilink.exception.ApiException;
import io.github.pigmesh.ai.ilink.internal.ApiClient;
import io.github.pigmesh.ai.ilink.internal.Auth;
import io.github.pigmesh.ai.ilink.internal.dto.GetUpdatesResp;
import io.github.pigmesh.ai.ilink.internal.dto.QrCodeResponse;
import io.github.pigmesh.ai.ilink.internal.dto.QrStatusResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * iLink Bot 接入层。
 * 屏蔽 SDK 内部细节，向上层暴露登录、收消息、发消息和生命周期事件。
 */
public class BotGateway {

    private static final String DEFAULT_BASE_URL = "https://ilinkai.weixin.qq.com";
    private static final long LOGIN_POLL_INTERVAL_MS = 2_000L;
    private static final long MESSAGE_RETRY_DELAY_MS = 1_000L;

    private final String tokenPath;
    private final ScheduledExecutorService loginScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "ilink-login-poll");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService messageExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ilink-message-poll");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, String> contextTokens = new ConcurrentHashMap<>();
    private final List<Consumer<IncomingTextMessage>> messageHandlers = new CopyOnWriteArrayList<>();
    private final List<Consumer<LoginResult>> loginStateHandlers = new CopyOnWriteArrayList<>();
    private final List<Consumer<String>> errorHandlers = new CopyOnWriteArrayList<>();

    private volatile String activeBaseUrl;
    private volatile Credentials credentials;
    private volatile String cursor = "";
    private volatile boolean stopped;
    private volatile String activeQrCode;
    private volatile String activeQrContent;
    private volatile String lastQrStatus;
    private volatile ScheduledFuture<?> loginFuture;
    private volatile Future<?> messageFuture;

    /**
     * 使用默认凭证路径创建网关。
     */
    public BotGateway() {
        this(null);
    }

    /**
     * 使用指定凭证路径创建网关。
     *
     * @param tokenPath 凭证路径，传空时复用 SDK 默认路径
     */
    public BotGateway(String tokenPath) {
        this.tokenPath = tokenPath;
        this.activeBaseUrl = DEFAULT_BASE_URL;
    }

    /**
     * 尝试恢复本地登录凭证。
     *
     * @return 凭证存在且解析成功时返回 {@code true}
     */
    public boolean tryRestoreSession() {
        Credentials stored = Auth.loadCredentials(tokenPath);
        if (stored == null) {
            return false;
        }
        credentials = stored;
        activeBaseUrl = stored.getBaseUrl();
        return true;
    }

    /**
     * 获取当前 bot 账号 ID。
     *
     * @return 当前账号 ID，未登录时返回空字符串
     */
    public String currentAccountId() {
        return credentials != null ? credentials.getAccountId() : "";
    }

    /**
     * 拉起二维码登录流程并返回首个二维码结果。
     *
     * @return 登录页初始结果
     */
    public synchronized LoginResult beginLogin() {
        clearRuntimeSession(false);
        cancelLoginPolling();

        QrCodeResponse qr = ApiClient.fetchQrCode(activeBaseUrl);
        activeQrCode = qr.qrcode;
        activeQrContent = qr.qrcodeImgContent;
        lastQrStatus = "wait";
        loginFuture = loginScheduler.scheduleWithFixedDelay(
                this::pollQrStatus,
                LOGIN_POLL_INTERVAL_MS,
                LOGIN_POLL_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
        return LoginResult.qrRequired(activeQrContent, "请使用微信扫码登录");
    }

    /**
     * 注册入站文本消息监听器。
     *
     * @param handler 文本消息处理器
     */
    public void registerMessageHandler(Consumer<IncomingTextMessage> handler) {
        messageHandlers.add(Objects.requireNonNull(handler, "handler"));
    }

    /**
     * 注册登录状态监听器。
     *
     * @param handler 登录状态处理器
     */
    public void registerLoginStateHandler(Consumer<LoginResult> handler) {
        loginStateHandlers.add(Objects.requireNonNull(handler, "handler"));
    }

    /**
     * 注册错误消息监听器。
     *
     * @param handler 错误处理器
     */
    public void registerErrorHandler(Consumer<String> handler) {
        errorHandlers.add(Objects.requireNonNull(handler, "handler"));
    }

    /**
     * 启动消息长轮询。
     */
    public synchronized void startPolling() {
        if (stopped || credentials == null) {
            return;
        }
        if (messageFuture != null && !messageFuture.isDone()) {
            return;
        }
        messageFuture = messageExecutor.submit(this::runMessageLoop);
    }

    /**
     * 发送文本消息。
     *
     * @param conversationId 会话 ID
     * @param text           文本内容
     */
    public void sendText(String conversationId, String text) {
        String contextToken = contextTokens.get(conversationId);
        if (contextToken == null || contextToken.isBlank()) {
            throw new IllegalStateException("当前会话尚未建立上下文，暂时无法主动发送");
        }
        Credentials current = requireCredentials();
        ApiClient.sendMessage(activeBaseUrl, current.getToken(), conversationId, contextToken, text);
    }

    /**
     * 清除本地登录凭证。
     */
    public synchronized void clearStoredCredentials() {
        clearRuntimeSession(true);
    }

    /**
     * 停止登录轮询和消息长轮询。
     */
    public synchronized void stop() {
        stopped = true;
        cancelLoginPolling();
        if (messageFuture != null) {
            messageFuture.cancel(true);
        }
        loginScheduler.shutdownNow();
        messageExecutor.shutdownNow();
    }

    /**
     * 供测试子类向外发射入站消息。
     *
     * @param message 规范化入站消息
     */
    protected void emitIncomingMessage(IncomingTextMessage message) {
        for (Consumer<IncomingTextMessage> handler : messageHandlers) {
            handler.accept(message);
        }
    }

    /**
     * 供测试子类向外发射登录状态。
     *
     * @param result 登录状态结果
     */
    protected void emitLoginResult(LoginResult result) {
        for (Consumer<LoginResult> handler : loginStateHandlers) {
            handler.accept(result);
        }
    }

    private void pollQrStatus() {
        String qrcode = activeQrCode;
        if (stopped || qrcode == null || qrcode.isBlank()) {
            return;
        }
        try {
            QrStatusResponse status = ApiClient.pollQrStatus(activeBaseUrl, qrcode);
            if (!Objects.equals(lastQrStatus, status.status)) {
                lastQrStatus = status.status;
                switch (status.status) {
                    case "scaned" -> emitLoginResult(LoginResult.qrRequired(activeQrContent, "已扫码，请在微信内确认"));
                    case "expired" -> {
                        cancelLoginPolling();
                        emitLoginResult(LoginResult.failed("二维码已过期，请按 r 重试"));
                    }
                    case "confirmed" -> handleLoginConfirmed(status);
                    default -> {
                        // wait 状态由 beginLogin 的返回值覆盖，无需重复推送
                    }
                }
            }
        } catch (RuntimeException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("timed out")) {
                return;
            }
            cancelLoginPolling();
            emitError("二维码状态检查失败：" + firstNonBlank(ex.getMessage(), ex.getClass().getSimpleName()));
            emitLoginResult(LoginResult.failed("获取登录状态失败，请按 r 重试"));
        }
    }

    private synchronized void handleLoginConfirmed(QrStatusResponse status) {
        cancelLoginPolling();
        if (status.botToken == null || status.ilinkBotId == null || status.ilinkUserId == null) {
            emitLoginResult(LoginResult.failed("登录已确认，但凭证返回不完整"));
            return;
        }
        String resolvedBaseUrl = status.baseurl != null ? status.baseurl : activeBaseUrl;
        Credentials restored = new Credentials(status.botToken, resolvedBaseUrl, status.ilinkBotId, status.ilinkUserId);
        Auth.saveCredentials(restored, tokenPath);
        credentials = restored;
        activeBaseUrl = resolvedBaseUrl;
        cursor = "";
        contextTokens.clear();
        emitLoginResult(LoginResult.restored(restored.getAccountId(), "登录成功"));
        startPolling();
    }

    private void runMessageLoop() {
        while (!stopped && credentials != null) {
            try {
                Credentials current = requireCredentials();
                GetUpdatesResp updates = ApiClient.getUpdates(activeBaseUrl, current.getToken(), cursor);
                if (updates.getUpdatesBuf != null && !updates.getUpdatesBuf.isBlank()) {
                    cursor = updates.getUpdatesBuf;
                }
                List<Map<String, Object>> rawMessages = updates.msgs != null ? updates.msgs : List.of();
                for (Map<String, Object> raw : rawMessages) {
                    rememberContext(raw);
                    IncomingTextMessage incoming = toIncomingTextMessage(raw);
                    if (incoming != null) {
                        emitIncomingMessage(incoming);
                    }
                }
            } catch (ApiException apiException) {
                if (apiException.isSessionExpired()) {
                    handleSessionExpired();
                    return;
                }
                emitError("消息同步失败：" + firstNonBlank(apiException.getMessage(), "未知接口错误"));
                sleepQuietly(MESSAGE_RETRY_DELAY_MS);
            } catch (RuntimeException ex) {
                emitError("消息同步失败：" + firstNonBlank(ex.getMessage(), ex.getClass().getSimpleName()));
                sleepQuietly(MESSAGE_RETRY_DELAY_MS);
            }
        }
    }

    private synchronized void handleSessionExpired() {
        clearRuntimeSession(true);
        emitError("登录已失效，正在重新生成二维码");
        try {
            emitLoginResult(beginLogin());
        } catch (RuntimeException ex) {
            emitError("重新拉起登录失败：" + firstNonBlank(ex.getMessage(), ex.getClass().getSimpleName()));
            emitLoginResult(LoginResult.failed("登录失效，请按 r 重新生成二维码"));
        }
    }

    private synchronized void clearRuntimeSession(boolean clearStoredCredentials) {
        cancelLoginPolling();
        if (messageFuture != null) {
            messageFuture.cancel(true);
            messageFuture = null;
        }
        credentials = null;
        cursor = "";
        activeQrCode = null;
        activeQrContent = null;
        lastQrStatus = null;
        contextTokens.clear();
        if (clearStoredCredentials) {
            Auth.clearCredentials(tokenPath);
        }
        stopped = false;
    }

    private void cancelLoginPolling() {
        if (loginFuture != null) {
            loginFuture.cancel(true);
            loginFuture = null;
        }
    }

    private Credentials requireCredentials() {
        if (credentials == null) {
            throw new IllegalStateException("当前未登录");
        }
        return credentials;
    }

    @SuppressWarnings("unchecked")
    private void rememberContext(Map<String, Object> raw) {
        Object messageType = raw.get("message_type");
        int msgType = messageType instanceof Number number ? number.intValue() : 0;
        String userId = msgType == 1
                ? (String) raw.get("from_user_id")
                : (String) raw.get("to_user_id");
        String contextToken = (String) raw.get("context_token");
        if (userId != null && contextToken != null) {
            contextTokens.put(userId, contextToken);
        }
    }

    @SuppressWarnings("unchecked")
    private IncomingTextMessage toIncomingTextMessage(Map<String, Object> raw) {
        Object messageType = raw.get("message_type");
        int msgType = messageType instanceof Number number ? number.intValue() : 0;
        if (msgType != 1) {
            return null;
        }

        String userId = (String) raw.get("from_user_id");
        List<Map<String, Object>> itemList = (List<Map<String, Object>>) raw.getOrDefault("item_list", List.of());
        long createTimeMs = raw.get("create_time_ms") instanceof Number number ? number.longValue() : 0L;
        String text = extractText(itemList);
        return new IncomingTextMessage(userId, text, Instant.ofEpochMilli(createTimeMs));
    }

    @SuppressWarnings("unchecked")
    private String extractText(List<Map<String, Object>> itemList) {
        List<String> parts = new ArrayList<>();
        for (Map<String, Object> item : itemList) {
            int type = item.get("type") instanceof Number number ? number.intValue() : 0;
            switch (type) {
                case 1 -> {
                    Map<String, Object> textItem = (Map<String, Object>) item.get("text_item");
                    if (textItem != null) {
                        String text = (String) textItem.get("text");
                        if (text != null && !text.isBlank()) {
                            parts.add(text);
                        }
                    }
                }
                case 2 -> parts.add("[image]");
                case 3 -> parts.add("[voice]");
                case 4 -> parts.add("[file]");
                case 5 -> parts.add("[video]");
                default -> {
                }
            }
        }
        return parts.isEmpty() ? "" : String.join("\n", parts);
    }

    private void emitError(String message) {
        for (Consumer<String> handler : errorHandlers) {
            handler.accept(message);
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }
}
