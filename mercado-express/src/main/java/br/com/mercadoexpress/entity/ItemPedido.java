package br.com.mercadoexpress.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity @Table(name="itens_pedido")
public class ItemPedido {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false) private Long pedidoId;
 @Column(nullable=false) private Long produtoId;
 @Column(nullable=false) private String produtoNome;
 @Column(nullable=false) private int quantidade;
 @Column(nullable=false,precision=12,scale=2) private BigDecimal precoUnitario;
 @Column(nullable=false,precision=12,scale=2) private BigDecimal custoUnitario;
 public ItemPedido() {}
 public Long getId(){return id;} public Long getPedidoId(){return pedidoId;} public void setPedidoId(Long v){pedidoId=v;}
 public Long getProdutoId(){return produtoId;} public void setProdutoId(Long v){produtoId=v;}
 public String getProdutoNome(){return produtoNome;} public void setProdutoNome(String v){produtoNome=v;}
 public int getQuantidade(){return quantidade;} public void setQuantidade(int v){quantidade=v;}
 public BigDecimal getPrecoUnitario(){return precoUnitario;} public void setPrecoUnitario(BigDecimal v){precoUnitario=v;}
 public BigDecimal getCustoUnitario(){return custoUnitario;} public void setCustoUnitario(BigDecimal v){custoUnitario=v;}
}
