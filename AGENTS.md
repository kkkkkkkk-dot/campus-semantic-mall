# AGENTS.md

## 项目概述
Mall4j 校园智能商品交易平台，基于 Spring Boot 4 + MyBatis-Plus + Redis + Spring AI 构建。

## 技术栈版本
- Java 17
- Spring Boot 4.x
- MyBatis-Plus 3.5.x
- Spring AI 1.x
- Redis 7.x

## 核心编码规范
- 统一使用 BusinessException 处理业务异常。
- Service 层接口以 Service 结尾，控制器以 Controller 结尾。
- 所有新增接口必须有 Swagger/OpenAPI 注解。

## 模块结构
- /yami-shop-api 前台接口模块
- /yami-shop-admin 后台管理模块
- /yami-shop-service 业务逻辑模块
- /yami-shop-bean 实体类模块

## 当前改造目标
在 SearchController 中新增一个 AI 语义排序接口 `/search/semanticSearch`。
- 原有链路：SearchController → ProductService.getSearchProdDtoPageByProdName → MyBatis-Plus + MySQL LIKE 查询。
- 改造方案：
    1. 先调用原有 Service 方法，利用 MySQL LIKE 查出候选商品（Top 50）。
    2. 将这 50 个商品和用户的搜索词发送给 Spring AI（DeepSeek），让模型返回排序后的商品 ID 列表。
    3. 根据 AI 返回的 ID 顺序，重新组装并返回分页结果。
- 降级逻辑：如果 AI 调用失败（超时、限流、Token 超限），自动返回原有 MySQL 查询结果，保证接口可用性。
## 常用命令
- 启动项目：mvn spring-boot:run
- 打包：mvn clean package
- 运行测试：mvn test