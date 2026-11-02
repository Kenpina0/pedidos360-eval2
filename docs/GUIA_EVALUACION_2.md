# Guía de implementación · Evaluación Parcial N°2 (DSY1107)

> ⚠️ **Nota de vigencia (2026-11-02):** este documento describe el procedimiento general.
> El despliegue que quedó **realmente funcionando** usa `https://98.93.243.92:8443` para el frontend
> (Cognito exige HTTPS), el App Client `261rvdvemjgib6n86vrb05tl9k` y el API Gateway `bf0plxi897`.
> Consulta **[`DESPLIEGUE_REAL.md`](DESPLIEGUE_REAL.md)** para los valores exactos y las diferencias.



Esta guía reproduce, paso a paso y con comandos reales, **las 5 tareas de los videos del curso** y **los 8 indicadores de la rúbrica de RabbitMQ**. Cada sección indica qué hacer, qué comando ejecutar y **qué evidencia capturar** para la evaluación.

> Convención: los valores entre `<...>` debes reemplazarlos por los tuyos (región, IP, IDs de Cognito, etc.). Todos los comandos se ejecutan desde la raíz del repositorio salvo que se indique otra carpeta.

---

## 0. Requisitos previos

| Herramienta | Versión mínima | Verificación |
|---|---|---|
| Java JDK | 17 | `java -version` |
| Maven | 3.8+ | `mvn -v` |
| Node.js | 18+ | `node -v` |
| Docker | 24+ | `docker --version` |
| AWS CLI | 2.x | `aws --version` |
| Git | 2.x | `git --version` |

Cuenta de AWS con permisos sobre EC2, Cognito, API Gateway y (opcional) ECR/GHCR. Repositorio en GitHub con permisos de Actions y Packages.

---

## 1. Tarea 1 · Subir una instancia EC2 y comprobar el acceso por SSH

### 1.1 Crear el key pair

```bash
aws ec2 create-key-pair \
  --key-name pedidos360-key \
  --query 'KeyMaterial' \
  --output text > pedidos360-key.pem
chmod 400 pedidos360-key.pem
```

### 1.2 Crear el Security Group

```bash
SG_ID=$(aws ec2 create-security-group \
  --group-name pedidos360-sg \
  --description "SSH, HTTP, backend y RabbitMQ" \
  --query 'GroupId' --output text)

# SSH desde tu IP
aws ec2 authorize-security-group-ingress --group-id $SG_ID \
  --protocol tcp --port 22 --cidr $(curl -s https://checkip.amazonaws.com)/32

# HTTP, backend y consolas RabbitMQ
aws ec2 authorize-security-group-ingress --group-id $SG_ID --protocol tcp --port 80   --cidr 0.0.0.0/0
aws ec2 authorize-security-group-ingress --group-id $SG_ID --protocol tcp --port 8080 --cidr 0.0.0.0/0
aws ec2 authorize-security-group-ingress --group-id $SG_ID --protocol tcp --port 15672 --cidr 0.0.0.0/0
aws ec2 authorize-security-group-ingress --group-id $SG_ID --protocol tcp --port 15673 --cidr 0.0.0.0/0
```

### 1.3 Lanzar la instancia (Amazon Linux 2023, t3.micro)

```bash
AMI_ID=$(aws ec2 describe-images \
  --owners amazon \
  --filters "Name=name,Values=al2023-ami-*-x86_64" "Name=state,Values=available" \
  --query 'sort_by(Images,&CreationDate)[-1].ImageId' --output text)

INSTANCE_ID=$(aws ec2 run-instances \
  --image-id $AMI_ID \
  --instance-type t3.micro \
  --key-name pedidos360-key \
  --security-group-ids $SG_ID \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=pedidos360-ec2}]' \
  --query 'Instances[0].InstanceId' --output text)

aws ec2 wait instance-running --instance-ids $INSTANCE_ID
PUBLIC_IP=$(aws ec2 describe-instances --instance-ids $INSTANCE_ID \
  --query 'Reservations[0].Instances[0].PublicIpAddress' --output text)
echo "IP pública: $PUBLIC_IP"
```

