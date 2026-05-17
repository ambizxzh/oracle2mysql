# Phase 1：表结构迁移设计

## 目标

Oracle 11g 表结构 → MySQL 8.0.25：完整要素（表/列注释、类型精度、约束、索引）+ 双端 DDL + 结构一致性监测 + 演进。

## 质量底线

禁止简陋 `CREATE TABLE`（无索引、无注释、无约束）。MySQL 脚本执行后须与 Oracle 期望 Canonical 一致（`verify` / `live-diff`），含**约束名**（Oracle `DEPT_PK` → MySQL `` `dept_pk` ``，与 `DefaultMysqlDdlGenerator` 规则一致）。

## NUMBER 映射

| Oracle | 条件 | MySQL 默认 |
|--------|------|------------|
| NUMBER | p/s 均为 null，PK 列 | BIGINT |
| NUMBER | p/s 均为 null，列名匹配 id 模式 | BIGINT |
| NUMBER | p/s 均为 null，其他 | DECIMAL(38,16) |
| NUMBER(p,s) | 显式 | DECIMAL(p,s) 或整数规则 |

配置见 `mapping/type-mapping.yaml`。

## DDL 策略

**结构化拼接**：字典/解析 → Canonical → `OracleDdlGenerator` / `MysqlDdlGenerator`。  
**禁止**对 DDL 文本做正则替换映射。

## CLI

（入口 `O2mApplication`，子命令勿加 `schema` 前缀。）

- `export` — 双端 DDL + 快照 + coverage
- `import --ddl` — DDL 解析 → Canonical
- `live-diff` — **Oracle + MySQL 双库 JDBC 直连对比**（期望=映射后 Canonical，实库=MySQL 元数据）
- `diff` / `verify` — 磁盘快照 vs MySQL 实库
- `apply` — 执行 MySQL DDL
- `migrate plan|apply` — 演进

详见根目录计划文档（不随代码修改）。
