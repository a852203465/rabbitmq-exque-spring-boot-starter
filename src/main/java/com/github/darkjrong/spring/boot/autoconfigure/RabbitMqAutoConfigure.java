package com.github.darkjrong.spring.boot.autoconfigure;

import com.github.darkjrong.rabbitmq.RabbitMqConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 *
 * RabbitMq 自动配置类
 * @author Rong.Jia
 * @date 2021/07/22
 */
@Configuration
@Import(RabbitMqConfig.class)
public class RabbitMqAutoConfigure {



}
