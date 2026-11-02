#!/usr/bin/env bash
# =============================================================================
# 03-apigateway.sh · HTTP API + integración a EC2 + autorizador JWT de Cognito
# -----------------------------------------------------------------------------
# Requisitos: haber ejecutado 01-ec2.sh y 02-cognito.sh
# Uso:
#   bash 03-apigateway.sh
#   BACKEND_URL=http://1.2.3.4 bash 03-apigateway.sh
# =============================================================================
set -euo pipefail

REGION="${AWS_DEFAULT_REGION:-us-east-1}"
HERE="$(dirname "$0")"
OUT_FILE="$HERE/.env.generated"

# shellcheck disable=SC1090
[[ -f "$OUT_FILE" ]] && source "$OUT_FILE"

BACKEND_URL="${BACKEND_URL:-http://${PUBLIC_IP:?Define PUBLIC_IP o ejecuta 01-ec2.sh}}"
POOL_ID="${COGNITO_POOL_ID:?Ejecuta 02-cognito.sh primero}"
CLIENT_ID="${COGNITO_CLIENT_ID:?Ejecuta 02-cognito.sh primero}"
ISSUER="${COGNITO_ISSUER_URI:-https://cognito-idp.$REGION.amazonaws.com/$POOL_ID}"

echo "==> Backend: $BACKEND_URL"
echo "==> Issuer : $ISSUER"

# ---------------------------------------------------------------------------
# 1) API HTTP con CORS
# ---------------------------------------------------------------------------
API_ID=$(aws apigatewayv2 create-api --region "$REGION" \
  --name pedidos360-api \
  --protocol-type HTTP \
  --cors-configuration 'AllowOrigins=*,AllowMethods=*,AllowHeaders=*' \
  --query 'ApiId' --output text)
echo "==> API: $API_ID"

# ---------------------------------------------------------------------------
# 2) Integración proxy al backend en EC2
# ---------------------------------------------------------------------------
INTEG_ID=$(aws apigatewayv2 create-integration --region "$REGION" \
  --api-id "$API_ID" \
  --integration-type HTTP_PROXY \
  --integration-uri "$BACKEND_URL" \
  --integration-method ANY \
  --payload-format-version 1.0 \
  --query 'IntegrationId' --output text)
echo "==> Integración: $INTEG_ID"

# ---------------------------------------------------------------------------
# 3) Autorizador JWT de Cognito
# ---------------------------------------------------------------------------
AUTH_ID=$(aws apigatewayv2 create-authorizer --region "$REGION" \
  --api-id "$API_ID" \
  --name cognito-authorizer \
  --authorizer-type JWT \
  --identity-source '$request.header.Authorization' \
  --jwt-configuration "Issuer=$ISSUER,Audience=$CLIENT_ID" \
  --query 'AuthorizerId' --output text)
echo "==> Autorizador: $AUTH_ID"

# ---------------------------------------------------------------------------
# 4) Rutas
# ---------------------------------------------------------------------------
aws apigatewayv2 create-route --region "$REGION" --api-id "$API_ID" \
  --route-key 'GET /api/health' --authorization-type NONE \
  --target "integrations/$INTEG_ID" >/dev/null
echo "    ruta pública:  GET /api/health"

aws apigatewayv2 create-route --region "$REGION" --api-id "$API_ID" \
  --route-key 'ANY /{proxy+}' --authorization-type JWT \
  --authorizer-id "$AUTH_ID" --target "integrations/$INTEG_ID" >/dev/null
echo "    ruta protegida: ANY /{proxy+}"

# ---------------------------------------------------------------------------
# 5) Stage con auto-deploy
# ---------------------------------------------------------------------------
aws apigatewayv2 create-stage --region "$REGION" --api-id "$API_ID" \
  --stage-name prod --auto-deploy >/dev/null

API_URL="https://$API_ID.execute-api.$REGION.amazonaws.com/prod"

cat >> "$OUT_FILE" <<EOF
API_ID=$API_ID
API_URL=$API_URL
EOF

echo
echo "=============================================================="
echo " API Gateway listo"
echo "   API_URL = $API_URL"
echo
echo " Pruebas:"
echo "   curl -i $API_URL/api/health                      # 200 público"
echo "   curl -i $API_URL/api/pedidos                     # 401 sin token"
echo "=============================================================="
