# oracle2mysql

Oracle 11g → MySQL 8.0.25 表结构迁移与一致性监测工具（Java 17 + JDBC Thin）。

## 本地开发环境

本地数据库通过 [docker/docker-compose.yml](docker/docker-compose.yml) 以 Docker 容器运行（Oracle 11g XE + MySQL 8.0.25）。

### 前置条件

- **Docker**：WSL 内 `docker` 可用；若使用 Docker Desktop，需先启动 Desktop
- **JDK 17**：例如 `/home/ambizxzh/.local/opt/jdk-17`（勿用系统默认 JDK 8）
- **Maven** 3.x

### 首次准备（拉镜像）

镜像已在 compose 中指定；若 Docker Hub 超时，可使用 [docker/start.sh](docker/start.sh) 从备用源拉取：

- Oracle：`ghcr.io/gvenzl/oracle-xe:11`
- MySQL：`mysql:8.0.25`（脚本内可通过 DaoCloud 镜像拉取并 `docker tag`）

```bash
cd docker
./start.sh    # 拉镜像（如需）并 docker compose up -d
```

### WSL 重启或关机后

每次需要测试前，先启动数据库容器：

```bash
cd docker
docker compose up -d          # 或 ./start.sh
docker compose ps             # 确认 STATUS 为 healthy
```

| 操作 | 命令 |
|------|------|
| 启动（保留数据） | `docker compose up -d` |
| 停止 | `docker compose down` |
| 停止并清空 MySQL 数据卷 | `docker compose down -v` |

说明：

- MySQL 数据持久化在命名卷 `o2m_o2m_mysql_data`
- Oracle 数据在容器内；**删除 `o2m-oracle11g` 容器会丢失库内数据**，需重新初始化
- Oracle 首次启动约 1–3 分钟，日志出现 `DATABASE IS READY TO USE!` 即可使用：`docker logs -f o2m-oracle11g`

### 快速验证（smoke test）

```bash
export JAVA_HOME=/path/to/jdk-17    # 例：/home/ambizxzh/.local/opt/jdk-17
export PATH="$JAVA_HOME/bin:$PATH"
export O2M_ORACLE_PASSWORD=hr
export O2M_MYSQL_PASSWORD=o2m_root
export ORACLE_SCHEMA=HR

cd /path/to/oracle2mysql
mvn -q clean package -DskipTests

JAR=o2m-cli/target/o2m-cli-1.0.0-SNAPSHOT.jar

java -jar $JAR export \
  --oracle-schema $ORACLE_SCHEMA --tag v1 --config config/application.yaml

java -jar $JAR apply \
  --input ./out/schema --config config/application.yaml

java -jar $JAR live-diff \
  --oracle-schema $ORACLE_SCHEMA --config config/application.yaml
```

### live-diff 说明

同时连接配置中的 Oracle 与 MySQL：从 Oracle 字典采集并经类型映射得到**期望 Canonical**，从 MySQL 采集**实库 Canonical**，输出 diff 报告到 `out/reports/`。

```bash
java -jar $JAR live-diff \
  --oracle-schema $ORACLE_SCHEMA \
  --config config/application.yaml \
  --tables EMPLOYEES,DEPARTMENTS \
  --save-expected    # 可选：将期望快照写入 out/snapshots/<tag>/
  --generate-migration  # 可选：根据差异直接生成增量迁移 SQL
```

与 `export` + `diff` 等价，但**不落盘也能对比**；`--save-expected` 便于审计或后续 `verify`。`--generate-migration` 可以在没有旧快照的情况下，直接基于 MySQL 实库与 Oracle 的差异生成增量 DDL。

