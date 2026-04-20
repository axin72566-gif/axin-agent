package com.axin.axinagent;

import cn.hutool.core.util.RandomUtil;
import com.axin.axinagent.app.LoveApp;
import com.axin.axinagent.model.LoveReport;
import com.axin.axinagent.rag.RagStrategy;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.ai.chat.messages.Message;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest
@Slf4j
class AxinAgentApplicationTests {

	@Resource
	private LoveApp loveApp;

	// 打印恋爱报告
	public void printLoveReport(LoveReport report) {
		System.out.println("======== 恋爱报告 ========");
		System.out.println("标题：" + report.getTitle());
		System.out.println("建议：");
		for (String suggestion : report.getSuggestions()) {
			System.out.println("- " + suggestion);
		}
	}

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

	@Test
	void doChatWithReport() {
		String chatId = RandomUtil.randomString(10);

		System.out.println("------------------------------------------------------------------------------------------------------------");
		String message = "你好，我是阿新";
		LoveReport report = loveApp.doChatWithReport(message, chatId);
		printLoveReport(report);
	}

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

	@Test
	void readKryoFile() {
		// ====================== 你只需要改这里 ======================
		// 你的 .kryo 文件完整路径
		String kryoFilePath = "D:\\java\\java_1\\axin-agent\\axin-agent\\chat-memory\\yHmGkZoBxP.kryo";
		// ==========================================================

		Kryo kryo = new Kryo();
		kryo.setRegistrationRequired(false);
		kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());

		try (Input input = new Input(new FileInputStream(kryoFilePath))) {
			// 反序列化读取文件
			@SuppressWarnings("unchecked")
			List<Message> messages = (List<Message>) kryo.readObject(input, ArrayList.class);

			System.out.println("======== 读取成功！文件内容如下 ========");
			for (int i = 0; i < messages.size(); i++) {
				Message msg = messages.get(i);
				System.out.println("第 " + (i + 1) + " 条消息");
				System.out.println("类型：" + msg.getMessageType());
				System.out.println("内容：" + msg.getText());
				System.out.println("----------------------------------");
			}

		} catch (Exception e) {
			log.error("读取失败！", e);
		}
	}
}

