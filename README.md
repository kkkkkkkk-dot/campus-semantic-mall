<img width="2048" height="1024" alt="image" src="https://github.com/user-attachments/assets/db8b4b92-aa01-4f40-ad01-19c1e5b001b9" />
# 校园语义购物中心（基于 Mall4j 的 AI 搜索增强版）

## 一、项目简介

基于开源商城 Mall4j 二次开发，在保留原有商品、SKU、购物车、订单等交易能力的基础上，新增 AI 语义搜索接口，解决传统关键词搜索无法理解用户模糊意图的问题。

核心目标不是简单地“让大模型直接排序”，而是把大模型的语义理解能力和本地可解释、可配置的业务排序规则结合起来：DeepSeek 只负责理解搜索词和商品标题之间的语义关系，价格异常惩罚、销量归一化、时间排序等确定性规则由 Java 负责。

## 二、我做了什么

1. 新增 `/search/semanticSearch` 接口：MySQL LIKE 召回 Top50 候选 → DeepSeek 语义重排 → 返回排序结果。
2. 发现 MySQL LIKE 按 `update_time` 排序、与相关性无关，低价清仓商品常被排到第一。
3. 引入 DeepSeek 语义重排后，测试集 NDCG@10 从 `0.548954` 提升到 `0.795857`。
4. 进一步发现 DeepSeek 只看标题、拿不到价格字段，无法识别“标题干净但价格异常”的商品。
5. 在 Java 侧增加确定性融合排序：语义名次分 + 价格异常惩罚 + 销量对数归一化 + 上架时间归一化，并支持配置权重。
6. 用训练集调参后，在独立测试集上 NDCG@10 进一步提升到 `0.879860`，异常低价商品 Top3 占比从 `63.33%` 降到 `0%`。
7. 增加离线评估模块，支持训练集/测试集切换、Mock 语义快照、NDCG@10、Top-3 命中率和异常低价 Top3 占比对比。

## 三、评估结果

| 方案 | NDCG@10 | 异常低价 Top3 占比 |
|---|---:|---:|
| LIKE 基线 | 0.548954 | 63.33% |
| 纯语义排序 | 0.795857 | 16.67% |
| 融合排序 | **0.879860** | **0%** |

以上数字来自独立测试集 `ai-evaluation/data/queries-test.jsonl`，与调参使用的训练集 `queries-train.jsonl` 不重叠。

最终融合参数：

```text
alpha=0.50
beta=0.35
gamma=0.15
delta=0.00
price-anomaly-ratio=0.50
```

## 四、技术栈

Spring Boot 4 / MyBatis-Plus / MySQL / Redis / Spring AI 2.0 / DeepSeek / Docker

## 五、如何运行

1. 启动 MySQL 和 Redis（根目录有 `docker-compose.yml`）。
2. 配置环境变量 `DEEPSEEK_API_KEY`。
3. 启动 `yami-shop-api` 模块下的 `ApiApplication`。
4. 调用接口：

```http
GET http://localhost:8086/search/semanticSearch?prodName=手机
```

当 DeepSeek 不可用、API Key 未配置或响应异常时，接口自动降级为原始 MySQL 查询结果，不影响前台搜索可用性。

## 六、评估模块

训练集：

```text
ai-evaluation/data/queries-train.jsonl
```

测试集：

```text
ai-evaluation/data/queries-test.jsonl
```

抓取 DeepSeek 语义快照：

```powershell
cd ai-evaluation
mvn exec:java "-Dexec.args=--capture-snapshot=true --data-dir=data --base-url=https://api.deepseek.com/v1 --model=deepseek-chat"
```

在训练集上评估：

```powershell
mvn exec:java "-Dexec.args=--data-dir=data --results-dir=results --queries-file=queries-train.jsonl --provider=mock --alpha=0.50 --beta=0.35 --gamma=0.15 --delta=0.00 --price-anomaly-ratio=0.50 --assert-hard-negatives-out=true"
```

在测试集上报最终结果：

```powershell
mvn exec:java "-Dexec.args=--data-dir=data --results-dir=results --queries-file=queries-test.jsonl --provider=mock --alpha=0.50 --beta=0.35 --gamma=0.15 --delta=0.00 --price-anomaly-ratio=0.50 --assert-hard-negatives-out=true"
```

结果文件：

```text
ai-evaluation/results/summary-report.csv
ai-evaluation/results/per-query.csv
ai-evaluation/results/fusion-warnings.txt
```
## 压测结果（单机，50 并发，JMeter）

| 场景 | QPS | 平均响应 | 错误率 |
|---|---|---|---|
| 无 AI 基线 | 109.2 | 333 ms | 0% |
| AI 成功 | 26.2 | 1099 ms | 0% |
| AI 降级 | 72.0 | 537 ms | 0% |

结论：AI 调用是性能瓶颈；降级后 QPS 恢复到基线的 66%，错误率 0%，验证降级策略有效。
## 本地验证截图

### 原版 MySQL LIKE 排序（异常低价商品排前）
<img width="1453" height="901" alt="image" src="https://github.com/user-attachments/assets/f0c429c0-d729-4eb2-b31f-6b127497e49a" />
<img width="1503" height="666" alt="image" src="https://github.com/user-attachments/assets/70e6567f-b23d-435d-a5a9-a65a6a060c3b" />



### AI 语义重排 + 融合排序（异常低价商品被压后）
<img width="1443" height="791" alt="image" src="https://github.com/user-attachments/assets/2a422c31-a048-4a02-aaf6-8f50083c751a" />
<img width="1191" height="663" alt="image" src="https://github.com/user-attachments/assets/cf5153bf-5909-44f4-acef-9c4f21a6bf51" />


## 七、后续优化方向

- 召回层从 MySQL LIKE 升级为 Elasticsearch 或向量检索。
- 引入 Rerank 模型对 Top-K 做精排。
- 扩充训练集和测试集，覆盖更多类目、品牌和口语表达。
- 将评估模块集成到 CI，每次改动后自动跑回归测试。
- 将线上搜索日志回流为人工标注样本，持续优化融合权重。
