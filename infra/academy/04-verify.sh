#!/usr/bin/env bash
# =============================================================================
# 04-verify.sh · Verificación integral (evidencias para la evaluación)
# -----------------------------------------------------------------------------
# Uso: bash 04-verify.sh
# =============================================================================
set -uo pipefail

REGION="${AWS_DEFAULT_REGION:-us-east-1}"
HERE="$(dirname "$0")"
OUT_FILE="$HERE/.env.generated"
# shellcheck disable=SC1090
[[ -f "$OUT_FILE" ]] && source "$OUT_FILE"

line() { printf '%s\n' "--------------------------------------------------------------"; }

line; echo "1) INSTANCIA EC2"
aws ec2 describe-instances --region "$REGION" \
  --filters "Name=tag:Name,Values=pedidos360-ec2" \
  --query 'Reservations[].Instances[].{ID:InstanceId,Estado:State.Name,IP:PublicIpAddress,Tipo:InstanceType}' \
  --output table 2>/dev/null

line; echo "2) COGNITO · User Pool y grupos"
aws cognito-idp describe-user-pool --region "$REGION" \
  --user-pool-id "${COGNITO_POOL_ID:-}" \
  --query 'UserPool.{Nombre:Name,Id:Id,Usuarios:EstimatedNumberOfUsers}' --output table 2>/dev/null
aws cognito-idp list-groups --region "$REGION" \
  --user-pool-id "${COGNITO_POOL_ID:-}" \
  --query 'Groups[].{Grupo:GroupName}' --output table 2>/dev/null
aws cognito-idp list-users --region "$REGION" \
  --user-pool-id "${COGNITO_POOL_ID:-}" \
  --query 'Users[].{Usuario:Username,Estado:UserStatus}' --output table 2>/dev/null

line; echo "3) API GATEWAY · rutas"
aws apigatewayv2 get-routes --region "$REGION" --api-id "${API_ID:-}" \
  --query 'Items[].{Ruta:RouteKey,Autorizacion:AuthorizationType}' --output table 2>/dev/null

line; echo "4) HEALTH público (debe ser 200)"
curl -s -m 10 -o /tmp/h.json -w "HTTP %{http_code}\n" "${API_URL:-}/api/health"
cat /tmp/h.json 2>/dev/null; echo

line; echo "5) ENDPOINT PROTEGIDO sin token (debe ser 401)"
curl -s -m 10 -o /dev/null -w "HTTP %{http_code}\n" "${API_URL:-}/api/pedidos"

line; echo "6) BACKEND directo en EC2 (si el SG lo permite)"
curl -s -m 10 -o /dev/null -w "HTTP %{http_code}\n" "http://${PUBLIC_IP:-}:8080/api/health"

line; echo "7) RABBITMQ (ejecutar en la EC2)"
cat <<EOF
  ssh -i labsuser.pem ec2-user@${PUBLIC_IP:-IP}
  cd pedidos360/infra && docker compose up -d rabbitmq-1 rabbitmq-2
  docker exec rabbitmq-1 rabbitmqctl cluster_status
  docker exec rabbitmq-1 rabbitmqctl list_queues name messages
  docker exec rabbitmq-1 rabbitmqctl list_exchanges name type
EOF

line; echo "8) VALORES GENERADOS"
[[ -f "$OUT_FILE" ]] && cat "$OUT_FILE"
line
