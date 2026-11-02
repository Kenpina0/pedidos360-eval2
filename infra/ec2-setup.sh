#!/usr/bin/env bash
# =============================================================================
# ec2-setup.sh · Bootstrap de una instancia Amazon Linux 2023 / 2 para Pedidos360
# -----------------------------------------------------------------------------
# Uso (como user-data o manual con sudo):
#   sudo bash ec2-setup.sh
#
# Instala Docker + Compose plugin, lo habilita y deja el usuario ec2-user en el
# grupo docker. NO contiene secretos: las credenciales se inyectan por variables
# de entorno o por GitHub Secrets en el workflow de despliegue.
# =============================================================================
set -euo pipefail

echo "==> [1/6] Actualizando paquetes del sistema"
dnf update -y

echo "==> [2/6] Instalando utilidades base"
dnf install -y git curl wget unzip tar

echo "==> [3/6] Instalando Docker Engine"
dnf install -y docker
systemctl enable --now docker

echo "==> [4/6] Instalando Docker Compose (plugin v2)"
mkdir -p /usr/local/lib/docker/cli-plugins
ARCH="$(uname -m)"
case "$ARCH" in
  x86_64)  COMPOSE_ARCH="x86_64" ;;
  aarch64) COMPOSE_ARCH="aarch64" ;;
  *) echo "Arquitectura no soportada: $ARCH"; exit 1 ;;
esac
curl -fsSL \
  "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-${COMPOSE_ARCH}" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

echo "==> [5/6] Permisos para el usuario ec2-user"
usermod -aG docker ec2-user || true

echo "==> [6/6] Verificación"
docker --version
docker compose version

cat <<'EOF'

===============================================================================
  Docker instalado correctamente en la instancia EC2.

  Siguientes pasos:
    1. Clonar el repositorio:
         git clone https://github.com/<TU_USUARIO>/pedidos360.git
         cd pedidos360/infra
    2. Levantar el clúster RabbitMQ de 2 nodos:
         docker compose up -d rabbitmq-1 rabbitmq-2
    3. Verificar el clúster:
         docker exec rabbitmq-1 rabbitmqctl cluster_status
    4. Abrir en el Security Group los puertos:
         22 (SSH), 80 (HTTP), 8080 (backend), 15672/15673 (management)
    5. Desplegar el backend desde GitHub Actions (push a main) o manualmente:
         docker run -d --name pedidos360-backend -p 80:8080 \
           -e COGNITO_ISSUER_URI=... ghcr.io/<owner>/pedidos360-backend:latest
===============================================================================
EOF
