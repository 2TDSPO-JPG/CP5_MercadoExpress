package br.com.mercadoexpress.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
public record CompraRequest(@NotBlank String clienteNome,@Email @NotBlank String clienteEmail,@NotBlank String metodoPagamento,@Valid @NotEmpty List<ItemRequest> itens,String resultadoSimulado) {
 public record ItemRequest(@NotNull @Positive Long produtoId,@Positive int quantidade) {}
}
