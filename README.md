# PaymentCore API

API REST de pagamentos simulados, escrita em Java 21 com Spring Boot. Ela cadastra um cliente, abre uma conta, autentica com JWT e registra transferências, extrato e estorno.

O PaymentCore API é uma simulação de operações financeiras para estudo e portfólio. Ele não é uma instituição financeira, não movimenta dinheiro real e não se conecta a um arranjo de pagamento.

## Objetivo

Exercitar, em um projeto pequeno, o desenho de uma API de conta e transferência: regras de saldo, transação única, idempotência, concorrência e histórico. O esquema do banco é controlado pelo Flyway. A aplicação valida esse esquema em vez de criá-lo em tempo de execução.

## Tecnologias

- Java 21
- Spring Boot 4.0.10
- Spring Web MVC
- Spring Data JPA e Hibernate
- Bean Validation
- Spring Security com JWT (OAuth2 Resource Server)
- BCrypt
- Flyway
- PostgreSQL 16
- springdoc OpenAPI 3.2.1 e Swagger UI
- Spring Boot Actuator, no endpoint de saúde
- JUnit 5, Mockito, Spring Boot Test e Testcontainers
- Maven Wrapper
- Docker Compose
- GitHub Actions

## Arquitetura

A aplicação é um monólito em camadas. O controlador recebe o HTTP e devolve DTOs. O caso de uso fica na camada de aplicação. Entidades, repositórios e regras de persistência ficam no domínio. A segurança lê o usuário autenticado do JWT e não aceita um identificador de usuário vindo do cliente.

O dinheiro usa `BigDecimal`. Cada transferência ou estorno ocorre em uma transação. As contas envolvidas são bloqueadas em ordem fixa de identificador, com lock pessimista, para duas operações simultâneas não gerarem saldo negativo nem deadlock. O Hibernate está em `validate`: a estrutura vem das migrations.

```
HTTP → controller → application → domain → PostgreSQL
                 ↘ security (JWT)
```

## Estrutura de diretórios

```
src/main/java/br/com/andreyoshinari/paymentcore
├── api
│   ├── controller          # autenticação, contas e transferências
│   ├── dto                 # requisições e respostas
│   ├── exception           # corpo de erro da API
│   └── openapi             # documentação
├── application
│   ├── account             # consulta de conta, saldo e extrato
│   ├── authentication      # login
│   ├── registration        # cadastro e número da conta
│   └── transfer            # transferência e estorno
├── domain
│   ├── entity
│   ├── enums
│   ├── exception
│   └── repository
└── security                # filtro JWT e usuário autenticado

src/main/resources/db/migration   # Flyway
src/test/java                     # testes unitários e de integração
.github/workflows/ci.yml
```

## Funcionalidades implementadas

- Cadastro de usuário com uma conta em reais, saldo zero e status `ACTIVE`.
- Login com JWT. A API não cria sessão.
- Consulta da própria conta e do próprio saldo.
- Transferência entre contas, com chave de idempotência.
- Extrato paginado da conta autenticada, com filtro opcional por período.
- Estorno integral de uma transferência concluída.
- Tratamento global de erros, sem devolver senha, hash ou stack trace.
- Documentação OpenAPI.
- Healthcheck em `/actuator/health`.
- Pipeline de build e testes em pull requests e pushes para `main` e `develop`.

Não há endpoint para bloquear ou encerrar conta, nem integração com um provedor de pagamento.

## Regras de negócio

### Cadastro

O nome tem no máximo 120 caracteres. O e-mail é obrigatório, validado e gravado em minúsculas, sem espaços nas pontas. A senha tem de 8 a 72 caracteres e é armazenada com BCrypt. Um e-mail repetido responde `409`.

O cadastro cria o usuário e uma única conta na mesma transação. A conta nasce com saldo `0.00`, moeda `BRL` e status `ACTIVE`. O número tem 12 dígitos e não começa com zero. Se a conta não for criada, o usuário também não permanece gravado.

### Autenticação

O token traz o identificador do usuário, o emissor `payment-core` e a expiração. A expiração padrão é 15 minutos. Senha errada, usuário desconhecido e usuário inativo recebem a mesma resposta `401`. Um token já emitido continua válido até expirar, mesmo que o usuário seja desativado depois.

Cadastro, login, documentação e `/actuator/health` são públicos. Os demais endpoints exigem `Authorization: Bearer <token>`.

