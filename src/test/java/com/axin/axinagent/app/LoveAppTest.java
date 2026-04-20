package com.axin.axinagent.app;

import cn.hutool.core.util.RandomUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Slf4j
class LoveAppTest {

	@Resource
	private LoveApp loveApp;

	@Test
	void doChat() {
	}

	@Test
	void doChatWithReport() {
	}

	@Test
	void doChatWithRag() {
	}

	@Test
	void doChatWithTools() {
	}

	@Test
	void doChatWithMcp() {
		// 测试图片搜索 MCP
		String chatId = RandomUtil.randomString(10);
		String message = "帮我搜索一些情侣的图片";
		String answer = loveApp.doChatWithMcp(message, chatId);
		log.info("doChatWithMcp 测试结果: {}", answer);
		Assertions.assertNotNull(answer);
	}

}