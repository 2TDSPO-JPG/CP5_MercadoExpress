package br.com.mercadoexpress.service;
import br.com.mercadoexpress.entity.Produto;
import br.com.mercadoexpress.repository.ProdutoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
@Service
public class ProdutoService {
 private final ProdutoRepository repo;
 public ProdutoService(ProdutoRepository repo){this.repo=repo;}
 public List<Produto> listar(){return repo.findAll();}
 public Produto buscar(Long id){return repo.findById(id).orElseThrow(()->new IllegalArgumentException("Produto não encontrado: "+id));}
 public Produto salvar(Produto p){p.setId(null);return repo.save(p);}
 public Produto atualizar(Long id,Produto novo){Produto p=buscar(id);p.setNome(novo.getNome());p.setDescricao(novo.getDescricao());p.setPrecoCusto(novo.getPrecoCusto());p.setPrecoVenda(novo.getPrecoVenda());p.setEstoque(novo.getEstoque());return repo.save(p);}
 @Transactional public void excluir(Long id){repo.delete(buscar(id));}
}
