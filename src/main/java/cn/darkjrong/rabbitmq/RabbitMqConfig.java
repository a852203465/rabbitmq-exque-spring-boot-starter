package cn.darkjrong.rabbitmq;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.*;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;
import java.util.stream.Collectors;


/**
 * rabbitmq 消息队列和交换机 配置文件
 *
 * @author Rong.Jia
 * @date 2021/07/22
 */
@Data
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "spring.rabbitmq")
public class RabbitMqConfig implements BeanFactoryAware {

    /**
     * 装载自定义配置交换机
     */
    private List<ExchangeConfig> exchanges = new ArrayList<>();

    /**
     * 装载自定义配置队列
     */
    private List<QueueConfig> queues = new ArrayList<>();

    private ConfigurableBeanFactory beanFactory;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = (ConfigurableBeanFactory) beanFactory;
    }

    @Data
    public static class QueueConfig {

        /**
         * 队列名（每个队列的名称应该唯一）
         * 必须*
         */
        private String name;

        /**
         * 指定绑定交互机，可绑定多个（逗号分隔）
         * 必须*
         */
        private String exchangeName;

        /**
         * 队列路由键（队列绑定交换机的匹配键，例如：“user” 将会匹配到指定路由器下路由键为：【*.user、#.user】的队列）
         */
        private String routingKey;

        /**
         * 是否为持久队列（该队列将在服务器重启后保留下来）
         */
        private Boolean durable = Boolean.TRUE;

        /**
         * 是否为排它队列
         */
        private Boolean exclusive = Boolean.FALSE;

        /**
         * 如果队列为空是否删除（如果服务器在不使用队列时是否删除队列）
         */
        private Boolean autoDelete = Boolean.FALSE;

        /**
         * 头队列是否全部匹配
         * 默认：是
         */
        private Boolean whereAll = Boolean.TRUE;

        /**
         * 参数
         */
        private Map<String, Object> args;

        /**
         * 消息头
         */
        private Map<String, Object> headers;

    }

    @Data
    public static class ExchangeConfig {

        /**
         * 交换机名
         */
        private String name;

        /**
         * 交换机类型
         */
        private ExchangeType type;

        /**
         * 自定义交换机类型
         */
        private String customType;

        /**
         * 交换机参数（自定义交换机）
         */
        private Map<String, Object> arguments;

    }

    public enum ExchangeType {
        /**
         * 自定义交换机
         */
        CUSTOM,
        /**
         * 直连交换机（全文匹配）
         */
        DIRECT,
        /**
         * 通配符交换机（两种通配符：*只能匹配一个单词，#可以匹配零个或多个）
         */
        TOPIC,
        /**
         * 头交换机（自定义键值对匹配，根据发送消息内容中的headers属性进行匹配）
         */
        HEADERS,
        /**
         * 扇形（广播）交换机 （将消息转发到所有与该交互机绑定的队列上）
         */
        FANOUT,

    }


    private ExchangeConfig getExchangeConfig(String name) {
        Map<String, ExchangeConfig> collect = exchanges.stream().collect(Collectors.toMap(ExchangeConfig::getName, e -> e));
        return collect.get(name);
    }

    /**
     * 动态创建交换机
     *
     */
    @Bean
    public void createExchange() {
        List<ExchangeConfig> exchanges = getExchanges();
        if (CollectionUtil.isNotEmpty(exchanges)) {
            exchanges.forEach(e -> {
                // 声明交换机
                Exchange exchange = null;
                switch (e.getType()) {
                    case DIRECT:
                        exchange = new DirectExchange(e.getName());
                        break;
                    case TOPIC:
                        exchange = new TopicExchange(e.getName());
                        break;
                    case HEADERS:
                        exchange = new HeadersExchange(e.getName());
                        break;
                    case FANOUT:
                        exchange = new FanoutExchange(e.getName());
                        break;
                    case CUSTOM:
                        exchange = new CustomExchange(e.getName(), e.getCustomType(), true, false, e.getArguments());
                        break;
                    default:
                        break;
                }

                // 将交换机注册到spring bean工厂 让spring实现交换机的管理
                if (ObjectUtil.isNotNull(exchange)) {
                    beanFactory.registerSingleton(e.getName(), exchange);
                }
            });
        }
    }

    /**
     * 动态绑定队列和交换机
     *
     */
    @Bean
    public void bindingQueueToExchange() {
        List<QueueConfig> queues = getQueues();
        if (CollectionUtil.isNotEmpty(queues)) {
            queues.forEach(q -> {

                // 创建队列
                Queue queue = new Queue(q.getName(), q.getDurable(),
                        q.getExclusive(), q.getAutoDelete(), q.getArgs());

                // 注入队列bean
                beanFactory.registerSingleton(q.getName(), queue);

                // 获取队列绑定交换机名
                List<String> exchangeNameList;
                if (q.getExchangeName().contains(StrUtil.COMMA)) {
                    String[] split = q.getExchangeName().split(StrUtil.COMMA);
                    exchangeNameList = Arrays.asList(split);
                } else {
                    exchangeNameList = Collections.singletonList(q.getExchangeName());
                }

                exchangeNameList.forEach(name -> {
                    // 获取交换机配置参数
                    ExchangeConfig exchangeConfig = getExchangeConfig(name);
                    Binding binding = bindingBuilder(queue, q, exchangeConfig);

                    // 将绑定关系注册到spring bean工厂 让spring实现绑定关系的管理
                    if (ObjectUtil.isNotNull(binding)) {
                        beanFactory.registerSingleton(q.getName() + StrUtil.DASHED + name, binding);
                    }
                });

            });
        }
    }

    private Binding bindingBuilder(Queue queue, QueueConfig q, ExchangeConfig exchangeConfig) {
        // 声明绑定关系
        Binding binding = null;

        // 根据不同的交换机模式 获取不同的交换机对象（注意：刚才注册时使用的是父类Exchange，这里获取的时候将类型获取成相应的子类）生成不同的绑定规则
        switch (exchangeConfig.getType()) {
            case TOPIC:
                binding = BindingBuilder.bind(queue)
                        .to(beanFactory.getBean(exchangeConfig.getName(), TopicExchange.class))
                        .with(q.getRoutingKey());
                break;
            case DIRECT:
                binding = BindingBuilder.bind(queue)
                        .to(beanFactory.getBean(exchangeConfig.getName(), DirectExchange.class))
                        .with(q.getRoutingKey());
                break;
            case HEADERS:
                if (q.getWhereAll()) {
                    binding = BindingBuilder.bind(queue)
                            .to(beanFactory.getBean(exchangeConfig.getName(), HeadersExchange.class))
                            .whereAll(q.getHeaders()).match();
                } else {
                    binding = BindingBuilder.bind(queue)
                            .to(beanFactory.getBean(exchangeConfig.getName(), HeadersExchange.class))
                            .whereAny(q.getHeaders()).match();
                }
                break;
            case FANOUT:
                binding = BindingBuilder.bind(queue)
                        .to(beanFactory.getBean(exchangeConfig.getName(), FanoutExchange.class));
                break;
            case CUSTOM:
                binding = BindingBuilder.bind(queue)
                        .to(beanFactory.getBean(exchangeConfig.getName(), CustomExchange.class))
                        .with(q.getRoutingKey()).noargs();
                break;
            default:
                log.warn("queue [{}] config unspecified exchange!", q.getName());
                break;
        }

        return binding;
    }
}






















