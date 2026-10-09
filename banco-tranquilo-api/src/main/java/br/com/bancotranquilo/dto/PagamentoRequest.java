package br.com.bancotranquilo.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record PagamentoRequest(@NotNull @Positive Long pedidoId,@NotNull @DecimalMin("0.01") BigDecimal valor,@NotBlank String metodoPagamento,String clienteNome,String clienteEmail,String resultadoSimulado) {}