### 1.4 Comprobar el acceso por SSH (evidencia obligatoria)

```bash
ssh -i pedidos360-key.pem ec2-user@$PUBLIC_IP "hostname && cat /etc/os-release | head -2 && uname -r"
```

Salida esperada (ejemplo):

```
ip-172-31-xx-xx.ec2.internal
NAME="Amazon Linux"
VERSION="2023"
6.1.x-xx.xx.amzn2023.x86_64
```

**Evidencia para la evaluación:** captura de pantalla del terminal mostrando el comando `ssh` y la salida con el hostname de la instancia. Guarda también el `INSTANCE_ID` y la IP.

### 1.5 Preparar la instancia (Docker)

Copia el script y ejecútalo en la instancia:

```bash
scp -i pedidos360-key.pem infra/ec2-setup.sh ec2-user@$PUBLIC_IP:~/
ssh -i pedidos360-key.pem ec2-user@$PUBLIC_IP "sudo bash ec2-setup.sh"
```

El script instala Docker Engine, el plugin `docker compose`, agrega `ec2-user` al grupo `docker` y verifica las versiones. Alternativamente puedes pegarlo como **user-data** al lanzar la instancia.

---

## 2. Tarea 2 · Proyecto Spring Boot, puerto adaptado y subida a GitHub

### 2.1 Estructura del backend

El proyecto ya está en `backend/` con Spring Boot 3.3.5 y Java 17. Dependencias principales (ver `backend/pom.xml`):

- `spring-boot-starter-web`
- `spring-boot-starter-security` + `spring-boot-starter-oauth2-resource-server` (validación JWT de Cognito)
- `spring-boot-starter-amqp` (RabbitMQ)
- `spring-boot-starter-validation` (Bean Validation)
- `spring-boot-starter-actuator` (health checks)

### 2.2 Adaptación del puerto

El puerto se controla por variable de entorno en `src/main/resources/application.yml`:

```yaml
server:
  port: ${SERVER_PORT:8080}
  forward-headers-strategy: framework
```

- Local: `8080`.
- En EC2 con Docker: se publica `-p 80:8080`, de modo que **el puerto 80 del host** apunta al **8080 del contenedor**. Así el backend queda accesible sin escribir el puerto.

### 2.3 Compilar y probar

```bash
cd backend
mvn -B clean verify
```

Debe terminar con `BUILD SUCCESS` y generar `target/pedidos360-backend-1.0.0.jar`.

Prueba local sin AWS (perfil `local`, desactiva seguridad y listeners):

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
curl http://localhost:8080/api/health
# {"status":"UP","app":"pedidos360-backend","timestamp":"..."}
```

### 2.4 Subir a GitHub

```bash
git init
git add .
git commit -m "feat: backend Spring Boot + RabbitMQ + Cognito"
git branch -M main
git remote add origin https://github.com/<TU_USUARIO>/pedidos360.git
git push -u origin main
```

**Evidencia:** captura del repositorio en GitHub con el código y del `BUILD SUCCESS` de Maven.

---

## 3. Tarea 3 · GitHub Actions + registry de GitHub + Docker hacia EC2

El workflow está en `.github/workflows/deploy.yml` y tiene **tres jobs encadenados**:

1. **build-and-test** — `mvn clean verify` con JDK 17 y publica el JAR como artefacto.
2. **docker-publish** — construye la imagen con Buildx y la publica en **GHCR** (`ghcr.io/<owner>/pedidos360-backend`) con tags `latest` y `sha-<commit>`.
3. **deploy-ec2** — entra por SSH a la instancia, hace `docker pull`, detiene el contenedor anterior y levanta el nuevo con `-p 80:8080`.

### 3.1 Secrets necesarios en GitHub

En **Settings → Secrets and variables → Actions** crea:

| Secret | Valor |
|---|---|
| `EC2_HOST` | IP pública o DNS de la instancia |
| `EC2_USER` | `ec2-user` |
| `EC2_SSH_KEY` | contenido completo del `.pem` (incluye `-----BEGIN...`) |
| `EC2_PORT` | `22` (opcional) |
| `GHCR_PAT` | Personal Access Token clásico con `read:packages` (para que EC2 pueda hacer pull) |
| `COGNITO_ISSUER_URI` | `https://cognito-idp.<region>.amazonaws.com/<pool-id>` |
| `RABBITMQ_HOST` | IP privada/pública del nodo RabbitMQ |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | credenciales del broker |
| `CORS_ALLOWED_ORIGINS` | URL del frontend (p. ej. `http://<ip>:5173`) |

