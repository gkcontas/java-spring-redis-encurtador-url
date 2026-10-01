# Encurtador de URLs com Cache

Encurtador de links no modelo clássico de cache-aside: o PostgreSQL é a fonte de verdade e o Redis guarda as leituras quentes.

O redirecionamento é, de longe, a operação mais frequente de um encurtador — muito mais que a criação de links. Por isso é exatamente ela que vai para o cache, enquanto escrita e desativação seguem direto para o banco.

## Tecnologias e bibliotecas

| | |
|---|---|
| Linguagem | Java 17 |
| Framework | Spring Boot 3.3 |
| Cache | Redis 7 via Spring Data Redis (`StringRedisTemplate`) |
| Persistência | Spring Data JPA, PostgreSQL 16 |
| Migrations | Flyway |
| Validação | Bean Validation |
| Build | Gradle Kotlin DSL (wrapper `gradlew`) |
| Testes | JUnit 5, Mockito, Awaitility, Testcontainers (PostgreSQL e Redis) |
| Apoio | Lombok |

## Pré-requisitos

- JDK 17 ou superior
- Docker

## Como rodar

```bash
docker compose up -d
```

```bash
./gradlew bootRun
```

A API fica em `http://localhost:8080`.

## Como o cache funciona

```
GET /{code}
     │
     ├── achou link:{code} no Redis ──────────► 302, sem tocar no Postgres
     │
     └── não achou ──► consulta o Postgres ──► grava no Redis (TTL 1h) ──► 302
```

- **Leitura** — tenta o Redis primeiro; em cache miss busca no banco, popula a chave com TTL de uma hora e redireciona.
- **Escrita** — criar e desativar vão direto ao PostgreSQL. Desativar também apaga a chave no Redis, para o link não continuar respondendo do cache depois de removido.
- **Contagem de acessos** — incrementada de forma assíncrona, fora do caminho do redirecionamento.

## Endpoints

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/links` | Cria um link curto a partir de uma URL |
| `GET` | `/{code}` | Redireciona (302) para a URL original |
| `GET` | `/links/{code}/stats` | Código, situação e total de acessos |
| `DELETE` | `/links/{code}` | Desativa o link e invalida o cache |

## Exemplos de uso

```bash
curl -s -X POST localhost:8080/links \
  -H "Content-Type: application/json" \
  -d '{"originalUrl": "https://example.com/very/long/path"}'
```

```bash
curl -si localhost:8080/bcMgp6R | head -1
```

```bash
curl -s localhost:8080/links/bcMgp6R/stats
```

```bash
curl -s -X DELETE localhost:8080/links/bcMgp6R -w "%{http_code}\n"
```

## Testes

```bash
./gradlew test
```

14 testes: 10 unitários e 4 de integração contra PostgreSQL e Redis reais em containers. O teste principal observa o repositório para confirmar que o segundo acesso ao mesmo código é servido pelo cache, sem nova consulta ao banco.
