# 部署到微信云托管（CloudRun）

本文档介绍如何把 `server/` 这个 FastAPI 后端部署到微信云托管。

## 前提

- 项目已自带容器化配置，无需改动代码：
  - `Dockerfile`：基于 `python:3.11-slim`，启动命令 `python main.py`
  - `main.py` 末尾的启动块会从环境变量 `PORT` 读取端口（默认 8000，云托管会注入 80）
  - `.dockerignore`：排除 `.venv/`、`tests/` 等无关文件
- 一个已实名认证的微信小程序或公众号账号

## 部署步骤

### 1. 新建环境

1. 打开 https://cloud.weixin.qq.com/cloudrun/service ，扫码登录
2. 选择你的小程序 / 公众号
3. 按提示**新建环境**（免费额度足够本轻量服务）

### 2. 新建服务

1. 进入环境，点击**新建服务**
2. 服务名例如填 `wealth-server`
3. **开启"允许公网访问"**（Android App 走公网调用；不开则只能被同环境服务内网调用）

### 3. 部署发布（手动上传代码包）

1. 进入服务 → **部署发布** → 选择**手动上传代码包**
2. 上传方式选**文件夹**，选中本 `server/` 目录（注意选到文件夹本身）
3. 点击**发布**，流程分"构建"和"部署"两步，约 3 分钟

### 4. 确认端口

服务设置里确认**端口 = 80**，与 `Dockerfile` 中 `ENV PORT=80` 一致。

### 5. 验证

部署完成后会分配一个公网 HTTPS 域名，浏览器访问：

```
https://<你的域名>/api/health
https://<你的域名>/api/lottery/ssq/draws
```

返回正常 JSON 即成功。

### 6. 配置 Android App

打开 App 的"数据"页，把服务器地址改为：

```
https://<你的域名>
```

云托管默认 HTTPS，**不要再带 `:8000` 端口**。

## 已知限制

- **容器文件系统不持久**：重启 / 重新部署后，运行期产生的数据（`ssq_state.json`、`ssq_model_evaluation.json`、`football_odds_snapshots.json`、手动导入的赛果等）**全部重置**，需重新调用 `/api/lottery/sports/football/results/sync` 同步或重新导入。
- 打包进镜像的兜底数据（`ssq_draws.json`、`worldcup_2026_*.json` 等）会保留，冷启动降级链不受影响。
- 后续若需要真正持久化，可迁移到云托管的对象存储 COS 或 Serverless MySQL。

## 本地开发不受影响

原来的本地启动方式照常可用：

```bash
uvicorn main:app --host 0.0.0.0 --port 8000
```