> `GITHUB_TOKEN` ya lo provee Actions y se usa para **publicar** en GHCR. El `GHCR_PAT` solo se usa en la EC2 para **descargar** la imagen.

### 3.2 Permisos del paquete

Tras el primer push, ve a **GitHub → Packages → pedidos360-backend → Package settings** y verifica que la visibilidad permita el pull desde la EC2 (público, o privado con el PAT).

### 3.3 Disparar el pipeline

```bash
git add .
git commit -m "ci: pipeline build + ghcr + deploy ec2"
git push origin main
```

En **Actions** verás los tres jobs en verde. Al final, en la EC2:

```bash
ssh -i pedidos360-key.pem ec2-user@$PUBLIC_IP "docker ps --filter name=pedidos360-backend"
curl http://$PUBLIC_IP/api/health
```

**Evidencia:** captura del run de GitHub Actions con los 3 jobs en verde + `docker ps` en la EC2 + `curl` al health.

---

## 4. Tarea 4 · Cognito (User Pool, Grupos) + API Gateway

### 4.1 Crear el User Pool

```bash
POOL_ID=$(aws cognito-idp create-user-pool \
  --pool-name pedidos360-pool \
  --auto-verified-attributes email \
  --username-attributes email \
  --policies '{"PasswordPolicy":{"MinimumLength":8,"RequireUppercase":true,"RequireLowercase":true,"RequireNumbers":true,"RequireSymbols":false}}' \
  --query 'UserPool.Id' --output text)
echo "POOL_ID=$POOL_ID"
```

### 4.2 Crear los tres grupos de permisos

```bash
for G in ADMIN COLLABORATOR CUSTOMER; do
  aws cognito-idp create-group --user-pool-id $POOL_ID --group-name $G
done
```

Estos grupos se traducen a roles en el backend: `ADMIN → ROLE_ADMIN`, `COLLABORATOR → ROLE_COLLABORATOR`, `CUSTOMER → ROLE_CUSTOMER`.

### 4.3 Crear el App Client (SPA, sin secret)

```bash
CLIENT_ID=$(aws cognito-idp create-user-pool-client \
  --user-pool-id $POOL_ID \
  --client-name pedidos360-spa \
  --no-generate-secret \
  --explicit-auth-flows ALLOW_USER_SRP_AUTH ALLOW_REFRESH_TOKEN_AUTH \
  --supported-identity-providers COGNITO \
  --callback-urls '["http://localhost:5173","http://<IP_PUBLICA>:5173"]' \
  --logout-urls '["http://localhost:5173","http://<IP_PUBLICA>:5173"]' \
  --allowed-o-auth-flows code \
  --allowed-o-auth-scopes openid email profile \
  --allowed-o-auth-flows-user-pool-client \
  --query 'UserPoolClient.ClientId' --output text)
echo "CLIENT_ID=$CLIENT_ID"
```

### 4.4 Crear el dominio del Hosted UI

```bash
aws cognito-idp create-user-pool-domain \
  --domain pedidos360-auth-<sufijo-unico> \
  --user-pool-id $POOL_ID
```

El issuer que usará el backend es:

```
https://cognito-idp.<region>.amazonaws.com/$POOL_ID
```

y la authority del frontend es:

```
https://pedidos360-auth-<sufijo-unico>.auth.<region>.amazoncognito.com
```

