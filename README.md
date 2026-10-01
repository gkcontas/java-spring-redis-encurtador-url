# Encurtador de URLs com Cache

Encurtador de URLs em Java com Spring Boot, usando PostgreSQL como fonte de verdade e Redis como cache de leitura (cache-aside) para os redirecionamentos mais acessados.

## Status

✅ MVP implementado.

## Stack

- Java 17 + Spring Boot 3.3
- Redis (Spring Data Redis, `StringRedisTemplate`)
- PostgreSQL + Spring Data JPA + Flyway
- Lombok (na entidade `Link`)
- Gradle (Kotlin DSL) + wrapper `gradlew`
- Testcontainers (Postgres + Redis) + Awaitility + JUnit 5 + Mockito

## O padrão cache-aside usado aqui

`GET /{code}` (o redirecionamento) é o caminho mais quente da aplicação — muitíssimo mais frequente que a criação de links — então vale a pena cachear especificamente essa leitura:

```
GET /{code}
      │
      ▼
  Redis tem "link:{code}"?
      │
   sim│                              não
      ▼                               ▼
retorna do cache                busca no PostgreSQL (ativo?)
(sem tocar o Postgres)                │
      │                        popula Redis (TTL 1h)
      │                               │
      └───────────────┬───────────────┘
                       ▼
              302 para a URL original
       (contagem de acesso incrementada
        de forma assíncrona, em ambos os casos)
```

- **Leitura**: tenta o Redis primeiro (`link:{code}` → URL original). Em cache miss, busca no Postgres (só se o link estiver ativo), grava no Redis com TTL de 1h, e só então redireciona.
- **Escrita**: criar e desativar um link sempre vão direto ao Postgres — é a fonte de verdade. Desativar um link **invalida ativamente** a chave no Redis (`DEL`), em vez de esperar o TTL expirar; caso contrário, o link continuaria respondendo do cache mesmo depois de "excluído".
- **Contagem de acessos**: incrementada em um método `@Async` separado (`AccessCounterService`), fora do caminho crítico do redirecionamento — uma falha ou lentidão nessa escrita nunca atrasa ou quebra o redirect.

Isso foi validado manualmente rodando a aplicação: criei um link, conferi a chave `link:{code}` no Redis via `redis-cli` (com TTL correto), fiz dois acessos e confirmei `totalAccesses: 2` nas estatísticas, desativei o link e confirmei que a chave sumiu do Redis e o redirecionamento passou a retornar 404.

## Como rodar

1. Suba o PostgreSQL e o Redis:
   ```bash
   docker compose up -d
   ```
2. Rode a aplicação:
   ```bash
   ./gradlew bootRun
   ```
3. A API sobe em `http://localhost:8080`.

## Como rodar os testes

```bash
./gradlew test
```

- `service` — testes unitários (geração de código, lógica de cache-aside com Redis/repositório mockados), não precisam de Docker.
- `integration` — testes de integração via MockMvc contra PostgreSQL e Redis reais (Testcontainers). O teste principal usa um `@SpyBean` sobre `LinkRepository` para comprovar que o segundo acesso ao mesmo código **não** dispara uma nova consulta ao banco (`verify(linkRepository, times(1)).findByCode(...)`, mesmo após dois `GET /{code}`).

Suíte completa: **14 testes, todos passando** — 10 unitários e 4 de integração contra PostgreSQL e Redis reais.

> O teste de cache-aside limpa as invocações do spy (`clearInvocations`) logo após criar o link. Motivo: o `ShortCodeGenerator` também chama `findByCode` para conferir que o código sorteado não está em uso, então contar essa chamada de setup junto com as de `resolve` faria a asserção significar algo diferente do que ela afirma.

### Nota sobre Testcontainers e Docker Engine recente

Se os testes falharem com `client version 1.32 is too old. Minimum supported API version is 1.40`, a causa é o `docker-java` embutido no Testcontainers negociar a API 1.32, abaixo do mínimo aceito pelo Docker Engine 29+. Correção global, de uma linha:

```bash
echo 'api.version=1.44' > ~/.docker-java.properties
```

## Endpoints principais

| Método | Rota                    | Descrição                                              |
|--------|--------------------------|-----------------------------------------------------------|
| POST   | `/links`                | Cria um link curto a partir de uma URL                    |
| GET    | `/{code}`               | Redireciona (302) para a URL original (cache-aside)        |
| DELETE | `/links/{code}`         | Desativa o link e invalida o cache                         |
| GET    | `/links/{code}/stats`   | Retorna código, status ativo/inativo e total de acessos     |

## Exemplo de uso

```bash
# Criar link
curl -s -X POST localhost:8080/links \
  -H "Content-Type: application/json" \
  -d '{"originalUrl": "https://example.com/very/long/path"}'
# {"code": "bcMgp6R", "shortUrl": "http://localhost:8080/bcMgp6R", "originalUrl": "..."}

# Acessar (redireciona)
curl -si localhost:8080/bcMgp6R | head -1

# Ver estatísticas
curl -s localhost:8080/links/bcMgp6R/stats

# Desativar
curl -s -X DELETE localhost:8080/links/bcMgp6R -w "%{http_code}\n"
```
