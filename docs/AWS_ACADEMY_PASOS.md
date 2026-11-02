# Guía para AWS Academy Learner Lab · Pedidos360 (DSY1107)

> ⚠️ **Nota de vigencia (2026-11-02):** este documento describe el procedimiento general.
> El despliegue que quedó **realmente funcionando** usa `https://98.93.243.92:8443` para el frontend
> (Cognito exige HTTPS), el App Client `261rvdvemjgib6n86vrb05tl9k` y el API Gateway `bf0plxi897`.
> Consulta **[`DESPLIEGUE_REAL.md`](DESPLIEGUE_REAL.md)** para los valores exactos y las diferencias.



Esta guía adapta el proyecto a **AWS Academy Learner Lab (Student)**. El laboratorio **no es una cuenta AWS normal**: tiene credenciales temporales, un rol precreado y varias restricciones. Aquí está exactamente qué cambia, qué datos se necesitan y dónde encontrarlos.

---

## 1. Diferencias clave con una cuenta AWS normal

| Tema | Cuenta AWS normal | AWS Academy Learner Lab |
|---|---|---|
| Credenciales | Usuario IAM con access key permanente | **Credenciales temporales** (access key + secret + **session token**) que caducan al terminar la sesión (≈4 h) |
| IAM | Puedes crear usuarios, roles, políticas | **No puedes crear IAM**. Existe el rol precreado `LabRole` y el perfil `LabInstanceProfile` |
| Key pair | Creas el tuyo con `create-key-pair` | Ya existe **`vockey`**; el `.pem` se descarga como **`labsuser.pem`** |
| Región | La que elijas | Normalmente **`us-east-1`** (N. Virginia); no cambiarla |
| Facturación | Consola de billing | **No hay billing**; hay un presupuesto de laboratorio y un límite de vCPU |
| Consola | Completa | Consola de AWS real, pero con permisos limitados por `LabRole` |
| Sesión | Permanente | Al cerrar la sesión **los recursos siguen**, pero las credenciales cambian |

> Consecuencia práctica: **no se puede usar GitHub Actions con OIDC hacia AWS** (requiere crear un rol IAM). Por eso el pipeline despliega por **SSH**, que sí funciona en el laboratorio. El diseño del proyecto ya contempla esto.

---

## 2. Datos que se necesitan y dónde encontrarlos

### 2.1 Credenciales de AWS Academy

1. Entra al portal de AWS Academy → tu curso → **Modules** → **Learner Lab**.
2. Pulsa **Start Lab** y espera a que el círculo junto a “AWS” se ponga **verde**.
3. Pulsa **AWS Details** → sección **AWS CLI** → **Show**.
4. Copia los tres valores:

```ini
[default]
aws_access_key_id=ASIA...
aws_secret_access_key=...
aws_session_token=...        # <- imprescindible, caduca con la sesión
```

5. En la misma ventana, sección **SSH key** → **Download PEM** → obtienes **`labsuser.pem`** (corresponde al key pair `vockey`).

**Región:** se ve arriba a la derecha de la consola AWS. Usa esa misma en todos los comandos.

### 2.2 Datos de GitHub

| Dato | Dónde obtenerlo |
|---|---|
| Usuario/organización | Tu perfil de GitHub |
| Nombre del repositorio | Lo eliges (p. ej. `pedidos360`) |
| **Personal Access Token (PAT)** | GitHub → Settings → Developer settings → **Personal access tokens** → *Fine-grained tokens* → **Generate new token** |

Permisos del PAT (fine-grained, sobre **un solo repositorio**):

- **Contents:** Read and write
- **Workflows:** Read and write ← *obligatorio*, si falta el push de `.github/workflows/deploy.yml` se rechaza
- **Metadata:** Read (se marca solo)
- **Administration:** Read and write (solo si quieres que se cree el repo automáticamente)

Alternativa con token clásico: marcar los scopes **`repo`** y **`workflow`**.

> Caduca cuando quieras: ponle 7 días y **revócalo** al terminar. Nunca lo dejes en un repositorio.

### 2.3 Datos que se generan solos (no los tienes que buscar)

Estos los producen los scripts y quedan en `infra/academy/.env.generated`:

`INSTANCE_ID`, `PUBLIC_IP`, `SECURITY_GROUP_ID`, `COGNITO_POOL_ID`, `COGNITO_CLIENT_ID`, `COGNITO_ISSUER_URI`, `COGNITO_AUTHORITY`, `API_ID`, `API_URL`.

---

## 3. Preparación del entorno local

