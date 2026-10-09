# CP5 — Parte 2 | Mercado Express + Banco Tranquilo

Projeto acadêmico de integração assíncrona com Java, Spring Boot, RabbitMQ, PostgreSQL, envio de e-mail e dashboard gerencial.

> **Importante sobre o Banco Tranquilo:** o enunciado disponibilizado não inclui URL nem documentação de uma API bancária real. Por isso, `banco-tranquilo-api` é uma API simulada para demonstrar o contrato de pagamento. Se o professor disponibilizar uma API oficial, substitua a URL e adapte o DTO em `mercado-express`.

## Arquitetura

- `mercado-express`: API de produtos/compras, persistência de pedidos/pagamentos/e-mails, produtor e consumidores RabbitMQ, dashboard REST.
- `banco-tranquilo-api`: serviço independente que simula aprovação/recusa de pagamentos.
- `docker-compose.yml`: RabbitMQ Management e PostgreSQL para execução local.

Fluxo: cliente → `POST /api/compras` → pedido PENDENTE → exchange `mercado.pagamentos.exchange` / fila `pagamento.solicitado` → consumidor chama Banco Tranquilo → resultado publicado na fila `pagamento.resultado` → resultado persistido → fila de confirmação de e-mail → envio e registro de e-mail.

## Requisitos

- JDK 21
- Maven 3.9+ (ou Maven Wrapper gerado pela IDE)
- Docker Desktop com Docker Compose
- Postman ou Insomnia
- Para e-mail real: credenciais SMTP fornecidas por um provedor. Sem SMTP, o pedido e a tentativa ficam registrados e o log da aplicação explica a falha.

## 1. Subir infraestrutura

Na raiz do projeto:

```bash
docker compose up -d
```

RabbitMQ Management: http://localhost:15672 (usuário `mercado`, senha `mercado_dev_123`)

PostgreSQL: `localhost:5432`, banco `mercado_express`, usuário `mercado`, senha `mercado_dev_123`.

Estas são credenciais **somente locais**. Troque-as antes de qualquer deploy e nunca suba segredos ao GitHub.

## 2. Iniciar os serviços

Abra dois terminais na raiz.

Terminal 1:

```bash
cd banco-tranquilo-api
mvn spring-boot:run
```

Terminal 2:

```bash
cd mercado-express
mvn spring-boot:run
```

Banco Tranquilo: http://localhost:8081
Mercado Express: http://localhost:8082
Dashboard JSON: http://localhost:8082/api/dashboard
Health: http://localhost:8082/actuator/health

## 3. Cadastrar produto

```http
POST http://localhost:8082/api/produtos
Content-Type: application/json
```

```json
{
  "nome": "Café 500g",
  "descricao": "Café torrado e moído",
  "precoCusto": 12.50,
  "precoVenda": 19.90,
  "estoque": 30
}
```

Guarde o `id` retornado para usar na compra. Cadastre pelo menos dois produtos para demonstrar estoque e lucro.

## 4. Enviar compra pelo Postman

```http
POST http://localhost:8082/api/compras
Content-Type: application/json
```

```json
{
  "clienteNome": "Cliente Demonstração",
  "clienteEmail": "seu-email-de-teste@exemplo.com",
  "metodoPagamento": "PIX",
  "itens": [
    { "produtoId": 1, "quantidade": 2 }
  ]
}
```

A API responde `202 Accepted` com `pedidoId` e status `PENDENTE`. O processamento é assíncrono. Consulte o status em `GET http://localhost:8082/api/compras/{pedidoId}`.

### Simular aprovação ou recusa

Por padrão, a API simulada aprova valores positivos. Para demonstrar recusas, envie opcionalmente `resultadoSimulado` com `RECUSADO` no corpo da compra. Esse campo é **apenas para a demonstração acadêmica** e não deve existir em uma integração bancária real.

```json
{
  "clienteNome": "Cliente Demonstração",
  "clienteEmail": "seu-email-de-teste@exemplo.com",
  "metodoPagamento": "PIX",
  "resultadoSimulado": "RECUSADO",
  "itens": [{ "produtoId": 1, "quantidade": 1 }]
}
```

