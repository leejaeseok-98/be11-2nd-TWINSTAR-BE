package com.TwinStar.TwinStar.common.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class RabbitMQConfig {
    @Value("${spring.rabbitmq.host}")
    private String host;

    @Value("${spring.rabbitmq.port}")
    private int port;

    @Value("${spring.rabbitmq.username}")
    private String username;

    @Value("${spring.rabbitmq.password}")
    private String password;

    // @Value("${spring.rabbitmq.virtual-host}")
    // private String virtualHost;

    public static final String BACKUP_QUEUE_AL="backupAddLike";
    public static final String BACKUP_QUEUE_ML="backupMinusLike";
    public static final String BACKUP_QUEUE_COMMENT_AL="backupAddCommentLike";
    public static final String BACKUP_QUEUE_COMMENT_ML="backupMinCommentLike";

    @Bean
    public Queue AddlikeQueue(){
        return new Queue(BACKUP_QUEUE_AL,true);
    }
    @Bean
    public Queue MinuslikeQueue(){
        return new Queue(BACKUP_QUEUE_ML,true);
    }

    @Bean
    public Queue AddCommentLikeQueue(){
        return new Queue(BACKUP_QUEUE_COMMENT_AL,true);
    }
    @Bean
    public Queue MinCommentLikeQueue(){
        return new Queue(BACKUP_QUEUE_COMMENT_ML,true);
    }

    @Bean
    public ConnectionFactory connectionFactory(){ //RabbitMq에 연결하기 위한 설정 세팅
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
        connectionFactory.setHost(host);
        connectionFactory.setPort(port);
        connectionFactory.setUsername(username);
        connectionFactory.setPassword(password);
        // connectionFactory.setVirtualHost(virtualHost);
        return connectionFactory;
    }

    @Bean //RabbitMq연결 객체
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory){
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(new Jackson2JsonMessageConverter());
        return rabbitTemplate;
    }
}
