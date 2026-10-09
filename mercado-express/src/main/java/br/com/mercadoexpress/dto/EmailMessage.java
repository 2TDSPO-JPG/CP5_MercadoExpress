package br.com.mercadoexpress.dto;
public record EmailMessage(Long pedidoId,String clienteNome,String clienteEmail,String statusPagamento,String valor) {}
