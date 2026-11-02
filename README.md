# Pedidos360 · Evaluación Parcial N°2 (DSY1107)

> ## ✅ Estado: desplegado y funcionando en AWS Academy
>
> | Qué | Dónde |
> |---|---|
> | Frontend (login Cognito) | **https://98.93.243.92:8443** (certificado autofirmado, aceptar advertencia) |
> | API Gateway | `https://bf0plxi897.execute-api.us-east-1.amazonaws.com/prod` |
> | Backend directo | `http://98.93.243.92:8080/api/health` |
> | RabbitMQ consolas | `http://98.93.243.92:15672` y `:15673` (`pedidos` / `pedidos123`) |
> | Instancia EC2 | `i-03a70f0a716b69333` · `t3.micro` · `us-east-1` |
> | Usuarios de prueba | `admin@pedidos360.cl`/`Admin1234` · `collab@pedidos360.cl`/`Collab1234` · `cliente@pedidos360.cl`/`Cliente1234` |
>
> **Documentos clave:** [`docs/DESPLIEGUE_REAL.md`](docs/DESPLIEGUE_REAL.md) (valores reales, cómo probarlo, cómo apagar la instancia) y
> [`docs/INFORME_EVIDENCIAS.md`](docs/INFORME_EVIDENCIAS.md) (pruebas ejecutadas con sus salidas).
>
> ⚠️ La instancia consume horas del laboratorio. Para detenerla:
> `aws ec2 stop-instances --region us-east-1 --instance-ids i-03a70f0a716b69333`

Proyecto integrador que cubre, en un solo repositorio, **las 5 tareas de los videos del curso** y **los 8 indicadores de la rúbrica de RabbitMQ** de la Evaluación Parcial N°2.

| Frente | Qué resuelve | Dónde está |
|---|---|---|
| Tarea 1 | Instancia EC2 + acceso SSH comprobado | `infra/ec2-setup.sh`, `docs/GUIA_EVALUACION_2.md` §1 |
| Tarea 2 | Proyecto Spring Boot, puerto adaptado, subido a GitHub | `backend/` |
| Tarea 3 | GitHub Actions → GHCR (Docker) → EC2 | `.github/workflows/deploy.yml` |
| Tarea 4 | Cognito (User Pool, Grupos) + API Gateway | `backend/.../config/SecurityConfig.java`, guía §4 |
| Tarea 5 | Cognito integrado con React y login probado | `frontend/` |
| Rúbrica | RabbitMQ: 3 colas + DLQ, direct/topic, ACK/NACK, admin REST, clúster 2 nodos | `backend/.../config`, `messaging`, `service`, `infra/` |

## Arquitectura

```
┌────────────────────┐        OAuth 2.0 / OIDC (PKCE)        ┌──────────────────┐
│  React + Vite      │ ────────────────────────────────────▶ │  AWS Cognito     │
│  (react-oidc-ctx)  │ ◀──────────── JWT (id/access) ─────── │  User Pool+Groups│
└─────────┬──────────┘                                        └──────────────────┘
          │ Bearer JWT
          ▼
┌────────────────────┐        valida firma/issuer/aud         ┌──────────────────┐
│  AWS API Gateway   │ ────────────────────────────────────▶ │  Spring Boot 3   │
│  (HTTP API + JWT)  │ ◀──────────────────────────────────── │  EC2 / Docker    │
└────────────────────┘                                        └────────┬─────────┘
                                                                       │ AMQP
                                                              ┌────────▼─────────┐
                                                              │ RabbitMQ clúster │
                                                              │ 2 nodos · 3 colas│
                                                              │ + 3 DLQ · DLX    │
                                                              └──────────────────┘
```

## Arranque rápido (local, sin AWS)

```bash
# 1) Backend sin seguridad ni RabbitMQ (perfil local)
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
# → http://localhost:8080/api/health

# 2) Frontend
cd ../frontend
cp .env.example .env      # rellenar con tu Cognito
npm install && npm run dev
# → http://localhost:5173
```

## Arranque completo con RabbitMQ (clúster 2 nodos)

```bash
cd infra
docker compose up -d rabbitmq-1 rabbitmq-2
docker exec rabbitmq-1 rabbitmqctl cluster_status
# Consolas: http://localhost:15672 y http://localhost:15673 (pedidos / pedidos123)
```

## Documentación

- [`docs/AWS_ACADEMY_PASOS.md`](docs/AWS_ACADEMY_PASOS.md) — **guía específica para AWS Academy Learner Lab** (credenciales temporales, `vockey`, `LabRole`, scripts automáticos).
- [`docs/GUIA_EVALUACION_2.md`](docs/GUIA_EVALUACION_2.md) — paso a paso de las 5 tareas + rúbrica, con comandos y evidencias.
- [`docs/RUBRICA_CHECKLIST.md`](docs/RUBRICA_CHECKLIST.md) — mapeo indicador → archivo/línea.
- [`docs/GUION_PRESENTACION.md`](docs/GUION_PRESENTACION.md) — guion de 5–10 minutos.

## Scripts para AWS Academy

```bash
export AWS_ACCESS_KEY_ID=... AWS_SECRET_ACCESS_KEY=... AWS_SESSION_TOKEN=... AWS_DEFAULT_REGION=us-east-1
bash infra/academy/00-check.sh        # valida credenciales del lab
bash infra/academy/01-ec2.sh          # SG + EC2 Amazon Linux 2023 + Docker
bash infra/academy/02-cognito.sh      # User Pool + grupos + App Client + usuarios
bash infra/academy/03-apigateway.sh   # HTTP API + JWT authorizer + rutas
bash infra/academy/04-verify.sh       # evidencias y verificación integral
```

## Estructura

```
evaluacion2/
├── backend/                 Spring Boot 3.3.5 · Java 17
│   ├── src/main/java/com/pedidos360/
│   │   ├── config/          RabbitProperties, RabbitMQConfig, SecurityConfig
│   │   ├── messaging/       Publisher + 3 consumers con ACK/NACK
│   │   ├── service/         PedidoService, RabbitAdminService
│   │   ├── web/             PedidoController, RabbitAdminController, HealthController
│   │   ├── dto/             PedidoRequest, QueueRequest, ExchangeRequest (validación)
│   │   └── exception/       GlobalExceptionHandler
│   ├── src/main/resources/  application.yml (nombres centralizados)
│   ├── Dockerfile
│   └── .github/workflows/deploy.yml
├── frontend/                React 18 + TS + Vite + react-oidc-context
├── infra/                   docker-compose (clúster RMQ), definitions.json, ec2-setup.sh
└── docs/                    guía, checklist, guion
```
