package br.com.mercadoexpress.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name="emails_enviados")
public class EmailEnviado {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 private Long pedidoId;
 @Column(nullable=false) private String destinatario;
 @Column(nullable=false) private String assunto;
 @Column(nullable=false) private String status;
 @Column(length=2000) private String detalhe;
 @Column(nullable=false) private LocalDateTime criadoEm=LocalDateTime.now();
 public EmailEnviado() {}
 public EmailEnviado(Long pedidoId,String destinatario,String assunto,String status,String detalhe){this.pedidoId=pedidoId;this.destinatario=destinatario;this.assunto=assunto;this.status=status;this.detalhe=detalhe;}
 public Long getId(){return id;} public Long getPedidoId(){return pedidoId;} public String getDestinatario(){return destinatario;} public String getAssunto(){return assunto;} public String getStatus(){return status;} public String getDetalhe(){return detalhe;} public LocalDateTime getCriadoEm(){return criadoEm;}
}
