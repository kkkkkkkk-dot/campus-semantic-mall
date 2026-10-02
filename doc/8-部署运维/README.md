# 部署运维

本目录记录生产部署、Docker、Nginx 和运维配置。

读完本目录后，你应该能：

- 判断本项目适合先用 jar 部署还是 Docker 部署。
- 知道生产配置、前端构建、Nginx、静态资源域名分别在哪里改。
- 识别当前 Docker Compose 不能直接跑通的原因。
- 上线前检查端口、数据库、Redis、文件上传、HTTPS、支付回调等关键项。

建议阅读顺序：

1. [部署路径选择](1-部署路径选择.md)
2. [部署总览](2-部署总览.md)
3. [生产配置](3-生产配置.md)
4. [Nginx配置](4-Nginx配置.md)
5. [Docker部署](5-Docker部署.md)
6. [上线检查题-含答案](6-上线检查题-含答案.md)

当前仓库提供 `docker-compose.yml`，包含：

- MySQL
- Redis
- `mall4j-admin`
- `mall4j-api`

注意：当前 `docker-compose.yml` 引用了 `db/Dockerfile`，但仓库 `db/` 目录下当前只有 `yami_shop.sql`。直接执行 Docker Compose 前需要先修正 MySQL 镜像构建方式。
