---

## Features

### C++ Agent

- Windows 시스템 이벤트 수집 (예: 키 이벤트)
- 이벤트 JSON 직렬화
- WebSocket으로 백엔드에 전송
- (선택) 연결/재연결, Heartbeat

### Java Backend (Spring Boot)

- WebSocket 연결 수신 및 에이전트 세션 관리
- 이벤트 처리 및 DB 저장(MySQL)
- 실시간 스트리밍(SSE)
- Agent 제어용 REST API (Start/Stop)

### Vue Dashboard

* 실시간 이벤트 표시(SSE)
* 에이전트 연결 상태 표시
* Start/Stop 등 제어 요청(REST)

---

## Tech Stack

- **Agent**: C++ (WinAPI), `std::jthread`, semaphore
- **Backend**: Java 17, Spring Boot, WebSocket, JPA, Flyway, MySQL
- **Frontend**: Vue

---

## Getting Started

### Prerequisites

- Windows (Agent 실행용)
- JDK 17+
- Node.js (npm 또는 pnpm)
- MySQL (DB 사용 시)

### 1) Backend

```bash
# (예시) backend 디렉토리
cd backend
# (예시) Gradle
./gradlew bootRun
```

환경변수 예시:

* `OFF_THE_RECORD_DB_URL`
* `OFF_THE_RECORD_DB_USERNAME`
* `OFF_THE_RECORD_DB_PASSWORD`

Health check (Actuator 사용 시):

* `GET /actuator/health`

### 2) Frontend

```bash
cd frontend
npm install
npm run dev
```

### 3) Agent (Windows)

```bash
cd agent
# (빌드/실행 방법을 프로젝트 실제 방식대로 작성)
```

---

## API Spec

### REST API

| Name                  | Method | Endpoint                               | Request     | Response            |
| --------------------- | ------ | -------------------------------------- | ----------- | ------------------- |
| Start agent streaming | POST   | `/readStart/{machineGuid}/{commandId}` | Path params | (예시) `202 Accepted` |
| Stop agent streaming  | POST   | `/readStop/{machineGuid}/{commandId}`  | Path params | (예시) `202 Accepted` |

**Path Params**

* `machineGuid`: 에이전트 식별자
* `commandId`: 요청 식별자(클라이언트에서 생성)

**Response (example)**

```json
{
  "commandId": "abc-123",
  "accepted": true
}
```
 

---

### SSE

| Name            | Method | Endpoint                | Request     | Response            |
| --------------- | ------ | ----------------------- | ----------- | ------------------- |
| Open SSE stream | GET    | `/stream/{machineGuid}` | Path params | `text/event-stream` |

**Event (example)**

```text
event: key_event
data: {"timeStamp":"...","eventType":"KEYDOWN","keyString":"A"}
```

---

### WebSocket

* Endpoint: (예시) `/ws/agent`
* Payload: JSON string

#### Agent → Backend

| Type      | Prefix    | TaskType  | Payload        |
| --------- | --------- | --------- | -------------- |
| Key event | READ      | START     | `string(json)` |
| Heartbeat | HEARTBEAT | HEARTBEAT | `string(json)` |

**Example: Key event**

```json
{
  "prefix": "READ",
  "taskType": "START",
  "payload": "{\"timeStamp\":\"...\",\"eventType\":\"KEYDOWN\",\"keyString\":\"A\"}"
}
```

**Example: Heartbeat**

```json
{
  "prefix": "HEARTBEAT",
  "taskType": "HEARTBEAT",
  "payload": "{\"machineGuid\":\"...\",\"status\":\"OK\"}"
}
``` 
---

## Screenshots

![Dashboard](docs/images/dashboard.png)

---

## Status

In progress / Experimental

```

---

## 3) 딱 3개만 더하면 “포트폴리오 문서”가 됨
1) **Architecture 이미지 한 장** (draw.io png export)  
2) **실행 순서**(backend → frontend → agent) 실제 명령어  
3) **API 예시**(요청/응답 JSON 1개씩이라도)

---

원하면 너 레포 구조(backend/frontend/agent 디렉토리 이름이랑 실행 명령) 기준으로 위 “Getting Started”를 **진짜 실행 가능한** 형태로 딱 맞춰서 다시 정리해줄게요.
::contentReference[oaicite:0]{index=0}
```
