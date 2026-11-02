# Guion de presentación · Evaluación Parcial N°2 (5–10 minutos)

**Duración objetivo:** 8 minutos. **Formato:** individual, con demo en vivo y diapositivas de respaldo.

---

## 0:00 – 0:40 · Apertura

> «Buenos días. Voy a presentar **Pedidos360**, una aplicación cloud-native que integra las cinco tareas del curso con la arquitectura de mensajería de la rúbrica: EC2, Spring Boot, GitHub Actions, Cognito, API Gateway, React y un clúster RabbitMQ de dos nodos.»

Muestra la diapositiva de arquitectura (frontend → API Gateway → backend → RabbitMQ).

---

## 0:40 – 1:40 · Tarea 1: EC2 y SSH

> «Levanté una instancia Amazon Linux 2023 en EC2, con un Security Group que expone SSH, HTTP, el backend y las consolas de RabbitMQ. La comprobación de acceso fue por SSH.»

**Demo:**

```bash
ssh -i pedidos360-key.pem ec2-user@$PUBLIC_IP "hostname && cat /etc/os-release | head -2"
```

> «Como ven, el hostname y la versión del sistema confirman que el acceso es exitoso.»

---

## 1:40 – 2:40 · Tarea 2: Spring Boot en GitHub

> «El backend es Spring Boot 3.3.5 con Java 17. El puerto se parametriza con `SERVER_PORT`, por defecto 8080, y en EC2 se publica como 80. El proyecto está versionado en GitHub y compila con `mvn clean verify`.»

**Demo:** muestra el repositorio y el `BUILD SUCCESS`.

> «La estructura separa configuración, mensajería, servicios, controladores y DTOs, lo que facilita cumplir los indicadores de separación de responsabilidades.»

---

## 2:40 – 4:00 · Tarea 3: CI/CD con GitHub Actions y GHCR

> «Cada push a `main` dispara tres jobs: build y test con Maven, construcción y publicación de la imagen Docker en GitHub Container Registry, y despliegue por SSH a la EC2 con `docker pull` y `docker run` en el puerto 80.»

**Demo:** muestra el run con los tres jobs en verde y luego:

```bash
ssh ... "docker ps --filter name=pedidos360-backend"
curl http://$PUBLIC_IP/api/health
```

> «El pipeline es reproducible y no requiere intervención manual.»

---

## 4:00 – 5:00 · Tarea 4: Cognito + API Gateway

> «En Cognito creé un User Pool con tres grupos: ADMIN, COLLABORATOR y CUSTOMER. El App Client es SPA sin secret y usa Authorization Code con PKCE. El API Gateway HTTP valida el JWT con un autorizador de Cognito antes de enrutar al backend.»

**Demo:** consola de Cognito (grupos/usuarios) y API Gateway (rutas + autorizador). Luego:

```bash
curl -i http://$API_URL/api/pedidos          # 401 sin token
curl -i -H "Authorization: Bearer $TOKEN" http://$API_URL/api/pedidos   # 200
```

> «El backend, además, traduce el claim `cognito:groups` a roles `ROLE_*` y aplica reglas por método HTTP: DELETE solo ADMIN, POST/PUT ADMIN o COLLABORATOR.»

---

## 5:00 – 6:00 · Tarea 5: React + login

> «El frontend es React 18 con TypeScript y Vite, usando `react-oidc-context`. Al pulsar “Iniciar sesión”, redirige al Hosted UI de Cognito, vuelve con el token y lo adjunta como Bearer en cada llamada al API.»

**Demo:** login con `admin@pedidos360.cl`, muestra el grupo ADMIN, crea un pedido y aparece en la tabla. Cierra sesión.

> «Según el grupo, la interfaz y el backend permiten o bloquean acciones; un CUSTOMER solo puede leer.»

---

## 6:00 – 8:00 · Rúbrica RabbitMQ

> «La mensajería cumple los ocho indicadores. Los nombres están centralizados en `application.yml` y se leen con `@ConfigurationProperties`. `RabbitMQConfig` declara tres exchanges —direct, topic y DLX—, tres colas y sus tres DLQ, con sus bindings.»

> «La lógica de negocio está en `PedidoService`, separada del publisher y de la configuración. Hay tres consumidores `@RabbitListener`, uno por dominio: notificaciones, tickets y documentos. Cada uno hace ACK al procesar y NACK sin reencolar ante error, lo que envía el mensaje a la DLQ; además hay reintentos con backoff.»

> «El microservicio de administración expone endpoints REST para crear, listar y eliminar colas y exchanges; toda la lógica está encapsulada en `RabbitAdminService` y los parámetros se validan con Bean Validation.»

**Demo del clúster y la DLQ:**

```bash
docker exec rabbitmq-1 rabbitmqctl cluster_status
docker exec rabbitmq-1 rabbitmqctl list_queues name messages
```

Luego crea un pedido con `cantidad: 150` y muestra:

```bash
docker exec rabbitmq-1 rabbitmqctl list_queues name messages | grep tickets.dlq
```

> «El mensaje inválido fue rechazado y terminó en `tickets.dlq`, demostrando el flujo de error completo.»

---

## 8:00 – 8:30 · Cierre

> «En resumen: la aplicación está desplegada en EC2 mediante CI/CD, protegida con Cognito y API Gateway, consumida por un frontend OIDC, y respaldada por un clúster RabbitMQ de dos nodos con tres colas, sus DLQ y exchanges direct y topic. Gracias. ¿Preguntas?»

---

## Preguntas frecuentes del evaluador

**¿Por qué manual ACK y no automático?**
Porque permite decidir explícitamente entre confirmar (`basicAck`) y rechazar sin reencolar (`basicNack(requeue=false)`), garantizando que los errores de negocio lleguen a la DLQ en lugar de reprocesarse infinitamente.

**¿Qué pasa si RabbitMQ cae?**
El clúster de dos nodos mantiene las colas replicadas (política `ha-all`). El backend usa reintentos con backoff y `default-requeue-rejected: false`.

**¿Cómo se valida el JWT?**
Dos capas: el autorizador JWT del API Gateway valida antes de enrutar, y Spring Security valida firma, issuer, expiración y audience con el JWKS de Cognito.

**¿Por qué GHCR y no ECR?**
Porque la tarea pide el registry de GitHub; GHCR se integra nativamente con Actions usando `GITHUB_TOKEN`.

**¿Cómo se separa la lógica de mensajería?**
`PedidoService` no conoce RabbitMQ; publica a través de `PedidoEventPublisher`, y la topología vive solo en `RabbitMQConfig`.
