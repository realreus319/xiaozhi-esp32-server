import json
import aiohttp
TAG = __name__
EMOJI_MAP = {
    "😂": "laughing",
    "😭": "crying",
    "😠": "angry",
    "😔": "sad",
    "😍": "loving",
    "😲": "surprised",
    "😱": "shocked",
    "🤔": "thinking",
    "😌": "relaxed",
    "😴": "sleepy",
    "😜": "silly",
    "🙄": "confused",
    "😶": "neutral",
    "🙂": "happy",
    "😆": "laughing",
    "😳": "embarrassed",
    "😉": "winking",
    "😎": "cool",
    "🤤": "delicious",
    "😘": "kissy",
    "😏": "confident",
}
EMOJI_RANGES = [
    (0x1F600, 0x1F64F),
    (0x1F300, 0x1F5FF),
    (0x1F680, 0x1F6FF),
    (0x1F900, 0x1F9FF),
    (0x1FA70, 0x1FAFF),
    (0x2600, 0x26FF),
    (0x2700, 0x27BF),
]


def get_string_no_punctuation_or_emoji(s):
    """去除字符串首尾的空格、标点符号和表情符号"""
    chars = list(s)
    # 处理开头的字符
    start = 0
    while start < len(chars) and is_punctuation_or_emoji(chars[start]):
        start += 1
    # 处理结尾的字符
    end = len(chars) - 1
    while end >= start and is_punctuation_or_emoji(chars[end]):
        end -= 1
    return "".join(chars[start : end + 1])


def is_punctuation_or_emoji(char):
    """检查字符是否为空格、指定标点或表情符号"""
    # 定义需要去除的中英文标点（包括全角/半角）
    punctuation_set = {
        "，",
        ",",  # 中文逗号 + 英文逗号
        "。",
        ".",  # 中文句号 + 英文句号
        "！",
        "!",  # 中文感叹号 + 英文感叹号
        "“",
        "”",
        '"',  # 中文双引号 + 英文引号
        "：",
        ":",  # 中文冒号 + 英文冒号
        "-",
        "－",  # 英文连字符 + 中文全角横线
        "、",  # 中文顿号
        "[",
        "]",  # 方括号
        "【",
        "】",  # 中文方括号
    }
    if char.isspace() or char in punctuation_set:
        return True
    return is_emoji(char)


async def get_emotion(conn, text):
    """获取文本内的情绪消息"""
    emoji = "🙂"
    emotion = "happy"
    for char in text:
        if char in EMOJI_MAP:
            emoji = char
            emotion = EMOJI_MAP[char]
            break
    try:
        # 惰性导入，避免循环依赖
        from core.websocket_server import WebSocketServer

        # 优先从 ConnectionHandler 获取 client_id（在握手时已记录）
        client_id = getattr(conn, "client_id", None)
        if not client_id:
            # 兜底：从 headers 或 websocket 上尝试获取
            headers = getattr(conn, "headers", {}) or {}
            client_id = headers.get("client-id") or headers.get("device-id")

        # 获取设备MAC地址（优先 headers 的 device-id，其次 conn.device_id）
        mac = headers.get("device-id") if isinstance(headers, dict) else None
        if not mac:
            mac = getattr(conn, "device_id", None)

        if not client_id:
            print("发送情绪表情失败：缺少 client_id")
            return

        # 上报到外部接口（n8n），包含 client_mac / client_id / emoji 标签
        try:
            webhook_url = "https://n8n.leefun.top/webhook/api/xiaozhiqx"
            params = {
                "client_mac": mac or "",
                "client_id": client_id,
                "emoji": emotion,
            }
            timeout = aiohttp.ClientTimeout(total=3)
            async with aiohttp.ClientSession(timeout=timeout) as session:
                async with session.get(webhook_url, params=params) as resp:
                    print(
                        f"Webhook上报: status={resp.status}, mac={params['client_mac']}, client_id={client_id}, emoji={emotion}"
                    )
        except Exception as hook_err:
            print(f"Webhook上报失败: {hook_err}")

        # 使用 nowait 便捷读取，验证是否能取到保存的连接
        all_ids = WebSocketServer.list_client_ids_nowait()
        print(f"连接映射校验: 请求client_id={client_id}, 当前映射keys={all_ids}")
        target_conn = WebSocketServer.get_connection_nowait(client_id)
        if target_conn is None:
            print(f"未找到已保存的连接: client_id={client_id}, 可用keys={all_ids}")
            # 退回到当前连接发送，便于功能不中断且协助验证
            target_ws = getattr(conn, "websocket", None)
        else:
            target_ws = getattr(target_conn, "websocket", None)

        # 安全发送
        if not target_ws:
            print(f"目标连接无websocket: client_id={client_id}")
            return
        can_send = True
        try:
            if hasattr(target_ws, "closed") and target_ws.closed:
                can_send = False
            elif hasattr(target_ws, "state") and getattr(target_ws.state, "name", "") == "CLOSED":
                can_send = False
        except Exception:
            pass
        if not can_send:
            print(f"目标websocket已关闭: client_id={client_id}")
            return

        await target_ws.send(
            json.dumps(
                {
                    "type": "llm",
                    "text": emoji,
                    "emotion": emotion,
                    "session_id": conn.session_id,
                }
            )
        )
        print(f"已向 client_id={client_id} 发送情绪表情: {emoji}")
    except Exception as e:
        print(f"发送情绪表情失败，错误:{e}")
    return


def is_emoji(char):
    """检查字符是否为emoji表情"""
    code_point = ord(char)
    return any(start <= code_point <= end for start, end in EMOJI_RANGES)


def check_emoji(text):
    """去除文本中的所有emoji表情"""
    return ''.join(char for char in text if not is_emoji(char) and char != "\n")
