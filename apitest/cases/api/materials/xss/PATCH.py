"""验证恶意标题在 API 层原样保存，前端 DOM 由前端测试单独断言。"""

from common.assertions import require, require_equal, require_mapping
from common.fixtures import upload_material

ORDER = 81
NAME = "材料标题 XSS 文本处理"
METHOD = "PATCH"
PATH = "/api/materials/{id}"

PAYLOAD = "<script>alert(1)</script>"
TEXT = "合成 XSS 材料：<img src=x onerror=alert(1)>"


def run(context) -> None:
    try:
        material_id = upload_material(
            context, f"<script>alert(1)</script>-{context.run_id}.md", TEXT)
    except AssertionError:
        # 文件名校验拒绝恶意字符时，仍用同一文本验证标题更新路径。
        material_id = upload_material(context, f"xss-{context.run_id}.md", TEXT)

    teacher = context.state["teacher"]["token"]
    response = context.http.request(
        "PATCH", f"/api/materials/{material_id}", token=teacher, json_body={"title": PAYLOAD})
    require_equal(response.status, 200, "教师更新合成材料标题应成功")
    body = require_mapping(response.json(), "材料响应应为对象")
    require_equal(body.get("title"), PAYLOAD, "PATCH 响应应原样返回恶意标题")
    require("<script>" in response.body.decode("utf-8"), "API 不应把标题 HTML 转义成实体")

    stored = context.db.query_one("SELECT title FROM materials WHERE id = %s", (material_id,))
    require(stored is not None, "合成材料应存在于 MySQL")
    require_equal(stored["title"], PAYLOAD, "MySQL 标题应原样保存")

    detail = context.http.request("GET", f"/api/materials/{material_id}", token=teacher)
    require_equal(detail.status, 200, "教师读取合成材料应成功")
    require_equal(require_mapping(detail.json(), "材料详情应为对象").get("title"), PAYLOAD,
                  "材料详情应返回同一标题")
