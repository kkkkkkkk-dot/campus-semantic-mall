# Docker 部署

仓库根目录提供：

```text
docker-compose.yml
yami-shop-admin/Dockerfile
yami-shop-api/Dockerfile
```

## 当前状态

`docker-compose.yml` 中 MySQL 服务写的是：

```text
dockerfile: ./db/Dockerfile
```

但当前 `db/` 目录下只有：

```text
db/yami_shop.sql
```

也就是说，当前仓库状态下直接执行：

```powershell
docker compose up -d
```

会因为 `db/Dockerfile` 缺失而失败。需要先处理 MySQL 镜像构建方式。

这个问题要明确写在部署文档里，避免读者以为是自己 Docker 环境装错了。

## 可选处理方式

方式一：补齐 `db/Dockerfile`，把 `yami_shop.sql` 放入 MySQL 初始化目录。

方式二：修改 `docker-compose.yml`，直接使用官方 MySQL 镜像，并挂载初始化 SQL。

无论选择哪种方式，都要确保容器内初始化数据库名为：

```text
yami_shops
```

## 后端镜像

管理端 Dockerfile 使用：

```text
openjdk:17.0.2
```

当前 Dockerfile 的目标是使用 `docker` profile 启动对应 jar。建议启动命令采用 JVM 参数在 `-jar` 之前的写法：

```text
java -Xms512m -Xmx512m -Xss256k -XX:SurvivorRatio=8 -Dspring.profiles.active=docker -jar yami-shop-admin-0.0.1-SNAPSHOT.jar
```

用户端 Dockerfile 同理，启动 `yami-shop-api`。

注意：当前仓库 Dockerfile 中的 JVM 参数写在 `-jar` 之后，实际部署前建议调整命令顺序，否则可能被 Java 当作 jar 参数处理。

## Docker 环境配置

Docker 配置文件：

```text
yami-shop-admin/src/main/resources/application-docker.yml
yami-shop-api/src/main/resources/application-docker.yml
```

支持通过环境变量配置：

```text
MYSQL_HOST
MYSQL_PORT
MYSQL_DATABASE
MYSQL_USERNAME
MYSQL_PASSWORD
REDIS_HOST
REDIS_PORT
REDIS_DATABASE
```

管理端还支持：

```text
XXL_JOB_ACCESS_TOKEN
XXL_JOB_LOG_PATH
XXL_JOB_ADDRESS
```

## 部署前检查

- 已执行 `mvn clean package -DskipTests`，目标 jar 存在。
- MySQL 初始化方式已修正。
- Redis 能被两个后端容器访问。
- `8085`、`8086` 端口未被占用。
- 文件上传配置和静态资源域名已确认。

## 检查题-含答案

问题：当前 Docker Compose 失败，最先查什么？

答案：先查 `db/Dockerfile` 是否存在。当前仓库 `db/` 只有 `yami_shop.sql`，compose 中的 MySQL 构建方式需要修正。

问题：Docker 部署还需要先打包 jar 吗？

答案：当前后端 Dockerfile 依赖目标 jar，部署前应先执行 `mvn clean package -DskipTests`。

问题：Docker 启动命令里 JVM 参数应该放在哪里？

答案：放在 `-jar` 前，例如 `java -Dspring.profiles.active=docker -jar xxx.jar`。内存参数也应放在 `-jar` 前。

## 本文依据

- `docker-compose.yml`
- `yami-shop-admin/Dockerfile`
- `yami-shop-api/Dockerfile`
- `db/yami_shop.sql`
