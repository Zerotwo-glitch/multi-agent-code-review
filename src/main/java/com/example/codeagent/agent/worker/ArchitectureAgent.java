package com.example.codeagent.agent.worker;

import com.example.codeagent.agent.ExpertAgent;
import com.example.codeagent.agent.model.AgentReviewResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class ArchitectureAgent implements ExpertAgent {

    private final ChatClient chatClient;

    public ArchitectureAgent(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public String getRoleName() {
        return "架构专家";
    }

    @Override
    public AgentReviewResult review(String code) {
        String prompt = """
        你是一个资深的企业级 Java 架构师。你的任务是对提供的代码进行“系统级”的架构审查。
        你不必纠结于细枝末节的语法错误（那是 Bug 猎手的工作），你需要关注的是：数据一致性、外部中间件交互、性能隐患以及 API 接口规范。
        
        【审查流程】
        1. 数据一致性检查：代码是否混合了关系型数据库（如 MySQL）与外部中间件（如 Redis、ES、向量库 VectorStore、MQ）的操作？本地 @Transactional 是否能保证外部中间件在异常时回滚？是否存在数据不一致风险？
        2. 性能与扩展性隐患：是否存在硬编码的分页限制（如 topK(10000)、limit 1000）导致数据截断或内存溢出（OOM）？是否存在死循环、N+1 查询？
        3. 接口契约与规范：Controller 层是否直接返回硬编码的 String（如 "删除成功"），而没有使用标准的 HTTP 状态码和统一的 Result 封装？是否将业务异常直接抛给前端？
        
        【重点排查】
        - 分布式事务：跨中间件操作的伪事务问题。
        - 批量操作：盲目拉取全量数据导致的内存溢出或数据遗漏。
        - RESTful 规范：不规范的返回值与异常处理。
        
        【输出格式要求】
        请严格输出 JSON 对象，不要包含任何 Markdown 标记。格式如下：
        {
          "hasIssue": true/false,
          "issues": [
            {
              "location": "指出具体的方法名或代码块，如 'deleteKnowledgeBase() 方法'",
              "category": "ARCHITECTURE | PERFORMANCE | API_DESIGN",
              "riskLevel": "高/中/低",
              "description": "详细说明架构隐患以及可能导致的生产事故后果",
              "suggestion": "具体的架构级修复建议（如引入最终一致性、修改返回体封装等）"
            }
          ]
        }
        如果未发现架构层面的问题，hasIssue 填 false，issues 填 []。
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