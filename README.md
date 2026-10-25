# 按工厂编码动态切换数据源 —— 原理与参考实现

多工厂场景(MES / ERP / SaaS 多租户):每个工厂一个独立的数据库,业务系统按请求携带的
**工厂编码** 把 SQL 路由到对应的库。本项目是一套可直接落地的实现,基于
`AbstractRoutingDataSource` + `ThreadLocal` + AOP,不引入第三方数据源框架。

---

## 一、原理:一次查询是怎么被路由到正确工厂的

Spring 体系里所有数据库操作(JdbcTemplate / MyBatis / 事务管理器)最终都要经过
`DataSource.getConnection()` 拿连接。动态数据源的全部秘密就是:**把这一个 DataSource bean
换成一个"分发器",它在 getConnection() 的瞬间决定用哪个工厂的池。**

```
HTTP 请求(Header: X-Factory-Code: F002)
        │
        ▼
① FactoryCodeInterceptor.preHandle()          ── 写入线程上下文
        FactoryContext.set("F002")   →  ThreadLocal<String>
        │
        ▼
② 业务方法执行(可被 @FactoryDS 注解二次覆盖)
        │
        ▼
③ JdbcTemplate / MyBatis 执行 SQL
        dataSource.getConnection()
        │
        ▼
④ FactoryRoutingDataSource(继承 AbstractRoutingDataSource)
        determineCurrentLookupKey()  →  FactoryContext.get()  →  "F002"
        resolved.get("F002")         →  HikariDataSource(pool: factory-F002)
        │
        ▼
⑤ 真实的 factory-F002 连接池.getConnection() ── SQL 落到 F002 的库
        │
        ▼
⑥ afterCompletion():FactoryContext.clear()    ── 归还线程前清理,防止串厂
```

### 三个关键角色

| 角色 | 类 | 职责 |
|---|---|---|
| 决策器 | `FactoryRoutingDataSource` | `getConnection()` 时按当前工厂编码选池 |
| 上下文 | `FactoryContext`(ThreadLocal) | 每个请求线程各自持有"当前工厂编码" |
| 写入时机 | 拦截器 / `@FactoryDS` 切面 / 编程式 | 在请求最外层写入,方法结束清理 |

### AbstractRoutingDataSource 核心源码(Spring 未变过的骨架)

```java
public abstract class AbstractRoutingDataSource extends AbstractDataSource {

    private Map<Object, Object> targetDataSources;   // 工厂编码 -> DataSource(可动态改)
    private Map<Object, DataSource> resolvedDataSources; // 初始化时 resolve 好的快照

    @Override
    public Connection getConnection() throws SQLException {
        return determineTargetDataSource().getConnection();   // ① 先选池,再拿连接
    }

    protected DataSource determineTargetDataSource() {
        Object lookupKey = determineCurrentLookupKey();       // ② 子类决定 key(我们返回工厂编码)
        return this.resolvedDataSources.get(lookupKey);       // ③ 按 key 取真实数据源
    }

    protected abstract Object determineCurrentLookupKey();    // ④ 唯一需要实现的钩子
}
```

所以"动态切换"= **继承它,实现 `determineCurrentLookupKey()` 返回 ThreadLocal 里的工厂编码**,
仅此而已。其余全部是工程化问题:编码从哪来、何时写、何时清理、能否运行期加工厂。

### 为什么切换必须在"拿到连接之前"

这是整套机制最重要的一条边界:**连接一旦被事务绑定,路由就不再发生。**

`@Transactional` 方法进入时,`DataSourceTransactionManager` 立即调用
`getConnection()` 并把连接绑定到线程(`TransactionSynchronizationManager`)。
同一次事务内的后续 SQL 复用这条连接 —— 中途改 ThreadLocal 不会生效。

由此推出两条铁律:

1. **切面顺序**:`FactoryDataSourceAspect` 的 `@Order` 必须小于事务切面(默认
   `Ordered.LOWEST_PRECEDENCE`),即数据源切面在更外层。本实现用
   `@Order(Ordered.LOWEST_PRECEDENCE - 100)`;
2. **同一事务不能跨工厂**:跨工厂读写要么拆成两个事务,要么用 `REQUIRES_NEW`
   并保证进入新事务前工厂编码已切换(注解切面天然满足)。

---

## 二、快速开始

```bash
# 1. 建两个库:mes_f001 / mes_f002,各自建演示表
#    create table t_order (id bigint primary key auto_increment, ...);
# 2. 改 application.yml 里的连接信息
mvn spring-boot:run
```

### 配置工厂清单(application.yml)

