#!/usr/bin/env bash
# =============================================================================
# 00-check.sh · Verifica que las credenciales de AWS Academy Learner Lab sirven
# -----------------------------------------------------------------------------
# Uso:
#   export AWS_ACCESS_KEY_ID=...
#   export AWS_SECRET_ACCESS_KEY=...
#   export AWS_SESSION_TOKEN=...
#   export AWS_DEFAULT_REGION=us-east-1
#   bash 00-check.sh
# =============================================================================
set -uo pipefail

echo "== Región =="
echo "AWS_DEFAULT_REGION=${AWS_DEFAULT_REGION:-<no definida>}"

if [[ -z "${AWS_ACCESS_KEY_ID:-}" || -z "${AWS_SECRET_ACCESS_KEY:-}" || -z "${AWS_SESSION_TOKEN:-}" ]]; then
  echo "ERROR: faltan AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY / AWS_SESSION_TOKEN."
  echo "En el Learner Lab: AWS Details -> AWS CLI: Show, y copia los tres valores."
  exit 1
fi

echo
echo "== Identidad =="
aws sts get-caller-identity || { echo "ERROR: credenciales inválidas o expiradas."; exit 1; }

echo
echo "== Key pair disponible (vockey) =="
aws ec2 describe-key-pairs --key-names vockey \
  --query 'KeyPairs[0].{Name:KeyName,Fingerprint:KeyFingerprint}' --output table 2>/dev/null \
  || echo "AVISO: no se encontró 'vockey'. Podrás crear uno propio con 01-ec2.sh (KEY_NAME=...)."

echo
echo "== Límites de la cuenta (vCPU On-Demand) =="
aws service-quotas get-service-quota \
  --service-code ec2 --quota-code L-1216C47A \
  --query 'Quota.{Nombre:QuotaName,Valor:Value}' --output table 2>/dev/null \
  || echo "AVISO: sin permiso para leer cuotas (normal en Learner Lab)."

echo
echo "OK: credenciales operativas."
