package br.com.bancotranquilo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Protege a API de pagamentos do Banco Tranquilo: toda requisição a /api/pagamentos
 * precisa do cabeçalho X-API-KEY com a chave configurada. Sem a chave correta → 401.
 * O endpoint /api/pagamentos/health continua público para monitoramento.
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private final byte[] chave;

    public ApiKeyFilter(@Value("${app.api-key}") String chave) {
        this.chave = chave.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/pagamentos") || uri.equals("/api/pagamentos/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String enviada = request.getHeader("X-API-KEY");
        // Comparação em tempo constante para não vazar informação sobre a chave
        if (enviada == null || !MessageDigest.isEqual(chave, enviada.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"erro\":\"Chave de API ausente ou inválida (cabeçalho X-API-KEY).\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
