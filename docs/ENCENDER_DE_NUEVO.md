# Cómo volver a encender todo (AWS Academy)

> **Resumen:** la IP es fija (**32.200.126.178**, Elastic IP), así que apagar y encender
> **no rompe nada**: no hay que tocar API Gateway, Cognito ni certificados.
> Solo hay que arrancar la instancia y esperar ~2 minutos a que los contenedores levanten.

---

## 1. Antes de empezar: abrir el laboratorio

1. Entra a **AWS Academy** → curso **DSY1107** → **Modules** → **Launch AWS Academy Learner Lab**.
2. Pulsa **Start Lab** y espera a que el círculo junto a *AWS* se ponga **verde**.
   (Tarda entre 1 y 3 minutos.)
3. Si el círculo está verde pero llevas mucho rato, pulsa **End Lab** y **Start Lab** de nuevo
   para refrescar la sesión de 4 horas.

> El laboratorio **no se factura**: tiene un presupuesto fijo. Apagar la instancia cuando no la uses
> solo evita consumir ese presupuesto y las horas de la sesión.

---

## 2. Arrancar la instancia EC2

### Opción A — desde la consola (la más fácil)

1. En el panel del lab pulsa **AWS** (abre la consola con tu sesión ya federada).
2. Ve a **EC2** → **Instances**.
3. Selecciona la instancia **`pedidos360-eval2`** (ID `i-03a70f0a716b69333`).
4. **Instance state → Start instance**.
5. Espera a que *Instance state* diga **Running** y *Status checks* diga **2/2 passed**
   (unos 60–90 segundos).

### Opción B — por CLI (si tienes credenciales válidas)

```bash
aws ec2 start-instances --region us-east-1 --instance-ids i-03a70f0a716b69333
aws ec2 wait instance-running --region us-east-1 --instance-ids i-03a70f0a716b69333
```

Para obtener credenciales válidas: en el panel del lab, **AWS Details → AWS CLI → Show**,
y copia las **tres** líneas (`aws_access_key_id`, `aws_secret_access_key` y `aws_session_token`).

---

## 3. Esperar a que los contenedores levanten solos

Los cinco contenedores tienen `restart: unless-stopped`, así que **arrancan automáticamente**
cuando la instancia enciende. No hay que ejecutar nada a mano.

Dales **2 minutos** (el backend y RabbitMQ tardan en estar listos) y luego comprueba:

```bash
curl -s http://32.200.126.178:8080/api/health
# {"status":"UP","app":"pedidos360-backend",...}
```

Si quieres verlo por SSH:

```bash
ssh -i labsuser.pem ec2-user@32.200.126.178
docker ps
# deben aparecer: rabbitmq-1, rabbitmq-2, pedidos360-backend, pedidos360-frontend, pedidos360-frontend-tls
```

Si alguno quedó caído (pasa cuando el disco estaba lleno o hubo un OOM):

```bash
cd ~/pedidos360/infra
docker compose up -d
```

---

## 4. Verificar que todo funciona

| Prueba | Comando / acción | Resultado esperado |
|---|---|---|
| Backend directo | `curl http://32.200.126.178:8080/api/health` | `{"status":"UP",...}` |
| API Gateway | `curl https://bf0plxi897.execute-api.us-east-1.amazonaws.com/prod/api/health` | 200 |
| API sin token | `curl -o /dev/null -w "%{http_code}" https://bf0plxi897.execute-api.us-east-1.amazonaws.com/prod/api/pedidos` | `401` |
| Frontend | abrir `https://32.200.126.178:8443` | pantalla con botón *Iniciar sesión con Cognito* |
| RabbitMQ nodo 1 | `http://32.200.126.178:15672` (`pedidos` / `pedidos123`) | consola con 6 colas |
| RabbitMQ nodo 2 | `http://32.200.126.178:15673` (`pedidos` / `pedidos123`) | consola, 2 nodos en *Overview → Nodes* |

**Login:** `admin@pedidos360.cl` / `Admin1234` → debe aparecer el badge **ADMIN** y la tabla de pedidos.
Acepta la advertencia de certificado (*Avanzado → Continuar*): es normal, el certificado es autofirmado.

> ⚠️ **La base de datos es H2 en memoria.** Cada vez que el contenedor del backend se recrea
> (o la instancia se reinicia), los pedidos de prueba se borran. Es intencional: el proyecto no usa
> RDS porque no entra en la capa gratuita del lab. Vuelve a crear pedidos desde la interfaz cuando
> hagas la demostración.

---

## 5. Apagar de nuevo (recomendado al terminar)

```bash
aws ec2 stop-instances --region us-east-1 --instance-ids i-03a70f0a716b69333
```

