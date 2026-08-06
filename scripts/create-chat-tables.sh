#!/usr/bin/env bash
# Creates the DynamoDB tables backing delivery chat and AI support chat.
#
# umameats-delivery-chat already exists in production (driver-api writes to it)
# but was never scripted, so it is included here and the script is idempotent.
set -euo pipefail

REGION="${AWS_REGION:-us-east-1}"

create_table() {
  local description="$1"
  shift
  if aws dynamodb create-table --region "$REGION" "$@" >/dev/null 2>&1; then
    echo "Created $description"
  else
    echo "Skipped $description (already exists — verify its key schema)"
  fi
}

# Customer <-> driver messages for one order. Partitioned by order so a thread
# is a single query, sorted by "<epochMillis>#<uuid>" for chronological reads.
create_table "umameats-delivery-chat" \
  --table-name umameats-delivery-chat \
  --billing-mode PAY_PER_REQUEST \
  --attribute-definitions \
    AttributeName=orderId,AttributeType=S \
    AttributeName=messageId,AttributeType=S \
  --key-schema \
    AttributeName=orderId,KeyType=HASH \
    AttributeName=messageId,KeyType=RANGE

# Messages in an AI support conversation.
create_table "umameats-support-chat" \
  --table-name umameats-support-chat \
  --billing-mode PAY_PER_REQUEST \
  --attribute-definitions \
    AttributeName=threadId,AttributeType=S \
    AttributeName=messageId,AttributeType=S \
  --key-schema \
    AttributeName=threadId,KeyType=HASH \
    AttributeName=messageId,KeyType=RANGE

# Support thread state. The GSI answers "does this user already have an open
# thread?" without scanning, which is the hot path on every support screen open.
create_table "umameats-support-threads" \
  --table-name umameats-support-threads \
  --billing-mode PAY_PER_REQUEST \
  --attribute-definitions \
    AttributeName=threadId,AttributeType=S \
    AttributeName=principalId,AttributeType=S \
    AttributeName=updatedAt,AttributeType=N \
  --key-schema \
    AttributeName=threadId,KeyType=HASH \
  --global-secondary-indexes \
    "[
      {
        \"IndexName\": \"support-principal-index\",
        \"KeySchema\": [
          {\"AttributeName\":\"principalId\",\"KeyType\":\"HASH\"},
          {\"AttributeName\":\"updatedAt\",\"KeyType\":\"RANGE\"}
        ],
        \"Projection\": {\"ProjectionType\":\"ALL\"}
      }
    ]"

echo
echo "Done. All tables use PAY_PER_REQUEST (no provisioned capacity to pay for while idle)."
