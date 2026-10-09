package br.com.mercadoexpress.dto;
import java.math.BigDecimal;
public record PagamentoResult(Long pedidoId, BigDecimal valor, String status, String transacaoId, String detalhe) {}
