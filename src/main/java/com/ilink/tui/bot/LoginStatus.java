package com.ilink.tui.bot;

/**
 * 登录流程状态。
 */
public enum LoginStatus {
    /**
     * 已恢复或完成登录。
     */
    RESTORED,

    /**
     * 需要展示二维码等待扫码。
     */
    QR_REQUIRED,

    /**
     * 登录流程失败。
     */
    FAILED
}
