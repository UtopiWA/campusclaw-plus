PYTHON ?= python
ENV_FILE ?= .env.team

.PHONY: check check-security

check:
	$(PYTHON) scripts/check.py --mode all

check-security:
	$(PYTHON) scripts/security_gate.py --env-file $(ENV_FILE)