### 4.5 Crear usuarios de prueba y asignar grupos

```bash
aws cognito-idp admin-create-user --user-pool-id $POOL_ID \
  --username admin@pedidos360.cl --user-attributes Name=email,Value=admin@pedidos360.cl \
  --message-action SUPPRESS

aws cognito-idp admin-set-user-password --user-pool-id $POOL_ID \
  --username admin@pedidos360.cl --password 'Admin1234' --permanent

aws cognito-idp admin-add-user-to-group --user-pool-id $POOL_ID \
  --username admin@pedidos360.cl --group-name ADMIN
```

Repite para un `collab@pedidos360.cl` (grupo `COLLABORATOR`) y un `cliente@pedidos360.cl` (grupo `CUSTOMER`).

### 4.6 Validación del JWT en Spring Boot

`SecurityConfig.java` configura el resource server:

```java
http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
    jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
```

y el converter mapea el claim `cognito:groups` a autoridades `ROLE_*`:

```java
private JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter groups = new JwtGrantedAuthoritiesConverter();
    groups.setAuthoritiesClaimName("cognito:groups");
    groups.setAuthorityPrefix("ROLE_");
    ...
}
```

Reglas de autorización:

- `GET /api/pedidos/**` → autenticado (cualquier grupo).
- `POST`/`PUT` → `ROLE_ADMIN` o `ROLE_COLLABORATOR`.
- `DELETE` y `/api/admin/**` → `ROLE_ADMIN`.

El issuer se toma de `COGNITO_ISSUER_URI`; Spring descarga el JWKS y valida **firma, issuer, expiración y audience**.

### 4.7 API Gateway (HTTP API) con autorizador Cognito

```bash
# 1) Crear la API HTTP
API_ID=$(aws apigatewayv2 create-api \
  --name pedidos360-api \
  --protocol-type HTTP \
  --query 'ApiId' --output text)

# 2) Integración con el backend en EC2 (puerto 80)
INTEG_ID=$(aws apigatewayv2 create-integration \
  --api-id $API_ID \
  --integration-type HTTP_PROXY \
  --integration-uri http://<IP_PUBLICA> \
  --integration-method ANY \
  --payload-format-version 1.0 \
  --query 'IntegrationId' --output text)

# 3) Autorizador JWT de Cognito
AUTH_ID=$(aws apigatewayv2 create-authorizer \
  --api-id $API_ID \
  --name cognito-authorizer \
  --authorizer-type JWT \
  --identity-source '$request.header.Authorization' \
  --jwt-configuration Issuer=https://cognito-idp.<region>.amazonaws.com/$POOL_ID,Audience=$CLIENT_ID \
  --query 'AuthorizerId' --output text)

# 4) Rutas
aws apigatewayv2 create-route --api-id $API_ID \
  --route-key 'ANY /{proxy+}' --authorization-type JWT \
  --authorizer-id $AUTH_ID --target integrations/$INTEG_ID

aws apigatewayv2 create-route --api-id $API_ID \
  --route-key 'GET /api/health' --authorization-type NONE \
  --target integrations/$INTEG_ID

# 5) Stage con auto-deploy
aws apigatewayv2 create-stage --api-id $API_ID \
  --stage-name prod --auto-deploy

echo "https://$API_ID.execute-api.<region>.amazonaws.com/prod"
```

> El health queda **público** para monitoreo; el resto exige JWT válido de Cognito.

**Evidencia:** capturas de la consola de Cognito (User Pool, grupos, usuarios), del API Gateway (rutas + autorizador) y de un `curl` con token que devuelve `200`, y sin token que devuelve `401`.

---

## 5. Tarea 5 · Integrar Cognito con React y probar el login

### 5.1 Configuración

```bash
cd frontend
cp .env.example .env
```

Edita `.env`:

```env
VITE_COGNITO_AUTHORITY=https://pedidos360-auth-<sufijo>.auth.<region>.amazoncognito.com
VITE_COGNITO_CLIENT_ID=<CLIENT_ID>
VITE_COGNITO_REDIRECT_URI=http://localhost:5173
VITE_API_URL=http://<IP_PUBLICA>
```

