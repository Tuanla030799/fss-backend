# CI/CD deploy nhánh `dev`

Workflow này deploy backend tự động mỗi khi có push vào nhánh `dev`.

File workflow: [`.github/workflows/deploy-dev.yml`](../.github/workflows/deploy-dev.yml)

## Cách hoạt động

1. GitHub Actions trigger khi push vào `dev`.
2. Runner mở SSH vào server.
3. Server chạy:
   - `git fetch origin dev`
   - `git checkout dev`
   - `git pull --ff-only origin dev`
   - `./scripts/deploy-prod.sh`
4. Workflow kiểm tra lại:
   - `docker compose ... ps`
   - health endpoint `http://127.0.0.1:8080/api/health` từ bên trong container `api`

## GitHub Secrets cần tạo

Trong repo GitHub, vào `Settings -> Secrets and variables -> Actions`, tạo các secrets sau:

- `DEV_DEPLOY_HOST`
  Ví dụ: IP public hoặc hostname của server.
- `DEV_DEPLOY_USER`
  User SSH dùng để deploy. User này phải có quyền vào thư mục project và chạy Docker.
- `DEV_DEPLOY_SSH_PRIVATE_KEY`
  Private key tương ứng với public key đã thêm vào `~/.ssh/authorized_keys` của user deploy trên server.
- `DEV_DEPLOY_PATH`
  Đường dẫn repo backend trên server.
  Với trao đổi hiện tại, khuyến nghị: `/opt/fds-backend`
- `DEV_DEPLOY_PORT`
  Tùy chọn. Nếu bỏ trống, workflow dùng mặc định port `22`.

## Chuẩn bị server

User deploy trên server phải:

- clone repo backend sẵn ở `DEV_DEPLOY_PATH`
- checkout nhánh `dev`
- có file `.env.production`
- có Docker và Docker Compose plugin
- có quyền chạy Docker
- có quyền `git pull` từ `origin` trên chính server

Kiểm tra nhanh trên server:

```bash
cd /opt/fds-backend
git branch --show-current
git remote -v
ls -la .env.production
docker --version
docker compose version
```

Kiểm tra luôn server pull được từ GitHub:

```bash
cd /opt/fds-backend
git fetch origin dev
```

## SSH key

Tạo key riêng cho GitHub Actions trên máy local hoặc server quản trị:

```bash
ssh-keygen -t ed25519 -C "github-actions-dev-deploy" -f ~/.ssh/github-actions-dev-deploy
```

Thêm public key lên server:

```bash
cat ~/.ssh/github-actions-dev-deploy.pub
```

Copy nội dung đó vào:

```bash
~/.ssh/authorized_keys
```

Thêm private key vào GitHub secret `DEV_DEPLOY_SSH_PRIVATE_KEY`.

## Lưu ý

- Workflow này không build Docker image trên GitHub rồi push registry. Nó deploy trực tiếp trên server bằng cách `git pull` và `docker compose up -d --build`.
- `git pull --ff-only` sẽ fail nếu server có local changes. Server deploy branch phải giữ sạch.
- Nếu `git fetch origin dev` fail trên server, cần cấu hình SSH key hoặc deploy key để chính server truy cập được repo GitHub.
- Nếu frontend `nginx` container đang proxy sang alias `backend`, backend deploy phải tiếp tục dùng network `thepocketshoes_net` như trong `docker-compose.prod.yml`.
- Nếu SSH port khác `22`, điền thêm `DEV_DEPLOY_PORT`.

## Test thủ công

Sau khi tạo workflow và secrets, có thể test bằng:

1. Push một commit vào `dev`, hoặc
2. Vào `Actions -> Deploy Dev -> Run workflow`