## 5. Endpoints

| Método | Endpoint | Finalidade |
|---|---|---|
| GET | `/api/produtos` | Lista produtos |
| POST | `/api/produtos` | Cadastra produto |
| GET | `/api/produtos/{id}` | Consulta produto |
| PUT | `/api/produtos/{id}` | Atualiza produto |
| DELETE | `/api/produtos/{id}` | Exclui produto |
| POST | `/api/compras` | Registra compra e publica mensagem |
| GET | `/api/compras/{id}` | Consulta pedido/status |
| GET | `/api/dashboard` | Indicadores gerenciais |
| GET | `/api/emails` | Histórico de e-mails |
| GET | `/actuator/health` | Health check |
| POST | `http://localhost:8081/api/pagamentos` | Endpoint do Banco Tranquilo simulado |

## 6. Dashboard RabbitMQ

Acesse http://localhost:15672. Abra **Exchanges** e procure `mercado.pagamentos.exchange`; em **Queues**, verifique `pagamento.solicitado`, `pagamento.resultado` e `email.confirmacao`. Em **Connections/Channels**, verifique consumidores conectados. Gere compras no Postman e atualize a tela. A aplicação declara exchanges, filas e bindings automaticamente ao iniciar.

## 7. E-mail

Configure variáveis de ambiente antes de iniciar `mercado-express`:

- `MAIL_HOST`
- `MAIL_PORT` (normalmente 587)
- `MAIL_USERNAME`
- `MAIL_PASSWORD`
- `MAIL_FROM`

Sem essas configurações, o registro do e-mail permanece no banco com status `FALHOU`; o serviço não afirma que enviou um e-mail que não foi entregue. Em ambiente de testes, use uma caixa SMTP própria e não coloque senhas no repositório.

## 8. Dashboard

`GET /api/dashboard` retorna totais calculados a partir do PostgreSQL: pedidos confirmados, faturamento aprovado, lucro bruto estimado (preço de venda menos custo cadastrado), produtos com estoque baixo e pagamentos por status. Lucro bruto não considera impostos, frete ou despesas não cadastradas.

## 9. Testes sugeridos

1. Cadastrar produto com estoque.
2. Fazer compra válida e verificar `PENDENTE` → `CONFIRMADO`.
3. Verificar mensagem nas filas do RabbitMQ.
4. Confirmar atualização de estoque e pagamento no PostgreSQL.
5. Configurar SMTP e verificar recebimento e registro em `emails_enviados`.
6. Fazer compra com `resultadoSimulado: RECUSADO` e conferir que não há e-mail de confirmação.
7. Tentar comprar quantidade acima do estoque.
8. Consultar dashboard antes e depois das compras.
9. Desligar temporariamente o Banco Tranquilo e documentar o tratamento de erro.

## 10. Deploy

A infraestrutura de produção precisa oferecer RabbitMQ acessível com TLS, PostgreSQL, SMTP e dois serviços Java. Configure as variáveis de ambiente por meio do painel da hospedagem; não versionar senhas. O `docker-compose.yml` fornecido é para desenvolvimento local, não uma configuração de produção pronta. Após publicar, atualize as URLs neste README e valide o fluxo ponta a ponta.

## 11. Entrega

- Adicione nomes/RMs do grupo no `ENTREGA.txt`.
- Atualize o link do repositório e IDE utilizada.
- Tire prints reais da aplicação, Postman, PostgreSQL e RabbitMQ.
- Grave vídeo de 5–7 minutos demonstrando código, compra, mensageria, resposta de pagamento, e-mail e dashboard.
- Gere ZIP sem `.git`, diretórios `target`, credenciais ou dados pessoais.

## Tecnologias

Java 21 · Spring Boot 3.4.5 · Spring Web · Spring Data JPA · Spring AMQP · PostgreSQL · RabbitMQ · Spring Mail · Actuator · Maven.
