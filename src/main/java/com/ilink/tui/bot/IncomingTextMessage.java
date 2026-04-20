package com.ilink.tui.bot;

import java.time.Instant;

/**
 * 规范化后的入站文本消息。
 *
 * @param userId    对方用户 ID
 * @param text      文本内容
 * @param timestamp 发送时间
 */
public record IncomingTextMessage(String userId, String text, Instant timestamp) {
}
