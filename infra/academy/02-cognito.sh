#!/usr/bin/env bash
# =============================================================================
# 02-cognito.sh · Crea User Pool, grupos, App Client SPA, dominio y usuarios
# -----------------------------------------------------------------------------
# Uso:
#   bash 02-cognito.sh
#   FRONTEND_URL=http://1.2.3.4:5173 bash 02-cognito.sh
#
# Salidas: añade COGNITO_* a infra/academy/.env.generated
# =============================================================================
set -euo pipefail

REGION="${AWS_DEFAULT_REGION:-us-east-1}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:5173}"
OUT_FILE="$(dirname "$0")/.env.generated"
POOL_NAME="${POOL_NAME:-pedidos360-pool}"

echo "==> Región: $REGION | Frontend: $FRONTEND_URL"

# ---------------------------------------------------------------------------
# 1) User Pool
# ---------------------------------------------------------------------------
POOL_ID=$(aws cognito-idp create-user-pool --region "$REGION" \
  --pool-name "$POOL_NAME" \
  --auto-verified-attributes email \
  --username-attributes email \
  --policies '{"PasswordPolicy":{"MinimumLength":8,"RequireUppercase":true,"RequireLowercase":true,"RequireNumbers":true,"RequireSymbols":false}}' \
  --query 'UserPool.Id' --output text)
echo "==> User Pool: $POOL_ID"

# ---------------------------------------------------------------------------
# 2) Grupos de permisos
# ---------------------------------------------------------------------------
for G in ADMIN COLLABORATOR CUSTOMER; do
  aws cognito-idp create-group --region "$REGION" \
    --user-pool-id "$POOL_ID" --group-name "$G" \
    --description "Grupo $G de Pedidos360" >/dev/null
  echo "    grupo creado: $G"
done

# ---------------------------------------------------------------------------
# 3) App Client SPA (sin secret, Authorization Code + PKCE)
# ---------------------------------------------------------------------------
CLIENT_ID=$(aws cognito-idp create-user-pool-client --region "$REGION" \
  --user-pool-id "$POOL_ID" \
  --client-name pedidos360-spa \
  --no-generate-secret \
  --explicit-auth-flows ALLOW_USER_SRP_AUTH ALLOW_REFRESH_TOKEN_AUTH \
  --supported-identity-providers COGNITO \
  --callback-urls "[\"$FRONTEND_URL\",\"http://localhost:5173\"]" \
  --logout-urls "[\"$FRONTEND_URL\",\"http://localhost:5173\"]" \
  --allowed-o-auth-flows code \
  --allowed-o-auth-scopes openid email profile \
  --allowed-o-auth-flows-user-pool-client \
  --query 'UserPoolClient.ClientId' --output text)
echo "==> App Client: $CLIENT_ID"

# ---------------------------------------------------------------------------
# 4) Dominio del Hosted UI (debe ser único a nivel global)
# ---------------------------------------------------------------------------
SUFFIX=$(echo "$POOL_ID" | tr '[:upper:]' '[:lower:]' | tr -d '-' | tail -c 9)
DOMAIN_PREFIX="pedidos360-$SUFFIX"
aws cognito-idp create-user-pool-domain --region "$REGION" \
  --domain "$DOMAIN_PREFIX" --user-pool-id "$POOL_ID" >/dev/null 2>&1 || true
AUTHORITY="https://$DOMAIN_PREFIX.auth.$REGION.amazoncognito.com"
echo "==> Hosted UI: $AUTHORITY"

ISSUER="https://cognito-idp.$REGION.amazonaws.com/$POOL_ID"

# ---------------------------------------------------------------------------
# 5) Usuarios de prueba (uno por grupo)
# ---------------------------------------------------------------------------
create_user() {
  local USER="$1" PASS="$2" GROUP="$3"
  aws cognito-idp admin-create-user --region "$REGION" \
    --user-pool-id "$POOL_ID" --username "$USER" \
    --user-attributes Name=email,Value="$USER" Name=email_verified,Value=true \
    --message-action SUPPRESS >/dev/null 2>&1 || true
  aws cognito-idp admin-set-user-password --region "$REGION" \
    --user-pool-id "$POOL_ID" --username "$USER" \
    --password "$PASS" --permanent >/dev/null
  aws cognito-idp admin-add-user-to-group --region "$REGION" \
    --user-pool-id "$POOL_ID" --username "$USER" --group-name "$GROUP" >/dev/null
  echo "    usuario: $USER  (grupo $GROUP, pass $PASS)"
}

create_user "admin@pedidos360.cl"  "Admin1234" ADMIN
create_user "collab@pedidos360.cl" "Collab1234" COLLABORATOR
create_user "cliente@pedidos360.cl" "Cliente1234" CUSTOMER

# ---------------------------------------------------------------------------
# 6) Guardar resultados
# ---------------------------------------------------------------------------
cat >> "$OUT_FILE" <<EOF
COGNITO_POOL_ID=$POOL_ID
COGNITO_CLIENT_ID=$CLIENT_ID
COGNITO_ISSUER_URI=$ISSUER
COGNITO_AUTHORITY=$AUTHORITY
EOF

echo
echo "=============================================================="
echo " Cognito listo"
echo "   POOL_ID    = $POOL_ID"
echo "   CLIENT_ID  = $CLIENT_ID"
echo "   ISSUER     = $ISSUER"
echo "   AUTHORITY  = $AUTHORITY"
echo "=============================================================="
echo
echo "Frontend .env:"
echo "  VITE_COGNITO_AUTHORITY=$AUTHORITY"
echo "  VITE_COGNITO_CLIENT_ID=$CLIENT_ID"
echo "  VITE_COGNITO_REDIRECT_URI=$FRONTEND_URL"
