package br.com.mercadoexpress.repository;
import br.com.mercadoexpress.entity.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface PedidoRepository extends JpaRepository<Pedido,Long> { Optional<Pedido> findByIdempotencyKey(String key); long countByStatus(String status); }
