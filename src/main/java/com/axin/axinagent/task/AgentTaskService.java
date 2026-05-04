package com.axin.axinagent.task;

import com.axin.axinagent.agent.model.AgentState;
import com.axin.axinagent.config.RocketMqConfig;
import com.axin.axinagent.infrastructure.db.entity.AgentTaskEntity;
import com.axin.axinagent.infrastructure.db.mapper.AgentTaskMapper;
import com.axin.axinagent.observability.MetricsService;
import com.axin.axinagent.task.model.AgentTaskMessage;
import com.axin.axinagent.task.model.AgentTaskStatus;
import com.axin.axinagent.task.model.TaskSubmitResponse;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Agent 任务服务：负责任务提交、状态查询、取消操作。
 * <p>
 * 任务状态流转：PENDING → RUNNING → FINISHED / ERROR / TIMEOUT / CANCELLED
 */
@Slf4j
@Service
public class AgentTaskService {

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private AgentTaskMapper agentTaskMapper;

    @Resource
    private MetricsService metricsService;

    /**
     * 提交 Agent 任务：生成 taskId，写入 Redis PENDING 状态，发送 RocketMQ 消息。
     *
     * @param userMessage 用户输入
     * @return 任务提交响应（含 taskId）
     */
    public TaskSubmitResponse submitTask(String userMessage) {
        return submitTask(userMessage, "SINGLE");
    }

    /**
     * 提交 Agent 任务（指定模式）。
     *
     * @param userMessage 用户输入
     * @param mode        执行模式：SINGLE / MULTI
     * @return 任务提交响应（含 taskId）
     */
    public TaskSubmitResponse submitTask(String userMessage, String mode) {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        long now = System.currentTimeMillis();
        String finalMode = mode == null ? "SINGLE" : mode;

        // 1. 写入 Redis 任务状态（Hash）
        String sessionKey = RocketMqConfig.REDIS_SESSION_PREFIX + taskId;
        Map<String, String> sessionMap = Map.of(
                "taskId", taskId,
                "state", AgentState.PENDING.name(),
                "message", userMessage,
                "createTime", String.valueOf(now),
                "errorMessage", "",
                "mode", finalMode
        );
        stringRedisTemplate.opsForHash().putAll(sessionKey, sessionMap);
        stringRedisTemplate.expire(sessionKey, RocketMqConfig.REDIS_TTL_SECONDS, TimeUnit.SECONDS);

        // 2. 同步写 DB（冷数据持久化）
        try {
            LocalDateTime nowTime = LocalDateTime.now();
            AgentTaskEntity entity = AgentTaskEntity.builder()
                    .taskId(taskId)
                    .state(AgentState.PENDING.name())
                    .message(userMessage)
                    .mode(finalMode)
                    .errorMessage("")
                    .result(null)
                    .createTime(nowTime)
                    .updateTime(nowTime)
                    .build();
            agentTaskMapper.insert(entity);
        } catch (Exception e) {
            // DB 持久化失败不影响主流程
            log.error("提交任务写入 DB 失败，继续执行主流程: taskId={}", taskId, e);
        }

        // 3. 发送 RocketMQ 消息
        AgentTaskMessage taskMessage = new AgentTaskMessage(taskId, userMessage, now, finalMode);
        rocketMQTemplate.convertAndSend(RocketMqConfig.AGENT_TASK_TOPIC, taskMessage);
        log.info("任务已提交: taskId={}, mode={}, message={}", taskId, finalMode, userMessage);

        return new TaskSubmitResponse(taskId, "任务已提交（" + finalMode + " 模式），请使用 taskId 订阅进度");
    }

    /**
     * 查询任务状态。
     *
     * @param taskId 任务 ID
     * @return 任务状态 VO，若不存在返回 null
     */
    public AgentTaskStatus getTaskStatus(String taskId) {
        String sessionKey = RocketMqConfig.REDIS_SESSION_PREFIX + taskId;
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(sessionKey);
        if (!entries.isEmpty()) {
            return toTaskStatus(entries);
        }

        // Redis miss 回退 DB
        AgentTaskEntity entity = agentTaskMapper.selectById(taskId);
        if (entity == null) {
            return null;
        }

        // 回填 Redis，避免重复穿透
        backfillRedis(taskId, entity);
        return toTaskStatus(entity);
    }

