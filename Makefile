PYTHON ?= python
ENV_FILE ?= .env.team
SECURITY_EXCEPTIONS ?= security-exceptions.json
COMPOSE := docker compose --env-file $(ENV_FILE)
GIT_COMMIT ?= $(shell git rev-parse HEAD 2>/dev/null || echo unknown)
IMAGE_TAG ?= $(shell git rev-parse --short HEAD 2>/dev/null || echo local)

.PHONY: check check-security build-images up down bench observe pipeline

# 功能门禁：规约、前端、非容器后端、MCP、脚本测试，以及集成与接口回归。
check:
	$(PYTHON) scripts/check.py --mode all

# 安全门禁：密钥/依赖扫描、授权、审计、接口反例和脚本测试。
check-security:
	$(PYTHON) scripts/security_gate.py --env-file $(ENV_FILE) --exceptions $(SECURITY_EXCEPTIONS)

# 构建三个镜像；GIT_COMMIT 写入 OCI revision 标签，IMAGE_TAG 绑定提交短哈希。
build-images:
	GIT_COMMIT=$(GIT_COMMIT) IMAGE_TAG=$(IMAGE_TAG) $(COMPOSE) build

up:
	GIT_COMMIT=$(GIT_COMMIT) IMAGE_TAG=$(IMAGE_TAG) $(COMPOSE) up -d --build --wait --wait-timeout 300

down:
	$(COMPOSE) down

bench:
	$(PYTHON) scripts/bench.py --env-file $(ENV_FILE)

observe:
	$(PYTHON) scripts/observe.py --env-file $(ENV_FILE)

# 本地等价流水线：功能门禁 -> 安全门禁 -> 构建带提交标识的镜像。
pipeline: check check-security build-images
