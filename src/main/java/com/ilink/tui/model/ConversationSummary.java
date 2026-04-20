package com.ilink.tui.model;

import java.time.Instant;

/**
 * 会话摘要信息。
 *
 * @param conversationId 会话唯一标识，第一版直接使用对方用户 ID
 * @param title          会话标题，第一版与 {@code conversationId} 相同
 * @param lastActivity   最近活跃时间
 */
public record ConversationSummary(String conversationId, String title, Instant lastActivity) {
}