```bash
# Credenciales (pégalas en tu terminal, NO en un archivo versionado)
export AWS_ACCESS_KEY_ID="ASIA..."
export AWS_SECRET_ACCESS_KEY="..."
export AWS_SESSION_TOKEN="..."
export AWS_DEFAULT_REGION="us-east-1"

# Verificación
bash infra/academy/00-check.sh
```

Debe mostrar tu identidad (`arn:aws:sts::...:assumed-role/voclabs/...`) y el key pair `vockey`.

> Si `00-check.sh` falla con `ExpiredToken`, vuelve a **Start Lab** y copia credenciales nuevas. Esto pasa cada ~4 horas.

---

## 4. Paso a paso completo

### Paso 1 · Instancia EC2

```bash
bash infra/academy/01-ec2.sh
```

Crea el Security Group (22, 80, 8080, 5672, 15672, 15673), lanza una **Amazon Linux 2023 `t3.micro`** con `LabInstanceProfile` y un user-data que instala Docker + Compose.

Al terminar imprime la IP y guarda los valores. Espera ~2 minutos y comprueba el acceso SSH:

```bash
chmod 400 labsuser.pem
ssh -i labsuser.pem ec2-user@<PUBLIC_IP> "hostname && cat /etc/os-release | head -2 && docker --version"
```

**Evidencia 1:** captura del comando SSH con el hostname de la instancia.

### Paso 2 · Cognito

```bash
FRONTEND_URL="http://<PUBLIC_IP>:5173" bash infra/academy/02-cognito.sh
```

Crea el User Pool, los grupos `ADMIN` / `COLLABORATOR` / `CUSTOMER`, el App Client SPA (sin secret, code + PKCE), el dominio del Hosted UI y tres usuarios de prueba:

| Usuario | Contraseña | Grupo |
|---|---|---|
| `admin@pedidos360.cl` | `Admin1234` | ADMIN |
| `collab@pedidos360.cl` | `Collab1234` | COLLABORATOR |
| `cliente@pedidos360.cl` | `Cliente1234` | CUSTOMER |

**Evidencia 2:** consola de Cognito con el User Pool, los 3 grupos y los 3 usuarios.

### Paso 3 · API Gateway

```bash
bash infra/academy/03-apigateway.sh
```

Crea la HTTP API, la integración proxy hacia `http://<PUBLIC_IP>`, el autorizador **JWT** (issuer + audience del User Pool), la ruta pública `GET /api/health`, la ruta protegida `ANY /{proxy+}` y el stage `prod`.

**Evidencia 3:** consola de API Gateway con las rutas y el autorizador.

### Paso 4 · Repositorio en GitHub

```bash
cd evaluacion2
git init
git add .
git commit -m "feat: Pedidos360 - EC2, Spring Boot, CI/CD, Cognito, API Gateway y RabbitMQ"
git branch -M main
git remote add origin https://github.com/<USUARIO>/pedidos360.git
git push -u origin main
```

### Paso 5 · Secrets del repositorio

Con `gh` (o desde la web: Settings → Secrets and variables → Actions):

```bash
gh secret set EC2_HOST            --body "<PUBLIC_IP>"
gh secret set EC2_USER            --body "ec2-user"
gh secret set EC2_SSH_KEY         --body "$(cat labsuser.pem)"
gh secret set COGNITO_ISSUER_URI  --body "<COGNITO_ISSUER_URI>"
gh secret set CORS_ALLOWED_ORIGINS --body "http://<PUBLIC_IP>:5173"
gh secret set RABBITMQ_HOST       --body "<PUBLIC_IP>"
gh secret set RABBITMQ_USER       --body "pedidos"
gh secret set RABBITMQ_PASSWORD   --body "pedidos123"
```

`GHCR_PAT` solo es necesario si el paquete de GHCR queda **privado**. Recomendación para el laboratorio: deja el paquete **público** (Packages → pedidos360-backend → Package settings → Change visibility) y así la EC2 puede hacer `docker pull` sin token.

### Paso 6 · Desplegar

```bash
git commit --allow-empty -m "ci: disparar pipeline"
git push
```

En **Actions** verás: `Build & Test` → `Docker → GHCR` → `Deploy en EC2`. Al terminar:

```bash
curl http://<PUBLIC_IP>/api/health
```

### Paso 7 · RabbitMQ en clúster (en la EC2)

```bash
ssh -i labsuser.pem ec2-user@<PUBLIC_IP>
git clone https://github.com/<USUARIO>/pedidos360.git
cd pedidos360/infra
docker compose up -d rabbitmq-1 rabbitmq-2
docker exec rabbitmq-1 rabbitmqctl cluster_status
docker exec rabbitmq-1 rabbitmqctl list_queues name messages
docker exec rabbitmq-1 rabbitmqctl list_exchanges name type
```

