package com.axin.axinagent.rag;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * RAG 策略上下文
 * Spring 启动时自动收集所有 RagAdvisorStrategy 实现，
 * 根据 RagStrategy 枚举分发对应的 Advisor
 */
@Component
public class RagStrategyContext {

    /**
     * key: RagStrategy 枚举，value: 对应策略实现
     * 利用 Spring 自动注入 List<RagAdvisorStrategy> 收集所有实现
     */
    private final Map<RagStrategy, RagAdvisorStrategy> strategyMap;

    public RagStrategyContext(List<RagAdvisorStrategy> strategies) {
        strategyMap = strategies.stream()
                .collect(Collectors.toMap(RagAdvisorStrategy::getStrategy, Function.identity()));
    }

    /**
     * 根据策略类型获取对应 Advisor
     *
     * @param strategy RAG 策略枚举
     * @return 对应的 Advisor
     */
    public Advisor getAdvisor(RagStrategy strategy) {
        RagAdvisorStrategy ragAdvisorStrategy = strategyMap.get(strategy);
        if (ragAdvisorStrategy == null) {
            throw new IllegalArgumentException("不支持的 RAG 策略：" + strategy);
        }
        return ragAdvisorStrategy.getAdvisor();
    }
}