`src/authConfig.ts` configura el flujo **Authorization Code + PKCE** con `react-oidc-context`:

```ts
export const authConfig: AuthProviderProps = {
  authority: import.meta.env.VITE_COGNITO_AUTHORITY,
  client_id: import.meta.env.VITE_COGNITO_CLIENT_ID,
  redirect_uri: import.meta.env.VITE_COGNITO_REDIRECT_URI || window.location.origin,
  response_type: 'code',
  scope: 'openid email profile',
  automaticSilentRenew: true,
  loadUserInfo: true,
  onSigninCallback: () => { window.history.replaceState({}, document.title, window.location.pathname); },
};
```

### 5.2 Cliente del API con Bearer token

`src/api.ts` añade el header `Authorization: Bearer <access_token>` a cada llamada:

```ts
const res = await fetch(`${API_URL}${path}`, {
  ...options,
  headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}`, ...options.headers },
});
```

### 5.3 Ejecutar y probar el login

```bash
npm install
npm run dev
```

Abre `http://localhost:5173` → botón **Iniciar sesión con Cognito** → serás redirigido al Hosted UI → ingresa `admin@pedidos360.cl` / `Admin1234` → vuelves a la app autenticado.

La app muestra: email, grupos (`ADMIN`), token truncado, formulario de creación de pedidos y tabla con acciones (Cancelar / Eliminar). El rol determina qué acciones funcionan:

- `ADMIN`: crear, cancelar y eliminar.
- `COLLABORATOR`: crear y cancelar (el DELETE devuelve 403).
- `CUSTOMER`: solo lectura (POST devuelve 403).

**Evidencia:** captura del Hosted UI de Cognito, de la app ya autenticada mostrando el grupo, y de la creación de un pedido que aparece en la tabla.

### 5.4 Build de producción

```bash
npm run build
# genera dist/ listo para nginx o para la imagen Docker (frontend/Dockerfile)
```

---

## 6. Rúbrica de RabbitMQ · implementación de los 8 indicadores

### 6.1 (12%) Nombres centralizados de colas, exchanges y bindings

Todos los nombres viven en `backend/src/main/resources/application.yml` bajo `app.rabbit`, y se leen con `@ConfigurationProperties`:

```yaml
app:
  rabbit:
    direct-exchange: pedidos.direct
    topic-exchange: pedidos.topic
    dlx-exchange: pedidos.dlx
    queues:
      notificaciones: notificaciones.queue
      tickets: tickets.queue
      documentos: documentos.queue
    dlqs:
      notificaciones: notificaciones.dlq
      tickets: tickets.dlq
      documentos: documentos.dlq
    routing-keys:
      pedido-creado: pedido.creado
      pedido-creado-ticket: pedido.creado.ticket
      pedido-creado-documento: pedido.creado.documento
      pedido-actualizado-ticket: pedido.actualizado.ticket
      pedido-cancelado-documento: pedido.cancelado.documento
      notificaciones-dlq: notificaciones.dlq
      tickets-dlq: tickets.dlq
      documentos-dlq: documentos.dlq
```

**Evidencia:** `application.yml` + `RabbitProperties.java`. Ningún nombre está hardcodeado en los consumidores ni en el publisher.

### 6.2 (13%) Beans de configuración Queue / Exchange / Binding por caso de uso

`config/RabbitMQConfig.java` declara explícitamente:

- Exchanges: `pedidosDirectExchange()`, `pedidosTopicExchange()`, `pedidosDlxExchange()`.
- Colas: `notificacionesQueue()`, `ticketsQueue()`, `documentosQueue()` (cada una con `x-dead-letter-exchange` y `x-dead-letter-routing-key`).
- DLQ: `notificacionesDlq()`, `ticketsDlq()`, `documentosDlq()`.
- Bindings: notificaciones → direct (`pedido.creado`); tickets → topic (`pedido.*.ticket`); documentos → topic (`pedido.*.documento`); cada DLQ → DLX con su routing key.

