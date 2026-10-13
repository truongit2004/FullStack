#!/usr/bin/env bash
# Build image cho 15 service, lần lượt từng cái (build song song dễ hết RAM vì mỗi Dockerfile chạy Maven).
#
#   ./k8s/build-images.sh                                   # image cục bộ: ecommerce/<tên>:latest
#   ./k8s/build-images.sh myecommerceacr.azurecr.io v1      # build + push lên registry với tag v1
#
# Chạy từ thư mục Microserivce_ECommerSystem.
set -euo pipefail

REGISTRY="${1:-}"
TAG="${2:-latest}"

build() {
  local name="$1" dir="$2"
  local image="ecommerce/$name:$TAG"
  if [ -n "$REGISTRY" ]; then image="$REGISTRY/$image"; fi
  echo "===== $image  <-  $dir ====="
  docker build -t "$image" "$dir"
  if [ -n "$REGISTRY" ]; then docker push "$image"; fi
}

build eureka-server         "Eureka/Eureka"
build api-gateway           "API Gateway/API-Gateway"
build user-service          "User Service/User-Service"
build auth-service          "Auth Service/Auth-Service"
build product-service       "ProductService/ProductService"
build inventory-service     "InventoryService/InventoryService"
build order-service         "OrderService/OrderService"
build cart-service          "CartService/CartService"
build payment-service       "PaymentService/PaymentService"
build notification-service  "NotificationService/NotificationService"
build chat-service          "ChatService/ChatService"
build review-service        "ReviewService/ReviewService"
build shipping-service      "ShippingService/ShippingService"
build search-service        "SearchService/SearchService"
build point-service         "PointService/PointService"
build coupon-service        "CouponService/CouponService"

echo "Xong."
