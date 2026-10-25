package com.example.codeagent.agent;

import com.example.codeagent.agent.model.AgentReviewResult;

public interface ExpertAgent {
    /**
     * 获取当前专家的角色名称
     */
    String getRoleName();

    /**
     * 执行具体的审查逻辑
     * @param code 待审查的代码
     * @return 结构化的审查结果
     */
    AgentReviewResult review(String code);
}