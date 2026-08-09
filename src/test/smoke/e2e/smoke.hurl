# order-api — public list by store (unknown store still routes)
GET {{BASE_URL}}/api/v1/orders/store/smoke
HTTP 200

# Delivery chat + AI support must be mounted (401 without JWT, not 404).
GET {{BASE_URL}}/api/v1/support/thread
HTTP 401

GET {{BASE_URL}}/api/v1/orders/smoke-order/chat
HTTP 401
