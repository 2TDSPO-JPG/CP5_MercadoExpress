package br.com.mercadoexpress.controller;

import br.com.mercadoexpress.dto.CompraRequest;
import br.com.mercadoexpress.entity.EmailEnviado;
import br.com.mercadoexpress.entity.Pagamento;
import br.com.mercadoexpress.entity.Pedido;
import br.com.mercadoexpress.repository.EmailEnviadoRepository;
import br.com.mercadoexpress.repository.PagamentoRepository;
import br.com.mercadoexpress.repository.PedidoRepository;
import br.com.mercadoexpress.service.CompraService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/compras")
public class CompraController {

    private final CompraService service;
    private final PedidoRepository pedidos;
    private final PagamentoRepository pagamentos;
    private final EmailEnviadoRepository emails;

    public CompraController(CompraService service, PedidoRepository pedidos,
                            PagamentoRepository pagamentos, EmailEnviadoRepository emails) {
        this.service = service;
        this.pedidos = pedidos;
        this.pagamentos = pagamentos;
        this.emails = emails;
    }

    private static boolean admin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    /** Cliente só enxerga os próprios pedidos; a gerência enxerga todos. */
    private Pedido pedidoPermitido(Long id, Authentication auth) {
        Pedido p = service.buscar(id);
        if (!admin(auth) && !p.getClienteEmail().equalsIgnoreCase(auth.getName()))
            throw new AccessDeniedException("Pedido de outro cliente");
        return p;
    }

    /**
     * Registra a compra e publica a solicitação de pagamento no RabbitMQ (processamento assíncrono).
     * Para o perfil CLIENTE o e-mail do pedido é sempre o da conta logada (não dá para comprar em nome de outro).
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> criar(@Valid @RequestBody CompraRequest req,
                                                     @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                     Authentication auth) {
        CompraRequest efetiva = admin(auth) ? req
                : new CompraRequest(req.clienteNome(), auth.getName(), req.metodoPagamento(), req.itens(), req.resultadoSimulado());
        Pedido p = service.criar(efetiva, key);
        if (!admin(auth) && !p.getClienteEmail().equalsIgnoreCase(auth.getName()))
            throw new IllegalArgumentException("Idempotency-Key já utilizada.");
        return ResponseEntity.accepted().body(Map.of(
                "pedidoId", p.getId(),
                "status", p.getStatus(),
                "total", p.getTotal(),
                "mensagem", "Compra recebida para processamento assíncrono."));
    }

    @GetMapping("/{id}")
    public Pedido buscar(@PathVariable Long id, Authentication auth) {
        return pedidoPermitido(id, auth);
    }

    /** Situação completa do pedido: pedido, pagamento (Banco Tranquilo) e e-mail. Usado pela loja. */
    @GetMapping("/{id}/status")
    public Map<String, Object> status(@PathVariable Long id, Authentication auth) {
        Pedido p = pedidoPermitido(id, auth);
        Pagamento pg = pagamentos.findByPedidoId(id).orElse(null);
        EmailEnviado em = emails.findFirstByPedidoIdOrderByIdDesc(id).orElse(null);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("pedidoId", p.getId());
        r.put("status", p.getStatus());
        r.put("total", p.getTotal());
        r.put("statusPagamento", pg == null ? null : pg.getStatus());
        r.put("transacaoId", pg == null ? null : pg.getTransacaoId());
        r.put("statusEmail", em == null ? null : em.getStatus());
        r.put("destinatarioEmail", em == null ? null : em.getDestinatario());
        r.put("detalheEmail", em == null ? null : em.getDetalhe());
        return r;
    }

    /** Pedidos do usuário logado. */
    @GetMapping("/minhas")
    public List<Map<String, Object>> minhas(Authentication auth) {
        return pedidos.findTop20ByClienteEmailIgnoreCaseOrderByIdDesc(auth.getName()).stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("pedidoId", p.getId());
            m.put("criadoEm", p.getCriadoEm());
            m.put("total", p.getTotal());
            m.put("status", p.getStatus());
            return m;
        }).toList();
    }

    /** Reenvia para a fila um pedido cujo pagamento ficou em ERRO (somente ADMIN). */
    @PostMapping("/{id}/reprocessar")
    public ResponseEntity<Map<String, Object>> reprocessar(@PathVariable Long id) {
        Pedido p = service.reprocessar(id);
        return ResponseEntity.accepted().body(Map.of(
                "pedidoId", p.getId(),
                "status", p.getStatus(),
                "mensagem", "Pedido reenviado para a fila pagamento.solicitado."));
    }
}
