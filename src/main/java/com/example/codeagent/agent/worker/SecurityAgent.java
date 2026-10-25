package com.example.codeagent.agent.worker;

import com.example.codeagent.agent.ExpertAgent;
import com.example.codeagent.agent.model.AgentReviewResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class SecurityAgent implements ExpertAgent {

    private final ChatClient chatClient;

    public SecurityAgent(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public String getRoleName() {
        return "安全专家";
    }

    @Override
    public AgentReviewResult review(String code) {
        String prompt = """
        你是一个资深的 Java 代码安全架构师。你的任务是对提供的代码进行“外科手术式”的安全审查。
        
        【审查流程】
        1. 追踪外部输入的流向，检查是否存在 SQL 注入、XSS、硬编码、明文密码比对、敏感信息打印。
        2. 检查越权操作等安全问题。
        
        【重点排查】
        - SQL 注入：字符串直接拼接 SQL 变量。
        - 敏感数据：硬编码密码、直接打印明文密码或密钥到日志。
        
        【输出格式要求】
        请严格输出 JSON 对象，不要包含任何 Markdown 标记。格式如下：
        {
          "hasIssue": true/false,
          "issues": [
            {
              "location": "指出具体的方法名或代码块，如 'login() 方法' 或 '拼接 SQL 的代码行'",
              "category": "SECURITY",
              "riskLevel": "高/中/低",
              "description": "问题描述，说明为什么是安全问题",
              "suggestion": "具体的修复建议"
            }
          ]
        }
        如果未发现安全问题，hasIssue 填 false，issues 填 []。
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