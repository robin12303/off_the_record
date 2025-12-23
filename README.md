# 프로젝트: Off the record (개발: 김연성)

- [GitHub 링크](https://github.com/robin12303/off_the_record)
- [Notion 링크](https://www.notion.so/2d0401ce7bab80eb8704c2c5dce7c4d5?pvs=21)

**Off the record**는 Windows 환경에서 C++로 만든 에이전트가 키보드 입력 같은 시스템 이벤트를 실시간으로 수집하고, 이를 Spring Boot 백엔드로 전송해 **MySQL에 저장**하며, 동시에 **SSE(Server-Sent Events)** 로 Vue 대시보드에 실시간 스트리밍하는 학습용 프로젝트입니다.

에이전트–서버–대시보드까지 이어지는 전체 흐름을 직접 구현하면서, **멀티스레딩/비동기 처리(WebSocket·SSE)** 기반의 이벤트 파이프라인 구조를 이해하고 검증하는 데 초점을 맞췄습니다.

> ⚠️ 윤리/보안 주의
> 
> 
> 본 프로젝트는 학습·실험 목적의 구현이며, 사용자 동의 없이 사용하면 안 됩니다.
> 
> 또한 비밀번호/개인정보 등 민감 정보가 포함될 수 있는 입력은 **저장/전송 대상에서 제외**하도록 필터링 또는 마스킹이 필요합니다.
>

---

## 소개

**Windows 키보드 이벤트를 실시간 수집 → 서버 저장/스트리밍까지 연결한 멀티스레드 이벤트 처리 시스템**

### Architecture

- **C++ Agent (Windows)** → **WebSocket (Boost.Asio)** → **Spring Boot Backend** → **SSE** → **Vue Dashboard**

### C++ Agent (Windows)

- WinAPI로 **키보드 이벤트 실시간 캡처**
- 이벤트를 **JSON 직렬화** 후 WebSocket으로 전송
- `std::jthread`로 I/O 컨텍스트를 별도 스레드에서 실행
- `std::counting_semaphore + std::mutex` 기반 **Producer–Consumer 이벤트 큐**
- 별도 스레드 **Heartbeat**로 연결 유지

### Backend (Spring Boot)

- WebSocket 세션 관리 (**WebSocketAgentHandler**)
- **RateLimiter**로 초당 이벤트 처리량 제한
- 수신 이벤트 **MySQL(JPA)** 저장 + 동시에 **SSE로 대시보드 실시간 스트리밍**
- `/readStart`, `/readStop` REST API로 스트리밍 제어

### Key Points

- 멀티스레딩 + 비동기 처리 기반의 **저지연 실시간 이벤트 파이프라인**
- WebSocket / REST / SSE를 결합한 **복합 통신 구조 설계 및 구현**

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

## 실행 방법

### Prerequisites
- Docker Desktop (추천)
- (로컬 실행 시) Windows, JDK 17+, Node.js, MySQL

---

### 1) Backend + MySQL (Docker Compose)

```bash
docker compose up --build
```

 

> Health check:
> 

```bash
curl http://localhost:8080/actuator/health
```

> Logs:
> 

```bash
docker compose logs -f backend
```

> Stop:
> 

```bash
docker compose down
```

> DB 초기화(선택):
> 

```bash
docker compose down -v
```

> Docker Compose 실행 시 DB 접속 정보는 compose 환경변수로 설정됩니다.
> 

> **테스트 품질**
> 
- `./gradlew test`로 단위/통합 테스트 실행
- Testcontainers로 로컬/환경 차이 없이 DB 의존성 재현 가능하게 구성
- WebSocket 메시지 파싱 오류(Invalid JSON)에서도 서버가 죽지 않도록 예외 안전성 검증

>
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

**API 요청 및 응답 예시**
1) Command 요청 예시 (Web/Backend → Agent)
```json
{
  "prefix": "READ",
  "commandId": "web-6855092-fd5c-4249-86d0-c54e2d3f39bc",
  "machineGuid": "********-****-****-****-********",
  "taskType": "START"
}
```
2) Command 요청 예시 (Web/Backend → Agent, STOP)  

```json
{
  "prefix": "READ",
  "commandId": "web-6855092c-fd5c-4249-86d0-c54e2d3f39bc",
  "machineGuid": "1111-11-1111-1111-111111111",
  "taskType": "START"
}
```
3) Key Event 메시지 예시

```json
{
  "prefix": "READ",
  "commandId": "N/A",
  "taskType": "EVENT",
  "machineGuid": "11111-11-11-11-1111111",
  "payload": "{\"timeStamp\":\"2025-12-21 11:12:42\",\"capsLock\":\"OFF\",\"eventType\":\"KEY_DOWN\",\"keyString\":\"a\"}"
}
```
4) Heartbeat

```json
{
  "prefix": "HEARTBEAT",
  "commandId": "N/A",
  "taskType": "HEARTBEAT",
  "payload": "{\"cpuName\":\"Intel(R) ...\",\"gpuName\":\"NVIDIA ...\",\"ramTotalMb\":32768,\"osName\":\"Windows 11\",\"osVersion\":\"10.0.22631\",\"machineGuid\":\"{A1B2-C3D4-...}\",\"hostName\":\"DESKTOP-XXXX\"}",
  "machineGuid": "{A1B2-C3D4-...}"
}

```

5) Frontend 응답 예시 (Web/Backend → Frontend, /api/recent)

```json
[
    {
        "id": 1,
         "machineGuid": "11111-11-11-11-1111111",
        "ipAddress": "0:0:0:0:0:0:0:1",
        "hostName": "device",
        "cpuName": "12th Gen Intel(R) Core(TM) i7-12700K",
        "gpuName": "Intel(R) UHD Graphics 770",
        "ramTotalMb": "32.00 GB",
        "osName": "Windows 10 Pro",
        "osVersion": "24H2 (6.3.26100.7462)",
        "lastSeenAt": "2025-12-21T10:56:09"
    }
]
```
---
## Screenshots
### [Frontend]
1) **Home.vue**
<img width="1342" height="1038" alt="about" src="https://github.com/user-attachments/assets/e63e4173-9bcb-480c-a81a-c4295864d7b4" />

2) **About.vue**
<img width="1342" height="1038" alt="about" src="https://github.com/user-attachments/assets/4de65ec2-d894-4c4d-923b-99e1ffb580c4" />

3) **DashBoard.vue**
<img width="1131" height="366" alt="dashboard" src="https://github.com/user-attachments/assets/946871c3-c613-486d-92f9-dd3b3fb8f418" />

4) **Log.vue**
<img width="1734" height="916" alt="log" src="https://github.com/user-attachments/assets/79354516-c0e6-4ce9-98dc-70f2ea593144" />

### [Agent]
1) **정보 수집 경고 팝업창**
<img width="1117" height="629" alt="warning" src="https://github.com/user-attachments/assets/470b945e-e832-4899-8a8e-7ac80c7e550e" />

2) **동의 후 실행 화면**
<img width="1118" height="631" alt="running" src="https://github.com/user-attachments/assets/163e6580-aee9-4cf0-8b15-7fae481f2265" />


## Status
In progress / Experimental
