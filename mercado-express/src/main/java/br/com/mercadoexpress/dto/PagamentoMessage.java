package br.com.mercadoexpress.dto;
import java.math.BigDecimal;
public record PagamentoMessage(Long pedidoId, BigDecimal valor, String metodoPagamento, String clienteNome, String clienteEmail, String resultadoSimulado) {}
