package br.com.bancotranquilo.controller;
import br.com.bancotranquilo.dto.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/pagamentos")
public class PagamentoController {
 @PostMapping public PagamentoResponse pagar(@Valid @RequestBody PagamentoRequest req){
  // API simulada para fins acadêmicos: não processa dinheiro real nem dados de cartão.
  String status="RECUSADO".equalsIgnoreCase(req.resultadoSimulado())?"RECUSADO":"APROVADO";
  String detalhe="APROVADO".equals(status)?"Pagamento aprovado pela API simulada Banco Tranquilo.":"Pagamento recusado pela regra de simulação acadêmica.";
  return new PagamentoResponse(req.pedidoId(),req.valor(),status,"BT-"+UUID.randomUUID(),detalhe);
 }
 @GetMapping("/health") public java.util.Map<String,String> health(){return java.util.Map.of("servico","Banco Tranquilo API simulada","status","UP");}
}
