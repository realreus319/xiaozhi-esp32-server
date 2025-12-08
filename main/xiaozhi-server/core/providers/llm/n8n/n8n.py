import json
import time
import requests
from typing import Dict, Any, List

from config.logger import setup_logging
from core.providers.llm.base import LLMProviderBase
from core.utils.util import check_model_key

TAG = __name__
logger = setup_logging()


class LLMProvider(LLMProviderBase):
    """N8N-based LLM provider.

    This provider posts chat requests to an N8N webhook which returns a sequence
    of top-level JSON objects (begin/item/.../end). We stream the response and
    yield the item contents (text fragments) to match the other providers' API.
    """

    def __init__(self, config: Dict[str, Any]):
        self.base_url = str(config.get("base_url", "")).rstrip("/")
        self.api_key = config.get("api_key", "")
        self.mode = config.get("mode", "")
        self.yuliu1 = config.get("yuliu1", "")
        self.yuliu2 = config.get("yuliu2", "")
        self.yuliu3 = config.get("yuliu3", "")

        model_key_msg = check_model_key("N8NLLM", self.api_key)
        if model_key_msg:
            logger.bind(tag=TAG).error(model_key_msg)

    def response(self, session_id, dialogue, device_id=None, client_id=None, headers=None, **kwargs):
        try:
            # 取最后一条用户消息
            last_msg = next(m for m in reversed(dialogue) if m["role"] == "user")

            payload = {
                "action": "sendMessage",
                "sessionId": session_id,
                "chatInput": last_msg["content"],
                "times": int(kwargs.get("times", int(time.time() * 1000))),
                "mode": self.mode or kwargs.get("mode", ""),
                "api_key": self.api_key or kwargs.get("api_key", ""),
                "yuliu1": self.yuliu1,
                "yuliu2": self.yuliu2,
                "yuliu3": self.yuliu3,
                "mac": device_id or "",
            }

            headers_req = {
                "User-Agent": "xiaozhi/1.0",
                "Content-Type": "application/json",
                "Accept": "*/*",
            }
            if self.api_key:
                headers_req["Authorization"] = f"Bearer {self.api_key}"

            # 使用 requests 的 stream 模式按行读取
            with requests.post(self.base_url, headers=headers_req, json=payload, stream=True, timeout=30) as r:
                for line in r.iter_lines():
                    if not line:
                        continue
                    try:
                        text = line.decode("utf-8")
                    except Exception:
                        try:
                            text = line.decode("latin-1")
                        except Exception:
                            continue
                    text = text.strip()
                    if not text:
                        continue
                    # 解析为 JSON 顶层对象
                    try:
                        obj = json.loads(text)
                    except Exception:
                        # 忽略无法解析的行
                        continue
                    # 仅当为 item 且包含 content 时产出 content
                    if obj.get("type") == "item" and "content" in obj:
                        yield obj["content"]

        except Exception as e:
            logger.bind(tag=TAG).error(f"N8N response error: {e}")
            yield "【服务响应异常】"

    def response_with_functions(self, session_id, dialogue, functions=None, device_id=None, client_id=None, headers=None):
        # N8N workflow typically doesn't support function-calling in the same way;
        # we simply fallback to response().
        for token in self.response(session_id, dialogue, device_id=device_id, client_id=client_id, headers=headers):
            yield token, None

