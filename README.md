# off_the_record

Windows 환경에서 **C++ Agent가 시스템 이벤트(예: 키보드 입력)를 수집**하고,  
이를 **Spring Boot Backend로 전송**하여 **저장(MySQL)** 및 **실시간 스트리밍(SSE)** 으로 **Vue Dashboard**에서 확인하는 학습용 프로젝트입니다.

> ⚠️ **윤리/보안 주의**  
> 본 프로젝트는 학습/실험 목적이며, 사용자 동의 없이 사용하면 안 됩니다.  
> 민감정보(비밀번호/개인정보 등)는 저장/전송하지 않도록 필터링/마스킹이 필요합니다.

---

## Architecture
<img width="965" height="744" alt="아키텍처6" src="https://github.com/user-attachments/assets/e2550f4f-0b7b-4f1f-8d2b-7527f8a54081" />

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

- 실시간 이벤트 표시(SSE)
- 에이전트 연결 상태 표시
- Start/Stop 등 제어 요청(REST)

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
$env:OFF_THE_RECORD_DB_URL="jdbc:mysql://localhost:3306/off_the_record"
$env:OFF_THE_RECORD_DB_USERNAME="root"
$env:OFF_THE_RECORD_DB_PASSWORD="1234"
.\gradlew clean bootRun --args="--spring.profiles.active=local"
```

환경변수 예시:

* `OFF_THE_RECORD_DB_URL`
* `OFF_THE_RECORD_DB_USERNAME`
* `OFF_THE_RECORD_DB_PASSWORD`

Health check (Actuator 사용 시):

* `GET /actuator/health`

### 2) Frontend

```bash
$env:VITE_API_URL="http://localhost:8080"
npm run dev
```

### 3) Agent (Windows / Visual Studio)

#### Prerequisites
- Windows 10/11
- Visual Studio (C++ Desktop Development workload)
- **Boost C++ Libraries** (required)

#### Build / Run
1. Repository를 클론합니다.
2. `agent/agent.slnx` 를 Visual Studio로 엽니다.
3. 빌드 구성(예: Debug/x64)을 선택합니다.
4. Build 후 실행합니다.

#### Boost Setup (Windows)
이 프로젝트는 Boost를 사용하므로, 아래 중 한 방식으로 Boost include/lib 경로가 잡혀 있어야 합니다.

**Option A) vcpkg 사용 (권장)**
1. vcpkg 설치 후 Boost 설치:
   ```bash
   vcpkg install boost
Visual Studio와 연동:

bash
코드 복사
vcpkg integrate install
Visual Studio에서 다시 열고 빌드합니다.

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
| Key event | READ      | STOP      | `string(json)` |
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
