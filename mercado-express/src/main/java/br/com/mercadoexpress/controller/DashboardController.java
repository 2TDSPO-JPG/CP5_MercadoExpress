package br.com.mercadoexpress.controller;

import br.com.mercadoexpress.entity.*;
import br.com.mercadoexpress.repository.*;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Indicadores da gerência do Mercado Express, calculados a partir do PostgreSQL.
 * Consumidos pela página /dashboard (gráficos com Chart.js) e disponíveis em JSON para o Postman.
 */
@RestController
public class DashboardController {

    private final PedidoRepository pedidos;
    private final PagamentoRepository pagamentos;
    private final ProdutoRepository produtos;
    private final ItemPedidoRepository itens;
    private final EmailEnviadoRepository emails;

    public DashboardController(PedidoRepository pedidos, PagamentoRepository pagamentos, ProdutoRepository produtos,
                               ItemPedidoRepository itens, EmailEnviadoRepository emails) {
        this.pedidos = pedidos;
        this.pagamentos = pagamentos;
        this.produtos = produtos;
        this.itens = itens;
        this.emails = emails;
    }

    private static BigDecimal lucroItem(ItemPedido i) {
        return i.getPrecoUnitario().subtract(i.getCustoUnitario()).multiply(BigDecimal.valueOf(i.getQuantidade()));
    }

    private Map<Long, List<ItemPedido>> itensPorPedido() {
        return itens.findAll().stream().collect(Collectors.groupingBy(ItemPedido::getPedidoId));
    }

