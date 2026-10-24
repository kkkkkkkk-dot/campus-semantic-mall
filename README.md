<img width="2048" height="1024" alt="image" src="https://github.com/user-attachments/assets/db8b4b92-aa01-4f40-ad01-19c1e5b001b9" />
# 校园语义购物中心（AI 语义搜索与排序优化）

基于 [Mall4j] 开源商城二次开发的 AI 语义搜索项目。
新增 `/search/semanticSearch` 接口：先 MySQL LIKE 召回 Top50 候选，再由 DeepSeek 做语义重排，
Java 侧做确定性业务融合，失败自动降级回原结果；并用离线评估量化排序质量，结合 Redis 缓存保证高并发可用性。

## 项目亮点

- **离线评估驱动**：自建训练集/测试集，用 NDCG@10 等指标对比三套方案，用数据而非感觉做决策
- **确定性融合排序**：DeepSeek 只输出语义顺序，价格/销量/时间等业务信号在 Java 侧加权融合，可控、可解释、可调权
- **缓存与降级**：Redis 缓存搜索结果（防穿透/击穿/雪崩），AI 失败自动回退 MySQL，接口始终可用
- **可观测与压测**：记录 AI 调用成功率/降级比例/耗时，单机压测覆盖四种场景

## 技术栈

Spring Boot 4 / Spring AI 2 / DeepSeek / MyBatis-Plus / Redis / MySQL / JMeter

## 核心链路

```text
用户搜索词
  -> MySQL LIKE 召回 Top50 候选
  -> 查 Redis 缓存，命中直接返回
  -> 未命中：DeepSeek 输出语义 ID 顺序
  -> Java 侧确定性融合（语义分 + 价格惩罚 + 销量 + 时间）
  -> 写入 Redis 缓存
  -> 返回排序结果
  -> AI 异常时降级返回 MySQL 原始结果
```

## 确定性融合公式

```text
finalScore(i) = alpha * semanticScore(i)
              + beta  * priceScore(i)
              + gamma * salesScore(i)
              + delta * timeScore(i)
```

- **semanticScore**：按 DeepSeek 名次线性映射 `(N - rank) / (N - 1)`，第 1 名 1.0、最后一名 0
- **priceScore**：`threshold = 同类目均价 × priceAnomalyRatio`（样本不足回退全体中位数）；
  价格低于阈值时 `price / threshold` 线性扣分，否则为 1.0
- **salesScore**：`ln(1 + soldNum)` 对数归一化，拉平头部销量的数量级差距
- **timeScore**：上架时间归一化到 0-1，越新越高；为空回退创建时间
- 权重通过 `application.yml` 配置，不写死在代码里

## 离线评估

### 数据集

```text
ai-evaluation/data/queries-train.jsonl   # 训练集（20 条，调权重用）
ai-evaluation/data/queries-test.jsonl    # 测试集（10 条，只报结果不调参）
ai-evaluation/data/catalog.jsonl         # 商品字段（价格/销量/库存/上架时间/类目/店铺）
ai-evaluation/data/mock-rankings.jsonl   # DeepSeek 语义顺序快照（评估时用 mock 保证可复现）
```

标注集采用分层抽样，覆盖品牌词、口语词、价格约束词，并包含“标题干净但价格异常”的硬负例。

### 评估指标

- **NDCG@10**：按 3/2/1/0 相关度计算，衡量排序质量
- **Top-3 命中率**：前 3 个结果命中相关商品的比例
- **异常低价商品 Top3 占比**：追踪“1 元清仓”类坏 case 是否被压出 Top3

### 运行命令

抓取 DeepSeek 语义快照：

```powershell
cd ai-evaluation
mvn exec:java "-Dexec.args=--capture-snapshot=true --data-dir=data --base-url=https://api.deepseek.com/v1 --model=deepseek-chat"
```

在训练集上评估（调权重）：

```powershell
mvn exec:java "-Dexec.args=--data-dir=data --results-dir=results --queries-file=queries-train.jsonl --provider=mock --alpha=0.50 --beta=0.35 --gamma=0.10 --delta=0.05 --price-anomaly-ratio=0.50 --assert-hard-negatives-out=true"
```

