package com.axin.axinagent.demo.learning;

import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
class ReactLearningTest {

	@Resource
	private ReactLearning reactLearning;

	@Test
	void test1() throws GraphRunnerException {
		reactLearning.test();
	}
}