    /** Resumo geral (cards do dashboard). */
    @GetMapping("/api/dashboard")
    public Map<String, Object> dashboard() {
        var allPedidos = pedidos.findAll();
        var aprovados = allPedidos.stream().filter(p -> "CONFIRMADO".equals(p.getStatus())).toList();
        var porPedido = itensPorPedido();

        BigDecimal faturamento = aprovados.stream().map(Pedido::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal lucro = BigDecimal.ZERO;
        long unidadesVendidas = 0;
        for (Pedido p : aprovados)
            for (ItemPedido i : porPedido.getOrDefault(p.getId(), List.of())) {
                lucro = lucro.add(lucroItem(i));
                unidadesVendidas += i.getQuantidade();
            }
        BigDecimal ticketMedio = aprovados.isEmpty() ? BigDecimal.ZERO
                : faturamento.divide(BigDecimal.valueOf(aprovados.size()), 2, java.math.RoundingMode.HALF_UP);

        var todosProdutos = produtos.findAll();
        long estoqueTotal = todosProdutos.stream().mapToLong(Produto::getEstoque).sum();
        BigDecimal valorEstoque = todosProdutos.stream()
                .map(p -> p.getPrecoCusto().multiply(BigDecimal.valueOf(p.getEstoque())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Map<String, Object>> statusPagamento = pagamentos.findAll().stream()
                .collect(Collectors.groupingBy(Pagamento::getStatus, TreeMap::new, Collectors.counting()))
                .entrySet().stream()
                .map(e -> Map.<String, Object>of("status", e.getKey(), "quantidade", e.getValue())).toList();
        List<Map<String, Object>> statusPedido = allPedidos.stream()
                .collect(Collectors.groupingBy(Pedido::getStatus, TreeMap::new, Collectors.counting()))
                .entrySet().stream()
                .map(e -> Map.<String, Object>of("status", e.getKey(), "quantidade", e.getValue())).toList();

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("totalPedidos", allPedidos.size());
        r.put("pedidosConfirmados", aprovados.size());
        r.put("faturamentoAprovado", faturamento);
        r.put("lucroBrutoEstimado", lucro);
        r.put("ticketMedio", ticketMedio);
        r.put("unidadesVendidas", unidadesVendidas);
        r.put("produtos", todosProdutos.size());
        r.put("produtosEstoqueBaixo", todosProdutos.stream().filter(p -> p.getEstoque() <= 5).count());
        r.put("estoqueTotalUnidades", estoqueTotal);
        r.put("valorEstoqueCusto", valorEstoque);
        r.put("emailsEnviados", emails.countByStatus("ENVIADO"));
        r.put("emailsComFalha", emails.countByStatus("FALHOU"));
        r.put("pagamentosPorStatus", statusPagamento);
        r.put("pedidosPorStatus", statusPedido);
        return r;
    }

    /** Faturamento, lucro e quantidade de pedidos confirmados por dia (gráfico de linhas). */
    @GetMapping("/api/dashboard/vendas-por-dia")
    public List<Map<String, Object>> vendasPorDia(@RequestParam(defaultValue = "14") int dias) {
        dias = Math.max(1, Math.min(dias, 90));
        var porPedido = itensPorPedido();
        LocalDate hoje = LocalDate.now();
        LocalDate inicio = hoje.minusDays(dias - 1L);

        Map<LocalDate, BigDecimal[]> acumulado = new TreeMap<>();
        for (LocalDate d = inicio; !d.isAfter(hoje); d = d.plusDays(1))
            acumulado.put(d, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});

        for (Pedido p : pedidos.findAll()) {
            if (!"CONFIRMADO".equals(p.getStatus())) continue;
            BigDecimal[] v = acumulado.get(p.getCriadoEm().toLocalDate());
            if (v == null) continue;
            v[0] = v[0].add(p.getTotal());
            for (ItemPedido i : porPedido.getOrDefault(p.getId(), List.of())) v[1] = v[1].add(lucroItem(i));
            v[2] = v[2].add(BigDecimal.ONE);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        acumulado.forEach((dia, v) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("dia", dia.toString());
            m.put("faturamento", v[0]);
            m.put("lucro", v[1]);
            m.put("pedidos", v[2].intValue());
            out.add(m);
        });
        return out;
    }

    /** Vendas, lucro e estoque por produto (gráficos de barras e tabela de estoque). */
    @GetMapping("/api/dashboard/produtos")
    public List<Map<String, Object>> porProduto() {
        Set<Long> confirmados = pedidos.findAll().stream()
                .filter(p -> "CONFIRMADO".equals(p.getStatus())).map(Pedido::getId).collect(Collectors.toSet());
        Map<Long, List<ItemPedido>> vendidosPorProduto = itens.findAll().stream()
                .filter(i -> confirmados.contains(i.getPedidoId()))
                .collect(Collectors.groupingBy(ItemPedido::getProdutoId));

        List<Map<String, Object>> out = new ArrayList<>();
        for (Produto p : produtos.findAll(org.springframework.data.domain.Sort.by("id"))) {
            var vendidos = vendidosPorProduto.getOrDefault(p.getId(), List.of());
            int qtd = vendidos.stream().mapToInt(ItemPedido::getQuantidade).sum();
            BigDecimal fat = vendidos.stream()
                    .map(i -> i.getPrecoUnitario().multiply(BigDecimal.valueOf(i.getQuantidade())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal luc = vendidos.stream().map(DashboardController::lucroItem).reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("nome", p.getNome());
            m.put("precoCusto", p.getPrecoCusto());
            m.put("precoVenda", p.getPrecoVenda());
            m.put("estoque", p.getEstoque());
            m.put("estoqueBaixo", p.getEstoque() <= 5);
            m.put("quantidadeVendida", qtd);
            m.put("faturamento", fat);
            m.put("lucro", luc);
            out.add(m);
        }
        return out;
    }

    /** Compras realizadas (mais recentes primeiro) com itens, pagamento e situação do e-mail. */
    @GetMapping("/api/dashboard/compras")
    public List<Map<String, Object>> compras(@RequestParam(defaultValue = "50") int limite) {
        var porPedido = itensPorPedido();
        Map<Long, Pagamento> pagPorPedido = pagamentos.findAll().stream()
                .collect(Collectors.toMap(Pagamento::getPedidoId, p -> p, (a, b) -> a));
        Map<Long, EmailEnviado> emailPorPedido = emails.findAll().stream()
                .filter(e -> e.getPedidoId() != null)
                .collect(Collectors.toMap(EmailEnviado::getPedidoId, e -> e, (a, b) -> b));

        return pedidos.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id"))
                .stream().limit(Math.max(1, Math.min(limite, 500))).map(p -> {
                    Pagamento pg = pagPorPedido.get(p.getId());
                    EmailEnviado em = emailPorPedido.get(p.getId());
                    String descricao = porPedido.getOrDefault(p.getId(), List.of()).stream()
                            .map(i -> i.getQuantidade() + "x " + i.getProdutoNome())
                            .collect(Collectors.joining(", "));
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("pedidoId", p.getId());
                    m.put("criadoEm", p.getCriadoEm());
                    m.put("clienteNome", p.getClienteNome());
                    m.put("clienteEmail", p.getClienteEmail());
                    m.put("metodoPagamento", p.getMetodoPagamento());
                    m.put("itens", descricao);
                    m.put("total", p.getTotal());
                    m.put("statusPedido", p.getStatus());
                    m.put("statusPagamento", pg == null ? null : pg.getStatus());
                    m.put("transacaoId", pg == null ? null : pg.getTransacaoId());
                    m.put("detalhePagamento", pg == null ? null : pg.getDetalhe());
                    m.put("statusEmail", em == null ? null : em.getStatus());
                    return m;
                }).toList();
    }

    /** Histórico de e-mails gravado no PostgreSQL (controle de envio). */
    @GetMapping("/api/emails")
    public List<EmailEnviado> emails() {
        return emails.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id"));
    }
}