**Evidencia:** el archivo completo `RabbitMQConfig.java`.

### 6.3 (10%) Separación de lógica de negocio y configuración de mensajería

- `service/PedidoService.java` contiene **solo** reglas de negocio (crear, actualizar, cancelar, eliminar) y llama a `PedidoEventPublisher`.
- `messaging/PedidoEventPublisher.java` encapsula el envío a RabbitMQ.
- `config/RabbitMQConfig.java` solo declara topología.
- Los consumidores (`messaging/*Consumer.java`) solo procesan mensajes.

**Evidencia:** muestra que `PedidoService` no importa `AmqpTemplate` ni conoce exchanges.

### 6.4 (15%) Consumidores `@RabbitListener` agrupados por dominio funcional

| Dominio | Clase | Cola |
|---|---|---|
| Notificaciones | `NotificacionConsumer` | `notificaciones.queue` |
| Tickets | `TicketConsumer` | `tickets.queue` |
| Documentos | `DocumentoConsumer` | `documentos.queue` |

Cada uno usa `@RabbitListener(queues = "${app.rabbit.queues.<dominio>}")`.

### 6.5 (20%) ACK/NACK y manejo explícito de errores (retry, NACK, DLQ)

Patrón implementado en los tres consumidores:

```java
@RabbitListener(queues = "${app.rabbit.queues.tickets}")
public void consumir(Pedido pedido, Channel channel, Message message) throws IOException {
    long tag = message.getMessageProperties().getDeliveryTag();
    try {
        if (pedido.getCantidad() == null || pedido.getCantidad() > 100) {
            throw new IllegalArgumentException("Cantidad fuera de rango para ticket");
        }
        // ... lógica de negocio
        channel.basicAck(tag, false);
    } catch (Exception ex) {
        log.error("[TICKETS] Error procesando pedido {}: {}", pedido.getId(), ex.getMessage());
        channel.basicNack(tag, false, false); // requeue=false → va a la DLQ
    }
}
```

Además, en `application.yml`:

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        acknowledge-mode: manual
        default-requeue-rejected: false
        retry:
          enabled: true
          max-attempts: 3
          initial-interval: 1000ms
          multiplier: 2.0
```

Flujo de error: excepción → `basicNack(requeue=false)` → exchange `pedidos.dlx` → cola `<dominio>.dlq`. Los reintentos con backoff los gestiona el contenedor de listeners antes del NACK final.

**Cómo demostrarlo:** crea un pedido con `cantidad: 150`; `TicketConsumer` lanza excepción y el mensaje aparece en `tickets.dlq` (visible en la consola de RabbitMQ).

### 6.6 (13%) Microservicio de administración con endpoints REST

`web/RabbitAdminController.java`, protegido con `@PreAuthorize("hasRole('ADMIN')")`:

| Método | Ruta | Acción |
|---|---|---|
| GET | `/api/admin/queues` | Lista colas y DLQ |
| POST | `/api/admin/queues` | Crea cola (+ DLQ asociada) |
| DELETE | `/api/admin/queues/{name}` | Elimina cola y su DLQ |
| GET | `/api/admin/exchanges` | Lista exchanges |
| POST | `/api/admin/exchanges` | Crea exchange (direct/topic/fanout) |

Ejemplo:

```bash
curl -X POST http://localhost:8080/api/admin/queues \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"auditoria.queue","durable":true,"dlq":"auditoria.dlq"}'
```

### 6.7 (10%) Lógica de creación encapsulada en un servicio dedicado

`service/RabbitAdminService.java` usa `AmqpAdmin` y `RabbitProperties` para:

- Derivar el nombre de la DLQ (`nombre + ".dlq"` si no se indica).
- Declarar la cola durable con argumentos de DLX.
- Declarar y bindear la DLQ al exchange `pedidos.dlx`.
- Crear exchanges según tipo (`DirectExchange`, `TopicExchange`, `FanoutExchange`).
- Eliminar cola + DLQ de forma consistente.

El controller solo delega; **toda** la lógica de topología está en el servicio.

### 6.8 (7%) Validación de parámetros de entrada

`dto/QueueRequest.java` y `dto/ExchangeRequest.java` usan Bean Validation:

```java
public record QueueRequest(
    @NotBlank(message = "El nombre de la cola es obligatorio")
    @Pattern(regexp = "^[a-zA-Z0-9._-]{1,64}$", message = "Nombre de cola inválido")
    String name,
    Boolean durable,
    String dlq) {}