Consolas: `http://<PUBLIC_IP>:15672` y `:15673` (usuario `pedidos` / `pedidos123`).

**Evidencia 4:** `cluster_status` con los dos nodos y `list_queues` con las 6 colas.

### Paso 8 · Frontend

**Opción A — local (la más simple para la demo):**

```bash
cd frontend
cp .env.example .env
# editar:
#   VITE_COGNITO_AUTHORITY=<COGNITO_AUTHORITY>
#   VITE_COGNITO_CLIENT_ID=<COGNITO_CLIENT_ID>
#   VITE_COGNITO_REDIRECT_URI=http://localhost:5173
#   VITE_API_URL=<API_URL>
npm install && npm run dev
```

Abre `http://localhost:5173` → **Iniciar sesión con Cognito** → `admin@pedidos360.cl` / `Admin1234`.

**Opción B — en la EC2 con Docker:**

```bash
cd frontend
docker build \
  --build-arg VITE_COGNITO_AUTHORITY=<COGNITO_AUTHORITY> \
  --build-arg VITE_COGNITO_CLIENT_ID=<COGNITO_CLIENT_ID> \
  --build-arg VITE_COGNITO_REDIRECT_URI=http://<PUBLIC_IP>:5173 \
  --build-arg VITE_API_URL=<API_URL> \
  -t pedidos360-frontend .
docker run -d --name pedidos360-frontend -p 5173:80 pedidos360-frontend
```

> Si usas la Opción B, vuelve a ejecutar `02-cognito.sh` con `FRONTEND_URL=http://<PUBLIC_IP>:5173` o actualiza el App Client para incluir esa callback URL.

**Evidencia 5:** Hosted UI de Cognito + app autenticada mostrando el grupo `ADMIN` + un pedido creado en la tabla.

### Paso 9 · Verificación integral

```bash
bash infra/academy/04-verify.sh
```

---

## 5. Demostrar la DLQ (indicador del 20 %)

1. Inicia sesión como `admin@pedidos360.cl`.
2. Crea un pedido con **cantidad 150** (el `TicketConsumer` rechaza cantidades > 100).
3. Consulta la DLQ:

```bash
docker exec rabbitmq-1 rabbitmqctl list_queues name messages | grep tickets.dlq
```

Debe aparecer `tickets.dlq 1`. Eso demuestra ACK/NACK + DLX + DLQ funcionando de punta a punta.

---

## 6. Limitaciones del Learner Lab que debes conocer

| Limitación | Impacto | Mitigación |
|---|---|---|
| Credenciales caducan (~4 h) | Los scripts fallan con `ExpiredToken` | Reinicia el lab y vuelve a exportar las variables |
| No se pueden crear roles IAM | No hay OIDC de GitHub Actions a AWS | El deploy va por SSH (ya implementado) |
| Límite de vCPU | No lances muchas instancias | Una sola `t3.micro` es suficiente |
| Presupuesto del lab | Consumo innecesario lo agota | Detén la instancia al terminar: `aws ec2 stop-instances --instance-ids <ID>` |
| Recursos al cerrar el curso | Se eliminan | Descarga evidencias (capturas) antes |
| Región fija | Recursos en otra región no se ven | Usa siempre la región que indica la consola |

**Al terminar la sesión de trabajo**, para no gastar presupuesto:

```bash
aws ec2 stop-instances --region us-east-1 --instance-ids $(grep INSTANCE_ID infra/academy/.env.generated | cut -d= -f2)
```

---

## 7. Checklist final de evidencias

- [ ] `ssh` a la EC2 con hostname y versión del SO.
- [ ] `mvn clean verify` → BUILD SUCCESS.
- [ ] Repositorio en GitHub con el código.
- [ ] GitHub Actions con los 3 jobs en verde.
- [ ] `docker ps` en la EC2 con `pedidos360-backend`.
- [ ] `curl` a `/api/health` → 200 y a `/api/pedidos` sin token → 401.
- [ ] Cognito: User Pool + 3 grupos + 3 usuarios.
- [ ] API Gateway: rutas + autorizador JWT.
- [ ] Frontend autenticado con grupo visible y pedido creado.
- [ ] RabbitMQ: `cluster_status` con 2 nodos, 6 colas, exchanges direct/topic/dlx.
- [ ] Mensaje en `tickets.dlq` tras forzar el error.
