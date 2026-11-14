"""合成材料上传与清理辅助；只操作本轮唯一命名的资源。"""
from __future__ import annotations

import uuid

from common.assertions import require, require_equal, require_mapping


def upload_material(context, filename: str, text: str, *, expected: int = 201) -> int:
    """上传一份合成 Markdown，返回 materialId 并登记按 ID 的 API 删除。"""
    boundary = "campusclaw-" + uuid.uuid4().hex
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'
        "Content-Type: text/markdown\r\n\r\n"
        f"{text}\r\n"
        f"--{boundary}--\r\n"
    ).encode("utf-8")
    teacher = context.state["teacher"]["token"]
    response = context.http.request(
        "POST", "/api/materials/upload", token=teacher, data=body,
        content_type=f"multipart/form-data; boundary={boundary}",
    )
    require_equal(response.status, expected, "合成材料上传状态应可预期")
    payload = require_mapping(response.json(), "上传响应应为对象")
    material_id = payload.get("materialId")
    require(isinstance(material_id, int) and material_id > 0, "上传响应应包含 materialId")
    _register_cleanup(context, material_id)
    return material_id


def _register_cleanup(context, material_id: int) -> None:
    def cleanup() -> None:
        teacher = context.state["teacher"]["token"]
        response = context.http.request("DELETE", f"/api/materials/{material_id}", token=teacher)
        if response.status not in (204, 404):
            raise AssertionError(f"合成材料清理失败：HTTP {response.status}")

    context.add_cleanup(f"删除合成材料 {material_id}", cleanup)
