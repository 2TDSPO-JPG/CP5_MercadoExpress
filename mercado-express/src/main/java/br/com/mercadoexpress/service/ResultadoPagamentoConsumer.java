package br.com.mercadoexpress.service;

import br.com.mercadoexpress.config.RabbitConfig;
import br.com.mercadoexpress.dto.EmailMessage;
import br.com.mercadoexpress.dto.PagamentoResult;
import br.com.mercadoexpress.entity.Pagamento;
import br.com.mercadoexpress.entity.Pedido;
import br.com.mercadoexpress.repository.PagamentoRepository;
import br.com.mercadoexpress.repository.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consome a fila "pagamento.resultado": atualiza pagamento e pedido no PostgreSQL
 * e, se aprovado, publica na fila "email.confirmacao".
 */
@Component
public class ResultadoPagamentoConsumer {

    private static final Logger log = LoggerFactory.getLogger(ResultadoPagamentoConsumer.class);

    private final PedidoRepository pedidos;
    private final PagamentoRepository pagamentos;
    private final CompraService compras;
    private final RabbitTemplate rabbit;

    public ResultadoPagamentoConsumer(PedidoRepository pedidos, PagamentoRepository pagamentos,
                                      CompraService compras, RabbitTemplate rabbit) {
        this.pedidos = pedidos;
        this.pagamentos = pagamentos;
        this.compras = compras;
        this.rabbit = rabbit;
    }

    @RabbitListener(queues = RabbitConfig.PAYMENT_RESULT)
    @Transactional
    public void processar(PagamentoResult result) {
        log.info("[pagamento.resultado] pedido={} status={}", result.pedidoId(), result.status());
        Pedido pedido = pedidos.findById(result.pedidoId()).orElse(null);
        Pagamento pagamento = pagamentos.findByPedidoId(result.pedidoId()).orElse(null);
        if (pedido == null || pagamento == null) {
            log.warn("Resultado para pedido inexistente {}; mensagem descartada.", result.pedidoId());
            return;
        }
        if (!"PENDENTE".equals(pagamento.getStatus())) return; // idempotência do consumidor

        pagamento.setStatus(result.status());
        pagamento.setTransacaoId(result.transacaoId());
        pagamento.setDetalhe(result.detalhe());
        pagamentos.save(pagamento);

        if ("APROVADO".equals(result.status())) {
            pedido.setStatus("CONFIRMADO"); // o estoque já foi reservado na criação da compra
            pedidos.save(pedido);
            rabbit.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.EMAIL_QUEUE,
                    new EmailMessage(pedido.getId(), pedido.getClienteNome(), pedido.getClienteEmail(),
                            result.status(), pedido.getTotal().toPlainString()));
        } else {
            pedido.setStatus("CANCELADO");
            pedidos.save(pedido);
            compras.devolverEstoque(pedido.getId());
        }
    }
}
