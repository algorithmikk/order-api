#!/usr/bin/env bash
# Routes /api/v1/support* to the existing order-api target group and provisions
# the LLM gateway API key.
#
# Delivery chat needs no rule of its own: /api/v1/orders/{orderId}/chat/* already
# matches the existing /api/v1/orders* rule. Support gets one new rule pointing at
# the SAME target group, so this adds no target group, no service and no cost.
set -euo pipefail

REGION="${AWS_REGION:-us-east-1}"
ALB_NAME="${ALB_NAME:-umameats-api-alb}"
TARGET_GROUP_NAME="${TARGET_GROUP_NAME:-order-api-tg}"
RULE_PRIORITY="${RULE_PRIORITY:-41}"
SECRET_NAME="${SECRET_NAME:-prod/llm-gateway}"

echo "Resolving ALB listener and target group..."
LOAD_BALANCER_ARN=$(aws elbv2 describe-load-balancers \
  --region "$REGION" \
  --names "$ALB_NAME" \
  --query 'LoadBalancers[0].LoadBalancerArn' \
  --output text)

# The HTTPS listener carries the api.umameats.com rules.
LISTENER_ARN=$(aws elbv2 describe-listeners \
  --region "$REGION" \
  --load-balancer-arn "$LOAD_BALANCER_ARN" \
  --query 'Listeners[?Port==`443`].ListenerArn | [0]' \
  --output text)

TARGET_GROUP_ARN=$(aws elbv2 describe-target-groups \
  --region "$REGION" \
  --names "$TARGET_GROUP_NAME" \
  --query 'TargetGroups[0].TargetGroupArn' \
  --output text)

echo "Listener:     $LISTENER_ARN"
echo "Target group: $TARGET_GROUP_ARN"

EXISTING_RULE=$(aws elbv2 describe-rules \
  --region "$REGION" \
  --listener-arn "$LISTENER_ARN" \
  --query "Rules[?Conditions[?Values[?@=='/api/v1/support*']]].RuleArn | [0]" \
  --output text)

if [ "$EXISTING_RULE" != "None" ] && [ -n "$EXISTING_RULE" ]; then
  echo "Rule for /api/v1/support* already exists: $EXISTING_RULE"
else
  echo "Creating listener rule at priority $RULE_PRIORITY..."
  aws elbv2 create-rule \
    --region "$REGION" \
    --listener-arn "$LISTENER_ARN" \
    --priority "$RULE_PRIORITY" \
    --conditions Field=path-pattern,Values='/api/v1/support*' \
    --actions Type=forward,TargetGroupArn="$TARGET_GROUP_ARN" \
    --query 'Rules[0].RuleArn' \
    --output text
fi

echo
echo "Provisioning $SECRET_NAME ..."
if [ -z "${LLM_GATEWAY_API_KEY:-}" ]; then
  echo "LLM_GATEWAY_API_KEY is not set; skipping secret creation."
  echo "Run: LLM_GATEWAY_API_KEY=sk-... $0"
  exit 0
fi

SECRET_JSON=$(printf '{"LLM_GATEWAY_API_KEY":"%s"}' "$LLM_GATEWAY_API_KEY")
if aws secretsmanager describe-secret --region "$REGION" --secret-id "$SECRET_NAME" >/dev/null 2>&1; then
  aws secretsmanager put-secret-value \
    --region "$REGION" \
    --secret-id "$SECRET_NAME" \
    --secret-string "$SECRET_JSON" >/dev/null
  echo "Updated $SECRET_NAME"
else
  aws secretsmanager create-secret \
    --region "$REGION" \
    --name "$SECRET_NAME" \
    --description "API key for the OpenAI-compatible LLM gateway used by the support agent" \
    --secret-string "$SECRET_JSON" >/dev/null
  echo "Created $SECRET_NAME"
fi
