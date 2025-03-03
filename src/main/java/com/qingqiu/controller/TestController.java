package com.qingqiu.controller;

import com.qingqiu.AiManager;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/ai")
@Slf4j
public class TestController {

   @Resource
    AiManager aiManager;

    /**
     * 发送问题
     *
     * @param message 问题
     * @return 星火大模型的回答
     */
    @GetMapping("/sendMessage")
    public SseEmitter sendQuestion(String message) throws IOException {
      return  aiManager.sendMessage(message);
    }
}
