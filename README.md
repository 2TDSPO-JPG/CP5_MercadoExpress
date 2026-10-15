# CP5 — Parte 2 | Mercado Express + Banco Tranquilo

Projeto acadêmico de integração assíncrona com Java, Spring Boot, RabbitMQ, PostgreSQL, envio de e-mail e dashboard gerencial.


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


## Tecnologias

Java 21 · Spring Boot 3.4.5 · Spring Web · Spring Data JPA · Spring AMQP · PostgreSQL · RabbitMQ · Spring Mail · Actuator · Maven.


![alt text](image.png)


# Configuração de e-mail com Gmail SMTP

A aplicação Mercado Express utiliza o servidor SMTP do Gmail para realizar o envio de e-mails. As credenciais são configuradas por meio de variáveis de ambiente, evitando armazenar dados sensíveis diretamente no código-fonte.

### 1. Configurações SMTP

- **Servidor:** `smtp.gmail.com`
- **Porta:** `587`
- **Autenticação SMTP:** habilitada
- **Segurança:** STARTTLS

### 2. Variáveis de ambiente

Configure as seguintes variáveis no ambiente de execução da aplicação:

```env
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=seuemail@gmail.com
MAIL_PASSWORD=sua_senha_de_app
MAIL_SMTP_AUTH=true
MAIL_STARTTLS=true
MAIL_FROM=seuemail@gmail.com
```

Substitua `seuemail@gmail.com` pelo endereço utilizado e `sua_senha_de_app` pela senha de app gerada pelo Google.

![alt text](image-1.png)

**Importante:** utilize uma senha de app do Google, e não a senha normal da conta. A criação exige que a verificação em duas etapas esteja ativada.

Gere sua senha em: https://myaccount.google.com/apppasswords

### 3. Configuração no Spring Boot

No arquivo `application.yml`, configure o SMTP da seguinte forma:

```yaml
spring:
  mail:
    host: ${MAIL_HOST:smtp.gmail.com}
    port: ${MAIL_PORT:587}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
    properties:
      mail:
        smtp:
          auth: ${MAIL_SMTP_AUTH:true}
          starttls:
            enable: ${MAIL_STARTTLS:true}
            required: true

app:
  mail:
    from: ${MAIL_FROM:no-reply@mercadoexpress.local}
```

A propriedade `app.mail.from` define o endereço remetente utilizado pela aplicação. Para enviar e-mails reais, configure `MAIL_FROM` com um endereço autorizado.

### 4. Execução local

No IntelliJ IDEA, acesse **Run → Edit Configurations → Environment variables** e cadastre cada variável individualmente.

Alternativamente, no PowerShell, defina as variáveis antes de iniciar a aplicação:

```powershell
$env:MAIL_HOST = "smtp.gmail.com"
$env:MAIL_PORT = "587"
$env:MAIL_USERNAME = "seuemail@gmail.com"
$env:MAIL_PASSWORD = "sua_senha_de_app"
$env:MAIL_SMTP_AUTH = "true"
$env:MAIL_STARTTLS = "true"
$env:MAIL_FROM = "seuemail@gmail.com"

mvn spring-boot:run
```

Não coloque todas as variáveis em um único campo ou no valor de `MAIL_HOST`. Cada variável deve possuir seu próprio nome e valor.

### 5. Solução de problemas

- **Senha ausente:** verifique se `MAIL_PASSWORD` está definida no mesmo ambiente em que a aplicação é executada.
- **Host inválido



## Links Uteis

- Youtube: 
- GitHub: [CP5_MercadoExpress](https://github.com/2TDSPO-JPG/CP5_MercadoExpress.git)