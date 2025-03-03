package com.qingqiu.listener;

import com.alibaba.fastjson.JSONObject;
import com.qingqiu.dto.MsgDTO;
import com.qingqiu.dto.ResponseDTO;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.jetbrains.annotations.NotNull;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
public class MyWebSocketListener extends WebSocketListener {

    private final SseEmitter emitter;
    private final Runnable completionCallback;
    private WebSocket webSocket;

    public MyWebSocketListener(SseEmitter emitter, Runnable completionCallback) {
        this.emitter = emitter;
        this.completionCallback = completionCallback;
    }

    @Override
    public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
        super.onOpen(webSocket, response);
    }

    @Override
    public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
        ResponseDTO responseData = JSONObject.parseObject(text, ResponseDTO.class);

        // 错误处理
        if (responseData.getHeader().getCode() != 0) {
            log.error("错误码：{}，信息：{}",
                responseData.getHeader().getCode(),
                responseData.getHeader().getMessage());

            try {
                emitter.send("大模型响应错误，请稍后再试");
                emitter.complete();
            } catch (IOException e) {
                log.error("SSE发送失败", e);
            }
            return;
        }

        // 流式拼接并发送
        StringBuilder content = new StringBuilder();
        for (MsgDTO msgDTO : responseData.getPayload().getChoices().getText()) {
            content.append(msgDTO.getContent());
        }

        try {
            // 发送增量内容
            if (content.length() > 0) {
                emitter.send(content.toString());
            }
        } catch (IOException e) {
            log.error("SSE发送失败", e);
        }

        // 最终消息处理
        if (responseData.getHeader().getStatus() == 2) {
            try {
                emitter.complete();
            } finally {
                cleanup();
            }
        }
    }

    @Override
    public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, @Nullable Response response) {
        log.error("WebSocket连接失败", t);
        try {
            emitter.send("连接异常，请重试");
            emitter.completeWithError(t);
        } catch (IOException e) {
            log.error("SSE发送失败", e);
        }
        cleanup();
    }

   public void cleanup() {
        if (completionCallback != null) {
            completionCallback.run();
        }
        if (webSocket != null) {
            webSocket.close(1000, "清理关闭");
        }
    }

    @Override
    public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
        super.onClosed(webSocket, code, reason);
    }
}