对比项含：列类型、唯一/外键/**约束名**（Oracle `EMP_EMAIL_UK` → MySQL `emp_email_uk`）、索引名等。主键在 DDL 中仍生成 `` CONSTRAINT `dept_pk` PRIMARY KEY ``，但 **MySQL/InnoDB 数据字典中主键名固定为 `PRIMARY`**，对比时视为与 Oracle 映射名等价。请用 `apply` 执行 `out/schema/schema.mysql.sql`（或目录，见下）后再 `live-diff`。

`export` 会同时写出合并脚本（见下节）。`apply --input ./out/schema` 若存在 `schema.mysql.sql` 则只执行该文件，避免与单表 DDL 重复建表。

## DBeaver 连接（Win11 → WSL Docker）

| 项目 | Oracle 11g XE | MySQL 8.0.25 |
|------|---------------|--------------|
| 主机 | `localhost` | `localhost` |
| 端口 | `1521` | **`3307`** |
| 库/服务名 | **SID = `XE`**（非 ORCL） | 数据库 `hr` |
| 应用账号 | `hr` / `hr` | `root` / `o2m_root` |
| 管理账号 | `system` / `oracle` 或 `sys` / `oracle`（SYSDBA） | — |

**DBeaver 配置要点**

- **Oracle**：驱动 Oracle Thin；连接类型选 **SID**，SID 填 `XE`；主机 `localhost` 即可；浏览 Schema `HR`
- **MySQL**：驱动 MySQL 8；主机 `localhost`，端口 **`3307`**（非 3306）；用户 `root`，密码 `o2m_root`；驱动属性 `allowPublicKeyRetrieval=true`

### 为何 MySQL 用 3307

Docker 映射为 `3307:3306`（见 [docker-compose.yml](docker/docker-compose.yml)），避免与 **Win11 本机 MySQL** 占用 `3306` 冲突。若 DBeaver 仍填 `localhost:3306`，可能连到 Windows 那台并报：

```text
Access denied for user 'root'@'localhost' (using password: YES)
```

**备选**：不用 `localhost:3307` 时，可用 WSL IP（`hostname -I` 第一个）+ 端口 `3307`。

### 连不上时排查

- WSL 执行 `docker ps`，确认 `o2m-mysql8025` 端口为 `0.0.0.0:3307->3306`
- 连上后版本应为 **8.0.25**；若是其他版本，说明连错实例

### 与项目配置

[config/application.yaml](config/application.yaml) 与 DBeaver 一致，均使用 **`localhost:3307`**：

```yaml
oracle.jdbcUrl: jdbc:oracle:thin:@localhost:1521:XE
mysql.jdbcUrl:  jdbc:mysql://localhost:3307/hr?useSSL=false&allowPublicKeyRetrieval=true
```

## 构建

```bash
export JAVA_HOME=/path/to/jdk-17
export PATH="$JAVA_HOME/bin:$PATH"
mvn -q clean package
java -jar o2m-cli/target/o2m-cli-1.0.0-SNAPSHOT.jar --help
```

## 配置

复制并编辑（`config/application.yaml` 已加入 `.gitignore`，勿提交密码）：

```bash
cp config/application.yaml.example config/application.yaml
```

- `config/application.yaml` — Oracle / MySQL JDBC、输出目录、规则
- `mapping/type-mapping.yaml` — NUMBER 语义映射等

密码通过环境变量注入（推荐）：

```bash
export O2M_ORACLE_PASSWORD=hr
export O2M_MYSQL_PASSWORD=o2m_root
export ORACLE_SCHEMA=HR
export JAR=o2m-cli/target/o2m-cli-1.0.0-SNAPSHOT.jar
```

`application.yaml` 中使用占位符：`password: "${O2M_ORACLE_PASSWORD}"`。

本地 Docker 环境 Oracle **SID 为 `XE`**（`application.yaml.example` 已与之一致）。

### 配置大小写注意

连接信息中的 **username、password、database 通常区分大小写**，填写时请与 DBA 确认：

- **Oracle**：`username` / `schema` 一般**大写**（如 `MAPLE_TRADE`）；若 DBA 创建用户时使用了小写，则需保持一致
- **MySQL**：`username` / `database` 一般**小写**（如 `maple_trade`）

如果填错大小写，可能报 `Access denied for user ...`（认证失败）或 `Unknown database ...` 等错误。

## CLI

入口为 `O2mApplication`，命令行**不需要**写 `schema` 前缀。`$JAR` 即 `o2m-cli/target/o2m-cli-1.0.0-SNAPSHOT.jar`。

> 以下命令按**工作流模式**分组。模式一与模式二的核心区别在于：模式一需要**同时连接 Oracle + MySQL**（实时性强，适合快速验证）；模式二基于**已保存的快照**（Oracle 与 MySQL 操作可分离，适合版本管理与生产审计）。

### 模式一：双库实时工作流（Oracle + MySQL 同时在线）

一步完成采集、映射、对比，无需管理中间快照文件。

```bash
# 实时导出 Oracle 结构为双端 DDL，并保存快照
java -jar $JAR export \
  --oracle-schema $ORACLE_SCHEMA --tag v1 --config config/application.yaml

# 双库直连实时对比（Oracle 映射后结构 vs MySQL 实库，不落盘）
java -jar $JAR live-diff \
  --oracle-schema $ORACLE_SCHEMA --config config/application.yaml

# 实时对比并直接生成增量迁移 SQL（以 MySQL 实库为基线，无需旧快照）
java -jar $JAR live-diff \
  --oracle-schema $ORACLE_SCHEMA --config config/application.yaml \
  --generate-migration
```

### 模式二：基于快照的离线/半离线工作流

依赖 `out/snapshots/<tag>/canonical.json`，支持分步执行、版本演进与历史审计。

```bash
# 1. 从 Oracle 导出并保存结构化快照（后续 diff / verify / migrate 的基准）
java -jar $JAR export \
  --oracle-schema $ORACLE_SCHEMA --tag v1 --config config/application.yaml

# 2. 快照 vs MySQL 实库对比
java -jar $JAR diff \
  --expected ./out/snapshots/v1 --config config/application.yaml

# 3. 执行 MySQL 脚本后校验
java -jar $JAR verify \
  --expected ./out/snapshots/v1 --config config/application.yaml

# 4. 演进：旧快照 v1 → 当前 Oracle，生成增量 DDL 到 out/migrations/
java -jar $JAR migrate plan \
  --from v1 --tag v2 --config config/application.yaml
java -jar $JAR migrate apply \
  --input ./out/migrations --config config/application.yaml
```

### 独立命令（不依赖特定工作流）

```bash
# 从 Oracle DDL 文件导入（JSQLParser → Canonical，无需任何数据库连接）
java -jar $JAR import \
  --ddl ./oracle-ddl/ --oracle-schema $ORACLE_SCHEMA --tag v1 --config config/application.yaml

# 执行 MySQL DDL 脚本（目录优先 schema.mysql.sql，避免重复建表）
java -jar $JAR apply \
  --input ./out/schema --config config/application.yaml

# export 同时生成单表文件（<table>.mysql.sql / <table>.oracle.sql）
java -jar $JAR export \
  --oracle-schema $ORACLE_SCHEMA --tag v1 --config config/application.yaml \
  --split-tables
```

### export 产物（`out/schema/`）

| 文件 | 说明 |
|------|------|
| **`schema.mysql.sql`** / **`schema.oracle.sql`** | 全库合并 DDL，所有表结构语句合并到一个脚本，适合一次执行 |
| `<table>.coverage.json` | 该表的类型映射覆盖率报告 |
| `out/snapshots/<tag>/canonical.json` | 结构化快照（供 `diff` / `verify` 使用） |

## 连接远程或其他数据库

工具通过 JDBC 连接，不限于本机 Docker。适用于测试环境、生产只读库等。

### 1. 单独配置文件

```bash
cp config/application.yaml.example config/application-remote.yaml
# 编辑 jdbcUrl、username、schema、database 等
```

示例：

```yaml
oracle:
  jdbcUrl: "jdbc:oracle:thin:@db.example.com:1521:ORCL"   # SID 形式
  # jdbcUrl: "jdbc:oracle:thin:@//db.example.com:1521/ORCLPDB1"  # Service Name 形式
  username: "hr"
  password: "${O2M_ORACLE_PASSWORD}"
  schema: "HR"

mysql:
  jdbcUrl: "jdbc:mysql://mysql.example.com:3306/mydb?useSSL=false&allowPublicKeyRetrieval=true"
  username: "migrate_user"
  password: "${O2M_MYSQL_PASSWORD}"
  database: "mydb"
```

### 2. 指定配置运行

```bash
export O2M_ORACLE_PASSWORD='...'
export O2M_MYSQL_PASSWORD='...'

java -jar $JAR export \
  --oracle-schema $ORACLE_SCHEMA --tag v1 --config config/application-remote.yaml
```

### 3. 按场景选择命令

| 场景 | 命令 | 依赖 |
|------|------|------|
| 有 Oracle 字典查询权限 | `export` | Oracle + 配置 |
| **双库结构实时对比** | **`live-diff`** | **Oracle + MySQL** |
| 仅有 DDL 文件 | `import --ddl ...` | 不需要 Oracle 连接 |
| 对比 / 校验 MySQL（离线期望） | `diff` / `verify` | MySQL + 已有 `out/snapshots` |
| 下发 DDL | `apply` | MySQL |

### 4. 网络说明

- 在 **WSL** 中运行 CLI 时，只要 WSL 能访问远程 `host:port`（VPN、安全组放行）即可
- **DBeaver** 在 Windows 上单独配置同一远程地址，与 CLI 互不干扰
- Oracle 用户需能查询 `ALL_TABLES`、`ALL_TAB_COLUMNS` 等字典视图（或对目标 schema 有等价权限）

## IntelliJ IDEA 运行与调试

### 导入项目

1. **File → Open** → 选择根目录 [pom.xml](pom.xml)，以 Maven 工程导入
2. **File → Project Structure → Project SDK** → 选择 **JDK 17**
3. 等待 Maven 导入完成（模块 `o2m-api` … `o2m-cli`）

### Application 运行配置（推荐）

| 字段 | 值 |
|------|-----|
| Type | Application |
| Main class | `io.o2m.cli.O2mApplication` |
| Module | `o2m-cli` |
| Working directory | 项目根目录 `.../oracle2mysql` |
| Program arguments | `live-diff --oracle-schema $ORACLE_SCHEMA --config config/application.yaml`（可加 `--generate-migration`） |
| Environment variables | `O2M_ORACLE_PASSWORD=hr;O2M_MYSQL_PASSWORD=o2m_root` |

其他子命令只需修改 **Program arguments**，例如：

```
diff --expected ./out/snapshots/v1 --config config/application.yaml
```

### 调试

1. 在上述配置点击 **Debug**
2. 在 `SchemaExportService`、`OracleMetadataCollector`、`DefaultTypeMappingStrategy` 等类设断点
3. 确保本地 Docker 数据库已启动（见「WSL 重启或关机后」）

### 单元测试

右键 `o2m-translate`、`o2m-diff`、`o2m-migrate` 模块下 `src/test/java` → **Run Tests**（不依赖 Docker）。

## 模块

| 模块 | 说明 |
|------|------|
| o2m-api | SPI 与领域模型 |
| o2m-core | 配置、Registry、流水线 |
| o2m-oracle | Oracle 字典采集 |
| o2m-mysql | MySQL 采集与脚本执行 |
| o2m-translate | 类型映射、DDL 生成、Coverage、DDL 解析 |
| o2m-diff | 结构监测与对比 |
| o2m-migrate | 快照与迁移计划 |
| o2m-cli | 命令行入口 |

设计文档：`docs/design/`
