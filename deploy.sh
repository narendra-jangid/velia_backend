#!/bin/bash

set -e

echo "========================================"
echo "Starting Velia Backend Deployment"
echo "========================================"

cd /opt/velia_backend

echo "Pulling latest code..."

git fetch origin
git reset --hard origin/main

echo "Latest code pulled successfully."

echo "Building all Maven modules..."

mvn clean package -DskipTests

echo "Maven build completed successfully."

echo "Restarting Eureka..."
systemctl restart eureka.service

echo "Restarting Gateway..."
systemctl restart gateway.service

echo "Restarting User Service..."
systemctl restart user.service

echo "Restarting Product Service..."
systemctl restart product.service

echo "Restarting Order Service..."
systemctl restart order.service

echo "Checking service status..."

systemctl is-active --quiet eureka.service
systemctl is-active --quiet gateway.service
systemctl is-active --quiet user.service
systemctl is-active --quiet product.service
systemctl is-active --quiet order.service

echo "========================================"
echo "Deployment completed successfully."
echo "All services are running."
echo "========================================"
