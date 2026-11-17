"""显式、可审计的开发依赖安全例外。

例外只有同时满足以下条件才会被 `scan.py` 接受：
- 文件显式传入（`--exceptions`），不是默认静默生效；
- `approved=true`、批准人/日期、到期日、补偿措施齐全且未过期；
- npm audit 中每一个 high/critical 包都能匹配到例外列出的包和公告；
- 原始 audit JSON/失败码继续保留，输出标记“接受例外”，不写成 0 告警。
"""
from __future__ import annotations

from datetime import date
import json
from pathlib import Path
import re
from typing import Any

ADVISORY = re.compile(r"(GHSA-[a-z0-9-]+)", re.IGNORECASE)


def _date(value: Any, field: str, errors: list[str]) -> date | None:
    if not isinstance(value, str):
        errors.append(f"{field} 必须是 ISO 日期")
        return None
    try:
        return date.fromisoformat(value)
    except ValueError:
        errors.append(f"{field} 必须是 ISO 日期")
        return None


def validate(document: Any, today: date | None = None) -> list[str]:
    """返回结构/批准/有效期错误；空列表表示可接受。"""
    today = today or date.today()
    errors: list[str] = []
    if not isinstance(document, dict):
        return ["例外文件必须是 JSON 对象"]
    if document.get("schemaVersion") != 1:
        errors.append("schemaVersion 必须为 1")
    if document.get("approved") is not True:
        errors.append("approved 必须为 true")
    if not str(document.get("approvedBy", "")).strip():
        errors.append("approvedBy 不能为空")
    _date(document.get("approvedAt"), "approvedAt", errors)
    expires = _date(document.get("expiresAt"), "expiresAt", errors)
    if expires is not None and expires < today:
        errors.append("例外已过期")
    controls = document.get("compensatingControls")
    if not isinstance(controls, list) or not controls or not all(
            isinstance(item, str) and item.strip() for item in controls):
        errors.append("compensatingControls 必须是非空字符串数组")
    advisories = document.get("advisories")
    if not isinstance(advisories, list) or not advisories:
        errors.append("advisories 不能为空")
    else:
        for index, advisory in enumerate(advisories):
            if not isinstance(advisory, dict):
                errors.append(f"advisories[{index}] 必须是对象")
                continue
            if not ADVISORY.fullmatch(str(advisory.get("id", ""))):
                errors.append(f"advisories[{index}].id 必须是 GHSA 编号")
            packages = advisory.get("packages")
            if not isinstance(packages, list) or not packages or not all(
                    isinstance(item, str) and item.strip() for item in packages):
                errors.append(f"advisories[{index}].packages 必须是非空字符串数组")
            severities = advisory.get("severities", ["high", "critical"])
            if not isinstance(severities, list) or not severities:
                errors.append(f"advisories[{index}].severities 必须是非空数组")
    return errors


def load(path: Path, today: date | None = None) -> dict:
    """读取并校验例外；任何问题抛出 ValueError，调用方必须非零退出。"""
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exception:
        raise ValueError("例外文件无法读取或不是合法 JSON") from exception
    problems = validate(document, today)
    if problems:
        raise ValueError("; ".join(problems))
    return document


def evaluate(audit_text: str, document: dict, today: date | None = None) -> tuple[bool, list[str], list[str]]:
    """评估 npm audit JSON。

    返回 (是否全部覆盖, 未覆盖问题, 实际应用的公告 ID)。
    只有 high/critical 需要例外；moderate 及以下不进入本函数。
    """
    problems = validate(document, today)
    if problems:
        return False, problems, []
    try:
        audit = json.loads(audit_text)
    except json.JSONDecodeError:
        return False, ["npm audit 输出不是合法 JSON"], []
    vulnerabilities = audit.get("vulnerabilities") if isinstance(audit, dict) else None
    if not isinstance(vulnerabilities, dict):
        return False, ["npm audit JSON 缺少 vulnerabilities"], []

    exceptions = document["advisories"]
    covered_ids = {item["id"].upper() for item in exceptions}
    uncovered: list[str] = []
    applied: set[str] = set()
    for package, row in vulnerabilities.items():
        if not isinstance(row, dict):
            continue
        severity = str(row.get("severity", "")).lower()
        if severity not in {"high", "critical"}:
            continue
        matches = [
            item for item in exceptions
            if package in item.get("packages", [])
            and severity in [str(value).lower() for value in item.get("severities", ["high", "critical"])]
        ]
        if not matches:
            uncovered.append(f"{package}:{severity} 没有匹配的已批准例外")
        else:
            applied.update(item["id"] for item in matches)
        # via 里的 GHSA 公告也必须落在例外集合内；否则是未知的新公告。
        for via in row.get("via", []):
            if isinstance(via, dict):
                found = ADVISORY.search(str(via.get("url", "")))
                if found and found.group(1).upper() not in covered_ids:
                    uncovered.append(f"{package}:{severity} 新公告 {found.group(1)} 未覆盖")
    return not uncovered, uncovered, sorted(applied)
