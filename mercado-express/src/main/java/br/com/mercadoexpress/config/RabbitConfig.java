package br.com.mercadoexpress.config;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Configuration;
@Configuration
public class RabbitConfig {
 public static final String EXCHANGE="mercado.pagamentos.exchange";
 public static final String PAYMENT_REQUEST="pagamento.solicitado";
 public static final String PAYMENT_RESULT="pagamento.resultado";
 public static final String EMAIL_QUEUE="email.confirmacao";
 @Bean public MessageConverter jacksonMessageConverter(){ return new Jackson2JsonMessageConverter(); }
 @Bean public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter converter){ RabbitTemplate template=new RabbitTemplate(connectionFactory); template.setMessageConverter(converter); return template; }
 @Bean public TopicExchange paymentExchange(){ return new TopicExchange(EXCHANGE, true, false); }
 @Bean public Queue paymentRequestQueue(){ return QueueBuilder.durable(PAYMENT_REQUEST).build(); }
 @Bean public Queue paymentResultQueue(){ return QueueBuilder.durable(PAYMENT_RESULT).build(); }
 @Bean public Queue emailQueue(){ return QueueBuilder.durable(EMAIL_QUEUE).build(); }
 @Bean public Binding requestBinding(){ return BindingBuilder.bind(paymentRequestQueue()).to(paymentExchange()).with("pagamento.solicitado"); }
 @Bean public Binding resultBinding(){ return BindingBuilder.bind(paymentResultQueue()).to(paymentExchange()).with("pagamento.resultado"); }
 @Bean public Binding emailBinding(){ return BindingBuilder.bind(emailQueue()).to(paymentExchange()).with("email.confirmacao"); }
}
