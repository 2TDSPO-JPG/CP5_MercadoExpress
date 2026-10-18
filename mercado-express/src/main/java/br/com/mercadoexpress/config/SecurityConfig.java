package br.com.mercadoexpress.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Segurança do Mercado Express.
 *
 * Perfis:
 *  - ADMIN   (gerência): dashboard, cadastro/edição/exclusão de produtos, e-mails, reprocessamento, Swagger.
 *  - CLIENTE: loja, consulta de produtos, compra e acompanhamento dos PRÓPRIOS pedidos.
 *
 * Autenticação:
 *  - Navegador: tela de login própria (/login) com sessão e proteção CSRF.
 *  - Postman/Insomnia: HTTP Basic (e-mail + senha) — requisições com cabeçalho Authorization não usam
 *    cookie de sessão, por isso ficam fora da verificação de CSRF.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        RequestMatcher api = req -> req.getRequestURI().startsWith(req.getContextPath() + "/api/");
        RequestMatcher comAuthorizationHeader = req -> req.getHeader("Authorization") != null;

        http
            .authorizeHttpRequests(auth -> auth
                // Público
                .requestMatchers("/", "/index", "/login", "/cadastro", "/error",
                        "/css/**", "/js/**", "/favicon.ico", "/actuator/health", "/api/me").permitAll()
                // Somente gerência
                .requestMatchers("/dashboard", "/api/dashboard/**", "/api/emails/**",
                        "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/produtos/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/produtos/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/produtos/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/compras/*/reprocessar").hasRole("ADMIN")
                // Cliente ou gerência
                .requestMatchers("/loja").hasAnyRole("CLIENTE", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/produtos/**").hasAnyRole("CLIENTE", "ADMIN")
                .requestMatchers("/api/compras/**").hasAnyRole("CLIENTE", "ADMIN")
                // Qualquer outra rota exige login
                .anyRequest().authenticated())

            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("email")
                .passwordParameter("senha")
                .successHandler(redirecionarPorPerfil())
                .failureUrl("/login?erro")
                .permitAll())

            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?saiu")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID"))

            .httpBasic(Customizer.withDefaults())

            .csrf(csrf -> csrf.ignoringRequestMatchers(comAuthorizationHeader))

            // Na API, sem login devolve 401 (em vez de redirecionar para a tela de login)
            .exceptionHandling(ex -> ex
                .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), api))

            // Novo ID de sessão a cada login (proteção contra session fixation)
            .sessionManagement(s -> s.sessionFixation(f -> f.migrateSession()))

            .headers(h -> h
                .frameOptions(f -> f.deny())
                .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)));

        return http.build();
    }

    /** Após o login: gerência vai para o dashboard; cliente vai para a loja. */
    private AuthenticationSuccessHandler redirecionarPorPerfil() {
        return (request, response, authentication) -> {
            boolean admin = authentication.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
            response.sendRedirect(request.getContextPath() + (admin ? "/dashboard" : "/loja"));
        };
    }
}
