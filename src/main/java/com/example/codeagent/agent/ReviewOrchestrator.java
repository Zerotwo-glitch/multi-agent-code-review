package com.example.codeagent.agent;

import com.example.codeagent.agent.model.AgentReviewResult;
import com.example.codeagent.entity.ReviewIssue;
import com.example.codeagent.entity.ReviewTask;
import com.example.codeagent.mapper.ReviewIssueMapper;
import com.example.codeagent.mapper.ReviewTaskMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ReviewOrchestrator {

    private final List<ExpertAgent> allExperts;
    private final ChatClient chatClient;
    private final ReviewTaskMapper taskMapper;
    private final ReviewIssueMapper issueMapper;
    private final SseService sseService;

    // ================= 双层线程池 =================
    // 1. 调度池：只负责接收请求和流程控制，不干重活
    // 2. 专家池：专门并发调用大模型，大小受本地模型显存限制
    private final ExecutorService taskExecutor;
    private final ExecutorService expertExecutor;

    public ReviewOrchestrator(List<ExpertAgent> allExperts,
                              ChatClient.Builder builder,
                              ReviewTaskMapper taskMapper,
                              ReviewIssueMapper issueMapper,
                              SseService sseService,
                              @Value("${app.review.task-pool-size:20}") int taskPoolSize,
                              @Value("${app.review.expert-pool-size:5}") int expertPoolSize) {
        // 防御性过滤：正常情况下 List<ExpertAgent> 只会注入 ExpertAgent 的实现，
        // 这里排除掉可能因扫描范围过宽而混进来的 MyBatis 代理对象
        this.allExperts = allExperts.stream()
                .filter(expert -> !expert.getClass().getName().contains("MybatisMapperProxy"))
                .toList();
        this.chatClient = builder.build();
        this.taskMapper = taskMapper;
        this.issueMapper = issueMapper;
        this.sseService = sseService;

        this.taskExecutor = Executors.newFixedThreadPool(taskPoolSize);
        this.expertExecutor = Executors.newFixedThreadPool(expertPoolSize);
        log.info("已加载专家 {} 个，调度池 {} 线程，专家池 {} 线程", allExperts.size(), taskPoolSize, expertPoolSize);
    }

    /**
     * 第一步：同步创建任务记录并返回 TaskId
     */
    public Long createTask(String code, String userId, String language) {
        ReviewTask task = new ReviewTask();
        task.setUserId(userId);
        task.setLanguage(language);
        task.setCodeContent(code);
        task.setStatus("PROCESSING");
        task.setCreatedAt(LocalDateTime.now());
        taskMapper.insert(task);

        if (task.getId() == null) {
            throw new RuntimeException("任务创建失败，未能获取任务ID");
        }
        return task.getId();
    }

    /**
     * 第二步：把任务提交到调度线程池异步执行。
     * 提交失败时直接把任务置为 FAILED，避免状态永远停在 PROCESSING。
     */
    public void processAsync(Long taskId, String code) {
        try {
            taskExecutor.submit(() -> doProcess(taskId, code));
        } catch (RejectedExecutionException e) {
            log.error("任务 {} 提交被拒绝，线程池已关闭或已满", taskId, e);
            markFailed(taskId);
        }
    }

    private void doProcess(Long taskId, String code) {
        try {
            sseService.sendMessage(taskId, "任务已创建，正在启动多 Agent 并发审查...");

            // 1. 并发调度专家（子任务用 expertExecutor 跑，避免父子任务争抢同一个池）
            List<CompletableFuture<AgentReviewResult>> futures = allExperts.stream()
                    .map(expert -> CompletableFuture.supplyAsync(() -> {
                        sseService.sendMessage(taskId, "Agent [" + expert.getRoleName() + "] 正在扫描代码...");

                        AgentReviewResult result = expert.review(code);

                        if (result != null && result.isHasIssue() && result.getIssues() != null) {
                            sseService.sendMessage(taskId, "[" + expert.getRoleName() + "] 发现 " + result.getIssues().size() + " 处问题。");
                        } else {
                            sseService.sendMessage(taskId, "[" + expert.getRoleName() + "] 扫描完毕，未发现该维度问题。");
                        }
                        return result;
                    }, expertExecutor))
                    .toList();

            // 2. 等待所有专家执行完毕
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // 3. 收集结果
            List<AgentReviewResult> results = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(res -> res != null)
                    .toList();

            if (results.isEmpty()) {
                throw new RuntimeException("所有 Agent 审查均失败，请检查大模型服务状态。");
            }

            // 4. 落库明细表
            int totalIssues = 0;
            for (AgentReviewResult res : results) {
                if (res.isHasIssue() && res.getIssues() != null) {
                    for (AgentReviewResult.IssueDetail issue : res.getIssues()) {
                        ReviewIssue entity = new ReviewIssue();
                        entity.setTaskId(taskId);
                        entity.setAgentRole(res.getAgentRole());
                        entity.setRiskLevel(issue.getRiskLevel());
                        entity.setDescription(issue.getDescription());
                        entity.setSuggestion(issue.getSuggestion());
                        entity.setLocation(issue.getLocation());
                        entity.setCreatedAt(LocalDateTime.now());
                        issueMapper.insert(entity);
                        totalIssues++;
                    }
                }
            }
            sseService.sendMessage(taskId, "已将 " + totalIssues + " 处问题保存至数据库。");

            // 5. 汇总报告
            sseService.sendMessage(taskId, "正在生成最终审查报告...");
            String finalReport = generateFinalReport(results);

            // 6. 更新主表状态
            ReviewTask task = taskMapper.selectById(taskId);
            if (task != null) {
                task.setFinalReport(finalReport);
                task.setStatus("COMPLETED");
                taskMapper.updateById(task);
            }

            sseService.sendMessage(taskId, "审查流程全部完成。");

        } catch (Exception e) {
            log.error("任务 {} 审查流程发生异常", taskId, e);
            sseService.sendMessage(taskId, "审查流程发生异常，请查看后台日志。");
            markFailed(taskId);
        } finally {
            sseService.complete(taskId);
        }
    }

    /**
     * 异常兜底：保证状态不会永远卡在 PROCESSING
     */
    private void markFailed(Long taskId) {
        try {
            ReviewTask task = taskMapper.selectById(taskId);
            if (task != null) {
                task.setStatus("FAILED");
                taskMapper.updateById(task);
            }
        } catch (Exception ex) {
            log.error("更新任务 {} 失败状态时发生二次异常", taskId, ex);
        }
    }

    private String generateFinalReport(List<AgentReviewResult> results) {
        StringBuilder issuesText = new StringBuilder();
        for (AgentReviewResult res : results) {
            if (res.isHasIssue() && res.getIssues() != null) {
                for (AgentReviewResult.IssueDetail issue : res.getIssues()) {
                    issuesText.append("- 风险等级: [").append(issue.getRiskLevel()).append("] ")
                            .append("- [").append(res.getAgentRole()).append("发现] ")
                            .append(issue.getDescription())
                            .append(" (建议: ").append(issue.getSuggestion()).append(")\n");
                }
            }
        }

        if (issuesText.isEmpty()) {
            return "审查完成：代码质量良好，未发现明显问题。";
        }

        String managerPrompt = """
            你是一个技术主管。你的专家团队对一段代码提出了以下问题（已包含他们评估的风险等级）：
            %s

            请将这些问题整理成一份结构清晰的 Markdown 审查报告。
            【严格约束】：必须严格按照提供的[风险等级]进行分类汇总，绝对不能擅自修改风险等级！
            如果专家定为 LOW，报告中必须归类为 LOW。
            """.formatted(issuesText.toString());

        return chatClient.prompt().user(managerPrompt).call().content();
    }

    /**
     * 应用关闭时优雅停止两个线程池，避免残留线程阻塞停机
     */
    @PreDestroy
    public void shutdown() {
        taskExecutor.shutdown();
        expertExecutor.shutdown();
        try {
            if (!taskExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                taskExecutor.shutdownNow();
            }
            if (!expertExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                expertExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            taskExecutor.shutdownNow();
            expertExecutor.shutdownNow();
        }
        log.info("审查线程池已关闭");
    }
}
