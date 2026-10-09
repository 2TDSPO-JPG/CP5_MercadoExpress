package br.com.bancotranquilo.dto;
import java.math.BigDecimal;
public record PagamentoResponse(Long pedidoId,BigDecimal valor,String status,String transacaoId,String detalhe) {}
