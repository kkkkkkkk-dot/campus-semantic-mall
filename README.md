<img width="2048" height="1024" alt="image" src="https://github.com/user-attachments/assets/db8b4b92-aa01-4f40-ad01-19c1e5b001b9" />
# Campus Semantic Mall（校园智能商品交易平台）

## 一、项目简介

本项目基于开源商城 **Mall4j** 进行二次开发，面向校园场景，提供商品浏览、下单、支付等完整电商链路。

在原项目基础上，我新增了一个 **AI 语义搜索接口**，解决传统关键词搜索（MySQL LIKE）无法理解用户模糊意图的问题。例如用户搜索“熬夜复习用的东西”，原版只能匹配标题里包含这些字的商品，而 AI 语义搜索可以理解用户意图，返回更相关的商品。

**核心工作是用离线评估量化排序质量，并据此改进排序策略。**

---

## 二、我做了什么

### 1. 新增 AI 语义搜索接口

- 接口路径：`GET /search/semanticSearch`
- 流程：MySQL LIKE 召回 Top50 候选商品 → 调用 DeepSeek 做语义重排 → 返回排序后的结果。
- 保留原接口 `/search/searchProdPage` 不变，向前兼容。

### 2. 发现并修复了 AI 排序的业务缺陷

- **问题**：纯 AI 语义排序会把「1 元旧款 iPhone」排到第一位。
- **原因**：LLM 只看标题语义，完全忽略价格、销量等业务信号。
- **解决方案**：把融合排序从 Prompt 里抢回代码里，由代码负责确定性加权：
  - 语义分：DeepSeek 返回的排名，归一化到 0-1。
  - 价格异常惩罚：低于候选均价一定比例的商品扣分。
  - 销量加权：销量越高的商品分越高。
  - 上架时间衰减：越新上架的商品分越高。
  - `finalScore = α×语义分 + β×价格分 + γ×销量分 + δ×时间分`
  - 权重 α/β/γ/δ 放在 `application.yml` 里，可配置、可调优。

### 3. 搭建离线评估基准

- 自建 30 条 `<搜索词, 理想商品排序>` 人工标注集。
- 用 **NDCG@10** 和 **Top-3 命中率** 作为排序质量指标。
- 对比三种方案：MySQL LIKE 基线 / 纯语义排序 / 融合排序。
- 评估脚本支持 mock DeepSeek 返回，保证可复现。

### 4. 工程降级与可观测

- 对 DeepSeek 调用加超时、重试、Token 上限控制。
- AI 调用失败时自动回退原 MySQL 结果，保证接口可用性不劣于原接口。
- 记录 AI 调用成功率、平均耗时、降级比例，便于监控。

---

## 三、技术栈

- **后端**：Spring Boot 4 / MyBatis-Plus / MySQL / Redis
- **AI**：Spring AI 2.0 / DeepSeek（OpenAI 兼容接口）
- **部署**：Docker / Docker Compose

---

## 四、如何运行

### 1. 环境准备

- JDK 17+
- MySQL 8.0+
- Redis 7.0+
- DeepSeek API Key

### 2. 启动依赖服务

项目根目录提供了 `docker-compose.yml`，一键启动 MySQL 和 Redis：

```bash
docker-compose up -d
