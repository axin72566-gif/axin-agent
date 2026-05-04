package com.axin.axinagent.task;

import com.axin.axinagent.config.RocketMqConfig;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * SSE 进度推送服务。
 * <p>
 * 通过轮询 Redis Progress List 将 Agent 每步执行结果实时推送给前端。
 * 检测到结束标记或任务超时时自动关闭 SSE 连接。
 */
@Slf4j
@Service
public class ProgressSseService {

    /**
     * SSE 连接超时：10 分钟（毫秒）
     */
    private static final long SSE_TIMEOUT_MS = 600_000L;

    /**
     * Redis BLPOP 阻塞超时：2 秒（等待新消息到来）
     */
    private static final long BLPOP_TIMEOUT_SECONDS = 2L;

    /**
     * 轮询最大等待轮次（防止任务死亡后 SSE 永久挂起）
     * BLPOP_TIMEOUT_SECONDS * MAX_EMPTY_ROUNDS = 最长等待时间
     */
    private static final int MAX_EMPTY_ROUNDS = 300; // 最多等待 600 秒

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 订阅指定任务的实时执行进度，返回 SSE Emitter。
     *
     * @param taskId 任务 ID
     * @return SseEmitter，连接建立后持续推送步骤结果直至任务结束
     */
    public SseEmitter subscribeProgress(String taskId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        String progressKey = RocketMqConfig.REDIS_PROGRESS_PREFIX + taskId;

        emitter.onTimeout(() -> log.warn("SSE 连接超时: taskId={}", taskId));
        emitter.onError(e -> log.warn("SSE 连接异常: taskId={}, error={}", taskId, e.getMessage()));

        CompletableFuture.runAsync(() -> {
            int emptyRounds = 0;
            try {
                while (emptyRounds < MAX_EMPTY_ROUNDS) {
                    // BLPOP 阻塞等待进度消息，避免无限 CPU 空转
                    var result = stringRedisTemplate.opsForList()
                            .leftPop(progressKey, BLPOP_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                    if (result == null) {
                        // 本轮无消息，计数并继续等待
                        emptyRounds++;
                        continue;
                    }

                    // 重置空轮计数
                    emptyRounds = 0;

                    // 检测结束标记
                    if (RocketMqConfig.PROGRESS_END_MARKER.equals(result)) {
                        sendSseEvent(emitter, "[任务已完成]");
                        emitter.complete();
                        log.info("SSE 推送完毕，任务结束: taskId={}", taskId);
                        return;
                    }

                    // 推送步骤内容
                    sendSseEvent(emitter, result);
                }

                // 超过最大等待轮次，主动关闭
                sendSseEvent(emitter, "[连接超时，任务可能仍在执行，请查询状态]");
                emitter.complete();
                log.warn("SSE 轮询超时关闭: taskId={}", taskId);

            } catch (Exception e) {
                log.error("SSE 进度推送异常: taskId={}", taskId, e);
                try {
                    sendSseEvent(emitter, "推送异常: " + e.getMessage());
                    emitter.complete();
                } catch (Exception ignored) {
                    emitter.completeWithError(e);
                }
            }
        });

        return emitter;
    }

    private void sendSseEvent(SseEmitter emitter, String data) throws IOException {
        emitter.send(SseEmitter.event().data(data));
    }
}
