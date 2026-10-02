# 后台 CRUD 接口模板

新增后台管理功能时，优先保持接口形状稳定。这样前端列表、表单、权限和错误处理都能复用现有习惯。

## 推荐接口

| 动作 | 方法 | 路径 | 权限示例 |
| --- | --- | --- | --- |
| 分页 | `GET` | `/shop/helpArticle/page` | `shop:helpArticle:page` |
| 详情 | `GET` | `/shop/helpArticle/info/{id}` | `shop:helpArticle:info` |
| 新增 | `POST` | `/shop/helpArticle` | `shop:helpArticle:save` |
| 修改 | `PUT` | `/shop/helpArticle` | `shop:helpArticle:update` |
| 删除 | `DELETE` | `/shop/helpArticle/{id}` | `shop:helpArticle:delete` |

## Controller 职责

Controller 只做薄薄一层：

- 接收参数。
- 做 `@PreAuthorize` 权限控制。
- 写入当前店铺 `shopId`。
- 调用 Service。
- 返回 `ServerResponseEntity`。

业务规则不要堆在 Controller 里；复杂逻辑放到 Service。

## 返回格式

成功：

```text
ServerResponseEntity.success(...)
```

业务失败：

```text
ServerResponseEntity.showFailMsg(...)
```

或抛出：

```text
YamiShopBindException
```

## 完成后应该看到

- 分页接口返回 `code=00000`。
- `data.records` 是列表数据。
- `data.total` 是总数。
- 新增后数据库出现对应记录。
- 修改后再次查询详情能看到新值。
- 删除后列表不再返回该记录。

## 检查题-含答案

问题：新增接口为什么要写 `shopId`？

答案：后台数据通常属于当前店铺。写入 `shopId` 后，查询、修改、删除才能按店铺隔离，避免越权操作其他店铺数据。

问题：分页接口为什么建议叫 `/page`？

答案：项目已有后台列表普遍使用 `/page`、`PageParam`、`IPage` 风格，前端表格也按 `records` 和 `total` 读取分页结果。

问题：权限标识应该写在哪里？

答案：同一个权限标识要同时出现在后端 `@PreAuthorize`、数据库 `tz_sys_menu.perms`、前端 `isAuth()` 中。

## 本文依据

- `yami-shop-admin/src/main/java/com/yami/shop/admin/controller/NoticeController.java`
- `doc/4-技术实现/分页实现.md`
- `doc/4-技术实现/权限体系.md`