O en la consola: **EC2 → Instances → `pedidos360-eval2` → Instance state → Stop instance**.

Detener **no borra nada**: el disco EBS, las imágenes Docker y los volúmenes de RabbitMQ se conservan.
Al volver a arrancar, todo sigue igual y la IP **no cambia** gracias a la Elastic IP.

---

## 6. Si algo no responde

### 6.1 El backend no arranca (memoria)

`t3.micro` tiene solo 913 MB de RAM. Comprueba el swap:

```bash
free -m
swapon --show     # debe mostrar /swapfile de 2G
```

Si no aparece, recréalo:

```bash
sudo dd if=/dev/zero of=/swapfile bs=1M count=2048
sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
```

### 6.2 El clúster de RabbitMQ quedó con un solo nodo

```bash
docker restart rabbitmq-2
sleep 30
docker exec rabbitmq-1 rabbitmqctl cluster_status
```

### 6.3 Cambió la IP (solo si alguien liberó la Elastic IP)

```bash
NEW_IP=$(aws ec2 describe-instances --region us-east-1 --instance-ids i-03a70f0a716b69333 \
  --query 'Reservations[0].Instances[0].PublicIpAddress' --output text)
echo $NEW_IP
```

Luego hay que actualizar tres cosas:

```bash
# 1) API Gateway
aws apigatewayv2 update-integration --api-id bf0plxi897 --integration-id t9u5x51 \
  --integration-uri "http://$NEW_IP:8080/api/health"
aws apigatewayv2 update-integration --api-id bf0plxi897 --integration-id xqrxlt0 \
  --integration-uri "http://$NEW_IP:8080/{proxy}"

# 2) Cognito
aws cognito-idp update-user-pool-client --user-pool-id us-east-1_9b2C1fxtb \
  --client-id 261rvdvemjgib6n86vrb05tl9k \
  --callback-urls "[\"https://$NEW_IP:8443\",\"http://localhost:5173\"]" \
  --logout-urls  "[\"https://$NEW_IP:8443\",\"http://localhost:5173\"]" \
  --allowed-o-auth-flows code --allowed-o-auth-scopes openid email profile \
  --allowed-o-auth-flows-user-pool-client --supported-identity-providers COGNITO \
  --explicit-auth-flows ALLOW_USER_PASSWORD_AUTH ALLOW_REFRESH_TOKEN_AUTH

# 3) Certificado TLS + rebuild del frontend (el redirect_uri va compilado)
ssh -i labsuser.pem ec2-user@$NEW_IP 'cd ~/pedidos360/infra/nginx-tls && \
  sudo openssl req -x509 -nodes -newkey rsa:2048 -days 365 \
    -keyout certs/privkey.pem -out certs/fullchain.pem \
    -subj "/C=CL/ST=RM/L=Santiago/O=Pedidos360/CN='$NEW_IP'" \
    -addext "subjectAltName=IP:'$NEW_IP',DNS:localhost"'
```

Y reconstruir el frontend con `VITE_COGNITO_REDIRECT_URI=https://$NEW_IP:8443`
(ver `DESPLIEGUE_REAL.md` §3). Por eso conviene **no liberar la Elastic IP**.

---

## 7. Credenciales y usuarios de prueba

| Usuario | Contraseña | Grupo | Puede |
|---|---|---|---|
| `admin@pedidos360.cl` | `Admin1234` | ADMIN | ver, crear, editar, cancelar, eliminar, `/api/admin/**` |
| `collab@pedidos360.cl` | `Collab1234` | COLLABORATOR | ver, crear, editar, cancelar |
| `cliente@pedidos360.cl` | `Cliente1234` | CUSTOMER | solo ver |

**RabbitMQ:** usuario `pedidos`, contraseña `pedidos123` (consolas 15672 y 15673).

---

## 8. Datos del despliegue (referencia rápida)

```
Instancia EC2 ........ i-03a70f0a716b69333  (t3.micro, Amazon Linux 2023, us-east-1)
Elastic IP ........... 32.200.126.178  (eipalloc-04b1dfc182682f987)
Security Group ....... sg-0e1a6c1707b3096a4
Usuario SSH .......... ec2-user  (clave: labsuser.pem)
Cognito User Pool .... us-east-1_9b2C1fxtb
Cognito App Client ... 261rvdvemjgib6n86vrb05tl9k
Cognito Domain ....... https://pedidos360-b2c1fxtb.auth.us-east-1.amazoncognito.com
API Gateway .......... bf0plxi897  → https://bf0plxi897.execute-api.us-east-1.amazonaws.com/prod
Repositorio .......... https://github.com/Kenpina0/pedidos360-eval2
```
