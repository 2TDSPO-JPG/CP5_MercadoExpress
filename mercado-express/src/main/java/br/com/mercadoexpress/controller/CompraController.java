package br.com.mercadoexpress.controller;

import br.com.mercadoexpress.dto.CompraRequest;
import br.com.mercadoexpress.entity.Pedido;
import br.com.mercadoexpress.service.CompraService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/compras")
public class CompraController {

    private final CompraService service;

    public CompraController(CompraService service) {
        this.service = service;
    }

    /** Registra a compra e publica a solicitação de pagamento no RabbitMQ (processamento assíncrono). */
    @PostMapping
    public ResponseEntity<Map<String, Object>> criar(@Valid @RequestBody CompraRequest req,
                                                     @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        Pedido p = service.criar(req, key);
        return ResponseEntity.accepted().body(Map.of(
                "pedidoId", p.getId(),
                "status", p.getStatus(),
                "total", p.getTotal(),
                "mensagem", "Compra recebida para processamento assíncrono."));
    }

    @GetMapping("/{id}")
    public Pedido buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    /** Reenvia para a fila um pedido cujo pagamento ficou em ERRO (ex.: Banco Tranquilo estava fora do ar). */
    @PostMapping("/{id}/reprocessar")
    public ResponseEntity<Map<String, Object>> reprocessar(@PathVariable Long id) {
        Pedido p = service.reprocessar(id);
        return ResponseEntity.accepted().body(Map.of(
                "pedidoId", p.getId(),
                "status", p.getStatus(),
                "mensagem", "Pedido reenviado para a fila pagamento.solicitado."));
    }
}
