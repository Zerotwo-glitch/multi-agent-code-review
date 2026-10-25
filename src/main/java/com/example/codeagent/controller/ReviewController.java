package com.example.codeagent.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.codeagent.agent.ReviewOrchestrator;
import com.example.codeagent.agent.SseService;
import com.example.codeagent.entity.ReviewIssue;
import com.example.codeagent.entity.ReviewTask;
import com.example.codeagent.mapper.ReviewIssueMapper;
import com.example.codeagent.mapper.ReviewTaskMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/review")
public class ReviewController {

    private final ReviewOrchestrator orchestrator;
    private final ReviewTaskMapper taskMapper;
    private final ReviewIssueMapper issueMapper;
    private final SseService sseService;

    /**
     * 单次审查允许的最大代码长度。
     * 本地 14B 模型的上下文有限（OLLAMA_NUM_CTX），超长请求会直接失败，
     * 与其让用户等一分钟拿到"审查异常"，不如在入口就拒绝并给出明确提示。
     */
    private final int maxCodeLength;

    public ReviewController(ReviewOrchestrator orchestrator,
                            ReviewTaskMapper taskMapper,
                            ReviewIssueMapper issueMapper,
                            SseService sseService,
                            @Value("${app.review.max-code-length:12000}") int maxCodeLength) {
        this.orchestrator = orchestrator;
        this.taskMapper = taskMapper;
        this.issueMapper = issueMapper;
        this.sseService = sseService;
        this.maxCodeLength = maxCodeLength;
    }

    /**
     * 提交审查任务：同步落库拿到 taskId，异步执行审查，立刻返回。
     */
    @PostMapping("/multi-agent")
    public String multiAgentReview(@RequestBody String code,
                                   @RequestParam(defaultValue = "u1") String userId,
                                   @RequestParam(defaultValue = "java") String language) {
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "待审查代码不能为空");
        }
        if (code.length() > maxCodeLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "代码长度 " + code.length() + " 字符，超过单次审查上限 " + maxCodeLength + " 字符，请拆分后分批提交");
        }

        Long taskId = orchestrator.createTask(code, userId, language);
        orchestrator.processAsync(taskId, code);
        return "审查任务已提交，任务ID为：" + taskId;
    }

    /**
     * 根据任务ID查询详细审查报告
     */
    @GetMapping("/detail/{taskId}")
    public Map<String, Object> getReviewDetail(@PathVariable Long taskId) {
        ReviewTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在：" + taskId);
        }

        List<ReviewIssue> issues = issueMapper.selectList(
                new LambdaQueryWrapper<ReviewIssue>()
                        .eq(ReviewIssue::getTaskId, taskId)
        );

        Map<String, Object> result = new HashMap<>();
        result.put("taskInfo", task);
        result.put("issueList", issues);
        return result;
    }

    /**
     * 订阅任务进度（SSE）
     */
    @GetMapping(value = "/stream/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamProgress(@PathVariable Long taskId) {
        return sseService.createEmitter(taskId);
    }
}
