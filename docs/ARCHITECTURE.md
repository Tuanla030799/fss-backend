# Architecture

Mục tiêu: giữ codebase dễ mở rộng khi thêm module/domain mới, nhưng vẫn vừa đủ đơn giản cho baseline hiện tại.

## Nguyên tắc
1. Controller chỉ nhận/trả HTTP và validate request.
2. Service domain là API nghiệp vụ cho từng bounded context.
3. Mỗi domain có controller, service, `*Document` (record ánh xạ collection MongoDB) và `*Repository`
   (truy vấn bằng `MongoTemplate`) khi có persistence riêng.
4. Repository trả về record API cũ (`Brand`, `ProductSummary`, ...) để controller/service và JSON response không đổi.
5. Index MongoDB khai báo tập trung trong `shared/mongo/MongoIndexInitializer`; không có migration dữ liệu khi boot.
6. Package top-level thể hiện bounded context, không gom tất cả dưới `ecommerce`.

## Bounded Contexts
- `auth`: đăng nhập admin, refresh token, JWT security.
- `auth/account`: quản lý tài khoản admin.
- `file`: upload, xử lý ảnh, `FileStorage` (local/R2), public URL, cleanup file không dùng.
- `catalog/category`: category record, request, controller, service, mapper.
- `catalog/product`: product summary/detail, image, variant, request, controller, service, mapper.
- `catalog/sku`: SKU record và request.
- `sales/order`: order record, payment/shipping status, status history, controller, service, mapper.
- `promotion/coupon`: coupon record, validate request/response, controller, service, mapper.
- `content/banner`: landing banner record, request, controller, service, mapper.
- `content/page`: admin-edited site pages with fixed keys (`SitePageKey`: about, purchase-shipping).
- `content/faq`: FAQ record, request, controller, service, repository.
- `customer`: customer user record, request, controller, service, mapper.
- `shared`: request/helper dùng chung như `UpdateStatusRequest`, `EcommerceSupport`.

## Database
- MongoDB (replica set, có transaction). Chi tiết collection, index, transaction đơn hàng và vòng đời file:
  [deploy-heroku-mongodb-r2.md](deploy-heroku-mongodb-r2.md).
- Schema PostgreSQL cũ (các script Flyway V1–V18) được giữ trong `migration-tool/src/main/resources/legacy-schema`
  làm tài liệu tham chiếu và cho test của công cụ chuyển dữ liệu.

Project đã bỏ phần copy cũ như `preset/template` và `design_submissions`.