    /**
     * 取消任务：在 Redis 写入取消标记，消费端检测后停止执行。
     *
     * @param taskId 任务 ID
     * @return true 表示标记成功，false 表示任务不存在
     */
    public boolean cancelTask(String taskId) {
        String sessionKey = RocketMqConfig.REDIS_SESSION_PREFIX + taskId;
        Boolean exists = stringRedisTemplate.hasKey(sessionKey);
        if (Boolean.FALSE.equals(exists)) {
            return false;
        }
        // 写入取消标记
        String cancelKey = RocketMqConfig.REDIS_CANCEL_PREFIX + taskId;
        stringRedisTemplate.opsForValue().set(cancelKey, "1", RocketMqConfig.REDIS_TTL_SECONDS, TimeUnit.SECONDS);
        log.info("任务取消标记已设置: taskId={}", taskId);
        return true;
    }

    /**
     * 更新任务状态（供消费端调用）。
     *
     * @param taskId       任务 ID
     * @param state        新状态
     * @param errorMessage 错误信息（可为空）
     */
    public void updateTaskState(String taskId, AgentState state, String errorMessage) {
        updateTaskState(taskId, state, errorMessage, null);
    }

    /**
     * 更新任务状态并可选保存最终结果（供消费端调用）。
     *
     * @param taskId       任务 ID
     * @param state        新状态
     * @param errorMessage 错误信息（可为空）
     * @param result       任务最终结果（可为空）
     */
    public void updateTaskState(String taskId, AgentState state, String errorMessage, String result) {
        String sessionKey = RocketMqConfig.REDIS_SESSION_PREFIX + taskId;
        String oldState = String.valueOf(stringRedisTemplate.opsForHash().get(sessionKey, "state"));

        stringRedisTemplate.opsForHash().put(sessionKey, "state", state.name());
        if (errorMessage != null) {
            stringRedisTemplate.opsForHash().put(sessionKey, "errorMessage", errorMessage);
        }
        // 刷新 TTL
        stringRedisTemplate.expire(sessionKey, RocketMqConfig.REDIS_TTL_SECONDS, TimeUnit.SECONDS);

        try {
            LocalDateTime now = LocalDateTime.now();
            AgentTaskEntity updateEntity = new AgentTaskEntity();
            updateEntity.setTaskId(taskId);
            updateEntity.setState(state.name());
            updateEntity.setUpdateTime(now);
            if (errorMessage != null) {
                updateEntity.setErrorMessage(errorMessage);
            }
            if (result != null) {
                updateEntity.setResult(result);
            }
            int affectedRows = agentTaskMapper.updateById(updateEntity);

            if (affectedRows == 0) {
                Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(sessionKey);
                AgentTaskEntity fallbackInsert = AgentTaskEntity.builder()
                        .taskId(taskId)
                        .state(state.name())
                        .message((String) entries.getOrDefault("message", ""))
                        .mode((String) entries.getOrDefault("mode", "SINGLE"))
                        .errorMessage(errorMessage == null ? "" : errorMessage)
                        .result(result)
                        .createTime(now)
                        .updateTime(now)
                        .build();
                agentTaskMapper.insert(fallbackInsert);
            }
        } catch (Exception e) {
            // DB 写失败不影响主流程
            log.error("更新任务状态写入 DB 失败，继续执行主流程: taskId={}, state={}", taskId, state, e);
        }

        // 可观测性指标统计
        handleMetricsOnStateChange(oldState, state);

        log.info("任务状态更新: taskId={}, state={}", taskId, state);
    }

