# Checklist de la rúbrica · Evaluación Parcial N°2 (DSY1107)

Mapeo **indicador → evidencia concreta** (archivo y línea). Todos los porcentajes suman 100 %.

| # | Indicador | % | Evidencia (archivo : línea) | Cómo demostrarlo |
|---|---|---:|---|---|
| 1 | Nombres de colas/exchanges/bindings centralizados en `application.yml` o clase `@Configuration` | 12 % | `backend/src/main/resources/application.yml:39-59` (`app.rabbit.*`) + `config/RabbitProperties.java` (`@ConfigurationProperties(prefix="app.rabbit")`) | Abrir `application.yml` y mostrar que **ningún** nombre está hardcodeado en consumidores/publisher |
| 2 | Beans de configuración Queue, Exchange y Binding por caso de uso | 13 % | `config/RabbitMQConfig.java:33` (direct), `:38` (topic), `:43` (dlx), `:50-60` (3 colas), `:65-75` (3 DLQ), `:82-131` (6 bindings) | Mostrar el archivo; cada cola con `deadLetterExchange` en `:144-145` |
| 3 | Separación de lógica de negocio y configuración de mensajería | 10 % | `service/PedidoService.java` (solo reglas) → `messaging/PedidoEventPublisher.java` (envío) → `config/RabbitMQConfig.java` (topología) | `grep -n "AmqpTemplate\|RabbitTemplate" service/PedidoService.java` **no** devuelve nada |
| 4 | Consumidores `@RabbitListener` agrupados por dominio funcional | 15 % | `messaging/NotificacionConsumer.java:23`, `TicketConsumer.java:23`, `DocumentoConsumer.java:23` | Tres clases, una por dominio, cada una con su cola |
| 5 | ACK/NACK y manejo explícito de errores (retry, NACK, DLQ) | 20 % | `basicAck` en `NotificacionConsumer:31`, `TicketConsumer:32`, `DocumentoConsumer:32`; `basicNack(tag,false,false)` en `:35/:36/:36`; `application.yml` `acknowledge-mode: manual`, `default-requeue-rejected: false`, `retry.max-attempts: 3` | Crear pedido con `cantidad: 150` → mensaje en `tickets.dlq` |
| 6 | Microservicio de administración con endpoints REST (`POST /queues`, `DELETE /queues/{name}`, etc.) | 13 % | `web/RabbitAdminController.java:29-30` (`/api/admin`, `ROLE_ADMIN`), `:39` GET queues, `:44` POST queues, `:51` DELETE queue, `:57` GET exchanges, `:62` POST exchanges | `curl` a `/api/admin/queues` con token ADMIN |
| 7 | Lógica de creación de colas/exchanges encapsulada en servicio dedicado | 10 % | `service/RabbitAdminService.java:25` (clase), `:35` `crearCola`, `:55` `eliminarCola`, `:60` `crearExchange` | El controller solo delega en el servicio |
| 8 | Validación de parámetros de entrada (nombres vacíos, configs inválidas) | 7 % | `dto/QueueRequest.java:7-8` (`@NotBlank`, `@Pattern`), `dto/ExchangeRequest.java:7-12`, `dto/PedidoRequest.java:8-11`, `exception/GlobalExceptionHandler.java` | `curl -d '{"name":""}'` → `400` con detalle por campo |

**Total: 100 %**

---

## Requisitos de la presentación (además de los 8 indicadores)

| Requisito de la presentación | Dónde se cumple | Evidencia |
|---|---|---|
| API Manager / API Gateway en la nube | `docs/GUIA_EVALUACION_2.md` §4.7 | Consola API Gateway: rutas + autorizador JWT |
| Validación de JWT | `config/SecurityConfig.java:70,88` + autorizador JWT del API Gateway | `curl` sin token → 401; con token → 200 |
| IDaaS: tenant + usuarios | Cognito User Pool + 3 grupos + 3 usuarios (§4.1–4.5) | Consola Cognito |
| Frontend con OAuth 2.0 / OIDC | `frontend/src/authConfig.ts` (code + PKCE) | Hosted UI + app autenticada |
| Backend y frontend desplegados e integrados | GitHub Actions → GHCR → EC2 (`.github/workflows/deploy.yml`) | `docker ps` + app consumiendo el API |
| **Dos nodos RabbitMQ en clúster** | `infra/docker-compose.yml` (`rabbitmq-1`, `rabbitmq-2`) | `rabbitmqctl cluster_status` |
| **Tres colas con sus DLQ** | `infra/rabbitmq/definitions.json` + `RabbitMQConfig.java` | `rabbitmqctl list_queues` (6 colas) |
| **Exchanges direct y topic** | `RabbitMQConfig.java:33,38` + `definitions.json` | `rabbitmqctl list_exchanges` |
| **docker-compose con servicios RabbitMQ** | `infra/docker-compose.yml` | `docker compose ps` |

---

## Guion de verificación rápida (para el evaluador)

```bash
# 1) Clúster de 2 nodos
docker exec rabbitmq-1 rabbitmqctl cluster_status | grep -A3 "Running Nodes"

# 2) 3 colas + 3 DLQ
docker exec rabbitmq-1 rabbitmqctl list_queues name messages | sort

# 3) Exchanges direct + topic + dlx
docker exec rabbitmq-1 rabbitmqctl list_exchanges name type | grep pedidos

# 4) Bindings
docker exec rabbitmq-1 rabbitmqctl list_bindings source_name destination_name routing_key

# 5) Forzar DLQ: crear pedido con cantidad 150 desde el frontend o curl
curl -X POST http://localhost:8080/api/pedidos \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"producto":"Test","cantidad":150,"cliente":"QA"}'
docker exec rabbitmq-1 rabbitmqctl list_queues name messages | grep tickets.dlq
```

---

## Estado real al 2026-11-02

| Indicador | Peso | Evidencia | Estado |
|---|---|---|---|
| EC2 + SSH | 12 % | `i-03a70f0a716b69333` / `98.93.243.92` | ✅ |
| Spring Boot + GitHub | 13 % | repo `Kenpina0/pedidos360-eval2` | ✅ |
| GitHub Actions → GHCR → EC2 | 10 % | workflow listo, push bloqueado por scope `workflow` del PAT | ⚠️ |
| Cognito + API Gateway | 15 % | pool `us-east-1_9b2C1fxtb`, API `bf0plxi897` | ✅ |
| React + OIDC login | 20 % | `https://98.93.243.92:8443` | ✅ |
| RabbitMQ 2 nodos | 13 % | `pedidos360-cluster` | ✅ |
| 3 colas + 3 DLQ + direct/topic | 10 % | 6 colas, 3 exchanges, `tickets.dlq`=1 | ✅ |
| docker-compose | 7 % | `infra/docker-compose.yml` (5 servicios) | ✅ |

Detalle completo en [`INFORME_EVIDENCIAS.md`](INFORME_EVIDENCIAS.md).
