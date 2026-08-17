#!/usr/bin/env bash
# Creates umameats-user-taste (on-demand, PK customerId).
# Billable AWS change — do not run without explicit approval.
set -euo pipefail

REGION="${AWS_REGION:-us-east-1}"
TABLE="${TASTE_TABLE:-umameats-user-taste}"

if [[ "${CONFIRM_CREATE_TASTE_TABLE:-}" != "yes" ]]; then
  echo "Refusing to create $TABLE."
  echo "Set CONFIRM_CREATE_TASTE_TABLE=yes after Jay approves the spend."
  exit 1
fi

aws dynamodb create-table \
  --region "$REGION" \
  --table-name "$TABLE" \
  --billing-mode PAY_PER_REQUEST \
  --attribute-definitions AttributeName=customerId,AttributeType=S \
  --key-schema AttributeName=customerId,KeyType=HASH

echo "Created $TABLE (DynamoDB on-demand, single hash key)."
