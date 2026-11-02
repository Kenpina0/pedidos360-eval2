#!/usr/bin/env bash
# =============================================================================
# 01-ec2.sh · Crea Security Group + instancia EC2 en AWS Academy Learner Lab
# -----------------------------------------------------------------------------
# Requisitos:
#   - Credenciales del Learner Lab exportadas (ver 00-check.sh)
#   - Key pair: usa 'vockey' (el que entrega el lab) salvo que definas KEY_NAME
#
# Uso:
#   bash 01-ec2.sh
#   MY_IP=1.2.3.4/32 bash 01-ec2.sh     # restringe SSH a tu IP
#
# Salidas: guarda infra/academy/.env.generated con los valores creados.
# =============================================================================
set -euo pipefail

REGION="${AWS_DEFAULT_REGION:-us-east-1}"
KEY_NAME="${KEY_NAME:-vockey}"
INSTANCE_TYPE="${INSTANCE_TYPE:-t3.micro}"
SG_NAME="${SG_NAME:-pedidos360-sg}"
OUT_FILE="$(dirname "$0")/.env.generated"

echo "==> Región: $REGION | Key pair: $KEY_NAME | Tipo: $INSTANCE_TYPE"

# ---------------------------------------------------------------------------
# 1) AMI más reciente de Amazon Linux 2023 (vía parámetro público de SSM)
# ---------------------------------------------------------------------------
AMI_ID=$(aws ssm get-parameter \
  --region "$REGION" \
  --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64 \
  --query 'Parameter.Value' --output text)
echo "==> AMI: $AMI_ID"

# ---------------------------------------------------------------------------
# 2) Security Group (reutiliza si ya existe)
# ---------------------------------------------------------------------------
SG_ID=$(aws ec2 describe-security-groups --region "$REGION" \
  --filters "Name=group-name,Values=$SG_NAME" \
  --query 'SecurityGroups[0].GroupId' --output text 2>/dev/null || echo "None")

if [[ "$SG_ID" == "None" || -z "$SG_ID" ]]; then
  SG_ID=$(aws ec2 create-security-group --region "$REGION" \
    --group-name "$SG_NAME" \
    --description "Pedidos360: SSH, HTTP, backend y RabbitMQ" \
    --query 'GroupId' --output text)
  echo "==> Security Group creado: $SG_ID"

  SSH_CIDR="${MY_IP:-0.0.0.0/0}"
  for P in 22 80 8080 15672 15673 5672; do
    aws ec2 authorize-security-group-ingress --region "$REGION" \
      --group-id "$SG_ID" --protocol tcp --port "$P" --cidr "$SSH_CIDR" >/dev/null
    echo "    puerto $P abierto a $SSH_CIDR"
  done
else
  echo "==> Reutilizando Security Group existente: $SG_ID"
fi

# ---------------------------------------------------------------------------
# 3) User data: instala Docker + Compose (Amazon Linux 2023)
# ---------------------------------------------------------------------------
USER_DATA=$(cat <<'EOF'
#!/bin/bash
set -euxo pipefail
dnf update -y
dnf install -y docker git curl
systemctl enable --now docker
usermod -aG docker ec2-user
mkdir -p /usr/local/lib/docker/cli-plugins
curl -fsSL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
docker --version
docker compose version
EOF
)
echo "$USER_DATA" > /tmp/user-data.sh

# ---------------------------------------------------------------------------
# 4) Lanzar la instancia (con LabInstanceProfile si existe)
# ---------------------------------------------------------------------------
run_instance() {
  aws ec2 run-instances --region "$REGION" \
    --image-id "$AMI_ID" \
    --instance-type "$INSTANCE_TYPE" \
    --key-name "$KEY_NAME" \
    --security-group-ids "$SG_ID" \
    --user-data file:///tmp/user-data.sh \
    --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=pedidos360-ec2},{Key=Proyecto,Value=DSY1107-Eval2}]' \
    "$@" \
    --query 'Instances[0].InstanceId' --output text
}

echo "==> Lanzando instancia..."
if INSTANCE_ID=$(run_instance --iam-instance-profile Name=LabInstanceProfile 2>/dev/null); then
  echo "    con perfil LabInstanceProfile"
else
  echo "    AVISO: LabInstanceProfile no disponible; se lanza sin perfil (no afecta a la app)."
  INSTANCE_ID=$(run_instance)
fi

aws ec2 wait instance-running --region "$REGION" --instance-ids "$INSTANCE_ID"
aws ec2 wait instance-status-ok --region "$REGION" --instance-ids "$INSTANCE_ID" || true

PUBLIC_IP=$(aws ec2 describe-instances --region "$REGION" --instance-ids "$INSTANCE_ID" \
  --query 'Reservations[0].Instances[0].PublicIpAddress' --output text)
PUBLIC_DNS=$(aws ec2 describe-instances --region "$REGION" --instance-ids "$INSTANCE_ID" \
  --query 'Reservations[0].Instances[0].PublicDnsName' --output text)

cat > "$OUT_FILE" <<EOF
AWS_REGION=$REGION
INSTANCE_ID=$INSTANCE_ID
PUBLIC_IP=$PUBLIC_IP
PUBLIC_DNS=$PUBLIC_DNS
SECURITY_GROUP_ID=$SG_ID
KEY_NAME=$KEY_NAME
SSH_USER=ec2-user
EOF

echo
echo "=============================================================="
echo " Instancia lista"
echo "   INSTANCE_ID = $INSTANCE_ID"
echo "   PUBLIC_IP   = $PUBLIC_IP"
echo "   SSH         = ssh -i labsuser.pem ec2-user@$PUBLIC_IP"
echo "   Valores guardados en: $OUT_FILE"
echo "=============================================================="
echo
echo "Espera ~2 minutos a que el user-data instale Docker y verifica:"
echo "  ssh -i labsuser.pem ec2-user@$PUBLIC_IP 'docker --version && docker compose version'"
