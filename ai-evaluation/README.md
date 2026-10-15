# AI 语义排序离线评估

该模块独立于业务代码，直接复用 `yami-shop-service` 中的 `DeterministicFusionRanker`。

## 数据文件

- `data/queries.jsonl`：query、候选商品、人工理想排序、hardNegatives
- `data/catalog.jsonl`：商品价格、销量、库存、上架时间、类目、店铺
- `data/mock-rankings.jsonl`：预录 DeepSeek 语义顺序

## 运行 mock 评估

先在项目根目录安装最新模块：

```powershell
mvn -pl ai-evaluation -am -DskipTests install
```

进入评估模块运行：

```powershell
cd ai-evaluation
mvn exec:java -Dexec.args="--data-dir=data --results-dir=results --provider=mock --assert-fusion-not-worse=true --assert-hard-negatives-out=true"
```

可通过命令行临时调整权重和异常低价阈值：

```powershell
mvn exec:java -Dexec.args="--data-dir=data --results-dir=results --provider=mock --alpha=0.50 --beta=0.25 --gamma=0.15 --delta=0.10 --price-anomaly-ratio=0.60"
```

`--assert-hard-negatives-out=true` 会在 FUSION 的任何 hardNegative 进入 Top3 时以非零状态退出。默认权重下样例数据可能故意触发该失败。

输出文件：

- `results/summary-report.csv`
- `results/per-query.csv`
- `results/fusion-warnings.txt`

## 抓取真实 DeepSeek 语义快照

真实 DeepSeek 只用于抓取快照，不作为默认调参入口：

```powershell
$env:DEEPSEEK_API_KEY="你的 API Key"
mvn exec:java -Dexec.args="--capture-snapshot=true --data-dir=data --base-url=https://api.deepseek.com/v1 --model=deepseek-chat"
```

快照会覆盖 `data/mock-rankings.jsonl`，之后继续使用 `--provider=mock` 调参。