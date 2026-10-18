package br.com.mercadoexpress.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Valores usados em todas as páginas (ex.: link do painel do RabbitMQ, que muda no deploy). */
@ControllerAdvice(annotations = Controller.class)
public class ViewModelAdvice {

    private final String rabbitUrl;

    public ViewModelAdvice(@Value("${app.rabbitmq-dashboard-url:http://localhost:15672}") String rabbitUrl) {
        this.rabbitUrl = rabbitUrl;
    }

    @ModelAttribute("rabbitUrl")
    public String rabbitUrl() {
        return rabbitUrl;
    }
}
