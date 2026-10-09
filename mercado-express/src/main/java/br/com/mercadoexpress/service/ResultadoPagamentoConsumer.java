package br.com.mercadoexpress.service;

import br.com.mercadoexpress.config.RabbitConfig;
import br.com.mercadoexpress.dto.EmailMessage;
import br.com.mercadoexpress.dto.PagamentoResult;
import br.com.mercadoexpress.entity.Pagamento;
import br.com.mercadoexpress.entity.Pedido;
import br.com.mercadoexpress.entity.Produto;
import br.com.mercadoexpress.repository.ItemPedidoRepository;
import br.com.mercadoexpress.repository.PagamentoRepository;
import br.com.mercadoexpress.repository.PedidoRepository;
import br.com.mercadoexpress.repository.ProdutoRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ResultadoPagamentoConsumer {
    private final PedidoRepository pedidos;
    private final PagamentoRepository pagamentos;
    private final ProdutoRepository produtos;
    private final ItemPedidoRepository itens;
    private final RabbitTemplate rabbit;

    public ResultadoPagamentoConsumer(PedidoRepository pedidos, PagamentoRepository pagamentos,
            ProdutoRepository produtos, ItemPedidoRepository itens, RabbitTemplate rabbit) {
        this.pedidos = pedidos; this.pagamentos = pagamentos; this.produtos = produtos;
        this.itens = itens; this.rabbit = rabbit;
    }

    @RabbitListener(queues = RabbitConfig.PAYMENT_RESULT)
    @Transactional
    public void processar(PagamentoResult result) {
        Pedido pedido = pedidos.findById(result.pedidoId()).orElseThrow();
        Pagamento pagamento = pagamentos.findByPedidoId(result.pedidoId()).orElseThrow();
        if (!"PENDENTE".equals(pagamento.getStatus())) return; // idempotência do consumidor

        pagamento.setStatus(result.status());
        pagamento.setTransacaoId(result.transacaoId());
        pagamento.setDetalhe(result.detalhe());
        pagamentos.save(pagamento);

        if ("APROVADO".equals(result.status())) {
            for (var item : itens.findByPedidoId(pedido.getId())) {
                Produto produto = produtos.findById(item.getProdutoId()).orElseThrow();
                if (produto.getEstoque() < item.getQuantidade()) {
                    throw new IllegalStateException("Estoque alterado durante o processamento do pagamento");
                }
                produto.setEstoque(produto.getEstoque() - item.getQuantidade());
                produtos.save(produto);
            }
            pedido.setStatus("CONFIRMADO");
        } else {
            pedido.setStatus("CANCELADO");
        }
        pedidos.save(pedido);
        rabbit.convertAndSend(RabbitConfig.EXCHANGE, "email.confirmacao",
                new EmailMessage(pedido.getId(), pedido.getClienteNome(), pedido.getClienteEmail(),
                        result.status(), pedido.getTotal().toPlainString()));
    }
}
