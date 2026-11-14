"""验证注入串、伪造 classId 和非法类型不会扩大检索范围或返回 500。"""

from common.assertions import require, require_equal, require_list, require_mapping

ORDER = 80
NAME = "检索注入与跨班隔离"
METHOD = "POST"
PATH = "/api/retrieval/search"


def _assert_hits(context, payload, class_id: int) -> int:
    hits = require_list(payload.get("hits"), "检索响应应包含 hits 数组")
    for hit in hits:
        item = require_mapping(hit, "每条检索结果应为对象")
        stored = context.db.query_one(
            """
            SELECT kc.class_id AS chunk_class, kc.index_status AS chunk_status,
                   m.class_id AS material_class, m.index_status AS material_status
            FROM knowledge_chunks kc
            JOIN materials m ON m.id = kc.material_id
            WHERE kc.id = %s AND kc.material_id = %s
            """,
            (item.get("chunkId"), item.get("materialId")),
        )
        require(stored is not None, "命中结果必须对应 MySQL 中的切片和材料")
        require_equal(stored["chunk_class"], class_id, "命中切片必须属于当前班级")
        require_equal(stored["material_class"], class_id, "命中材料必须属于当前班级")
        require_equal(stored["chunk_status"], "READY", "命中切片必须为 READY")
        require_equal(stored["material_status"], "READY", "命中材料必须为 READY")
    return len(hits)


def run(context) -> None:
    student = context.state["student"]
    class_id = student["user"]["classId"]
    before = tuple(context.db.query_one(f"SELECT COUNT(*) AS n FROM {table}")["n"]
                   for table in ("materials", "knowledge_chunks", "users"))

    probes = [
        "' OR '1'='1",
        "%' UNION SELECT id,class_id FROM materials -- ",
        "'; DROP TABLE materials; -- ",
    ]
    hit_counts = []
    for query in probes:
        response = context.http.request(
            METHOD, PATH, token=student["token"],
            json_body={"query": query, "mode": "keyword", "limit": 5},
        )
        require(response.status != 500, "注入串不得导致服务端 500")
        if response.status == 400:
            hit_counts.append(0)
            continue
        require_equal(response.status, 200, "合法身份检索应返回 200 或明确 400")
        hit_counts.append(_assert_hits(context, require_mapping(response.json(), "检索响应应为对象"), class_id))

    forged_class = context.http.request(
        METHOD, PATH, token=student["token"],
        json_body={"query": "材料", "mode": "keyword", "classId": 999999, "limit": 5},
    )
    require_equal(forged_class.status, 200, "伪造 classId 不应改变服务端当前班级")
    _assert_hits(context, require_mapping(forged_class.json(), "检索响应应为对象"), class_id)

    invalid = [
        {"query": "材料", "limit": "5 OR 1=1"},
        {"query": "   ", "limit": 5},
        {"query": "材料", "mode": "sql"},
        {"query": "材料", "limit": 0},
        {"query": "材料", "limit": 21},
    ]
    for body in invalid:
        response = context.http.request(METHOD, PATH, token=student["token"], json_body=body)
        require_equal(response.status, 400, "非法类型或范围应返回 400")

    after = tuple(context.db.query_one(f"SELECT COUNT(*) AS n FROM {table}")["n"]
                  for table in ("materials", "knowledge_chunks", "users"))
    require_equal(after, before, "注入探针不得改变相关表计数")
    context.state["security_sqli_hits"] = hit_counts
