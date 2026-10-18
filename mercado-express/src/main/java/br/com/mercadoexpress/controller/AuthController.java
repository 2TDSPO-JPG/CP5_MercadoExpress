package br.com.mercadoexpress.controller;

import br.com.mercadoexpress.repository.UsuarioRepository;
import br.com.mercadoexpress.security.UsuarioService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.Map;

/** Tela de login, cadastro de novos clientes (Sign Up) e dados do usuário logado. */
@Controller
public class AuthController {

    private final UsuarioService service;
    private final UsuarioRepository usuarios;

    public AuthController(UsuarioService service, UsuarioRepository usuarios) {
        this.service = service;
        this.usuarios = usuarios;
    }

    private static boolean logado(Authentication auth) {
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    }

    private static boolean admin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    @GetMapping("/login")
    public String login(Authentication auth) {
        if (logado(auth)) return admin(auth) ? "redirect:/dashboard" : "redirect:/loja";
        return "login";
    }

    @GetMapping("/cadastro")
    public String cadastro(Authentication auth) {
        if (logado(auth)) return "redirect:/loja";
        return "cadastro";
    }

    @PostMapping("/cadastro")
    public String cadastrar(@RequestParam(defaultValue = "") String nome,
                            @RequestParam(defaultValue = "") String email,
                            @RequestParam(defaultValue = "") String senha,
                            @RequestParam(defaultValue = "") String confirmacao,
                            Model model) {
        try {
            service.cadastrarCliente(nome, email, senha, confirmacao);
            return "redirect:/login?cadastrado";
        } catch (IllegalArgumentException e) {
            model.addAttribute("erro", e.getMessage());
            model.addAttribute("nome", nome);
            model.addAttribute("email", email);
            return "cadastro";
        }
    }

    /** Usado pelas páginas para mostrar o usuário logado e ajustar o menu. */
    @GetMapping("/api/me")
    @ResponseBody
    public Map<String, Object> me(Authentication auth) {
        Map<String, Object> r = new LinkedHashMap<>();
        if (!logado(auth)) {
            r.put("autenticado", false);
            return r;
        }
        r.put("autenticado", true);
        r.put("email", auth.getName());
        r.put("perfil", admin(auth) ? "ADMIN" : "CLIENTE");
        r.put("nome", usuarios.findByEmailIgnoreCase(auth.getName()).map(u -> u.getNome()).orElse(auth.getName()));
        return r;
    }
}
