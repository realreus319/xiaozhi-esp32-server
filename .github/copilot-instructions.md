## GitHub Copilot Instructions for xiaozhi-esp32-server

Purpose
- Help AI agents navigate, run, and extend this multi-module repo quickly.

Architecture (monorepo)
- `main/xiaozhi-server/` Python realtime core (WebSocket + HTTP tools/OTA).
- `main/manager-api/` Java Spring Boot admin API (MySQL, Redis, Shiro, OTA, docs).
- `main/manager-web/` Vue SPA admin console (Element UI, PWA).
- `main/manager-mobile/` uni-app + Vue3 mobile console.
- Ports: ws 8000 `/xiaozhi/v1/`; http 8003 `/xiaozhi/ota/` and `/mcp/vision/explain`; API 8002 `/xiaozhi/`; Web 8001 `/`.

Core workflow (xiaozhi-server)
- Entry: `app.py` checks ffmpeg, loads config, starts WebSocket and HTTP.
- Config: load `config.yaml`; if `read_config_from_api`, merge via `config_loader.py` + `manage_api_client.py`.
- WebSocket: `core/websocket_server.py` spawns `ConnectionHandler` per client.
- Per connection: VAD → ASR → LLM → TTS, optional Intent/Memory; see `core/connection.py`.
- Hot reload: `WebSocketServer.update_config()` checks `check_vad_update`/`check_asr_update` then reinit modules via `core/utils/modules_initialize.py`.

Project conventions
- Provider pattern under `core/providers/**`; selection via `config['selected_module']`.
- Headers: `device-id`, `client-id`, `authorization` (Bearer JWT). If headers missing, extract from WS query.
- Static connection map: `WebSocketServer` keeps class-level map client-id → `ConnectionHandler` with classmethods add/remove/get/list.
- Plugins and tools: `plugins_func/functions/` + `core/providers/tools/unified_tool_handler.py`; results use `plugins_func/register.Action`.
- MQTT gateway: 16-byte header audio handled in `ConnectionHandler._process_mqtt_audio_message` with timestamp ordering.

Run/dev (Windows cmd)
- Python: `cd main\xiaozhi-server && python app.py`. Test via `test/test_page.html`; benchmark via `performance_tester.py`.
- manager-api: open `main/manager-api` in IDE, run Spring Boot; API docs `/xiaozhi/doc.html`.
- manager-web: install deps and serve/build per its `README.md`.

Key references
- Auth + WS header extraction: `core/websocket_server.py::_handle_connection`.
- Connection init and streaming: `core/connection.py`.
- Hot config update: `core/websocket_server.py::update_config`.

Gotchas
- Local ASR may be shared; remote ASR is per-connection (`_initialize_asr`).
- Check `read_config_from_api` to enable per-device overrides and report threads.
- Avoid blocking the event loop; use thread pool in `ConnectionHandler` as shown.
- Cleanup on close: cancel `timeout_task`, drain queues, close TTS.
