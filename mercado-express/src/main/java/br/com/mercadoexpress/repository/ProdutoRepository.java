package br.com.mercadoexpress.repository;
import br.com.mercadoexpress.entity.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProdutoRepository extends JpaRepository<Produto,Long> {}
