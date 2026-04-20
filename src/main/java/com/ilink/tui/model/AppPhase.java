package com.ilink.tui.model;

/**
 * 应用顶层页面阶段。
 */
public enum AppPhase {
    /**
     * 需要展示二维码并等待扫码登录。
     */
    AUTH_REQUIRED,

    /**
     * 已登录，展示聊天主界面。
     */
    CHAT_READY
}
