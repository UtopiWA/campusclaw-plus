"""Bearer JWT 无状态项目的 CSRF 等价验收：无令牌伪造请求不得写入。"""

from common.assertions import require, require_equal
from common.fixtures import upload_material

ORDER = 82
NAME = "跨站伪造配置写入"
METHOD = "PATCH"
PATH = "/api/materials/{id}"


def run(context) -> None:
    material_id = upload_material(context, f"csrf-{context.run_id}.md", "合成 CSRF 材料")
    teacher = context.state["teacher"]["token"]
    before = context.db.query_one("SELECT title FROM materials WHERE id = %s", (material_id,))["title"]
    forged_headers = {
        "Origin": "https://evil.example",
        "Referer": "https://evil.example/csrf",
        "Cookie": "SESSION=forged-by-another-site",
    }

    forged = context.http.request(
        METHOD, f"/api/materials/{material_id}", headers=forged_headers,
        json_body={"title": f"csrf-forged-{context.run_id}"})
    require_equal(forged.status, 401, "没有合法 Bearer 令牌的跨站写入必须被拒绝")
    after_forged = context.db.query_one("SELECT title FROM materials WHERE id = %s", (material_id,))["title"]
    require_equal(after_forged, before, "拒绝跨站写入后数据库不得变化")

    control = context.http.request(
        METHOD, f"/api/materials/{material_id}", token=teacher,
        headers={"Origin": "http://localhost:18080", "Referer": "http://localhost:18080/materials"},
        json_body={"title": f"csrf-control-{context.run_id}"})
    require_equal(control.status, 200, "带合法 Bearer 的对照请求应成功")
    after_control = context.db.query_one("SELECT title FROM materials WHERE id = %s", (material_id,))["title"]
    require_equal(after_control, f"csrf-control-{context.run_id}", "合法请求应更新数据库")

    login = context.http.request(
        "POST", "/api/auth/login",
        json_body={"username": context.config.teacher_username,
                   "password": context.config.teacher_password})
    require_equal(login.status, 200, "登录对照请求应成功")
    require("set-cookie" not in login.headers, "Bearer JWT 登录不得设置会话 Cookie")

    cookie_only = context.http.request(
        METHOD, f"/api/materials/{material_id}",
        headers={"Cookie": "SESSION=only-cookie", "Origin": "https://evil.example"},
        json_body={"title": "csrf-cookie-only"})
    require_equal(cookie_only.status, 401, "仅携带 Cookie 的请求不得获得授权")
    require_equal(context.db.query_one("SELECT title FROM materials WHERE id = %s", (material_id,))["title"],
                  after_control, "仅 Cookie 请求不得改变数据库")
