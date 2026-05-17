# 架构与扩展点

## 模块

| 模块 | 职责 |
|------|------|
| o2m-api | SPI + 领域模型（无 JDBC） |
| o2m-core | 配置、Registry、工具 |
| o2m-oracle | Oracle MetadataCollector |
| o2m-mysql | MySQL MetadataCollector、MigrationExecutor |
| o2m-translate | 类型映射、DDL 生成、Coverage、DdlParser |
| o2m-diff | SchemaMonitor、SchemaComparator |
| o2m-migrate | 快照、MigrationPlanner |
| o2m-cli | Picocli 入口 |

## 流水线

```
Oracle ALL_* / DDL parse → Canonical → TypeMapper → MysqlDdlGenerator
                                      → OracleDdlGenerator
                                      → SchemaMonitor
```

## SPI（io.o2m.spi）

- `MetadataCollector`
- `TypeMappingStrategy`
- `DdlGenerator`（Oracle / MySQL 实现类）
- `SchemaComparator`
- `MigrationPlanner` / `MigrationExecutor`
- `SnapshotStore`
- `DdlParser`
- `CoverageValidator`

扩展：实现接口并注册到 `ServiceRegistry`。

## 监测时机

生成后（coverage）→ diff → apply → verify → migrate 后再 verify。
