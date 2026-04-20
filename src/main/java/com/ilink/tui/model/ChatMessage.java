package com.ilink.tui.model;

import java.time.Instant;

/**
 * 聊天窗口中的单条消息。
 *
 * @param conversationId 所属会话 ID
 * @param senderLabel    发送方展示标签
 * @param text           文本消息内容
 * @param outbound       是否为当前用户主动发送
 * @param timestamp      消息时间戳
 */
public record ChatMessage(
        String conversationId,
        String senderLabel,
        String text,
        boolean outbound,
        Instant timestamp
) {
}
