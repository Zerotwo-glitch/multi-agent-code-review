package com.example.codeagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("review_issue")
public class ReviewIssue {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private String agentRole;
    private String riskLevel;
    private String description;
    private String suggestion;
    private String location;
    private LocalDateTime createdAt;
}