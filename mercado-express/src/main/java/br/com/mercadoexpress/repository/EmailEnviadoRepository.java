package br.com.mercadoexpress.repository;

import br.com.mercadoexpress.entity.EmailEnviado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailEnviadoRepository extends JpaRepository<EmailEnviado, Long> {
    long countByStatus(String status);
    Optional<EmailEnviado> findFirstByPedidoIdOrderByIdDesc(Long pedidoId);
}
