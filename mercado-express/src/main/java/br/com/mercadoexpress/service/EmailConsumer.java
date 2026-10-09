package br.com.mercadoexpress.service;

import br.com.mercadoexpress.config.RabbitConfig;
import br.com.mercadoexpress.dto.EmailMessage;
import br.com.mercadoexpress.entity.EmailEnviado;
import br.com.mercadoexpress.repository.EmailEnviadoRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailConsumer {

 private final JavaMailSender mail;
 private final EmailEnviadoRepository repo;
 private final String from;

 public EmailConsumer(
         JavaMailSender mail,
         EmailEnviadoRepository repo,
         @Value("${app.mail.from}") String from
 ) {
  this.mail = mail;
  this.repo = repo;
  this.from = from;
 }

 @RabbitListener(queues = RabbitConfig.EMAIL_QUEUE)
 public void enviar(EmailMessage msg) {

  if (!"APROVADO".equals(msg.statusPagamento())) {
   return;
  }

  String assunto = "Pagamento confirmado - Pedido #" + msg.pedidoId();

  try {
   SimpleMailMessage email = new SimpleMailMessage();

   email.setFrom(from);
   email.setTo(msg.clienteEmail());
   email.setSubject(assunto);

   email.setText(
           "Olá, " + msg.clienteNome() + "!\n\n"
                   + "Seu pagamento do pedido #" + msg.pedidoId()
                   + " foi confirmado.\n"
                   + "Valor: R$ " + msg.valor() + "\n\n"
                   + "Obrigado por comprar no Mercado Express!"
   );

   mail.send(email);

   repo.save(new EmailEnviado(
           msg.pedidoId(),
           msg.clienteEmail(),
           assunto,
           "ENVIADO",
           "Mensagem aceita pelo servidor SMTP."
   ));

  } catch (Exception ex) {
   repo.save(new EmailEnviado(
           msg.pedidoId(),
           msg.clienteEmail(),
           assunto,
           "FALHOU",
           ex.getMessage()
   ));

   System.err.println(
           "Falha no envio de e-mail do pedido "
                   + msg.pedidoId() + ": " + ex.getMessage()
   );
  }
 }
}