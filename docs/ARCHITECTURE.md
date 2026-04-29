# Architecture

Mục tiêu: giữ codebase dễ mở rộng khi thêm module/domain mới, nhưng vẫn vừa đủ đơn giản cho baseline hiện tại.

## Nguyên tắc
1. Controller chỉ nhận/trả HTTP và validate request.
2. Service domain là API nghiệp vụ cho từng bounded context.
3. Mỗi domain có controller, service, mapper interface và mapper XML riêng khi có persistence riêng.
4. MyBatis mapper giữ phần SQL; XML mapper nằm trong `src/main/resources/mappers`.
5. Schema thay đổi qua Flyway trong `src/main/resources/db/migration`.
6. Package top-level thể hiện bounded context, không gom tất cả dưới `ecommerce`.

## Bounded Contexts
- `auth`: đăng nhập admin, refresh token, JWT security.
- `auth/account`: quản lý tài khoản admin.
- `file`: upload, public URL, cleanup file tạm.
- `catalog/category`: category record, request, controller, service, mapper.
- `catalog/product`: product summary/detail, image, variant, request, controller, service, mapper.
- `catalog/sku`: SKU record và request.
- `sales/order`: order record, payment/shipping status, status history, controller, service, mapper.
- `promotion/coupon`: coupon record, validate request/response, controller, service, mapper.
- `content/banner`: landing banner record, request, controller, service, mapper.
- `customer`: customer user record, request, controller, service, mapper.
- `shared`: request/helper dùng chung như `UpdateStatusRequest`, `EcommerceSupport`.

## Database
- `V1__init_schema.sql`: baseline schema cho auth, file, catalog, sales, promotion, content.
- `V2__seed_admin.sql`: seed admin mặc định.

Project đã bỏ phần copy cũ như `preset/template` và `design_submissions`.
