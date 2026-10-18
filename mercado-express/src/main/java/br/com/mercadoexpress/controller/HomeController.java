package br.com.mercadoexpress.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Páginas web: landing page, loja com carrinho e dashboard da gerência. */
@Controller
public class HomeController {

    @GetMapping({"/", "/index"})
    public String index() {
        return "index";
    }

    @GetMapping("/loja")
    public String loja() {
        return "loja";
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        return "dashboard";
    }
}
