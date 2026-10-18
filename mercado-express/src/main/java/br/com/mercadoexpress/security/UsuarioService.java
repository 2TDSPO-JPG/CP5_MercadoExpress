package br.com.mercadoexpress.security;

import br.com.mercadoexpress.entity.Usuario;
import br.com.mercadoexpress.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/** Cadastro de usuários com validação e hash BCrypt da senha. */
@Service
public class UsuarioService {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;

    public UsuarioService(UsuarioRepository usuarios, PasswordEncoder encoder) {
        this.usuarios = usuarios;
        this.encoder = encoder;
    }

    @Transactional
    public Usuario cadastrarCliente(String nome, String email, String senha, String confirmacao) {
        String n = nome == null ? "" : nome.trim();
        String e = email == null ? "" : email.trim().toLowerCase();
        if (n.length() < 2 || n.length() > 120) throw new IllegalArgumentException("Informe seu nome (2 a 120 caracteres).");
        if (!EMAIL.matcher(e).matches() || e.length() > 160) throw new IllegalArgumentException("Informe um e-mail válido.");
        if (senha == null || senha.length() < 8 || senha.length() > 72)
            throw new IllegalArgumentException("A senha deve ter entre 8 e 72 caracteres.");
        if (!senha.matches(".*[A-Za-z].*") || !senha.matches(".*\\d.*"))
            throw new IllegalArgumentException("A senha deve conter letras e números.");
        if (!senha.equals(confirmacao)) throw new IllegalArgumentException("As senhas não conferem.");
        if (usuarios.existsByEmailIgnoreCase(e)) throw new IllegalArgumentException("Já existe uma conta com este e-mail.");
        return usuarios.save(new Usuario(n, e, encoder.encode(senha), Usuario.Perfil.CLIENTE));
    }

    /** Cria o usuário se ainda não existir (usado para os usuários iniciais). */
    @Transactional
    public void garantirUsuario(String nome, String email, String senha, Usuario.Perfil perfil) {
        if (email == null || email.isBlank() || senha == null || senha.isBlank()) return;
        if (usuarios.existsByEmailIgnoreCase(email.trim())) return;
        usuarios.save(new Usuario(nome, email.trim().toLowerCase(), encoder.encode(senha), perfil));
    }
}
