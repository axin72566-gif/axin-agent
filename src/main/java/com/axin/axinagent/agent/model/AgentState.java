package com.axin.axinagent.agent.model;

/**
 * 代理执行状态的枚举类
 */
public enum AgentState {

	/**
	 * 待执行状态：任务已提交到队列，等待消费
	 */
	PENDING,

	/**
	 * 空闲状态
	 */
	IDLE,

	/**
	 * 运行中状态
	 */
	RUNNING,

	/**
	 * 暂停状态：任务暂时挂起，可恢复
	 */
	PAUSED,

	/**
	 * 已完成状态
	 */
	FINISHED,

	/**
	 * 错误状态
	 */
	ERROR,

	/**
	 * 超时状态：任务执行超过最大时限
	 */
	TIMEOUT,

	/**
	 * 已取消状态：用户主动取消任务
	 */
	CANCELLED
}

