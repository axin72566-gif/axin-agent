package com.axin.axinagent.observability;

import org.springframework.core.NamedThreadLocal;

/**
 * 任务上下文：基于 ThreadLocal 在同线程内传递 taskId。
 */
public final class TaskContext {

    private static final ThreadLocal<String> TASK_ID_HOLDER = new NamedThreadLocal<>("obs-task-id");

    private TaskContext() {
    }

    public static void setTaskId(String taskId) {
        TASK_ID_HOLDER.set(taskId);
    }

    public static String getTaskId() {
        return TASK_ID_HOLDER.get();
    }

    public static void removeTaskId() {
        TASK_ID_HOLDER.remove();
    }
}
