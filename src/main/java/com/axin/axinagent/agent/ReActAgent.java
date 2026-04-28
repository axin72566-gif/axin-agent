package com.axin.axinagent.agent;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * ReAct（Reasoning and Acting）模式的抽象代理类，实现思考-行动的迭代循环。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public abstract class ReActAgent extends BaseAgent {

	/**
	 * 分析当前状态并决定下一步行动。
	 *
	 * @return true 表示需要执行行动，false 表示无需行动
	 */
	public abstract boolean think();

	/**
	 * 执行已决定的行动。
	 *
	 * @return 行动执行结果
	 */
	public abstract String act();

	/**
	 * 执行单个步骤：先思考，再按需行动。
	 *
	 * @return 步骤执行结果
	 */
	@Override
	public String step() {
		try {
			boolean shouldAct = think();
			if (!shouldAct) {
				return "思考完成 - 无需行动";
			}
			return act();
		} catch (Exception e) {
			log.error("步骤执行失败", e);
			return "步骤执行失败: " + e.getMessage();
		}
	}
}

