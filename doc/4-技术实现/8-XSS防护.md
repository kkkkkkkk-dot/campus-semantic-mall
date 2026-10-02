# XSS 防护

项目提供基础 XSS 过滤能力。

核心文件：

```text
yami-shop-common/src/main/java/com/yami/shop/common/filter/XssFilter.java
yami-shop-common/src/main/java/com/yami/shop/common/xss/XssWrapper.java
yami-shop-common/src/main/java/com/yami/shop/common/xss/XssUtil.java
```

## 实现方式

`XssFilter` 将请求包装成 `XssWrapper`，读取请求参数、attribute 和 header 时调用 `XssUtil.clean()`。

注意：当前 `XssWrapper` 没有覆盖 `getInputStream()` 或 `getReader()`，因此不要把它理解为“会自动清洗 JSON 请求体”的机制。

`XssUtil` 基于 Jsoup：

```text
Safelist.relaxed()
```

并额外允许所有标签带 `style` 属性，以兼容富文本编辑器里的样式。

## 适用范围

这是一层基础输入清洗，主要防止常见脚本注入进入后端参数。

## 二开建议

- 富文本字段仍要谨慎展示。
- 不要因为有 XSS 过滤就允许任意 HTML。
- 新增公开展示内容时，前端展示层也要注意转义和白名单策略。
- 涉及支付、订单、权限等关键数据，不要依赖 XSS 过滤承担业务校验。