    /**
     * 查询任务历史（按更新时间倒序分页）。
     */
    public IPage<AgentTaskEntity> listTaskHistory(long current, long pageSize) {
        Page<AgentTaskEntity> page = new Page<>(Math.max(current, 1), Math.max(pageSize, 1));
        QueryWrapper<AgentTaskEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("update_time");
        return agentTaskMapper.selectPage(page, queryWrapper);
    }

    /**
     * 检测任务是否被取消。
     *
     * @param taskId 任务 ID
     * @return true 表示已被取消
     */
    public boolean isCancelled(String taskId) {
        String cancelKey = RocketMqConfig.REDIS_CANCEL_PREFIX + taskId;
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(cancelKey));
    }

    /**
     * 推送单条进度消息到 Redis List 缓冲。
     *
     * @param taskId  任务 ID
     * @param content 步骤结果内容
     */
    public void pushProgress(String taskId, String content) {
        String progressKey = RocketMqConfig.REDIS_PROGRESS_PREFIX + taskId;
        stringRedisTemplate.opsForList().rightPush(progressKey, content);
        stringRedisTemplate.expire(progressKey, RocketMqConfig.REDIS_TTL_SECONDS, TimeUnit.SECONDS);
    }

    private void handleMetricsOnStateChange(String oldState, AgentState newState) {
        AgentState old = parseState(oldState);

        if (newState == AgentState.RUNNING && old != AgentState.RUNNING) {
            metricsService.incrTaskTotal();
        }

        if (newState == AgentState.FINISHED && old != AgentState.FINISHED) {
            metricsService.incrTaskFinished();
            return;
        }
        if (newState == AgentState.ERROR && old != AgentState.ERROR) {
            metricsService.incrTaskError();
            return;
        }
        if (newState == AgentState.CANCELLED && old != AgentState.CANCELLED) {
            metricsService.incrTaskCancelled();
        }
    }

    private AgentState parseState(String state) {
        if (state == null || state.isBlank() || "null".equalsIgnoreCase(state)) {
            return null;
        }
        try {
            return AgentState.valueOf(state);
        } catch (Exception e) {
            return null;
        }
    }

    private AgentTaskStatus toTaskStatus(Map<Object, Object> entries) {
        return AgentTaskStatus.builder()
                .taskId((String) entries.get("taskId"))
                .state((String) entries.get("state"))
                .message((String) entries.get("message"))
                .createTime(Long.parseLong((String) entries.getOrDefault("createTime", "0")))
                .errorMessage((String) entries.getOrDefault("errorMessage", ""))
                .build();
    }

    private AgentTaskStatus toTaskStatus(AgentTaskEntity entity) {
        return AgentTaskStatus.builder()
                .taskId(entity.getTaskId())
                .state(entity.getState())
                .message(entity.getMessage())
                .createTime(toEpochMillis(entity.getCreateTime()))
                .errorMessage(entity.getErrorMessage() == null ? "" : entity.getErrorMessage())
                .build();
    }

    private void backfillRedis(String taskId, AgentTaskEntity entity) {
        String sessionKey = RocketMqConfig.REDIS_SESSION_PREFIX + taskId;
        Map<String, String> sessionMap = new HashMap<>();
        sessionMap.put("taskId", entity.getTaskId());
        sessionMap.put("state", entity.getState());
        sessionMap.put("message", entity.getMessage() == null ? "" : entity.getMessage());
        sessionMap.put("createTime", String.valueOf(toEpochMillis(entity.getCreateTime())));
        sessionMap.put("errorMessage", entity.getErrorMessage() == null ? "" : entity.getErrorMessage());
        sessionMap.put("mode", entity.getMode() == null ? "SINGLE" : entity.getMode());
        stringRedisTemplate.opsForHash().putAll(sessionKey, sessionMap);
        stringRedisTemplate.expire(sessionKey, RocketMqConfig.REDIS_TTL_SECONDS, TimeUnit.SECONDS);
    }

    private long toEpochMillis(LocalDateTime time) {
        if (time == null) {
            return 0L;
        }
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
