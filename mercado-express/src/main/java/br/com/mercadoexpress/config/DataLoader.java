package br.com.mercadoexpress.config;

import br.com.mercadoexpress.entity.Produto;
import br.com.mercadoexpress.entity.Usuario;
import br.com.mercadoexpress.repository.ProdutoRepository;
import br.com.mercadoexpress.security.UsuarioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Dados iniciais:
 *  - usuários de demonstração (gerência e cliente), criados apenas se ainda não existirem;
 *  - produtos de exemplo quando a tabela está vazia.
 * As credenciais vêm do application.yml / variáveis de ambiente.
 */
@Component
public class DataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);
    private final ProdutoRepository repo;
    private final UsuarioService usuarios;
    private final boolean seedProdutos;
    private final String adminEmail, adminSenha, clienteEmail, clienteSenha;

    public DataLoader(ProdutoRepository repo, UsuarioService usuarios,
                      @Value("${app.seed-produtos:true}") boolean seedProdutos,
                      @Value("${app.usuarios.admin-email:}") String adminEmail,
                      @Value("${app.usuarios.admin-senha:}") String adminSenha,
                      @Value("${app.usuarios.cliente-email:}") String clienteEmail,
                      @Value("${app.usuarios.cliente-senha:}") String clienteSenha) {
        this.repo = repo;
        this.usuarios = usuarios;
        this.seedProdutos = seedProdutos;
        this.adminEmail = adminEmail;
        this.adminSenha = adminSenha;
        this.clienteEmail = clienteEmail;
        this.clienteSenha = clienteSenha;
    }

    @Override
    public void run(String... args) {
        usuarios.garantirUsuario("Gerência Mercado Express", adminEmail, adminSenha, Usuario.Perfil.ADMIN);
        usuarios.garantirUsuario("Cliente Demonstração", clienteEmail, clienteSenha, Usuario.Perfil.CLIENTE);

        if (!seedProdutos || repo.count() > 0) return;
        salvar("Café 500g", "Café torrado e moído", "12.50", "19.90", 30);
        salvar("Leite integral 1L", "Caixa longa vida", "3.80", "5.99", 48);
        salvar("Pão de forma", "Pão de forma tradicional 500g", "5.20", "8.49", 20);
        salvar("Banana prata (kg)", "Fruta fresca", "3.00", "5.49", 25);
        salvar("Detergente 500ml", "Detergente neutro", "1.40", "2.79", 40);
        salvar("Meia esportiva", "Par de meias cano médio", "6.00", "12.90", 4);
        log.info("Produtos de exemplo cadastrados: {}", repo.count());
    }

    private void salvar(String nome, String desc, String custo, String venda, int estoque) {
        Produto p = new Produto();
        p.setNome(nome);
        p.setDescricao(desc);
        p.setPrecoCusto(new BigDecimal(custo));
        p.setPrecoVenda(new BigDecimal(venda));
        p.setEstoque(estoque);
        repo.save(p);
    }
}
