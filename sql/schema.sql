-- Code Agent 数据库结构
-- 用法：mysql -uroot -p < docs/schema.sql
-- Docker 方式：首次 docker compose up 时自动执行

CREATE DATABASE IF NOT EXISTS code_agent_db
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE code_agent_db;

-- 审查任务主表
DROP TABLE IF EXISTS review_issue;
DROP TABLE IF EXISTS review_task;

CREATE TABLE review_task
(
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    user_id      VARCHAR(64)  NOT NULL COMMENT '提交人标识',
    language     VARCHAR(32)  NOT NULL DEFAULT 'java' COMMENT '代码语言',
    code_content MEDIUMTEXT COMMENT '待审查的代码原文',
    status       VARCHAR(20)  NOT NULL DEFAULT 'PROCESSING' COMMENT 'PROCESSING / COMPLETED / FAILED',
    final_report MEDIUMTEXT COMMENT '汇总后的 Markdown 报告',
    created_at   DATETIME     NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_user_created (user_id, created_at),
    KEY idx_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='审查任务主表';

-- 问题明细表
CREATE TABLE review_issue
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '明细ID',
    task_id     BIGINT       NOT NULL COMMENT '所属任务ID',
    agent_role  VARCHAR(32)  NOT NULL COMMENT '发现问题的专家角色',
    risk_level  VARCHAR(16) COMMENT '风险等级：高/中/低',
    location    VARCHAR(512) COMMENT '问题所在方法或代码块',
    description TEXT COMMENT '问题描述',
    suggestion  TEXT COMMENT '修复建议',
    created_at  DATETIME     NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_task (task_id),
    CONSTRAINT fk_issue_task FOREIGN KEY (task_id) REFERENCES review_task (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='审查问题明细表';
