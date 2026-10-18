package br.com.mercadoexpress.service;

import br.com.mercadoexpress.config.RabbitConfig;
import br.com.mercadoexpress.dto.CompraRequest;
import br.com.mercadoexpress.dto.PagamentoMessage;
import br.com.mercadoexpress.entity.ItemPedido;
import br.com.mercadoexpress.entity.Pagamento;
import br.com.mercadoexpress.entity.Pedido;
import br.com.mercadoexpress.entity.Produto;
import br.com.mercadoexpress.repository.ItemPedidoRepository;
import br.com.mercadoexpress.repository.PagamentoRepository;
import br.com.mercadoexpress.repository.PedidoRepository;
import br.com.mercadoexpress.repository.ProdutoRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Regras da compra:
 * 1. Valida produtos e estoque e RESERVA o estoque (baixa imediata) para que dois clientes
 *    não comprem a mesma unidade enquanto o pagamento está sendo processado.
 * 2. Grava Pedido, Itens e Pagamento (PENDENTE) no PostgreSQL.
 * 3. Publica a solicitação de pagamento na exchange do RabbitMQ.
 * Se o pagamento for recusado ou falhar, o estoque reservado é devolvido.
 */
@Service
public class CompraService {

    private final ProdutoRepository produtos;
    private final PedidoRepository pedidos;
    private final ItemPedidoRepository itens;
    private final PagamentoRepository pagamentos;
    private final RabbitTemplate rabbit;

    public CompraService(ProdutoRepository produtos, PedidoRepository pedidos, ItemPedidoRepository itens,
                         PagamentoRepository pagamentos, RabbitTemplate rabbit) {
        this.produtos = produtos;
        this.pedidos = pedidos;
        this.itens = itens;
        this.pagamentos = pagamentos;
        this.rabbit = rabbit;
    }

    @Transactional
    public Pedido criar(CompraRequest req, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existente = pedidos.findByIdempotencyKey(idempotencyKey);
            if (existente.isPresent()) return existente.get();
        }

        BigDecimal total = BigDecimal.ZERO;
        for (var item : req.itens()) {
            Produto p = produtos.findById(item.produtoId())
                    .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado: " + item.produtoId()));
            if (p.getEstoque() < item.quantidade())
                throw new IllegalArgumentException("Estoque insuficiente para " + p.getNome()
                        + " (disponível: " + p.getEstoque() + ")");
            total = total.add(p.getPrecoVenda().multiply(BigDecimal.valueOf(item.quantidade())));
        }

        Pedido pedido = new Pedido();
        pedido.setClienteNome(req.clienteNome());
        pedido.setClienteEmail(req.clienteEmail());
        pedido.setMetodoPagamento(req.metodoPagamento());
        pedido.setTotal(total);
        pedido.setStatus("PENDENTE");
        pedido.setIdempotencyKey(idempotencyKey == null || idempotencyKey.isBlank()
                ? UUID.randomUUID().toString() : idempotencyKey);
        pedido = pedidos.save(pedido);

        for (var item : req.itens()) {
            Produto p = produtos.findById(item.produtoId()).orElseThrow();
            p.setEstoque(p.getEstoque() - item.quantidade()); // reserva do estoque
            produtos.save(p);

            ItemPedido ip = new ItemPedido();
            ip.setPedidoId(pedido.getId());
            ip.setProdutoId(p.getId());
            ip.setProdutoNome(p.getNome());
            ip.setQuantidade(item.quantidade());
            ip.setPrecoUnitario(p.getPrecoVenda());
            ip.setCustoUnitario(p.getPrecoCusto());
            itens.save(ip);
        }

        Pagamento pg = new Pagamento();
        pg.setPedidoId(pedido.getId());
        pg.setValor(total);
        pg.setStatus("PENDENTE");
        pagamentos.save(pg);

        rabbit.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.PAYMENT_REQUEST,
                new PagamentoMessage(pedido.getId(), total, req.metodoPagamento(), req.clienteNome(),
                        req.clienteEmail(), req.resultadoSimulado()));
        return pedido;
    }

    /** Devolve ao estoque as quantidades reservadas pelo pedido (pagamento recusado ou com erro). */
    @Transactional
    public void devolverEstoque(Long pedidoId) {
        for (ItemPedido item : itens.findByPedidoId(pedidoId)) {
            produtos.findById(item.getProdutoId()).ifPresent(p -> {
                p.setEstoque(p.getEstoque() + item.getQuantidade());
                produtos.save(p);
            });
        }
    }

    /**
     * Reenvia para a fila um pedido cujo pagamento falhou (ex.: Banco Tranquilo fora do ar).
     * Reserva o estoque novamente antes de republicar a mensagem.
     */
    @Transactional
    public Pedido reprocessar(Long pedidoId) {
        Pedido pedido = buscar(pedidoId);
        Pagamento pg = pagamentos.findByPedidoId(pedidoId).orElseThrow();
        if (!"ERRO".equals(pg.getStatus()))
            throw new IllegalArgumentException("Só é possível reprocessar pedidos com pagamento em ERRO. Status atual: " + pg.getStatus());

        List<ItemPedido> lista = itens.findByPedidoId(pedidoId);
        for (ItemPedido item : lista) {
            Produto p = produtos.findById(item.getProdutoId())
                    .orElseThrow(() -> new IllegalArgumentException("Produto removido: " + item.getProdutoNome()));
            if (p.getEstoque() < item.getQuantidade())
                throw new IllegalArgumentException("Estoque insuficiente para reprocessar " + p.getNome());
        }
        for (ItemPedido item : lista) {
            Produto p = produtos.findById(item.getProdutoId()).orElseThrow();
            p.setEstoque(p.getEstoque() - item.getQuantidade());
            produtos.save(p);
        }

        pg.setStatus("PENDENTE");
        pg.setDetalhe("Reprocessamento solicitado");
        pagamentos.save(pg);
        pedido.setStatus("PENDENTE");
        pedidos.save(pedido);

        rabbit.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.PAYMENT_REQUEST,
                new PagamentoMessage(pedido.getId(), pedido.getTotal(), pedido.getMetodoPagamento(),
                        pedido.getClienteNome(), pedido.getClienteEmail(), null));
        return pedido;
    }

    public Pedido buscar(Long id) {
        return pedidos.findById(id).orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado: " + id));
    }
}
