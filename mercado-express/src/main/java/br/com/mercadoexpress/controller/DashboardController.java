package br.com.mercadoexpress.controller;
import br.com.mercadoexpress.entity.*;
import br.com.mercadoexpress.repository.*;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
@RestController
public class DashboardController {
 private final PedidoRepository pedidos; private final PagamentoRepository pagamentos; private final ProdutoRepository produtos; private final ItemPedidoRepository itens; private final EmailEnviadoRepository emails;
 public DashboardController(PedidoRepository pedidos,PagamentoRepository pagamentos,ProdutoRepository produtos,ItemPedidoRepository itens,EmailEnviadoRepository emails){this.pedidos=pedidos;this.pagamentos=pagamentos;this.produtos=produtos;this.itens=itens;this.emails=emails;}
 @GetMapping("/api/dashboard") public Map<String,Object> dashboard(){
  var allPedidos=pedidos.findAll();var aprovados=allPedidos.stream().filter(p->"CONFIRMADO".equals(p.getStatus())).toList();
  BigDecimal faturamento=aprovados.stream().map(Pedido::getTotal).reduce(BigDecimal.ZERO,BigDecimal::add);
  BigDecimal lucro=BigDecimal.ZERO;for(Pedido p:aprovados)for(ItemPedido i:itens.findByPedidoId(p.getId()))lucro=lucro.add(i.getPrecoUnitario().subtract(i.getCustoUnitario()).multiply(BigDecimal.valueOf(i.getQuantidade())));
  List<Map<String,Object>> status=pagamentos.findAll().stream().collect(Collectors.groupingBy(Pagamento::getStatus,Collectors.counting())).entrySet().stream().map(e->Map.<String,Object>of("status",e.getKey(),"quantidade",e.getValue())).toList();
  return Map.of("totalPedidos",allPedidos.size(),"pedidosConfirmados",aprovados.size(),"faturamentoAprovado",faturamento,"lucroBrutoEstimado",lucro,"produtos",produtos.count(),"produtosEstoqueBaixo",produtos.findAll().stream().filter(p->p.getEstoque()<=5).count(),"emailsEnviados",emails.countByStatus("ENVIADO"),"emailsComFalha",emails.countByStatus("FALHOU"),"pagamentosPorStatus",status);
 }
 @GetMapping("/api/emails") public List<EmailEnviado> emails(){return emails.findAll();}
}
