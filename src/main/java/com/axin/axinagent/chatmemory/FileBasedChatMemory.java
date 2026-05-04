package com.axin.axinagent.chatmemory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import lombok.extern.slf4j.Slf4j;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于 Kryo 文件序列化的对话记忆存储实现（实现 ChatMemoryRepository）。
 * <p>Kryo 实例非线程安全，使用 {@link ThreadLocal} 确保每个线程独享一个实例。</p>
 */
@Slf4j
public class FileBasedChatMemory implements ChatMemoryRepository {

	private final String baseDir;

	/**
	 * 每线程独享 Kryo 实例，避免并发序列化冲突。
	 */
	private static final ThreadLocal<Kryo> KRYO_THREAD_LOCAL = ThreadLocal.withInitial(() -> {
		Kryo kryo = new Kryo();
		kryo.setRegistrationRequired(false);
		kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
		return kryo;
	});

	public FileBasedChatMemory(String dir) {
		this.baseDir = dir;
		File baseDirFile = new File(dir);
		if (!baseDirFile.exists()) {
			try {
				Files.createDirectories(baseDirFile.toPath());
			} catch (IOException e) {
				log.error("Failed to create base directory: {}", baseDirFile.getPath(), e);
			}
		}
	}

	@Override
	public List<String> findConversationIds() {
		File dir = new File(baseDir);
		if (!dir.exists() || !dir.isDirectory()) {
			return new ArrayList<>();
		}
		File[] files = dir.listFiles((d, name) -> name.endsWith(".kryo"));
		if (files == null) {
			return new ArrayList<>();
		}
		return Arrays.stream(files)
				.map(f -> f.getName().replace(".kryo", ""))
				.collect(Collectors.toList());
	}

	@Override
	public List<Message> findByConversationId(String conversationId) {
		File file = getConversationFile(conversationId);
		if (!file.exists()) {
			return new ArrayList<>();
		}
		try (Input input = new Input(new FileInputStream(file))) {
			return KRYO_THREAD_LOCAL.get().readObject(input, ArrayList.class);
		} catch (IOException e) {
			log.error("Failed to read conversation file: {}", file.getPath(), e);
			return new ArrayList<>();
		}
	}

	@Override
	public void saveAll(String conversationId, List<Message> messages) {
		File file = getConversationFile(conversationId);
		try (Output output = new Output(new FileOutputStream(file))) {
			KRYO_THREAD_LOCAL.get().writeObject(output, messages);
		} catch (IOException e) {
			log.error("Failed to write conversation file: {}", file.getPath(), e);
		}
	}

	@Override
	public void deleteByConversationId(String conversationId) {
		File file = getConversationFile(conversationId);
		if (file.exists()) {
			try {
				Files.delete(file.toPath());
			} catch (IOException e) {
				log.error("Failed to delete conversation file: {}", file.getPath(), e);
			}
		}
	}

	private File getConversationFile(String conversationId) {
		return new File(baseDir, conversationId + ".kryo");
	}
}
