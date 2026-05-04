package com.axin.axinagent.config;

/**
 * RocketMQ 常量配置：Topic 名称和 Consumer Group
 */
public class RocketMqConfig {

    /**
     * Agent 任务 Topic
     */
    public static final String AGENT_TASK_TOPIC = "AGENT_TASK_TOPIC";

    /**
     * Agent 任务 Consumer Group
     */
    public static final String AGENT_TASK_CONSUMER_GROUP = "axin-agent-consumer";

    // Redis Key 前缀
    /**
     * 任务会话状态 Hash：存储 taskId/state/message/createTime/errorMessage
     * key: agent:session:{taskId}
     */
    public static final String REDIS_SESSION_PREFIX = "agent:session:";

    /**
     * 任务进度缓冲 List：每步执行结果 RPUSH 至此
     * key: agent:progress:{taskId}
     */
    public static final String REDIS_PROGRESS_PREFIX = "agent:progress:";

    /**
     * 取消标记 String：存在即表示任务被取消
     * key: agent:cancel:{taskId}
     */
    public static final String REDIS_CANCEL_PREFIX = "agent:cancel:";

    /**
     * 进度结束标记，推送到进度 List 表示任务已结束
     */
    public static final String PROGRESS_END_MARKER = "__TASK_END__";

    /**
     * Redis Key TTL：1 小时（秒）
     */
    public static final long REDIS_TTL_SECONDS = 3600L;

    private RocketMqConfig() {
    }
}
