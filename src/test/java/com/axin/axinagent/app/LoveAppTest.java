package com.axin.axinagent.app;

import cn.hutool.core.util.RandomUtil;
import com.axin.axinagent.app.model.LoveReport;
import com.axin.axinagent.rag.RagStrategy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Slf4j
class LoveAppTest {

	@Resource
	private LoveApp loveApp;

	/**
	 * 测试 doChat 方法
	 */
	@Test
	void doChat() {
		String chatId = RandomUtil.randomString(10);

		System.out.println("------------------------------------------------------------------------------------------------------------");
		String message = "你好，我是阿新";
		String response = loveApp.doChat(message, chatId);
		System.out.println(response);

		System.out.println("------------------------------------------------------------------------------------------------------------");
		message = "我单身，想找对象";
		response = loveApp.doChat(message, chatId);
		System.out.println(response);

		System.out.println("------------------------------------------------------------------------------------------------------------");
		message = "我叫什么";
		response = loveApp.doChat(message, chatId);
		System.out.println(response);
	}

	/**
	 * 测试 doChatWithReport 方法
	 */
	@Test
	void doChatWithReport() {
		String chatId = RandomUtil.randomString(10);

		System.out.println("------------------------------------------------------------------------------------------------------------");
		String message = "你好，我是阿新";
		LoveReport report = loveApp.doChatWithReport(message, chatId);
		printLoveReport(report);
	}

	/**
	 * 测试 doChatWithRag 方法
	 */
	@Test
	void doChatWithRag() {
		String chatId = RandomUtil.randomString(10);

		System.out.println("------------------------------------------------------------------------------------------------------------");
		String message = "你好，我是阿新，我感觉婚后状态不是很好";

		// 使用本地 Redis 策略
		LoveReport localReport = loveApp.doChatWithRag(message, chatId, RagStrategy.LOCAL);
		System.out.println("===== 本地 Redis RAG =====");
		printLoveReport(localReport);

		// 使用云端 DashScope 策略
		LoveReport cloudReport = loveApp.doChatWithRag(message, chatId, RagStrategy.CLOUD);
		System.out.println("===== 云端 DashScope RAG =====");
		printLoveReport(cloudReport);
	}

	/**
	 * 测试 doChatWithTools 方法
	 */
	@Test
	void doChatWithTools() {
		String chatId = RandomUtil.randomString(10);
		String message = "你好，我是阿新，我感觉婚后状态不是很好";
		String answer = loveApp.doChatWithTools(message, chatId);
		log.info("doChatWithTools 测试结果: {}", answer);
		Assertions.assertNotNull(answer);
	}

	/**
	 * 测试 doChatWithMcp 方法
	 */
	@Test
	void doChatWithMcp() {
		// 测试图片搜索 MCP
		String chatId = RandomUtil.randomString(10);
		String message = "帮我搜索一些情侣的图片";
		String answer = loveApp.doChatWithMcp(message, chatId);
		log.info("doChatWithMcp 测试结果: {}", answer);
		Assertions.assertNotNull(answer);
	}

	/**
	 * 测试 doChatByStream 方法
	 */
	@Test
	void doChatByStream() {
		String chatId = RandomUtil.randomString(10);
		Flux<String> response = loveApp.doChatByStream("你好，我是阿新", chatId);
		// 使用 blockLast() 阻塞等待流完成，避免测试提前退出
		response.doOnNext(System.out::print)
				.doOnComplete(() -> System.out.println("\n[流式响应完成]"))
				.blockLast();
		Assertions.assertNotNull(response);
	}

	/**
	 * 打印恋爱报告
	 * @param report 恋爱报告
	 */
	public void printLoveReport(LoveReport report) {
		System.out.println("======== 恋爱报告 ========");
		System.out.println("标题：" + report.getTitle());
		System.out.println("建议：");
		for (String suggestion : report.getSuggestions()) {
			System.out.println("- " + suggestion);
		}
	}

}