package br.com.mercadoexpress.service;
import br.com.mercadoexpress.config.RabbitConfig;
import br.com.mercadoexpress.dto.*;
import br.com.mercadoexpress.entity.*;
import br.com.mercadoexpress.repository.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
@Component
public class PagamentoConsumer {
 private final RestClient rest; private final PedidoRepository pedidos; private final PagamentoRepository pagamentos; private final ProdutoRepository produtos; private final ItemPedidoRepository itens; private final RabbitTemplate rabbit;
 public PagamentoConsumer(@Value("${app.banco-tranquilo.url}") String url,PedidoRepository pedidos,PagamentoRepository pagamentos,ProdutoRepository produtos,ItemPedidoRepository itens,RabbitTemplate rabbit){this.rest=RestClient.builder().baseUrl(url).build();this.pedidos=pedidos;this.pagamentos=pagamentos;this.produtos=produtos;this.itens=itens;this.rabbit=rabbit;}
 @RabbitListener(queues=RabbitConfig.PAYMENT_REQUEST)
 public void processar(PagamentoMessage msg){
  Pedido p=pedidos.findById(msg.pedidoId()).orElseThrow();
  Pagamento pg=pagamentos.findByPedidoId(msg.pedidoId()).orElseThrow();
  if(!"PENDENTE".equals(pg.getStatus()))return;
  try {
   PagamentoResult result=rest.post().uri("/api/pagamentos").body(msg).retrieve().body(PagamentoResult.class);
   if(result==null)throw new IllegalStateException("Resposta vazia do Banco Tranquilo");
   rabbit.convertAndSend(RabbitConfig.EXCHANGE,"pagamento.resultado",result);
  } catch(Exception ex) {
   pg.setStatus("ERRO");pg.setDetalhe("Falha ao consultar Banco Tranquilo: "+ex.getMessage());pagamentos.save(pg);
   p.setStatus("PENDENTE");pedidos.save(p);
   // Falha explícita e persistida. Reprocessamento operacional pode ser feito após corrigir a indisponibilidade.
  }
 }
}
