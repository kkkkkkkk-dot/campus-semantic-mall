# 商品与 SKU

商品和 SKU 是交易链路的起点。

## 后台管理入口

管理端商品相关 Controller：

```text
yami-shop-admin/src/main/java/com/yami/shop/admin/controller/ProductController.java
yami-shop-admin/src/main/java/com/yami/shop/admin/controller/SpecController.java
yami-shop-admin/src/main/java/com/yami/shop/admin/controller/CategoryController.java
yami-shop-admin/src/main/java/com/yami/shop/admin/controller/ProdTagController.java
```

管理后台页面主要在：

```text
front-end/mall4v/src/views/modules/prod/
```

## 用户端入口

用户端商品相关 Controller：

```text
yami-shop-api/src/main/java/com/yami/shop/api/controller/ProdController.java
yami-shop-api/src/main/java/com/yami/shop/api/controller/SkuController.java
yami-shop-api/src/main/java/com/yami/shop/api/controller/SearchController.java
```

## 核心概念

| 概念 | 说明 |
| --- | --- |
| 商品 | 展示和售卖的 SPU，包含名称、主图、详情、分类、运费模板等。 |
| SKU | 具体可购买规格，包含价格、库存、销售属性组合。 |
| 规格 | 例如颜色、版本、内存。 |
| 规格值 | 例如红色、64GB。 |
| 商品分组 | 用于运营展示和商品集合管理。 |

在当前项目里，下单、购物车和库存最终都落到 SKU。即使商品只有一种规格，也会有对应 SKU 记录；商品表里的总库存可以理解为 SKU 库存的汇总或销售层面的冗余统计。

## 状态

商品表 `tz_prod.status`：

```text
1  正常或上架
0  下架
-1 删除
```

SKU 表 `tz_sku.status`：

```text
1 启用
0 禁用
```

购物车添加商品时会校验商品和 SKU 状态，状态不正常时返回“当前商品已下架”。

## 库存

商品表有总库存：

```text
tz_prod.total_stocks
```

SKU 表有实际库存和库存字段：

```text
tz_sku.actual_stocks
tz_sku.stocks
```

交易链路中库存会影响购物车添加、下单、取消订单和发货后的缓存清理。
