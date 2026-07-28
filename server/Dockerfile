# 微信云托管（CloudRun）镜像
# 控制台端口需与 ENV PORT 保持一致（默认 80），平台注入的 PORT 会覆盖此值
FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt -i https://mirrors.tencent.com/pypi/simple

COPY . .

EXPOSE 80
ENV PORT=80

CMD ["python", "main.py"]
