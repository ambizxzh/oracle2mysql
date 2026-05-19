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
- `OracleDdlGenerator` / `MysqlDdlGenerator`
- `SchemaComparator`
- `MigrationPlanner` / `MigrationExecutor`
- `SnapshotStore`
- `DdlParser`
- `CoverageValidator`

扩展：实现接口并注册到 `ServiceRegistry`。

## 监测时机

| 方式 | 期望侧 | 实库侧 | 命令 |
|------|--------|--------|------|
| 离线对比 | 磁盘快照 `canonical.json` | MySQL JDBC | `diff` / `verify` |
| **双库直连** | Oracle JDBC → 类型映射 Canonical | MySQL JDBC | `live-diff`（含 PK/UK/FK/CHECK/索引**约束名**） |
| 两步等价 | `export` 写快照后 | MySQL JDBC | `export` + `diff` |

典型流程：生成后（coverage）→ `live-diff` 或 `diff` → `apply` → `verify` → `migrate` 后再 `verify`。

### live-diff 流水线

```
Oracle ALL_* ──► TypeMapper ──► expected (Canonical)
                                      │
MySQL INFORMATION_SCHEMA ─────────────┼──► SchemaComparator ──► reports/
                                      │
                                 actual (Canonical)
```

实现：[LiveDiffService.java](../../o2m-cli/src/main/java/io/o2m/cli/LiveDiffService.java)
