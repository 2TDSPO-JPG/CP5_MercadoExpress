package br.com.mercadoexpress.entity;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
@Entity @Table(name="produtos")
public class Produto {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @NotBlank @Column(nullable=false) private String nome;
 @Column(length=1000) private String descricao;
 @NotNull @DecimalMin("0.01") @Column(nullable=false,precision=12,scale=2) private BigDecimal precoCusto;
 @NotNull @DecimalMin("0.01") @Column(nullable=false,precision=12,scale=2) private BigDecimal precoVenda;
 @Min(0) @Column(nullable=false) private int estoque;
 public Produto() {}
 public Long getId(){return id;} public void setId(Long id){this.id=id;}
 public String getNome(){return nome;} public void setNome(String v){nome=v;}
 public String getDescricao(){return descricao;} public void setDescricao(String v){descricao=v;}
 public BigDecimal getPrecoCusto(){return precoCusto;} public void setPrecoCusto(BigDecimal v){precoCusto=v;}
 public BigDecimal getPrecoVenda(){return precoVenda;} public void setPrecoVenda(BigDecimal v){precoVenda=v;}
 public int getEstoque(){return estoque;} public void setEstoque(int v){estoque=v;}
}