```yaml
factory:
  datasource:
    default-factory: F001        # 缺失/未注册时回落
    factories:
      F001:
        url: jdbc:mysql://127.0.0.1:3306/mes_f001
        username: root
        password: "123456"
      F002:
        url: jdbc:mysql://127.0.0.1:3306/mes_f002
        username: root
        password: "123456"
```

### 三种切换方式(优先级:Header < 类注解 < 方法注解)

**方式 1:请求头(推荐作为默认行为,业务代码零侵入)**

```bash
curl -H "X-Factory-Code: F002" http://localhost:8080/api/orders/1
```

`FactoryCodeInterceptor` 负责写入与清理。如果工厂编码在登录态里,把拦截器中
`request.getHeader(...)` 换成 `currentUser.getFactoryCode()` 即可,其余不变。

**方式 2:注解(主动指定工厂,比如跨工厂对账、复制)**

```java
@FactoryDS("F002")                    // 方法级
public Order pullFromF002(Long id) { ... }

@FactoryDS("F001")                    // 类级:整个 Service 默认走 F001
@Service
public class F001ReportService { ... }
```

**方式 3:编程式(方法内临时切换)**

```java
String previous = FactoryContext.setAndReturnPrevious("F003");
try {
    // ... 操作 F003
} finally {
    FactoryContext.restore(previous);   // 务必还原,支持嵌套
}
```

自检端点 `/api/orders/routing-info` 会返回当前请求实际路由到的 JDBC URL,
排查"怎么查到别的厂去了"时最直观。

---

## 三、运行期上新工厂(不重启)

```bash
curl -X POST http://localhost:8080/api/admin/factories \
  -H "Content-Type: application/json" \
  -d '{
    "factoryCode": "F003",
    "config": {
      "url": "jdbc:mysql://10.0.0.21:3306/mes_f003",
      "username": "root",
      "password": "123456",
      "maximumPoolSize": 5
    }
  }'
```

注册后立刻生效。更工程化的做法:`FactoryDataSourceManager` 在启动后从**主库的工厂
清单表**或配置中心(Nacos/Apollo)拉取工厂列表,并监听变更增删 —— 工厂与库的映射
完全数据驱动,见该类的 javadoc。

> ⚠️ 管理接口必须挂权限校验与操作审计,否则等于开放"把流量导到任意数据库"的能力。

---

## 四、生产环境必须知道的坑

1. **ThreadLocal 必须清理(Tomcat 线程复用)**。残留的工厂编码会把下一个请求静默
   路由到错误的库 —— 这是数据串厂事故的头号来源。本实现有三道防线:拦截器
   `preHandle` 无条件覆盖、`afterCompletion` 清理、切面 finally 还原。
2. **异步线程不继承 ThreadLocal**。`@Async` / 线程池 / `CompletableFuture` 里的
   数据库操作会读不到工厂编码,静默回落默认工厂。解决:`AsyncConfig` 里的
   `TaskDecorator`(本实现已配),或手动 `FactoryContextRunnable.of(task)`。
   如果任务来源复杂(消息队列消费者等),考虑接入阿里 TransmittableThreadLocal。
3. **同一事务内切换无效**(见上文"两条铁律")。跨工厂操作要么拆事务,要么
   `REQUIRES_NEW`。
4. **同类自调用不走代理**。`this.methodB()` 调用带 `@FactoryDS` 的方法,切面
   不生效 —— 和 `@Transactional` 一样的限制,改走代理对象或编程式切换。
5. **连接总数 = 工厂数 × maximumPoolSize**。50 个工厂 × 10 连接 = 500 连接,
   注意 MySQL `max_connections` 与连接池参数规划,低频工厂调小池子。
6. **JPA/Hibernate 慎用**。本方案对 JdbcTemplate / MyBatis / MyBatis-Plus 开箱即用
   (它们只认 DataSource 接口);但 JPA 的二级缓存、方言、`hibernate_sequence`
   等按 SessionFactory 全局工作,多库共享一个 EntityManagerFactory 会有
   主键/缓存串扰,需要每工厂一个 EMF,复杂度完全是另一个量级。

## 五、Spring Boot 2.x 适配

代码逻辑零改动,只需:`jakarta.servlet.*` → `javax.servlet.*`。本实现其余只用
`AbstractRoutingDataSource`、`HikariCP`,两个版本 API 一致。

## 六、和 baomidou dynamic-datasource 的关系

`dynamic-datasource-spring-boot-starter` 做的是同一件事(底层同样基于
`AbstractRoutingDataSource`),额外提供 `@DS` 注解、惰性加载、seata 集成等。
如果只是"按工厂编码路由"这一个诉求,自研这套约 500 行代码足够,少一个依赖、
行为完全可控;需要它的生态功能时再引入也不迟,业务侧 `FactoryContext` 的写入
逻辑可以平移。
