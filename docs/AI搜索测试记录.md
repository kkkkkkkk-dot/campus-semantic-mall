# AI 语义排序 A/B 测试记录

## 测试目的
验证新增的 `/search/semanticSearch` 接口，能否在原有 MySQL 召回的基础上，通过 DeepSeek 大模型对候选商品做二次语义排序。

## 测试接口
- 原版：`GET /search/searchProdPage?prodName=手机&sort=0&orderBy=1`
- AI 版：`GET /search/semanticSearch?prodName=手机`

## 顺序对比

**原版排序（MySQL 按更新时间倒序）：**
1. 小米手机 13 Ultra（5999 元）
2. 华为手机 Mate 60 Pro（6999 元）
3. OPPO手机 Find X7（4999 元）
4. vivo手机 X100 Pro（5499 元）
5. 荣耀手机 Magic 6（4399 元）
6. Redmi手机 K70（2499 元）
7. 三星手机 Galaxy S24（6999 元）
8. 一加手机 12（4299 元）
9. Apple iPhone XS Max 4G手机（1.01 元）

**AI 排序（DeepSeek 语义重排）：**
1. Apple iPhone XS Max 4G手机（1.01 元）
2. 华为手机 Mate 60 Pro（6999 元）
3. 小米手机 13 Ultra（5999 元）
4. OPPO手机 Find X7（4999 元）
5. vivo手机 X100 Pro（5499 元）
6. 荣耀手机 Magic 6（4399 元）
7. Redmi手机 K70（2499 元）
8. 三星手机 Galaxy S24（6999 元）
9. 一加手机 12（4299 元）

## 发现的缺陷
AI 把原版排第 9 的「Apple iPhone XS Max 4G手机」排到了第 1 位。该商品价格仅 1.01 元，无销量、无评价，属于异常/过时数据。

原因分析：AI 排序时只接收了「商品标题」和「搜索词」，没有接收价格、销量、上架时间等业务信号，导致它只关注标题语义，忽略了业务合理性。

## 改进思路
1. 在 Prompt 中加入价格区间、销量、上架时间等业务字段，让 AI 同时考虑语义相关性和业务合理性。
2. 保留规则兜底：销量低于阈值的商品，在 AI 排序结果中自动降权。
3. 后续可将召回层从 MySQL LIKE 升级为 ES 或向量检索，提升候选集质量。

## 测试结论
AI 语义排序链路已跑通，且确实改变了商品排序顺序。但当前 AI 排序忽略了业务信号，后续需通过 Prompt 增强 + 规则兜底来改进。