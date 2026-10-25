package com.example.codeagent.agent.worker;

import com.example.codeagent.agent.ExpertAgent;
import com.example.codeagent.agent.model.AgentReviewResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component // 必须加，交给 Spring 管理
public class BugHunterAgent implements ExpertAgent {

    private final ChatClient chatClient;

    public BugHunterAgent(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public String getRoleName() {
        return "BUG专家";
    }

    @Override
    public AgentReviewResult review(String code) {
        String prompt = """
        你是一个资深的 Java 代码质量与 Bug 猎手。你的任务是对提供的代码进行“外科手术式”的深度审查。
        
        【审查流程】
        1. 逐行检查空指针（NPE）、越界（ArrayIndexOutOfBounds）、自动拆箱 NPE。
        2. 检查资源泄漏（ResultSet/Statement 关闭）、异常处理、魔法值、线程安全。
        
        【重点排查】
        - 健壮性：list.get(0) 未判空、数组越界 i<=size、Integer 判等。
        - 资源：流未关闭、数据库游标耗尽。
        - 规范：吞掉异常、魔法值（如 id==0）。
        
        【输出格式要求】
        请严格输出严格输出 JSON 对象，不要包含任何 Markdown 标记。格式如下：
        {
          "hasIssue": true/false,
          "issues": [
            {
              "location": "指出具体的方法名或代码块，如 'process() 方法' 或 'processArray() 的 for 循环'",
              "category": "BUG | PERFORMANCE | CODE_SMELL",
              "riskLevel": "高/中/低",
              "description": "问题描述，说明为什么这是Bug或规范问题",
              "suggestion": "具体的修复建议"
            }
          ]
        }
        如果未发现问题，hasIssue 填 false，issues 填 []。
        """;
        try {
            AgentReviewResult result = chatClient.prompt()
                    .system(prompt + "\n请严格输出 JSON 格式，不要包含任何 Markdown 代码块标记。")
                    .user("待审查代码：\n" + code)
                    .call()
                    .entity(AgentReviewResult.class);
            result.setAgentRole(getRoleName());
            return result;
        } catch (Exception e) {
            System.err.println(getRoleName() + " 审查失败: " + e.getMessage());
            return null;
        }
    }
}