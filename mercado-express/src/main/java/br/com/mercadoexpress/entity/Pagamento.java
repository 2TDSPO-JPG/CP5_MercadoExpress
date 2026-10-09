package br.com.mercadoexpress.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Entity @Table(name="pagamentos")
public class Pagamento {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,unique=true) private Long pedidoId;
 @Column(nullable=false,precision=12,scale=2) private BigDecimal valor;
 @Column(nullable=false) private String status="PENDENTE";
 private String transacaoId;
 @Column(length=1000) private String detalhe;
 @Column(nullable=false) private LocalDateTime atualizadoEm=LocalDateTime.now();
 public Pagamento() {}
 public Long getId(){return id;} public Long getPedidoId(){return pedidoId;} public void setPedidoId(Long v){pedidoId=v;}
 public BigDecimal getValor(){return valor;} public void setValor(BigDecimal v){valor=v;}
 public String getStatus(){return status;} public void setStatus(String v){status=v;atualizadoEm=LocalDateTime.now();}
 public String getTransacaoId(){return transacaoId;} public void setTransacaoId(String v){transacaoId=v;}
 public String getDetalhe(){return detalhe;} public void setDetalhe(String v){detalhe=v;}
 public LocalDateTime getAtualizadoEm(){return atualizadoEm;}
}
