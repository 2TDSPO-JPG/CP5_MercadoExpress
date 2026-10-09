package br.com.mercadoexpress.service;
import br.com.mercadoexpress.config.RabbitConfig;
import br.com.mercadoexpress.dto.*;
import br.com.mercadoexpress.entity.*;
import br.com.mercadoexpress.repository.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.UUID;
@Service
public class CompraService {
 private final ProdutoRepository produtos; private final PedidoRepository pedidos; private final ItemPedidoRepository itens; private final PagamentoRepository pagamentos; private final RabbitTemplate rabbit;
 public CompraService(ProdutoRepository produtos,PedidoRepository pedidos,ItemPedidoRepository itens,PagamentoRepository pagamentos,RabbitTemplate rabbit){this.produtos=produtos;this.pedidos=pedidos;this.itens=itens;this.pagamentos=pagamentos;this.rabbit=rabbit;}
 @Transactional
 public Pedido criar(CompraRequest req,String idempotencyKey){
  if(idempotencyKey!=null&&!idempotencyKey.isBlank()){var existente=pedidos.findByIdempotencyKey(idempotencyKey);if(existente.isPresent())return existente.get();}
  BigDecimal total=BigDecimal.ZERO;
  for(var item:req.itens()){Produto p=produtos.findById(item.produtoId()).orElseThrow(()->new IllegalArgumentException("Produto não encontrado: "+item.produtoId()));if(p.getEstoque()<item.quantidade())throw new IllegalArgumentException("Estoque insuficiente para "+p.getNome());total=total.add(p.getPrecoVenda().multiply(BigDecimal.valueOf(item.quantidade())));}
  Pedido pedido=new Pedido();pedido.setClienteNome(req.clienteNome());pedido.setClienteEmail(req.clienteEmail());pedido.setMetodoPagamento(req.metodoPagamento());pedido.setTotal(total);pedido.setStatus("PENDENTE");pedido.setIdempotencyKey(idempotencyKey==null||idempotencyKey.isBlank()?UUID.randomUUID().toString():idempotencyKey);pedido=pedidos.save(pedido);
  for(var item:req.itens()){Produto p=produtos.findById(item.produtoId()).orElseThrow();ItemPedido ip=new ItemPedido();ip.setPedidoId(pedido.getId());ip.setProdutoId(p.getId());ip.setProdutoNome(p.getNome());ip.setQuantidade(item.quantidade());ip.setPrecoUnitario(p.getPrecoVenda());ip.setCustoUnitario(p.getPrecoCusto());itens.save(ip);}
  Pagamento pg=new Pagamento();pg.setPedidoId(pedido.getId());pg.setValor(total);pg.setStatus("PENDENTE");pagamentos.save(pg);
  rabbit.convertAndSend(RabbitConfig.EXCHANGE,"pagamento.solicitado",new PagamentoMessage(pedido.getId(),total,req.metodoPagamento(),req.clienteNome(),req.clienteEmail(),req.resultadoSimulado()));
  return pedido;
 }
 public Pedido buscar(Long id){return pedidos.findById(id).orElseThrow(()->new IllegalArgumentException("Pedido não encontrado: "+id));}
}
