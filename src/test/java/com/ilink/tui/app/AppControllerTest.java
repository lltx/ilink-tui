package com.ilink.tui.app;

import com.ilink.tui.bot.BotGateway;
import com.ilink.tui.bot.IncomingTextMessage;
import com.ilink.tui.bot.LoginResult;
import com.ilink.tui.bot.LoginStatus;
import com.ilink.tui.model.AppPhase;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppControllerTest {

    @Test
    void bootstrapRestoresSessionAndEntersChatReady() {
        TestBotGateway gateway = new TestBotGateway();
        gateway.restoreResult = true;
        gateway.currentAccountId = "bot-account";
        AppState state = new AppState();
        AppController controller = new AppController(state, gateway);

        controller.bootstrap();

        AppState.Snapshot snapshot = state.snapshot();
        assertEquals(AppPhase.CHAT_READY, snapshot.phase());
        assertEquals("bot-account", snapshot.currentAccountId());
        assertTrue(gateway.pollingStarted);
    }

    @Test
    void bootstrapWithoutSessionShowsQrLoginState() {
        TestBotGateway gateway = new TestBotGateway();
        gateway.restoreResult = false;
        gateway.beginLoginResult = new LoginResult(
                LoginStatus.QR_REQUIRED,
                "qr-content",
                "等待扫码",
                null
        );
        AppState state = new AppState();
        AppController controller = new AppController(state, gateway);

        controller.bootstrap();

        AppState.Snapshot snapshot = state.snapshot();
        assertEquals(AppPhase.AUTH_REQUIRED, snapshot.phase());
        assertEquals("qr-content", snapshot.qrContent());
        assertEquals("等待扫码", snapshot.authStatusText());
    }

    @Test
    void incomingMessageCreatesConversationAndSelectsIt() {
        TestBotGateway gateway = new TestBotGateway();
        AppState state = new AppState();
        AppController controller = new AppController(state, gateway);

        gateway.emitLogin(new LoginResult(LoginStatus.RESTORED, null, "已登录", "bot-account"));
        gateway.emitIncoming(new IncomingTextMessage("user-1", "hello", Instant.parse("2026-04-17T10:15:30Z")));

        AppState.Snapshot snapshot = state.snapshot();
        assertEquals(AppPhase.CHAT_READY, snapshot.phase());
        assertEquals(1, snapshot.conversations().size());
        assertEquals("user-1", snapshot.selectedConversationId());
        assertEquals(1, snapshot.messagesByConversation().get("user-1").size());
    }

    @Test
    void sendCurrentInputAppendsOutboundMessageAndClearsDraft() {
        TestBotGateway gateway = new TestBotGateway();
        AppState state = new AppState();
        AppController controller = new AppController(state, gateway);

        gateway.emitLogin(new LoginResult(LoginStatus.RESTORED, null, "已登录", "bot-account"));
        gateway.emitIncoming(new IncomingTextMessage("user-1", "hello", Instant.parse("2026-04-17T10:15:30Z")));
        controller.updateDraftInput("reply");

        controller.sendCurrentInput();

        AppState.Snapshot snapshot = state.snapshot();
        assertEquals("user-1", gateway.lastSentConversationId);
        assertEquals("reply", gateway.lastSentText);
        assertEquals("", snapshot.draftInput());
        assertEquals(2, snapshot.messagesByConversation().get("user-1").size());
    }

    @Test
    void sendFailureKeepsDraftAndShowsStatus() {
        TestBotGateway gateway = new TestBotGateway();
        gateway.throwOnSend = true;
        AppState state = new AppState();
        AppController controller = new AppController(state, gateway);

        gateway.emitLogin(new LoginResult(LoginStatus.RESTORED, null, "已登录", "bot-account"));
        gateway.emitIncoming(new IncomingTextMessage("user-1", "hello", Instant.parse("2026-04-17T10:15:30Z")));
        controller.updateDraftInput("reply");

        controller.sendCurrentInput();

        AppState.Snapshot snapshot = state.snapshot();
        assertEquals("reply", snapshot.draftInput());
        assertTrue(snapshot.globalStatusText().contains("发送失败"));
        assertEquals(1, snapshot.messagesByConversation().get("user-1").size());
    }

    @Test
    void loginRequirementClearsRuntimeConversationsAndReturnsToAuthPage() {
        TestBotGateway gateway = new TestBotGateway();
        AppState state = new AppState();
        AppController controller = new AppController(state, gateway);

        gateway.emitLogin(new LoginResult(LoginStatus.RESTORED, null, "已登录", "bot-account"));
        gateway.emitIncoming(new IncomingTextMessage("user-1", "hello", Instant.parse("2026-04-17T10:15:30Z")));

        gateway.emitLogin(new LoginResult(LoginStatus.QR_REQUIRED, "new-qr", "登录失效，请重新扫码", null));

        AppState.Snapshot snapshot = state.snapshot();
        assertEquals(AppPhase.AUTH_REQUIRED, snapshot.phase());
        assertTrue(snapshot.conversations().isEmpty());
        assertTrue(snapshot.messagesByConversation().isEmpty());
        assertEquals("new-qr", snapshot.qrContent());
        assertFalse(snapshot.authStatusText().isBlank());
    }

    private static final class TestBotGateway extends BotGateway {

        private boolean restoreResult;
        private boolean pollingStarted;
        private boolean throwOnSend;
        private String currentAccountId;
        private String lastSentConversationId;
        private String lastSentText;
        private LoginResult beginLoginResult;

        @Override
        public boolean tryRestoreSession() {
            return restoreResult;
        }

        @Override
        public String currentAccountId() {
            return currentAccountId;
        }

        @Override
        public LoginResult beginLogin() {
            return beginLoginResult;
        }

        @Override
        public void startPolling() {
            pollingStarted = true;
        }

        @Override
        public void sendText(String conversationId, String text) {
            if (throwOnSend) {
                throw new IllegalStateException("send failed");
            }
            lastSentConversationId = conversationId;
            lastSentText = text;
        }

        void emitIncoming(IncomingTextMessage message) {
            emitIncomingMessage(message);
        }

        void emitLogin(LoginResult result) {
            emitLoginResult(result);
        }
    }
}