### Conta e extrato

A conta lida é sempre a do usuário do token. Um `accountId` ou `userId` enviado pelo cliente é ignorado. Sem conta, a resposta é `404`.

O extrato volta da data mais recente para a mais antiga. `page` começa em 0. `size` padrão é 20 e o máximo aplicado é 100. `from` e `to` são opcionais, inclusivos e no formato ISO-8601. Se `from` for posterior a `to`, a resposta é `422`.

### Transferência

A origem é a conta do usuário autenticado. O valor é positivo e tem no máximo duas casas decimais. Origem e destino precisam estar `ACTIVE` e ser contas diferentes. Sem saldo suficiente, nada é gravado.

Débito, crédito, transferência e as duas linhas de extrato (`TRANSFER_SENT` e `TRANSFER_RECEIVED`) entram na mesma transação. A primeira execução de uma `Idempotency-Key` responde `201`. A mesma chave com os mesmos dados responde `200` e não debita de novo. A mesma chave com dados diferentes responde `409`.

### Estorno

Estas regras são do projeto de estudo. Elas não reproduzem o PIX nem outro arranjo de pagamento.

Só o dono da conta de origem pode estornar. Outro usuário recebe `403`. A transferência precisa existir e estar `COMPLETED`. O estorno é integral: o valor sai da conta de destino e volta para a origem. A conta de destino precisa cobrir o valor inteiro. O status da conta não bloqueia o estorno.

A transferência original permanece. O status passa a `REVERSED` e `reversedAt` é preenchido. O extrato recebe `REVERSAL_SENT` na conta de destino e `REVERSAL_RECEIVED` na conta de origem. Um segundo estorno responde `409`.

## Modelo de dados

As migrations estão em `src/main/resources/db/migration`. A versão 2 garante uma conta por usuário. A versão 3 cria transferências e extrato.

| Tabela | Papel |
| --- | --- |
| `users` | Nome, e-mail único, hash da senha e flag `active`. |
| `accounts` | Uma conta por usuário, número único, saldo `NUMERIC(19,2)` não negativo, moeda e status `ACTIVE`, `BLOCKED` ou `CLOSED`. |
| `transfers` | Origem, destino, valor positivo, status `COMPLETED` ou `REVERSED`, chave de idempotência única e `reversed_at`. Origem e destino não podem ser a mesma conta. |
| `account_transactions` | Movimento de uma conta ligado a uma transferência. O valor é positivo. O tipo indica o sentido: `TRANSFER_SENT`, `TRANSFER_RECEIVED`, `REVERSAL_SENT` ou `REVERSAL_RECEIVED`. |

Há índices por conta e data para o extrato e para as pontas da transferência.

## Endpoints

| Método | Caminho | Auth | Sucesso |
| --- | --- | --- | --- |
| `POST` | `/api/v1/auth/register` | não | `201` |
| `POST` | `/api/v1/auth/login` | não | `200` |
| `GET` | `/api/v1/accounts/me` | JWT | `200` |
| `GET` | `/api/v1/accounts/me/balance` | JWT | `200` |
| `GET` | `/api/v1/accounts/me/transactions` | JWT | `200` |
| `POST` | `/api/v1/transfers` | JWT | `201` ou `200` |
| `POST` | `/api/v1/transfers/{transferId}/reverse` | JWT | `200` |

Códigos de erro usados pela API: `400` validação ou corpo inválido, `401` autenticação, `403` estorno por quem não é o dono da origem, `404` recurso ausente, `409` conflito de e-mail, idempotência ou estorno, `422` regra de negócio.

O corpo de erro tem `timestamp`, `status`, `code`, `message` e `path`. Em validação, inclui `errors` com o campo e a mensagem. O valor rejeitado não é devolvido.

## Exemplos

### Cadastro

```http
POST /api/v1/auth/register
Content-Type: application/json

{
  "name": "Ana Silva",
  "email": "ana@example.com",
  "password": "senha-segura-1"
}
```

```json
{
  "user": {
    "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "name": "Ana Silva",
    "email": "ana@example.com",
    "createdAt": "2026-11-26T20:00:00Z"
  },
  "account": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "accountNumber": "123456789012",
    "balance": 0.00,
    "currency": "BRL",
    "status": "ACTIVE",
    "createdAt": "2026-11-26T20:00:00Z"
  }
}
```

