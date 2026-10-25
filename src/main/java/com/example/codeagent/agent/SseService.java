package com.example.codeagent.agent;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE 推送服务。
 *
 * <p>除了维护 taskId -> SseEmitter 的映射，还缓存每个任务已推送过的消息，
 * 用于前端晚连接时回放，避免"提交完任务再连流"这段时间的进度丢失。</p>
 */
@Service
public class SseService {

    /** 每个任务最多缓存多少条历史消息，防止长任务把内存吃满 */
    private static final int MAX_HISTORY = 200;

    /** 连接超时时间：10 分钟，给本地大模型留足时间 */
    private static final long TIMEOUT_MILLIS = 10 * 60 * 1000L;

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Map<Long, List<String>> histories = new ConcurrentHashMap<>();

    /**
     * 前端建立连接时调用：创建 emitter，并把之前已经推送过的消息补发一遍。
     */
    public SseEmitter createEmitter(Long taskId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);

        for (String message : histories.getOrDefault(taskId, List.of())) {
            try {
                emitter.send(SseEmitter.event().data(message));
            } catch (IOException e) {
                break;
            }
        }

        emitters.put(taskId, emitter);
        emitter.onCompletion(() -> emitters.remove(taskId));
        emitter.onTimeout(() -> emitters.remove(taskId));
        emitter.onError(e -> emitters.remove(taskId));
        return emitter;
    }

    /**
     * 向指定任务推送一条消息。即使前端尚未连接，消息也会被缓存下来。
     */
    public void sendMessage(Long taskId, String message) {
        List<String> history = histories.computeIfAbsent(taskId, k -> new CopyOnWriteArrayList<>());
        history.add(message);
        if (history.size() > MAX_HISTORY) {
            history.subList(0, history.size() - MAX_HISTORY).clear();
        }

        SseEmitter emitter = emitters.get(taskId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().data(message));
            } catch (IOException e) {
                emitters.remove(taskId);
            }
        }
    }

    /**
     * 任务结束时关闭连接并清理缓存。
     */
    public void complete(Long taskId) {
        SseEmitter emitter = emitters.get(taskId);
        if (emitter != null) {
            emitter.complete();
            emitters.remove(taskId);
        }
        histories.remove(taskId);
    }
}
