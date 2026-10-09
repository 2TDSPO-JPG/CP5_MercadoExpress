package br.com.mercadoexpress.repository;
import br.com.mercadoexpress.entity.Pagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface PagamentoRepository extends JpaRepository<Pagamento,Long> { List<Pagamento> findAllByStatus(String status); Optional<Pagamento> findByPedidoId(Long pedidoId); }
