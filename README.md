# rabbitmq 消息队列和交换机配置 starter

## 1. 使用方式

### 1. 下载源码

下载源码 并install，或者推送到私库引入使用

### 2. 引入依赖

```xml
        <dependency>
            <groupId>com.github.darkjrong</groupId>
            <artifactId>rabbitmq-exque-spring-boot-starter</artifactId>
            <version>1.0</version>
        </dependency>
```

### 3. 配置参数(application.properties)  yml配置
```yaml
spring:

  # rabbitmq
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest

    # 交换机
    exchanges:
      - name: exchange
        type: DIRECT

    # 队列
    queues:
      # 直连队列
      - name: SPC_RET_MFG_DATA
        exchange-name: exchange
      # 直连队列
      - name: SPC_RET_MFG_DATA_ERROR
        exchange-name: exchange

```














