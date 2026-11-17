"""本地告警接收器：把实际送达的 JSON 告警追加到指定文件，用于可复核的送达证据。

用法：
  python scripts/alert_receiver.py --port 18081 --output backend/target/observe/alert-deliveries.jsonl
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path


class Handler(BaseHTTPRequestHandler):
    output = Path("alert-deliveries.jsonl")

    def do_POST(self) -> None:  # noqa: N802 - stdlib 命名
        length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(length).decode("utf-8", errors="replace")
        try:
            payload = json.loads(body)
        except json.JSONDecodeError:
            payload = {"invalidJson": True, "length": length}
        record = {"receivedAt": datetime.now(timezone.utc).isoformat(),
                  "path": self.path, "payload": payload}
        self.output.parent.mkdir(parents=True, exist_ok=True)
        with self.output.open("a", encoding="utf-8") as stream:
            stream.write(json.dumps(record, ensure_ascii=False) + "\n")
        response = json.dumps({"received": True}).encode()
        self.send_response(202)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(response)))
        self.end_headers()
        self.wfile.write(response)

    def log_message(self, format: str, *args) -> None:  # noqa: A002 - 关闭访问日志
        return


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=18081)
    parser.add_argument("--output", type=Path,
                        default=Path("backend/target/observe/alert-deliveries.jsonl"))
    args = parser.parse_args(argv)
    Handler.output = args.output.resolve()
    server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    print(f"alert receiver listening on http://127.0.0.1:{args.port} -> {Handler.output}", flush=True)
    server.serve_forever()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
