package com.ilink.tui.app;

import com.ilink.tui.model.AppPhase;
import com.ilink.tui.model.ChatMessage;
import com.ilink.tui.model.ConversationSummary;
import com.ilink.tui.model.FocusPane;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 应用运行期状态容器。
 * 所有写操作都应由 {@link AppController} 统一驱动。
 */
public class AppState {

    private AppPhase phase = AppPhase.AUTH_REQUIRED;
    private String qrContent = "";
    private String authStatusText = "正在检查登录状态...";
    private String globalStatusText = "";
    private String currentAccountId = "";
    private final List<ConversationSummary> conversations = new ArrayList<>();
    private String selectedConversationId = "";
    private final Map<String, List<ChatMessage>> messagesByConversation = new LinkedHashMap<>();
    private String draftInput = "";
    private FocusPane focusPane = FocusPane.CONVERSATIONS;

    /**
     * 生成当前状态快照，供渲染层只读使用。
     *
     * @return 当前状态的不可变快照
     */
    public synchronized Snapshot snapshot() {
        Map<String, List<ChatMessage>> copiedMessages = new LinkedHashMap<>();
        for (Map.Entry<String, List<ChatMessage>> entry : messagesByConversation.entrySet()) {
            copiedMessages.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return new Snapshot(
                phase,
                qrContent,
                authStatusText,
                globalStatusText,
                currentAccountId,
                List.copyOf(conversations),
                Map.copyOf(copiedMessages),
                selectedConversationId,
                draftInput,
                focusPane
        );
    }

    /**
     * 切换到二维码登录页。
     *
     * @param qrContent        二维码原始内容
     * @param authStatusText   登录状态提示
     * @param clearRuntimeData 是否清空运行期会话和消息
     */
    public synchronized void showAuthRequired(String qrContent, String authStatusText, boolean clearRuntimeData) {
        phase = AppPhase.AUTH_REQUIRED;
        this.qrContent = defaultString(qrContent);
        this.authStatusText = defaultString(authStatusText);
        if (clearRuntimeData) {
            currentAccountId = "";
            conversations.clear();
            messagesByConversation.clear();
            selectedConversationId = "";
            draftInput = "";
            focusPane = FocusPane.CONVERSATIONS;
        }
    }

    /**
     * 更新登录页状态文案但不切换页面。
     *
     * @param authStatusText 登录状态提示
     */
    public synchronized void updateAuthStatus(String authStatusText) {
        this.authStatusText = defaultString(authStatusText);
    }

    /**
     * 切换到聊天页。
     *
     * @param accountId 当前登录账号 ID
     */
    public synchronized void showChatReady(String accountId) {
        phase = AppPhase.CHAT_READY;
        currentAccountId = defaultString(accountId);
        qrContent = "";
        authStatusText = "";
        if (!selectedConversationId.isBlank()) {
            focusPane = FocusPane.INPUT;
        }
    }

    /**
     * 更新底部全局状态文案。
     *
     * @param statusText 状态文案
     */
    public synchronized void updateGlobalStatus(String statusText) {
        globalStatusText = defaultString(statusText);
    }

    /**
     * 清除底部状态栏。
     */
    public synchronized void clearGlobalStatus() {
        globalStatusText = "";
    }

    /**
     * 更新当前输入框草稿内容。
     *
     * @param draftInput 草稿内容
     */
    public synchronized void updateDraftInput(String draftInput) {
        this.draftInput = defaultString(draftInput);
    }

    /**
     * 更新当前焦点区域。
     *
     * @param focusPane 当前焦点区域
     */
    public synchronized void setFocusPane(FocusPane focusPane) {
        this.focusPane = Objects.requireNonNull(focusPane, "focusPane");
    }

    /**
     * 选择指定会话。
     *
     * @param conversationId 会话 ID
     */
    public synchronized void selectConversation(String conversationId) {
        if (conversationId != null && !conversationId.isBlank()) {
            selectedConversationId = conversationId;
        }
    }

    /**
     * 选择下一个会话。
     */
    public synchronized void selectNextConversation() {
        if (conversations.isEmpty()) {
            return;
        }
        int currentIndex = selectedConversationIndex();
        int nextIndex = (currentIndex + 1) % conversations.size();
        selectedConversationId = conversations.get(nextIndex).conversationId();
    }

    /**
     * 选择上一个会话。
     */
    public synchronized void selectPreviousConversation() {
        if (conversations.isEmpty()) {
            return;
        }
        int currentIndex = selectedConversationIndex();
        int previousIndex = (currentIndex - 1 + conversations.size()) % conversations.size();
        selectedConversationId = conversations.get(previousIndex).conversationId();
    }

    /**
     * 追加收到的入站消息。
     *
     * @param conversationId 会话 ID
     * @param text           文本内容
     * @param timestamp      时间戳
     */
    public synchronized void addIncomingMessage(String conversationId, String text, Instant timestamp) {
        upsertConversation(conversationId, timestamp);
        messagesByConversation.computeIfAbsent(conversationId, ignored -> new ArrayList<>())
                .add(new ChatMessage(conversationId, conversationId, defaultString(text), false, timestamp));
        if (selectedConversationId.isBlank()) {
            selectedConversationId = conversationId;
        }
    }

    /**
     * 追加当前用户发出的出站消息。
     *
     * @param conversationId 会话 ID
     * @param text           文本内容
     * @param timestamp      时间戳
     */
    public synchronized void addOutgoingMessage(String conversationId, String text, Instant timestamp) {
        upsertConversation(conversationId, timestamp);
        messagesByConversation.computeIfAbsent(conversationId, ignored -> new ArrayList<>())
                .add(new ChatMessage(conversationId, "Me", defaultString(text), true, timestamp));
        selectedConversationId = conversationId;
    }

    private void upsertConversation(String conversationId, Instant timestamp) {
        conversations.removeIf(item -> item.conversationId().equals(conversationId));
        conversations.add(0, new ConversationSummary(conversationId, conversationId, timestamp));
    }

    private int selectedConversationIndex() {
        if (selectedConversationId.isBlank()) {
            return 0;
        }
        for (int i = 0; i < conversations.size(); i++) {
            if (conversations.get(i).conversationId().equals(selectedConversationId)) {
                return i;
            }
        }
        return 0;
    }

    private static String defaultString(String text) {
        return text == null ? "" : text;
    }

    /**
     * 渲染层使用的只读状态快照。
     *
     * @param phase                  顶层页面阶段
     * @param qrContent              二维码内容
     * @param authStatusText         登录页状态文案
     * @param globalStatusText       底部状态文案
     * @param currentAccountId       当前账号 ID
     * @param conversations          当前会话列表
     * @param selectedConversationId 当前选中的会话 ID
     * @param messagesByConversation 当前消息映射
     * @param draftInput             输入框草稿
     * @param focusPane              当前焦点区域
     */
    public record Snapshot(
            AppPhase phase,
            String qrContent,
            String authStatusText,
            String globalStatusText,
            String currentAccountId,
            List<ConversationSummary> conversations,
            Map<String, List<ChatMessage>> messagesByConversation,
            String selectedConversationId,
            String draftInput,
            FocusPane focusPane
    ) {
    }
}
