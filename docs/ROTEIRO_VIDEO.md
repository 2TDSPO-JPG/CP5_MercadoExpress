# Roteiro de vídeo — 5 a 7 minutos

1. **0:00–0:40 — Contexto:** apresente Mercado Express, Banco Tranquilo simulado, RabbitMQ e PostgreSQL.
2. **0:40–1:30 — Código:** mostre os dois projetos, DTOs, entidades, produtor e consumidores.
3. **1:30–2:30 — Compra:** no Postman, cadastre produto e envie uma compra; explique o `202 Accepted` e o status inicial `PENDENTE`.
4. **2:30–3:20 — Mensageria:** no RabbitMQ Management, mostre exchange, routing keys, filas e consumidores; envie uma segunda compra para observar o fluxo.
5. **3:20–4:10 — Pagamento:** mostre resposta da API simulada e o pedido confirmado/recusado.
6. **4:10–4:50 — E-mail:** mostre mensagem recebida em uma caixa de teste SMTP e o registro em `/api/emails`/PostgreSQL. Se SMTP não estiver configurado, explique honestamente o registro da falha; não alegue envio bem-sucedido.
7. **4:50–5:40 — Dashboard:** abra a página inicial e `/api/dashboard`; compare os números com os dados persistidos.
8. **5:40–6:30 — Cenários negativos:** mostre recusa simulada ou estoque insuficiente e conclua.

Grave uma execução real. Não inclua senhas, tokens, dados pessoais reais ou credenciais no vídeo.
