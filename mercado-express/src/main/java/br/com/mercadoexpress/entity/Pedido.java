package br.com.mercadoexpress.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Entity @Table(name="pedidos")
public class Pedido {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false) private String clienteNome;
 @Column(nullable=false) private String clienteEmail;
 @Column(nullable=false) private String metodoPagamento;
 @Column(nullable=false,precision=12,scale=2) private BigDecimal total;
 @Column(nullable=false) private String status="PENDENTE";
 @Column(nullable=false) private LocalDateTime criadoEm=LocalDateTime.now();
 @Column(unique=true) private String idempotencyKey;
 public Pedido() {}
 public Long getId(){return id;} public void setId(Long v){id=v;}
 public String getClienteNome(){return clienteNome;} public void setClienteNome(String v){clienteNome=v;}
 public String getClienteEmail(){return clienteEmail;} public void setClienteEmail(String v){clienteEmail=v;}
 public String getMetodoPagamento(){return metodoPagamento;} public void setMetodoPagamento(String v){metodoPagamento=v;}
 public BigDecimal getTotal(){return total;} public void setTotal(BigDecimal v){total=v;}
 public String getStatus(){return status;} public void setStatus(String v){status=v;}
 public LocalDateTime getCriadoEm(){return criadoEm;} public void setCriadoEm(LocalDateTime v){criadoEm=v;}
 public String getIdempotencyKey(){return idempotencyKey;} public void setIdempotencyKey(String v){idempotencyKey=v;}
}
