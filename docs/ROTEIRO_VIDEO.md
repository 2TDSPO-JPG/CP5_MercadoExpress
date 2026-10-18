# Roteiro do vídeo (5 a 7 minutos)

Antes de gravar: `docker compose up -d`, suba os dois serviços, configure o e-mail, abra em abas o RabbitMQ (15672), a loja, o dashboard, o Postman e a caixa de e-mail.

1. **0:00–0:40 — Apresentação.** Integrantes, objetivo e o desenho da arquitetura (README, seção 1).
2. **0:40–1:40 — Código.**
   - `RabbitConfig`: exchange, 3 filas e bindings.
   - `CompraService`: produtor da mensagem.
   - `PagamentoConsumer`: chama o Banco Tranquilo.
   - `ResultadoPagamentoConsumer` e `EmailConsumer`.
   - `PagamentoController` do Banco Tranquilo.
3. **1:40–2:20 — RabbitMQ Dashboard.** Exchange `mercado.pagamentos.exchange`, bindings, as 3 filas com 1 consumidor cada.
4. **2:20–3:20 — Compra pela loja.** Adicione produtos ao carrinho e finalize com o seu e-mail. Mostre os passos ficando verdes e, no RabbitMQ, o pico em *Message rates*.
5. **3:20–3:50 — E-mail.** Mostre o e-mail recebido e o registro em "Controle de e-mails" no dashboard (tabela `emails_enviados`).
6. **3:50–4:50 — Postman.** Rode a collection no **Collection Runner** (testes passando): compra aprovada, consulta, recusada, estoque insuficiente e dashboard.
7. **4:50–5:30 — Teste interno no RabbitMQ.** Em Queues → `email.confirmacao` → **Publish message**, com `content_type=application/json` e o JSON do README (seção 8). Mostre o e-mail chegando.
8. **5:30–6:10 — Cenário de falha.** Pare o Banco Tranquilo e faça uma compra: o pedido fica `ERRO_PAGAMENTO`. Suba o banco e clique em **Reprocessar** no dashboard.
9. **6:10–6:50 — Dashboard da gerência.** Cards, gráficos de vendas e lucro, estoque baixo em vermelho e lista de compras. Encerramento.

Não mostre senhas, a senha de app do Gmail nem o conteúdo de arquivos `.env` no vídeo.
