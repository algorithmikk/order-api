#!/bin/bash

# UmaEats Order API Deployment Script
# This script builds, tags, and deploys the order-api to AWS ECS

set -e

echo "🚀 Starting Order API Deployment..."

# Configuration
AWS_REGION="us-east-1"
AWS_ACCOUNT_ID="***REDACTED_AWS_ACCOUNT***"
ECR_REPOSITORY="order-api"
ECS_CLUSTER="umameats-api"
ECS_SERVICE="order-api"
IMAGE_TAG=$(git rev-parse HEAD)

echo "📦 Configuration:"
echo "  Region: $AWS_REGION"
echo "  Account: $AWS_ACCOUNT_ID"
echo "  Repository: $ECR_REPOSITORY"
echo "  Image Tag: $IMAGE_TAG"

# Step 1: Build the application
echo ""
echo "🔨 Step 1: Building application with Maven..."
./mvnw clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "❌ Maven build failed!"
    exit 1
fi

echo "✅ Maven build successful"

# Step 2: Login to ECR
echo ""
echo "🔐 Step 2: Logging into AWS ECR..."
aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

if [ $? -ne 0 ]; then
    echo "❌ ECR login failed!"
    exit 1
fi

echo "✅ ECR login successful"

# Step 3: Build Docker image
echo ""
echo "🐳 Step 3: Building Docker image..."
docker build -t $ECR_REPOSITORY:latest .
docker tag $ECR_REPOSITORY:latest $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPOSITORY:$IMAGE_TAG
docker tag $ECR_REPOSITORY:latest $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPOSITORY:latest

if [ $? -ne 0 ]; then
    echo "❌ Docker build failed!"
    exit 1
fi

echo "✅ Docker image built successfully"

# Step 4: Push to ECR
echo ""
echo "📤 Step 4: Pushing image to ECR..."
docker push $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPOSITORY:$IMAGE_TAG
docker push $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPOSITORY:latest

if [ $? -ne 0 ]; then
    echo "❌ Docker push failed!"
    exit 1
fi

echo "✅ Image pushed to ECR"

# Step 5: Update ECS service
echo ""
echo "🔄 Step 5: Updating ECS service..."

# Get current task definition
TASK_DEFINITION=$(aws ecs describe-services --cluster $ECS_CLUSTER --services $ECS_SERVICE --region $AWS_REGION --query 'services[0].taskDefinition' --output text)
echo "  Current task definition: $TASK_DEFINITION"

# Create new task definition with updated image
NEW_TASK_DEF=$(aws ecs describe-task-definition --task-definition $TASK_DEFINITION --region $AWS_REGION --query 'taskDefinition' | \
    jq --arg IMAGE "$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPOSITORY:$IMAGE_TAG" \
    '.containerDefinitions[0].image = $IMAGE | del(.taskDefinitionArn, .revision, .status, .requiresAttributes, .compatibilities, .registeredAt, .registeredBy)')

# Register new task definition
NEW_TASK_ARN=$(echo $NEW_TASK_DEF | aws ecs register-task-definition --region $AWS_REGION --cli-input-json file:///dev/stdin --query 'taskDefinition.taskDefinitionArn' --output text)

echo "  New task definition: $NEW_TASK_ARN"

# Update service to use new task definition
aws ecs update-service --cluster $ECS_CLUSTER --service $ECS_SERVICE --task-definition $NEW_TASK_ARN --region $AWS_REGION --force-new-deployment > /dev/null

if [ $? -ne 0 ]; then
    echo "❌ ECS service update failed!"
    exit 1
fi

echo "✅ ECS service updated"

# Step 6: Wait for deployment
echo ""
echo "⏳ Step 6: Waiting for deployment to complete..."
echo "  This may take a few minutes..."

aws ecs wait services-stable --cluster $ECS_CLUSTER --services $ECS_SERVICE --region $AWS_REGION

if [ $? -ne 0 ]; then
    echo "⚠️  Deployment did not stabilize within timeout"
    echo "  Check ECS console for details"
    exit 1
fi

echo ""
echo "✅ Deployment completed successfully!"
echo ""
echo "🎉 Order API is now running with the latest changes"
echo "   Image: $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPOSITORY:$IMAGE_TAG"
echo "   Service: https://api.umameats.com/api/v1/orders"
echo ""
echo "📝 Next steps:"
echo "   1. Test the new restaurant endpoint: PATCH /api/v1/orders/{orderId}/status/restaurant"
echo "   2. Deploy frontend changes to Vercel"
echo "   3. Test end-to-end order flow"

