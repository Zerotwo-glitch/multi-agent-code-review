package com.example.codeagent.agent.model;

import lombok.Data;
import java.util.List;

@Data
public class AgentReviewResult {
    private String agentRole;//专家名
    private boolean hasIssue;//是否发现问题
    private List<IssueDetail> issues;//问题明细

    @Data
    public static class IssueDetail {
        private String location;//位置
        private String category;//分类
        private String riskLevel;//风险等级
        private String description;//问题描述
        private String suggestion;//修改建议
    }
}