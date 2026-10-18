package br.com.mercadoexpress.service;

import br.com.mercadoexpress.config.RabbitConfig;
import br.com.mercadoexpress.dto.PagamentoMessage;
import br.com.mercadoexpress.dto.PagamentoResult;
import br.com.mercadoexpress.entity.Pagamento;
import br.com.mercadoexpress.entity.Pedido;
import br.com.mercadoexpress.repository.PagamentoRepository;
import br.com.mercadoexpress.repository.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Consome a fila "pagamento.solicitado", chama a API do Banco Tranquilo (HTTP)
 * e publica a resposta na fila "pagamento.resultado".
 */
@Component
public class PagamentoConsumer {

    private static final Logger log = LoggerFactory.getLogger(PagamentoConsumer.class);

    private final RestClient rest;
    private final PedidoRepository pedidos;
    private final PagamentoRepository pagamentos;
    private final CompraService compras;
    private final RabbitTemplate rabbit;

    public PagamentoConsumer(@Value("${app.banco-tranquilo.url}") String url, PedidoRepository pedidos,
                             PagamentoRepository pagamentos, CompraService compras, RabbitTemplate rabbit) {
        this.rest = RestClient.builder().baseUrl(url).build();
        this.pedidos = pedidos;
        this.pagamentos = pagamentos;
        this.compras = compras;
        this.rabbit = rabbit;
    }

    @RabbitListener(queues = RabbitConfig.PAYMENT_REQUEST)
    public void processar(PagamentoMessage msg) {
        log.info("[pagamento.solicitado] pedido={} valor={}", msg.pedidoId(), msg.valor());
        Pedido pedido = pedidos.findById(msg.pedidoId()).orElse(null);
        Pagamento pg = pagamentos.findByPedidoId(msg.pedidoId()).orElse(null);
        if (pedido == null || pg == null) {
            // A mensagem pode chegar antes do commit da compra: lançar a exceção faz o Spring AMQP
            // tentar de novo (retry configurado no application.yml). Após 3 tentativas ela é descartada.
            throw new IllegalStateException("Pedido " + msg.pedidoId() + " ainda não encontrado no banco");
        }
        if (!"PENDENTE".equals(pg.getStatus())) return; // idempotência: já processado

        try {
            PagamentoResult result = rest.post().uri("/api/pagamentos").body(msg).retrieve().body(PagamentoResult.class);
            if (result == null) throw new IllegalStateException("Resposta vazia do Banco Tranquilo");
            log.info("[Banco Tranquilo] pedido={} status={} transacao={}", result.pedidoId(), result.status(), result.transacaoId());
            rabbit.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.PAYMENT_RESULT, result);
        } catch (Exception ex) {
            // Banco indisponível: registra a falha, libera o estoque e permite reprocessar depois
            log.error("Falha ao consultar Banco Tranquilo para o pedido {}: {}", msg.pedidoId(), ex.getMessage());
            pg.setStatus("ERRO");
            pg.setDetalhe("Falha ao consultar Banco Tranquilo: " + ex.getMessage());
            pagamentos.save(pg);
            pedido.setStatus("ERRO_PAGAMENTO");
            pedidos.save(pedido);
            compras.devolverEstoque(pedido.getId());
        }
    }
}
