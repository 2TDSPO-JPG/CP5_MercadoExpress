package br.com.mercadoexpress.controller;
import br.com.mercadoexpress.entity.Produto;
import br.com.mercadoexpress.service.ProdutoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/produtos")
public class ProdutoController {
 private final ProdutoService service; public ProdutoController(ProdutoService service){this.service=service;}
 @GetMapping public List<Produto> listar(){return service.listar();}
 @GetMapping("/{id}") public Produto buscar(@PathVariable Long id){return service.buscar(id);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public Produto criar(@Valid @RequestBody Produto p){return service.salvar(p);}
 @PutMapping("/{id}") public Produto atualizar(@PathVariable Long id,@Valid @RequestBody Produto p){return service.atualizar(id,p);}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void excluir(@PathVariable Long id){service.excluir(id);}
}
