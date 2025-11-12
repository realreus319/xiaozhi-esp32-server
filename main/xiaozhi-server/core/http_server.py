import asyncio
import json
import os
import datetime
import hashlib
from aiohttp import web
from config.logger import setup_logging
from core.api.ota_handler import OTAHandler
from core.api.vision_handler import VisionHandler
from core.websocket_server import WebSocketServer

TAG = __name__


class SimpleHttpServer:
    def __init__(self, config: dict):
        self.config = config
        self.logger = setup_logging()
        self.ota_handler = OTAHandler(config)
        self.vision_handler = VisionHandler(config)

    # --- 简单鉴权：支持两种方式 ---
    # 1) 固定密钥：server.auth_key 作为 Bearer Token
    # 2) 每日临时密钥：sha256(YYYY-MM-DD + MQTT_SIGNATURE_KEY)
    def _auth_ok(self, request: web.Request) -> bool:
        try:
            auth = request.headers.get("Authorization", "")
            if not auth.startswith("Bearer "):
                return False
            token = auth[7:]
            # 方式1：固定密钥
            expected = self.config.get("server", {}).get("auth_key", "")
            if expected and token == expected:
                return True
            # 方式2：每日临时密钥
            signature_key = os.environ.get("MQTT_SIGNATURE_KEY", "")
            if signature_key:
                today = datetime.date.today().strftime("%Y-%m-%d")
                daily = hashlib.sha256((today + signature_key).encode("utf-8")).hexdigest()
                if token == daily:
                    return True
            return False
        except Exception:
            return False

    async def _handle_list_ws_connections(self, request: web.Request) -> web.StreamResponse:
        try:
            # 使用加锁的异步方法，确保一致性
            client_ids = await WebSocketServer.list_client_ids()
            resp = web.json_response({"connections": client_ids, "count": len(client_ids)})
            # CORS
            resp.headers["Access-Control-Allow-Origin"] = "*"
            resp.headers["Access-Control-Allow-Methods"] = "GET,POST,OPTIONS"
            resp.headers["Access-Control-Allow-Headers"] = "*"
            return resp
        except Exception as e:
            resp = web.json_response({"error": f"failed to list: {e}"}, status=500)
            resp.headers["Access-Control-Allow-Origin"] = "*"
            resp.headers["Access-Control-Allow-Methods"] = "GET,POST,OPTIONS"
            resp.headers["Access-Control-Allow-Headers"] = "*"
            return resp

    async def _handle_ws_command(self, request: web.Request) -> web.StreamResponse:
        try:
            client_id = request.match_info.get("clientId")
            if not client_id:
                resp = web.json_response({"error": "clientId is required"}, status=400)
                resp.headers["Access-Control-Allow-Origin"] = "*"
                resp.headers["Access-Control-Allow-Methods"] = "GET,POST,OPTIONS"
                resp.headers["Access-Control-Allow-Headers"] = "*"
                return resp
            body = await request.json()

            handler = WebSocketServer.get_connection_nowait(client_id)
            if handler is None or not getattr(handler, "websocket", None):
                resp = web.json_response({"error": f"device not connected: {client_id}"}, status=404)
                resp.headers["Access-Control-Allow-Origin"] = "*"
                resp.headers["Access-Control-Allow-Methods"] = "GET,POST,OPTIONS"
                resp.headers["Access-Control-Allow-Headers"] = "*"
                return resp

            ws = handler.websocket
            # 判断连接是否可发送
            if (hasattr(ws, "closed") and ws.closed) or (
                hasattr(ws, "state") and getattr(ws.state, "name", "") == "CLOSED"
            ):
                resp = web.json_response({"error": f"websocket closed: {client_id}"}, status=410)
                resp.headers["Access-Control-Allow-Origin"] = "*"
                resp.headers["Access-Control-Allow-Methods"] = "GET,POST,OPTIONS"
                resp.headers["Access-Control-Allow-Headers"] = "*"
                return resp

            # 将请求体原样作为JSON文本转发到WebSocket（与示例中的 sendJson 一致）
            await ws.send(json.dumps(body, ensure_ascii=False))
            resp = web.json_response({"success": True})
            resp.headers["Access-Control-Allow-Origin"] = "*"
            resp.headers["Access-Control-Allow-Methods"] = "GET,POST,OPTIONS"
            resp.headers["Access-Control-Allow-Headers"] = "*"
            return resp
        except Exception as e:
            resp = web.json_response({"error": f"failed to send: {e}"}, status=500)
            resp.headers["Access-Control-Allow-Origin"] = "*"
            resp.headers["Access-Control-Allow-Methods"] = "GET,POST,OPTIONS"
            resp.headers["Access-Control-Allow-Headers"] = "*"
            return resp

    async def _handle_options(self, request: web.Request) -> web.StreamResponse:
        # 通用的预检请求处理
        resp = web.Response(status=204)
        resp.headers["Access-Control-Allow-Origin"] = "*"
        resp.headers["Access-Control-Allow-Methods"] = "GET,POST,OPTIONS"
        resp.headers["Access-Control-Allow-Headers"] = "*"
        return resp

    def _get_websocket_url(self, local_ip: str, port: int) -> str:
        """获取websocket地址

        Args:
            local_ip: 本地IP地址
            port: 端口号

        Returns:
            str: websocket地址
        """
        server_config = self.config["server"]
        websocket_config = server_config.get("websocket")

        if websocket_config and "你" not in websocket_config:
            return websocket_config
        else:
            return f"ws://{local_ip}:{port}/xiaozhi/v1/"

    async def start(self):
        server_config = self.config["server"]
        read_config_from_api = self.config.get("read_config_from_api", False)
        host = server_config.get("ip", "0.0.0.0")
        port = int(server_config.get("http_port", 8003))

        if port:
            app = web.Application()

            if not read_config_from_api:
                # 如果没有开启智控台，只是单模块运行，就需要再添加简单OTA接口，用于下发websocket接口
                app.add_routes(
                    [
                        web.get("/xiaozhi/ota/", self.ota_handler.handle_get),
                        web.post("/xiaozhi/ota/", self.ota_handler.handle_post),
                        web.options("/xiaozhi/ota/", self.ota_handler.handle_post),
                    ]
                )
            # 添加路由
            app.add_routes(
                [
                    web.get("/mcp/vision/explain", self.vision_handler.handle_get),
                    web.post("/mcp/vision/explain", self.vision_handler.handle_post),
                    web.options("/mcp/vision/explain", self.vision_handler.handle_post),
                    # 管理端: 查询连接与HTTP->WebSocket消息桥接
                    web.get("/admin/ws/connections", self._handle_list_ws_connections),
                    web.options("/admin/ws/connections", self._handle_options),
                    # 与示例保持一致的命名和消息体：/api/commands/:clientId
                    web.post("/admin/ws/commands/{clientId}", self._handle_ws_command),
                    web.options("/admin/ws/commands/{clientId}", self._handle_options),
                    web.post("/api/commands/{clientId}", self._handle_ws_command),
                    web.options("/api/commands/{clientId}", self._handle_options),
                ]
            )

            # 运行服务
            runner = web.AppRunner(app)
            await runner.setup()
            site = web.TCPSite(runner, host, port)
            await site.start()

            # 保持服务运行
            while True:
                await asyncio.sleep(3600)  # 每隔 1 小时检查一次