A senha e o hash não aparecem na resposta. O número da conta é gerado pela aplicação.

### Login

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "ana@example.com",
  "password": "senha-segura-1"
}
```

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

`expiresIn` está em segundos. Com a expiração padrão de 15 minutos, o valor é `900`.

### Conta e saldo

```http
GET /api/v1/accounts/me
Authorization: Bearer <jwt>
```

```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "accountNumber": "123456789012",
  "balance": 100.00,
  "currency": "BRL",
  "status": "ACTIVE",
  "createdAt": "2026-11-26T20:00:00Z"
}
```

`GET /api/v1/accounts/me/balance` devolve `accountId`, `accountNumber`, `balance`, `currency` e `status`.

### Transferência

```http
POST /api/v1/transfers
Authorization: Bearer <jwt>
Idempotency-Key: transfer-001
Content-Type: application/json

{
  "recipientAccountNumber": "109876543210",
  "amount": 25.50,
  "description": "Almoço"
}
```

```json
{
  "id": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "senderAccountNumber": "123456789012",
  "recipientAccountNumber": "109876543210",
  "amount": 25.50,
  "description": "Almoço",
  "status": "COMPLETED",
  "createdAt": "2026-11-26T20:05:00Z",
  "reversedAt": null
}
```

### Extrato

```http
GET /api/v1/accounts/me/transactions?page=0&size=20&from=2026-11-01T00:00:00Z&to=2026-11-30T23:59:59Z
Authorization: Bearer <jwt>
```

```json
{
  "content": [
    {
      "id": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
      "transactionType": "TRANSFER_SENT",
      "amount": 25.50,
      "description": "Almoço",
      "createdAt": "2026-11-26T20:05:00Z",
      "transferId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### Estorno

```http
POST /api/v1/transfers/9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d/reverse
Authorization: Bearer <jwt>
```

A resposta tem o mesmo formato da transferência, com `status` `REVERSED` e `reversedAt` preenchido.

## Execução local

É preciso ter JDK 21 e Docker.

Suba só o PostgreSQL:

```bash
docker compose up -d postgres
```

No PowerShell, defina o segredo do JWT e inicie a API. O valor precisa ter no mínimo 32 bytes. Use um segredo seu:

```powershell
$env:JWT_SECRET = "<seu-segredo-com-pelo-menos-32-bytes>"
.\mvnw.cmd spring-boot:run
```

No bash:

```bash
export JWT_SECRET="<seu-segredo-com-pelo-menos-32-bytes>"
./mvnw spring-boot:run
```

A API escuta em `http://localhost:8080`. Sem variáveis de banco, ela usa o PostgreSQL local descrito em `src/main/resources/application-base.properties`.

## Docker Compose

O Compose sobe o PostgreSQL 16 e a API. A API espera o banco ficar saudável. O volume `payment_core_data` guarda os dados do Postgres. A API não tem volume.

```bash
docker compose up --build
```

A API fica em `http://localhost:8080` e o banco em `localhost:5432`. Defina `JWT_SECRET` no ambiente antes do comando. O valor precisa ter no mínimo 32 bytes.

## Variáveis de ambiente

| Variável | Uso |
| --- | --- |
| `JWT_SECRET` | Chave HMAC do token. Obrigatória. Mínimo de 32 bytes. |
| `JWT_EXPIRATION` | Validade do token. Padrão: `15m`. |
| `DB_URL` | JDBC do PostgreSQL. No Compose: `jdbc:postgresql://postgres:5432/payment_core`. |
| `DB_USERNAME` | Usuário do banco. |
| `DB_PASSWORD` | Senha do banco. |

Não grave um segredo real no repositório. A senha do PostgreSQL no Compose é só do banco local desta aplicação.

## Testes

Os testes de integração usam Testcontainers e precisam do Docker em execução.

Windows:

```powershell
.\mvnw.cmd test
```

Linux e o pipeline:

```bash
./mvnw -B clean verify
```

O GitHub Actions executa esse `verify` em pushes e pull requests para `main` e `develop`. O relatório do Surefire fica no artefato `surefire-reports` da execução.

## Swagger

Com a API no ar:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI: `http://localhost:8080/v3/api-docs`

No botão **Authorize**, cole apenas o `accessToken`, sem o prefixo `Bearer`.
