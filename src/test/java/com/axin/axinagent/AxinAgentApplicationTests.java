package com.axin.axinagent;


import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;

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

	@Test
	void readKryoFile() {
		// ====================== 你只需要改这里 ======================
		// 你的 .kryo 文件完整路径
		String kryoFilePath="D:\\java\\java_1\\axin-agent\\axin-agent\\chat-memory\\yHmGkZoBxP.kryo";
		// ==========================================================

		Kryo kryo=new Kryo();
		kryo.setRegistrationRequired(false);
		kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());

		try (Input input=new Input(new FileInputStream(kryoFilePath))) {
			// 反序列化读取文件
			@SuppressWarnings("unchecked")
			List<Message> messages=(List<Message>) kryo.readObject(input, ArrayList.class);

			System.out.println("======== 读取成功！文件内容如下 ========");
			for (int i=0; i < messages.size(); i++) {
				Message msg=messages.get(i);
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

