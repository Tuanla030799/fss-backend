# Ecommerce Implementation

## Scope
- Category CRUD: `/api/admin/categories`, public `/api/categories`.
- Product CRUD: `/api/admin/products`.
- Product variant/SKU upsert/delete riêng để giữ ID ổn định khi admin chỉnh sản phẩm.
- Product images, variants và SKUs được lưu cùng product payload.
- Public product listing/detail: `/api/products`, `/api/products/{slug}`.
- Featured products endpoint: `/api/products/featured`.
- Create order: `/api/orders`.
- Admin order list/detail/status: `/api/admin/orders`.
- Payment/shipping update và order status history: `/api/admin/orders/{id}/payment`, `/shipping`, `/status-history`.
- Landing banner CMS: `/api/admin/landing-banners`, public `/api/landing-banners`.
- Coupon CRUD và public validate: `/api/admin/coupons`, `/api/coupons/validate`.
- Admin users CRUD: `/api/admin/users`.
- Customer users CRUD: `/api/admin/customers`.

## Package layout
- `catalog/category`: category record, request, controller, service, mapper.
- `catalog/product`: product, image, variant record, request, controller, service, mapper.
- `catalog/sku`: SKU record và request.
- `sales/order`: order record, payment/shipping status, status history, controller, service, mapper.
- `promotion/coupon`: coupon record, request/response, controller, service, mapper.
- `content/banner`: banner record, request, controller, service, mapper.
- `auth/account`: admin account record, request, controller, service, mapper.
- `customer`: customer user record, request, controller, service, mapper.
- `shared/ecommerce`: helper dùng chung cho ecommerce contexts.

## Create product request

`POST /api/admin/products`

```json
{
  "categoryId": "uuid",
  "name": "Nike Air Max",
  "slug": "nike-air-max",
  "shortDescription": "Comfortable daily sneakers",
  "descriptionJson": "{\"blocks\":[]}",
  "status": "ACTIVE",
  "isFeatured": true,
  "featuredOrder": 1,
  "images": [
    { "fileId": "uuid", "altText": "Main image", "imageType": "MAIN", "sortOrder": 0, "isPrimary": true }
  ],
  "variants": [
    { "clientId": "uuid-from-fe", "name": "Black", "colorName": "Black", "colorCode": "#000000", "sortOrder": 0 }
  ],
  "skus": [
    { "variantClientId": "same-uuid-from-fe", "skuCode": "NAM-BLK-42", "size": "42", "price": 1200000, "salePrice": 990000, "stock": 10, "status": "ACTIVE" }
  ]
}
```

`clientId`/`variantClientId` cho phép frontend liên kết SKU với variant ngay trong một request create/update.

## Create order request

`POST /api/orders`

```json
{
  "customerName": "Nguyen Van A",
  "customerPhone": "0900000000",
  "customerEmail": "a@example.com",
  "shippingAddress": "Ha Noi",
  "note": "Call before delivery",
  "couponCode": "SALE10",
  "shippingFee": 30000,
  "items": [
    { "skuId": "uuid", "quantity": 1 }
  ]
}
```

## DB notes

Migration baseline: `src/main/resources/db/migration/V1__init_schema.sql`.

Main tables:
- `admin_users`, `refresh_tokens`
- `customers`
- `files`
- `categories`
- `products`, `product_images`, `product_variants`
- `skus`
- `orders`, `order_items`, `order_status_history`
- `landing_banners`
- `coupons`

`orders` và `order_items` lưu snapshot như product name, SKU code, size và unit price để lịch sử đơn không đổi khi product/SKU thay đổi sau này.