在测试集上报最终结果：

```powershell
mvn exec:java "-Dexec.args=--data-dir=data --results-dir=results --queries-file=queries-test.jsonl --provider=mock --alpha=0.50 --beta=0.35 --gamma=0.10 --delta=0.05 --price-anomaly-ratio=0.50 --assert-hard-negatives-out=true"
```

结果文件：

```text
ai-evaluation/results/summary-report.csv
ai-evaluation/results/per-query.csv
ai-evaluation/results/fusion-warnings.txt
```

### 评估结果（测试集）

| 方案 | NDCG@10 | 异常低价 Top3 占比 |
| ---- | ---- | ---- |
| LIKE 基线 | 0.549 | 63.33% |
| 纯语义排序 | 0.796 | 16.67% |
| 融合排序 | 0.880 | 0% |

结论：语义重排把 NDCG@10 从 0.549 提升到 0.796；融合排序进一步提到 0.880，
并把异常低价商品 Top3 占比从 16.67% 压到 0%。

## 缓存与降级

- **缓存搜索结果**：key 按 query + 排序条件生成，命中直接返回，跳过 MySQL 召回与 DeepSeek 调用
- **防穿透**：查询无结果时缓存空标记，短 TTL
- **防击穿**：热点 query 用 Redisson 互斥锁，同一 key 只允许一个线程重建缓存
- **防雪崩**：TTL 基准 1 小时 + 随机抖动
- **降级**：AI 超时/限流/Token 超限/返回非法 JSON 时，回退原 MySQL 结果
- **容错**：Redis 异常时跳过缓存，不影响主流程

## 压测结果（单机，50 并发，JMeter）

| 场景 | QPS | 平均响应 | 错误率 |
| ---- | ---- | ---- | ---- |
| 无 AI 基线（纯 MySQL LIKE） | 109.2 | 333 ms | 0% |
| AI 成功（缓存未命中，走 DeepSeek） | 26.2 | 1099 ms | 0% |
| AI 降级（错误 Key，回退 MySQL） | 72.0 | 537 ms | 0% |
| 缓存命中（预热后） | 237.0 | 18 ms | 0% |

结论：

- DeepSeek 调用是性能瓶颈：AI 成功路径 QPS 仅 26.2、平均 1099ms
- 引入 Redis 缓存后，命中缓存时 QPS 达 237、平均 18ms，约为无 AI 基线的 2 倍、AI 成功路径的 9 倍，彻底消除 DeepSeek 延迟瓶颈
- AI 降级后 QPS 恢复到基线的约 66%、错误率 0%，验证降级策略有效

## 本地验证截图

### 原版 MySQL LIKE 排序（异常低价商品排前）
<img width="1453" height="901" alt="image" src="https://github.com/user-attachments/assets/f0c429c0-d729-4eb2-b31f-6b127497e49a" />
<img width="1503" height="666" alt="image" src="https://github.com/user-attachments/assets/70e6567f-b23d-435d-a5a9-a65a6a060c3b" />

### AI 语义重排 + 融合排序（异常低价商品被压后）
<img width="1443" height="791" alt="image" src="https://github.com/user-attachments/assets/2a422c31-a048-4a02-aaf6-8f50083c751a" />
<img width="1191" height="663" alt="image" src="https://github.com/user-attachments/assets/cf5153bf-5909-44f4-acef-9c4f21a6bf51" />

## 快速开始

```powershell
# 1. 配置环境变量
$env:DEEPSEEK_API_KEY="你的 DeepSeek API Key"

# 2. 启动（需 MySQL、Redis）
cd yami-shop-api
mvn spring-boot:run

# 3. 调用
curl "http://localhost:8086/search/semanticSearch?prodName=手机&sort=0&orderBy=1"
```

## 后续优化方向

- 召回层从 MySQL LIKE 升级为 Elasticsearch 或向量检索
- 引入 Rerank 模型对 Top-K 做精排
- 扩充训练集和测试集，覆盖更多类目、品牌和口语表达
- 将评估模块集成到 CI，每次改动后自动跑回归
- 将线上搜索日志回流为人工标注样本，持续优化融合权重
