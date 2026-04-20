package com.ilink.tui.bot;

/**
 * 登录阶段向上层汇报的状态结果。
 *
 * @param status        登录状态
 * @param qrContent     二维码原始内容，通常是登录链接
 * @param statusMessage 需要展示给用户的状态文案
 * @param accountId     登录成功后的 bot 账号 ID
 */
public record LoginResult(
        LoginStatus status,
        String qrContent,
        String statusMessage,
        String accountId
) {

    /**
     * 创建“已登录”结果。
     *
     * @param accountId     当前 bot 账号 ID
     * @param statusMessage 状态提示
     * @return 登录成功结果
     */
    public static LoginResult restored(String accountId, String statusMessage) {
        return new LoginResult(LoginStatus.RESTORED, null, statusMessage, accountId);
    }

    /**
     * 创建“需要扫码”结果。
     *
     * @param qrContent     二维码原始内容
     * @param statusMessage 状态提示
     * @return 等待扫码结果
     */
    public static LoginResult qrRequired(String qrContent, String statusMessage) {
        return new LoginResult(LoginStatus.QR_REQUIRED, qrContent, statusMessage, null);
    }

    /**
     * 创建“登录失败”结果。
     *
     * @param statusMessage 失败提示
     * @return 失败结果
     */
    public static LoginResult failed(String statusMessage) {
        return new LoginResult(LoginStatus.FAILED, null, statusMessage, null);
    }
}
