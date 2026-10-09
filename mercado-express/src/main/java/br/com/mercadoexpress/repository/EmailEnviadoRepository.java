package br.com.mercadoexpress.repository;
import br.com.mercadoexpress.entity.EmailEnviado;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EmailEnviadoRepository extends JpaRepository<EmailEnviado,Long> { long countByStatus(String status); }