```

`GlobalExceptionHandler` transforma los errores en `400 Bad Request` con el detalle por campo. Los controllers usan `@Valid`.

**Evidencia:** `curl` con `{"name":""}` devuelve 400 con el mensaje de validación.

### 6.9 Clúster de 2 nodos + 3 colas con DLQ + exchanges direct/topic + docker-compose

`infra/docker-compose.yml` levanta:

- `rabbitmq-1` (semilla) y `rabbitmq-2` (se une con `rabbitmqctl join_cluster rabbit@rabbitmq-1`).
- Cookie Erlang compartida (`RABBITMQ_ERLANG_COOKIE`) para que puedan formar clúster.
- `infra/rabbitmq/definitions.json` importa automáticamente **3 colas + 3 DLQ + 3 exchanges + 6 bindings**.
- Consolas en `:15672` y `:15673`.

Verificación:

```bash
cd infra
docker compose up -d rabbitmq-1 rabbitmq-2
docker exec rabbitmq-1 rabbitmqctl cluster_status
docker exec rabbitmq-1 rabbitmqctl list_queues name messages consumers
docker exec rabbitmq-1 rabbitmqctl list_exchanges name type
docker exec rabbitmq-1 rabbitmqctl list_bindings
```

Debes ver `rabbit@rabbitmq-1` y `rabbit@rabbitmq-2` en el clúster, las 6 colas y los 3 exchanges.

---

## 7. Checklist de evidencias para la presentación

1. `ssh` exitoso a la EC2 (hostname + OS).
2. `mvn clean verify` con `BUILD SUCCESS`.
3. Repositorio en GitHub con el código.
4. Run de GitHub Actions con los 3 jobs en verde.
5. `docker ps` en la EC2 con el contenedor `pedidos360-backend`.
6. `curl` al `/api/health` (200) y a un endpoint protegido sin token (401).
7. Cognito: User Pool, 3 grupos, 3 usuarios.
8. API Gateway: rutas + autorizador JWT.
9. Frontend: Hosted UI + app autenticada con grupo visible + pedido creado.
10. RabbitMQ: `cluster_status` con 2 nodos, `list_queues` con 6 colas, `list_exchanges` con direct/topic/dlx, y un mensaje en `tickets.dlq` tras forzar el error.

---

## 8. Solución de problemas frecuentes

| Síntoma | Causa probable | Solución |
|---|---|---|
| `401 Unauthorized` en el API | Token expirado o issuer/audience no coincide | Revisa `COGNITO_ISSUER_URI` y el `Audience` del autorizador; vuelve a iniciar sesión |
| `403 Forbidden` al crear/eliminar | El usuario no tiene el grupo requerido | Asigna `ADMIN` o `COLLABORATOR` en Cognito |
| CORS bloqueado en el navegador | `CORS_ALLOWED_ORIGINS` no incluye el origen | Ajusta la variable y redepliega |
| El nodo 2 no se une al clúster | Cookie Erlang distinta o DNS | Verifica `RABBITMQ_ERLANG_COOKIE` y que `rabbitmq-1` esté healthy |
| Mensajes no llegan a la DLQ | `default-requeue-rejected: true` | Debe ser `false`; el NACK usa `requeue=false` |
| `docker pull` falla en EC2 | Paquete privado sin PAT | Ejecuta `docker login ghcr.io` con el `GHCR_PAT` |
| Frontend no compila | Falta `.env` | Copia `.env.example` y rellena las variables `VITE_*` |
