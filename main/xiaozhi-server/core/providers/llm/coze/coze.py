from config.logger import setup_logging
import json
from core.providers.llm.base import LLMProviderBase

# official coze sdk for Python [cozepy](https://github.com/coze-dev/coze-py)
from cozepy import COZE_CN_BASE_URL
from cozepy import (
    Coze,
    TokenAuth,
    Message,
    ChatEventType,
)  # noqa
from core.providers.llm.system_prompt import get_system_prompt_for_function
from core.utils.util import check_model_key

TAG = __name__
logger = setup_logging()


class LLMProvider(LLMProviderBase):
    def __init__(self, config):
        self.personal_access_token = config.get("personal_access_token")
        self.bot_id = str(config.get("bot_id"))
        self.user_id = str(config.get("user_id"))
        self.session_conversation_map = {}  # 存储session_id和conversation_id的映射
        model_key_msg = check_model_key("CozeLLM", self.personal_access_token)
        if model_key_msg:
            logger.bind(tag=TAG).error(model_key_msg)

    def response(self, session_id, dialogue, device_id=None, client_id=None, headers=None, **kwargs):
        coze_api_token = self.personal_access_token
        coze_api_base = COZE_CN_BASE_URL

        last_msg = next(m for m in reversed(dialogue) if m["role"] == "user")

        coze = Coze(auth=TokenAuth(token=coze_api_token), base_url=coze_api_base)
        conversation_id = self.session_conversation_map.get(session_id)

        # 输出调试信息
        logger.bind(tag=TAG).info(f"Coze LLM调用 - Session ID: {session_id}, Device ID: {device_id}")

        # 构造额外的元数据参数
        meta_data = {}
        if device_id:
            meta_data["device_id"] = device_id
        if client_id:
            meta_data["client_id"] = client_id
        if session_id:
            meta_data["session_id"] = session_id
            
        # 添加headers信息（可选，根据需要选择性添加）
        if headers:
            # 注意：headers可能包含敏感信息，只选择性添加需要的字段
            safe_headers = {}
            if "user-agent" in headers:
                safe_headers["user_agent"] = headers["user-agent"]
            if "x-forwarded-for" in headers:
                safe_headers["forwarded_for"] = headers["x-forwarded-for"]
            if "x-real-ip" in headers:
                safe_headers["real_ip"] = headers["x-real-ip"]
            meta_data["headers"] = safe_headers

        # 构造custom_variables参数
        custom_variables = {}
        if device_id:
            custom_variables["device_id"] = device_id
        if client_id:
            custom_variables["client_id"] = client_id
        if session_id:
            custom_variables["session_id"] = session_id
            
        # 添加headers信息到custom_variables（可选）
        if headers:
            # 注意：headers可能包含敏感信息，只选择性添加需要的字段
            if "user-agent" in headers:
                custom_variables["user_agent"] = headers["user-agent"]
            if "x-forwarded-for" in headers:
                custom_variables["forwarded_for"] = headers["x-forwarded-for"]
            if "x-real-ip" in headers:
                custom_variables["real_ip"] = headers["x-real-ip"]

        # 构造parameters参数
        parameters = {}
        if device_id:
            parameters["device_id"] = device_id
        if client_id:
            parameters["client_id"] = client_id
        if session_id:
            parameters["session_id"] = session_id
            
        # 添加headers信息到parameters
        if headers:
            # 注意：headers可能包含敏感信息，只选择性添加需要的字段
            if "user-agent" in headers:
                parameters["user_agent"] = headers["user-agent"]
            if "x-forwarded-for" in headers:
                parameters["forwarded_for"] = headers["x-forwarded-for"]
            if "x-real-ip" in headers:
                parameters["real_ip"] = headers["x-real-ip"]

        # 构造additional_messages
        additional_messages = []
        for msg in dialogue:
            if msg["role"] == "user":
                additional_messages.append(Message.build_user_question_text(msg["content"]))
            elif msg["role"] == "assistant":
                additional_messages.append(Message.build_assistant_answer(msg["content"]))

        # 如果没有找到conversation_id，则创建新的对话
        if not conversation_id:
            conversation = coze.conversations.create(messages=[])
            conversation_id = conversation.id
            self.session_conversation_map[session_id] = conversation_id  # 更新映射

        # 使用v3/chat接口并传递parameters参数
        for event in coze.chat.stream(
            bot_id=self.bot_id,
            user_id=device_id;
            #user_id=self.user_id,
            additional_messages=additional_messages,
            conversation_id=conversation_id,
            parameters=parameters,  # 传递parameters参数
            meta_data=meta_data,  # 传递额外的元数据
            custom_variables=custom_variables  # 传递自定义变量
        ):
            if event.event == ChatEventType.CONVERSATION_MESSAGE_DELTA:
                print(event.message.content, end="", flush=True)
                yield event.message.content

    def response_with_functions(self, session_id, dialogue, functions=None, device_id=None, client_id=None, headers=None):
        if len(dialogue) == 2 and functions is not None and len(functions) > 0:
            # 第一次调用llm， 取最后一条用户消息，附加tool提示词
            last_msg = dialogue[-1]["content"]
            function_str = json.dumps(functions, ensure_ascii=False)
            modify_msg = get_system_prompt_for_function(function_str) + last_msg
            dialogue[-1]["content"] = modify_msg

        # 如果最后一个是 role="tool"，附加到user上
        if len(dialogue) > 1 and dialogue[-1]["role"] == "tool":
            assistant_msg = "\ntool call result: " + dialogue[-1]["content"] + "\n\n"
            while len(dialogue) > 1:
                if dialogue[-1]["role"] == "user":
                    dialogue[-1]["content"] = assistant_msg + dialogue[-1]["content"]
                    break
                dialogue.pop()

        for token in self.response(session_id, dialogue, device_id=device_id, client_id=client_id, headers=headers):
            yield token, None