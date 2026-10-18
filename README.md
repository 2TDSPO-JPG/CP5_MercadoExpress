# CP5 — Parte 2 | Mercado Express + Banco Tranquilo

Microsserviços em **Java 21 / Spring Boot 3.4.5** que integram o **Mercado Express** à API de pagamentos do **Banco Tranquilo** usando **RabbitMQ** como broker. Compras confirmadas geram um **e-mail de confirmação de pagamento**, registrado no **PostgreSQL**, e a gerência acompanha tudo em um **dashboard com gráficos** de vendas, lucro e estoque.

- **Disciplina:** Java Advanced, prof. Dr. Marcel Stefan Wagner (FIAP, TDS)
- **IDE:** IntelliJ IDEA
- **Vídeo de demonstração:** _adicionar o link do YouTube (não listado)_
- **Repositório:** [github.com/2TDSPO-JPG/CP5_MercadoExpress](https://github.com/2TDSPO-JPG/CP5_MercadoExpress)

---

## Sumário
1. [Arquitetura](#1-arquitetura)
2. [RabbitMQ: exchange, filas e routing keys](#2-rabbitmq-exchange-filas-e-routing-keys)
3. [Tecnologias e dependências](#3-tecnologias-e-dependências)
4. [Como executar](#4-como-executar)
5. [Telas](#5-telas)
6. [Endpoints](#6-endpoints)
7. [Exemplos de requisição (Postman/Insomnia)](#7-exemplos-de-requisição-postmaninsomnia)
8. [Testes pelo RabbitMQ Management](#8-testes-pelo-rabbitmq-management)
9. [Envio de e-mail (Gmail SMTP)](#9-envio-de-e-mail-gmail-smtp)
10. [Banco de dados](#10-banco-de-dados)
11. [Tratamento de erros](#11-tratamento-de-erros)
12. [Estrutura do projeto](#12-estrutura-do-projeto)

---

## 1. Arquitetura

```
 ┌──────────────┐  POST /api/compras   ┌──────────────────────────────────────────────┐
 │ Loja (web) / │ ───────────────────▶ │ MERCADO EXPRESS (:8082)                      │
 │   Postman    │ ◀─── 202 Accepted ── │  grava Pedido/Itens/Pagamento (PENDENTE)     │
 └──────────────┘                      │  reserva estoque e publica a mensagem        │
                                       └──────────────┬───────────────────────────────┘
                                                      │ routing key: pagamento.solicitado
                                                      ▼
                             ┌──────────── RabbitMQ: mercado.pagamentos.exchange (topic) ─────────────┐
                             │   fila pagamento.solicitado   fila pagamento.resultado   fila email.confirmacao │
                             └───────┬───────────────────────────▲──────────┬────────────────────▲──────┬──┘
                                     │                           │          │                    │      │
                                     ▼                           │          ▼                    │      ▼
                            PagamentoConsumer ── HTTP POST ──▶ BANCO TRANQUILO (:8081)    Resultado     EmailConsumer
                            (chama a API do banco)  /api/pagamentos  APROVADO/RECUSADO  PagamentoConsumer   ├─ envia e-mail (SMTP)
                                     └──────── publica resposta ─┘                     ├─ CONFIRMADO/CANCELADO └─ grava emails_enviados
                                                                                        └─ publica e-mail se aprovado
```

**Fluxo de uma compra**
1. O cliente finaliza o carrinho na **Loja** (`/loja`) ou envia `POST /api/compras` pelo Postman.
2. O Mercado Express valida o estoque, **reserva** as unidades e grava `pedidos`, `itens_pedido` e `pagamentos` com status **PENDENTE**. Em seguida publica a mensagem na fila `pagamento.solicitado` e responde **202 Accepted**.
3. O `PagamentoConsumer` consome a fila e chama a **API do Banco Tranquilo** (`POST :8081/api/pagamentos`).
4. A resposta (APROVADO/RECUSADO e `transacaoId`) é publicada na fila `pagamento.resultado`.
5. O `ResultadoPagamentoConsumer` atualiza o pagamento e o pedido:
   - **APROVADO** → pedido **CONFIRMADO** e mensagem publicada em `email.confirmacao`.
   - **RECUSADO** → pedido **CANCELADO** e o estoque reservado é devolvido.
6. O `EmailConsumer` envia o **e-mail de confirmação de pagamento** e grava o resultado (ENVIADO/FALHOU) na tabela `emails_enviados`.
7. O **Dashboard da gerência** (`/dashboard`) mostra compras, vendas, lucro e estoque em tempo real.

## 2. RabbitMQ: exchange, filas e routing keys

Tudo é declarado automaticamente na inicialização, em `RabbitConfig.java`.

| Tipo | Nome | Routing key | Produtor | Consumidor |
|---|---|---|---|---|
| Exchange (topic, durável) | `mercado.pagamentos.exchange` | — | — | — |
| Fila durável | `pagamento.solicitado` | `pagamento.solicitado` | `CompraService` | `PagamentoConsumer` |
| Fila durável | `pagamento.resultado` | `pagamento.resultado` | `PagamentoConsumer` | `ResultadoPagamentoConsumer` |
| Fila durável | `email.confirmacao` | `email.confirmacao` | `ResultadoPagamentoConsumer` | `EmailConsumer` |

As mensagens trafegam em **JSON** (`Jackson2JsonMessageConverter`). Os consumidores têm **retry** (3 tentativas) e são **idempotentes**: uma mensagem repetida para um pagamento que já saiu de PENDENTE é ignorada.

## 3. Tecnologias e dependências

Projetos criados pelo **Spring Initializr** (Maven, Java 21, Spring Boot 3.4.5).

| Serviço | Dependências |
|---|---|
| `mercado-express` | Spring Web, Spring Data JPA, **Spring for RabbitMQ (AMQP)**, **Java Mail Sender**, Validation, Thymeleaf, Actuator, PostgreSQL Driver, Lombok, springdoc-openapi (Swagger) |
| `banco-tranquilo-api` | Spring Web, Validation, Actuator |

Dependências selecionadas no Spring Initializr:

![Spring Initializr - dependências](image.png)

Infraestrutura local pelo `docker-compose.yml`: **RabbitMQ 4 (com Management)** e **PostgreSQL 16**. Gráficos com **Chart.js** (CDN).

## 4. Como executar

**Pré-requisitos:** JDK 21, Maven 3.9+ (ou o Maven da IDE), Docker Desktop e Postman ou Insomnia.

### 4.1 Subir RabbitMQ e PostgreSQL
Na raiz do projeto:
```bash
docker compose up -d
```

| Serviço | Endereço | Usuário / senha |
|---|---|---|
| RabbitMQ Management | http://localhost:15672 | `mercado` / `mercado_dev_123` |
| RabbitMQ (AMQP) | `localhost:5672` | `mercado` / `mercado_dev_123` |
| PostgreSQL | `localhost:5433`, banco `mercado_express` | `mercado` / `mercado_dev_123` |

> O PostgreSQL do container é exposto na porta **5433** do computador para não conflitar com um PostgreSQL já instalado na 5432. Essas credenciais são **apenas de desenvolvimento local**.

### 4.2 Configurar o e-mail
Configure as variáveis `MAIL_*` (veja a [seção 9](#9-envio-de-e-mail-gmail-smtp)). Sem elas a aplicação funciona normalmente, mas o e-mail fica registrado com status `FALHOU`.

### 4.3 Iniciar os dois serviços
Pelo IntelliJ, rode `BancoTranquiloApplication` e depois `MercadoExpressApplication`. Ou pelo terminal:
```bash
# Terminal 1
cd banco-tranquilo-api
mvn spring-boot:run

# Terminal 2
cd mercado-express
mvn spring-boot:run
```

| Página / serviço | URL |
|---|---|
| Página inicial | http://localhost:8082 |
| **Loja com carrinho** | http://localhost:8082/loja |
| **Dashboard da gerência** | http://localhost:8082/dashboard |
| Swagger da API | http://localhost:8082/swagger-ui/index.html |
| Banco Tranquilo (API) | http://localhost:8081/api/pagamentos/health |
| Health check | http://localhost:8082/actuator/health |

Na primeira execução, com a tabela vazia, são cadastrados **6 produtos de exemplo** (`DataLoader`). Para desligar, use `SEED_PRODUTOS=false`.

### 4.4 Variáveis de ambiente (todas opcionais, com valores padrão)

| Variável | Padrão |
|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5433/mercado_express` / `mercado` / `mercado_dev_123` |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` / `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | `localhost` / `5672` / `mercado` / `mercado_dev_123` |
| `BANCO_TRANQUILO_URL` | `http://localhost:8081` |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | Gmail, porta 587, sem credenciais |

## 5. Telas

> Os prints ficam na pasta `docs/img/`.

### 5.1 Loja: carrinho e finalização da compra (`/loja`)
O cliente adiciona produtos ao carrinho, informa nome, e-mail e forma de pagamento e finaliza a compra. A tela acompanha o processamento assíncrono em tempo real: pedido registrado → mensagem publicada → resposta do Banco Tranquilo → e-mail enviado. A opção **"Simular recusa"** demonstra o fluxo de pagamento recusado.

![Loja com carrinho](docs/img/loja.png)

### 5.2 Dashboard da gerência (`/dashboard`)
Atualiza sozinho a cada 5 segundos, com dados lidos do PostgreSQL:
- **Indicadores:** faturamento aprovado, ticket médio, lucro bruto e margem, pedidos confirmados, unidades vendidas, estoque (unidades e valor a custo) e e-mails enviados/com falha.
- **Gráficos:** vendas e lucro por dia; faturamento e lucro por produto; estoque por produto (estoque baixo em vermelho); pedidos por status.
- **Compras realizadas:** cliente, itens, total, status do pedido, resposta do Banco Tranquilo (com `transacaoId`) e status do e-mail. Pedidos com erro de comunicação podem ser **reprocessados**.
- **Produtos e estoque:** cadastrar, ajustar estoque e excluir produtos (CRUD).
- **Controle de e-mails:** conteúdo da tabela `emails_enviados`.

![Dashboard - indicadores e gráficos](docs/img/dashboard-graficos.png)
![Dashboard - compras, estoque e e-mails](docs/img/dashboard-tabelas.png)

### 5.3 RabbitMQ Management
![Exchange e bindings](docs/img/rabbitmq-exchange.png)
![Filas e consumidores](docs/img/rabbitmq-filas.png)

### 5.4 Postman
![Collection no Postman](docs/img/postman.png)

### 5.5 E-mail de confirmação recebido
![E-mail de confirmação](docs/img/email.png)

## 6. Endpoints

### Mercado Express (`:8082`)
| Método | Endpoint | Finalidade |
|---|---|---|
| GET | `/api/produtos` | Lista produtos |
| POST | `/api/produtos` | Cadastra produto |
| GET | `/api/produtos/{id}` | Consulta produto |
| PUT | `/api/produtos/{id}` | Atualiza produto |
| DELETE | `/api/produtos/{id}` | Exclui produto |
| POST | `/api/compras` | Registra compra e publica em `pagamento.solicitado` (202) |
| GET | `/api/compras/{id}` | Consulta pedido e status |
| POST | `/api/compras/{id}/reprocessar` | Reenvia para a fila um pedido com pagamento em ERRO |
| GET | `/api/dashboard` | Indicadores gerais |
| GET | `/api/dashboard/vendas-por-dia?dias=14` | Faturamento, lucro e pedidos por dia |
| GET | `/api/dashboard/produtos` | Vendas, lucro e estoque por produto |
| GET | `/api/dashboard/compras?limite=50` | Compras realizadas com pagamento e e-mail |
| GET | `/api/emails` | Histórico de e-mails (`emails_enviados`) |

### Banco Tranquilo (`:8081`)
| Método | Endpoint | Finalidade |
|---|---|---|
| POST | `/api/pagamentos` | Autoriza ou recusa um pagamento e devolve `transacaoId` |
| GET | `/api/pagamentos/health` | Status do serviço |

## 7. Exemplos de requisição (Postman/Insomnia)

Importe `postman/CP5-Mercado-Express.postman_collection.json`. A collection tem variáveis (`baseUrl`, `bancoUrl`, `emailCliente`) e **scripts de teste automáticos**: valida os códigos HTTP e guarda o `pedidoId` criado. Altere `emailCliente` para o seu e-mail e rode a collection inteira pelo **Collection Runner**.

**Compra (JSON):**
```http
POST http://localhost:8082/api/compras
Content-Type: application/json
```
```json
{
  "clienteNome": "Cliente Demonstração",
  "clienteEmail": "seu-email@exemplo.com",
  "metodoPagamento": "PIX",
  "itens": [
    { "produtoId": 1, "quantidade": 2 },
    { "produtoId": 2, "quantidade": 1 }
  ]
}
```
**Resposta (202 Accepted):**
```json
{ "pedidoId": 1, "status": "PENDENTE", "total": 45.79, "mensagem": "Compra recebida para processamento assíncrono." }
```
Em cerca de 1 segundo, `GET /api/compras/1` passa a retornar `"status": "CONFIRMADO"`.

**Simular pagamento recusado:** acrescente `"resultadoSimulado": "RECUSADO"` ao JSON. Esse campo existe **apenas para a demonstração acadêmica**. O pedido fica `CANCELADO`, o estoque volta e nenhum e-mail é enviado.

**Cadastro de produto:**
```json
{ "nome": "Café 500g", "descricao": "Café torrado e moído", "precoCusto": 12.50, "precoVenda": 19.90, "estoque": 30 }
```

Métodos de pagamento aceitos pela loja: `PIX`, `CARTAO_CREDITO`, `CARTAO_DEBITO`, `BOLETO`. O cabeçalho opcional `Idempotency-Key` evita pedidos duplicados quando a mesma requisição é reenviada.

## 8. Testes pelo RabbitMQ Management

Acesse http://localhost:15672 (`mercado` / `mercado_dev_123`).

1. **Exchanges → `mercado.pagamentos.exchange`:** mostra os 3 bindings e a taxa de mensagens (*message rates*) durante as compras.
2. **Queues:** as filas `pagamento.solicitado`, `pagamento.resultado` e `email.confirmacao`, cada uma com **1 consumidor**. Faça compras pela loja ou pelo Postman e acompanhe os gráficos *Message rates*.
3. **Publicar uma mensagem manualmente (teste interno):** em **Queues → `email.confirmacao` → Publish message**:
   - **Properties:** `content_type` = `application/json`
   - **Payload:**
     ```json
     {"pedidoId": 1, "clienteNome": "Teste RabbitMQ", "clienteEmail": "seu-email@exemplo.com", "statusPagamento": "APROVADO", "valor": "45.79"}
     ```
   Clique em **Publish message**. O `EmailConsumer` consome a mensagem, envia o e-mail e grava em `emails_enviados`; o registro aparece no dashboard.
4. **Get messages:** para inspecionar uma mensagem parada na fila, pare o Mercado Express (os consumidores somem), publique uma mensagem em `email.confirmacao` como no passo 3 e use **Get messages** para ver o JSON. Ao subir a aplicação de novo, a mensagem é consumida.
5. **Banco fora do ar:** pare o `banco-tranquilo-api` e faça uma compra. O pagamento fica `ERRO`, o pedido `ERRO_PAGAMENTO` e o estoque é devolvido. Suba o banco novamente e clique em **Reprocessar** no dashboard (ou `POST /api/compras/{id}/reprocessar`).

## 9. Envio de e-mail (Gmail SMTP)

O e-mail de confirmação é enviado pelo SMTP do Gmail: `smtp.gmail.com`, porta `587`, autenticação ligada e STARTTLS. Cada tentativa é registrada na tabela `emails_enviados` com status `ENVIADO` ou `FALHOU` e o detalhe do servidor.

**Variáveis de ambiente:**
```env
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=seuemail@gmail.com
MAIL_PASSWORD=sua_senha_de_app
MAIL_SMTP_AUTH=true
MAIL_STARTTLS=true
MAIL_FROM=seuemail@gmail.com
```

![Senha de app do Google](image-1.png)

- Use uma **senha de app** do Google, não a senha normal da conta. Ela exige a verificação em duas etapas ativada: https://myaccount.google.com/apppasswords
- **Nunca versione a senha.** O `.gitignore` ignora arquivos `*.env`.

**No IntelliJ:** *Run → Edit Configurations → Environment variables*, uma variável por linha. Não coloque todas no campo `MAIL_HOST`.

**No PowerShell:**
```powershell
$env:MAIL_USERNAME = "seuemail@gmail.com"
$env:MAIL_PASSWORD = "sua_senha_de_app"
$env:MAIL_FROM     = "seuemail@gmail.com"
mvn spring-boot:run
```

**Solução de problemas**
- `Authentication failed` / `535-5.7.8`: senha de app incorreta ou verificação em duas etapas desativada.
- `Senha ausente` / e-mail `FALHOU` sem detalhe: a variável `MAIL_PASSWORD` não está definida no mesmo ambiente em que a aplicação roda.
- `UnknownHostException`: valor errado em `MAIL_HOST` (deve ser só `smtp.gmail.com`).
- E-mail não chegou: confira a caixa de spam e o status em `/api/emails`.

## 10. Banco de dados

**PostgreSQL** (o mesmo da Parte 1). As tabelas são criadas automaticamente pelo Hibernate (`ddl-auto: update`).

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/mercado_express
    username: mercado
    password: mercado_dev_123
```

| Tabela | Conteúdo |
|---|---|
| `produtos` | nome, descrição, preço de custo, preço de venda, estoque |
| `pedidos` | cliente, e-mail, forma de pagamento, total, status, data, chave de idempotência |
| `itens_pedido` | produto, quantidade, preço e custo unitários no momento da compra |
| `pagamentos` | status (PENDENTE/APROVADO/RECUSADO/ERRO), `transacaoId` do Banco Tranquilo, detalhe |
| `emails_enviados` | controle de envio: pedido, destinatário, assunto, status, detalhe, data |

As consultas de conferência estão em `docs/SQL_CONSULTAS.sql`.

## 11. Tratamento de erros

| Situação | Comportamento |
|---|---|
| Estoque insuficiente / produto inexistente | `400` com mensagem; nada é gravado |
| JSON inválido ou campos inválidos (ex.: e-mail) | `400` com os campos que falharam |
| RabbitMQ fora do ar | `503`; a transação é desfeita e a compra não fica "presa" |
| Banco Tranquilo fora do ar | pagamento `ERRO`, pedido `ERRO_PAGAMENTO`, estoque devolvido; permite reprocessar |
| Pagamento recusado | pedido `CANCELADO`, estoque devolvido, sem e-mail |
| Falha no SMTP | e-mail gravado com status `FALHOU` e o motivo |
| Mensagem duplicada | consumidores idempotentes ignoram |

## 12. Estrutura do projeto

```
CP5_Parte2_MercadoExpress/
├── docker-compose.yml            # RabbitMQ + PostgreSQL
├── ENTREGA.txt                   # integrantes, RM, GitHub, IDE, vídeo
├── postman/                      # collection com testes
├── docs/                         # roteiro do vídeo, consultas SQL, prints
├── banco-tranquilo-api/          # API do Banco Tranquilo (:8081)
│   └── controller/PagamentoController.java
└── mercado-express/              # Mercado Express (:8082)
    ├── config/      RabbitConfig (exchange/filas), DataLoader
    ├── controller/  Produto, Compra, Dashboard, Home (páginas), ApiExceptionHandler
    ├── service/     CompraService (produtor), PagamentoConsumer, ResultadoPagamentoConsumer, EmailConsumer
    ├── entity/      Produto, Pedido, ItemPedido, Pagamento, EmailEnviado
    ├── repository/  Spring Data JPA
    └── resources/
        ├── templates/  index.html, loja.html, dashboard.html
        └── static/     css/app.css, js/app.js
```
