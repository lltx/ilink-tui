package com.ilink.tui;

import com.ilink.tui.app.AppController;
import com.ilink.tui.app.AppState;
import com.ilink.tui.app.IlinkTuiApp;
import com.ilink.tui.bot.BotGateway;

/**
 * iLink TUI 应用入口。
 */
public final class Main {

    private static final String FORCE_LOGIN_FLAG = "--force-login";

    private Main() {
    }

    /**
     * 启动 iLink TUI 应用。
     *
     * @param args 命令行参数，当前仅支持 {@code --force-login}
     * @throws Exception 终端初始化或运行失败时抛出
     */
    public static void main(String[] args) throws Exception {
        BotGateway botGateway = new BotGateway();
        if (shouldForceLogin(args)) {
            botGateway.clearStoredCredentials();
        }

        AppState state = new AppState();
        AppController controller = new AppController(state, botGateway);
        registerShutdownHook(controller);

        new IlinkTuiApp(state, controller).run();
    }

    /**
     * 判断是否要求忽略已有凭证并强制重新登录。
     *
     * @param args 启动参数
     * @return 包含 {@code --force-login} 时返回 {@code true}
     */
    static boolean shouldForceLogin(String[] args) {
        if (args == null) {
            return false;
        }
        for (String arg : args) {
            if (FORCE_LOGIN_FLAG.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static void registerShutdownHook(AppController controller) {
        Runtime.getRuntime().addShutdownHook(new Thread(controller::shutdown, "ilink-tui-shutdown"));
    }
}
