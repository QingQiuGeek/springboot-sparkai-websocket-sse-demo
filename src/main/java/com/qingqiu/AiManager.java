package com.qingqiu;

import cn.hutool.core.util.StrUtil;
import com.qingqiu.component.StreamClient;
import com.qingqiu.config.SparkAiConfig;
import com.qingqiu.dto.MsgDTO;
import com.qingqiu.listener.MyWebSocketListener;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.WebSocket;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;


@Component
@Slf4j
public class AiManager {

  @Resource
  private StreamClient streamClient;

  @Resource
  private SparkAiConfig sparkAiConfig;

  public SseEmitter sendMessage(String message){

    SseEmitter emitter = new SseEmitter(60_000L); // 60秒超时

    // 无效消息处理
    if (StrUtil.isBlank(message)) {
      try {
        emitter.send("无效问题，请重新输入");
        emitter.complete();
      } catch (IOException e) {
        log.error("SSE发送失败", e);
      }
      return emitter;
    }
    // 令牌检查
    if (!streamClient.operateToken(StreamClient.GET_TOKEN_STATUS)) {
      try {
        emitter.send("当前大模型连接数过多，请稍后再试");
        emitter.complete();
      } catch (IOException e) {
        log.error("SSE发送失败", e);
      }
      return emitter;
    }


    // 创建消息对象
    MsgDTO msgDTO = MsgDTO.createUserMsg(message);
    // 创建监听器
    MyWebSocketListener listener = new MyWebSocketListener(emitter,()->{
      // 完成回调
      streamClient.operateToken(StreamClient.BACK_TOKEN_STATUS);
    });
    // 发送问题给大模型，生成 websocket 连接
    WebSocket webSocket = streamClient.sendMsg(UUID.randomUUID().toString().substring(0, 10), Collections.singletonList(msgDTO), listener);
    if (webSocket == null) {
      streamClient.operateToken(StreamClient.BACK_TOKEN_STATUS);
      try {
        emitter.send("系统内部错误，请联系管理员");
        emitter.complete();
      } catch (IOException e) {
        log.error("SSE发送失败", e);
      }
      return emitter;
    }

    // 配置SSE超时和完成处理
    emitter.onTimeout(() -> {
      log.warn("SSE超时关闭");
      webSocket.close(1000, "超时关闭");
      listener.cleanup();
    });

    emitter.onCompletion(() -> {
      log.info("SSE连接完成");
      webSocket.close(1000, "正常关闭");
      listener.cleanup();
    });


    return emitter;
    }
  }

