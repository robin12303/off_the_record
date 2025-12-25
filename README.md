# 프로젝트: Off the record (개발: 김연성)[![agent-v1-ci](https://github.com/robin12303/off_the_record3/actions/workflows/agent_v1_ci.yml/badge.svg)](https://github.com/robin12303/off_the_record3/actions/workflows/agent_v1_ci.yml)[![backend](https://github.com/robin12303/off_the_record3/actions/workflows/backend_ci.yml/badge.svg)](https://github.com/robin12303/off_the_record3/actions/workflows/backend_ci.yml)[![frontend-ci](https://github.com/robin12303/off_the_record3/actions/workflows/frontend_ci.yml/badge.svg)](https://github.com/robin12303/off_the_record3/actions/workflows/frontend_ci.yml) 
- [GitHub 링크](https://github.com/robin12303/off_the_record)
- [Notion 링크](https://www.notion.so/2d0401ce7bab80eb8704c2c5dce7c4d5?pvs=21)

**Off the record**는 Windows 환경의 C++ 에이전트가 시스템 이벤트를 실시간으로 집계하고, Spring Boot 백엔드로 전송해 **MySQL에 저장**한 뒤, **SSE(Server-Sent Events)** 로 Vue 대시보드에 실시간 스트리밍하는 학습용 프로젝트입니다.

에이전트–서버–대시보드까지 이어지는 전체 데이터 흐름을 직접 구현하면서 **멀티스레딩(Producer–Consumer) + 비동기 I/O(WebSocket/SSE)** 기반의 이벤트 파이프라인을 설계·검증하는 데 초점을 맞췄습니다.

> ⚠️ 윤리/보안 (중요)
>
> * 본 프로젝트는 **학습/실험 목적**이며, **사용자 명시적 동의 없이 사용하지 않습니다.**
> * 에이전트는 **입력 “내용”을 수집하지 않으며**, 전송 데이터는 **집계/메트릭 중심**으로 제한합니다.
>   (민감 정보가 포함될 수 있는 원문 입력/키 코드 등은 저장·전송하지 않도록 설계)
> * 실제 사용 환경에서는 **추가적인 안전장치(화이트리스트, 마스킹, 최소 권한, 보관 기간 제한)** 가 필요합니다.

---

## Overview

**Windows → WebSocket → Backend 저장/스트리밍 → SSE 대시보드**로 이어지는 실시간 이벤트 파이프라인

### 아키텍처
<img width="965" height="744" alt="아키텍처6" src="https://github.com/user-attachments/assets/e2550f4f-0b7b-4f1f-8d2b-7527f8a54081" />

### C++ Agent (Windows)

* WinAPI 기반 이벤트를 **실시간 집계/직렬화(JSON)** 후 WebSocket 전송
* `std::jthread`로 I/O 컨텍스트를 별도 스레드에서 실행
* `std::counting_semaphore + std::mutex` 기반 **Producer–Consumer 큐**
* 별도 스레드 **Heartbeat**로 연결 유지/복구

### Backend (Spring Boot)

* WebSocket 세션 관리 (**WebSocketAgentHandler**)
* **Rate Limiting**으로 과도한 이벤트 유입 제어
* 수신 이벤트 **MySQL 저장 + SSE로 실시간 스트리밍**
* `/metricsStart`, `/metricsStop` REST API로 스트리밍 제어

### Key Points

* 멀티스레딩 + 비동기 통신을 결합한 **저지연 이벤트 파이프라인**
* WebSocket / REST / SSE를 결합한 **복합 통신 구조 설계 및 구현**
* CI 및 테스트로 동작을 검증하고, Docker Compose로 로컬 재현 가능하도록 구성

---

## Features

### C++ Agent (Windows)

* Windows 이벤트를 **실시간 집계/메트릭화** (예: 입력 발생 빈도, 세션/상태 이벤트)
* 이벤트를 **JSON으로 직렬화**
* **WebSocket(Boost.Asio/Beast)** 로 백엔드 전송
* 연결 유지/복구: **Heartbeat + 재연결 로직** (선택)

### Java Backend (Spring Boot)

* WebSocket 수신 및 **에이전트 세션 관리**
* 이벤트 **검증/레이트 리밋** 후 DB 저장 (**MySQL, JPA**)
* 대시보드로 **SSE 실시간 스트리밍**
* Agent 제어용 REST API (**Start/Stop**)

### Vue Dashboard

* SSE 기반 **실시간 이벤트/메트릭 시각화**
* 에이전트 연결 상태(Online/Offline, last heartbeat) 표시
* Start/Stop 등 제어 요청(REST)

---

## Tech Stack

* **Agent**: C++20, WinAPI, `std::jthread`, `std::counting_semaphore`, Boost.Asio/Beast/JSON
* **Backend**: Java 17, Spring Boot, WebSocket, SSE, JPA, Flyway, MySQL
* **Frontend**: Vue (Vite)



## 실행 방법

### 요구사항

* Docker Desktop (추천)
* 로컬 실행 시: Windows, JDK 17+, Node.js 18+, MySQL

---

## 1) Backend + MySQL (Docker Compose)

```bash
docker compose up --build
```

확인:

```bash
curl http://localhost:8080/actuator/health
```

로그:

```bash
docker compose logs -f backend
```

종료:

```bash
docker compose down
```

DB 초기화(선택):

```bash
docker compose down -v
```

> Docker Compose 실행 시 DB 접속 정보는 compose 환경변수로 설정됩니다.

### 테스트 (Backend)

```bash
./gradlew test
```

* 단위/통합 테스트 포함
* Testcontainers로 DB 의존성을 재현 가능하게 구성
* Invalid JSON(WebSocket) 등 예외 상황에서 서버가 종료되지 않도록 검증

---

## 2) Frontend (Vue)

```powershell
$env:VITE_API_URL="http://localhost:8080"
npm ci
npm run dev
```

> 기본 포트: `http://localhost:5173` (Vite 기본)

---

## 3) Agent (Windows)

### Prerequisites

* Windows 10/11
* Visual Studio 2022 (Desktop development with C++)
* Boost (Boost.Asio/Beast/JSON 사용)

### Build / Run

1. Repository 클론
2. `agent_v1/agent_v1/agent_v1.vcxproj` (또는 `.sln/.slnx`)를 Visual Studio로 열기
3. 구성: `x64` + `Debug`(또는 `Release`) 선택
4. 빌드 후 실행

### Boost Setup (권장: vcpkg)

> 아래 방식은 Boost include/lib 경로를 자동으로 구성합니다.

```powershell
git clone https://github.com/microsoft/vcpkg extern/vcpkg
.\extern\vcpkg\bootstrap-vcpkg.bat
.\extern\vcpkg\vcpkg.exe install boost-asio boost-beast boost-json --triplet x64-windows
.\extern\vcpkg\vcpkg.exe integrate install
```

Visual Studio를 다시 열고 빌드합니다.

---

### CI

* GitHub Actions에서 Windows 빌드/테스트가 자동 실행됩니다. (README 상단 배지 참고)

---


## API 명세서

### REST API

| Name                  | Method | Endpoint                               | Request     | Response            |
| --------------------- | ------ | -------------------------------------- | ----------- | ------------------- |
| Start agent streaming | POST   | `/matricsStart/{machineUuid}/{commandId}` | Path params | (예시) `202 Accepted` |
| Stop agent streaming  | POST   | `/matricsStop/{machineUuid}/{commandId}`  | Path params | (예시) `202 Accepted` |

**Path Params**

* `machineUuid`: 에이전트 식별자
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
event: metric_event
data: {"timeStamp":"...","windowMs":"KEYDOWN","keystrokes":"A"}
```

---

## API Spec

### 공통

* Base URL: `http://localhost:8080`
* `machineUuid`: 에이전트 식별자(UUID)
* `commandId`: 요청 식별자(UUID, 클라이언트 생성)

---

## REST API (Agent Control)

| Name                 | Method | Endpoint                                   | Response       |
| ----------------------- | ------ | ------------------------------------------ | -------------- |
| 에이전트 스트리밍 시작 | POST   | `/metrics/start/{machineUuid}/{commandId}` | `202 Accepted` |
| 에이전트 스트리밍 종료 | POST   | `/metrics/stop/{machineUuid}/{commandId}`  | `202 Accepted` |

**Response example**

```json
{
  "commandId": "abc-123",
  "accepted": true
}
```

---

## SSE (Dashboard Streaming)

| Name            | Method | Endpoint                | Response            |
| --------------- | ------ | ----------------------- | ------------------- |
| Open SSE stream | GET    | `/stream/{machineUuid}` | `text/event-stream` |

**Event example**

```text
event: metric_event
data: {"timeStamp":"2025-12-21T11:12:42Z","eventType":"KEYDOWN","count":1}
```

---

## WebSocket (Agent ↔ Backend)

* Endpoint: `/ws/agent`
* Payload: JSON

### Message Envelope (권장)

```json
{
  "type": "METRIC_EVENT",
  "machineUuid": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "commandId": "optional-uuid",
  "payload": { }
}
```

### 1) Command (Backend → Agent)

**START**

```json
{
  "type": "COMMAND",
  "commandId": "web-6855092c-fd5c-4249-86d0-c54e2d3f39bc",
  "machineUuid": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "payload": { "action": "START" }
}
```

**STOP**

```json
{
  "type": "COMMAND",
  "commandId": "web-6855092c-fd5c-4249-86d0-c54e2d3f39bc",
  "machineUuid": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "payload": { "action": "STOP" }
}
```

### 2) Metric Event (Agent → Backend)

```json
{
  "type": "METRIC_EVENT",
  "machineUuid": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "payload": {
    "timeStamp": "2025-12-21T11:12:42Z",
    "windowMs": 1000,
    "keystrokes": 12
  }
}
```

### 3) Heartbeat (Agent → Backend)

```json
{
  "type": "HEARTBEAT",
  "machineUuid": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "payload": {
    "hostName": "DESKTOP-XXXX",
    "osName": "Windows 11",
    "osVersion": "10.0.22631",
    "cpuName": "Intel(R) ...",
    "gpuName": "NVIDIA ...",
    "ramTotalMb": 32768
  }
}
```

---

## REST (Frontend 용 예시)

### Recent agents

`GET /api/recent`

**Response example**

```json
[
  {
    "id": 1,
    "machineUuid": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
    "ipAddress": "::1",
    "hostName": "device",
    "cpuName": "12th Gen Intel(R) Core(TM) i7-12700K",
    "gpuName": "Intel(R) UHD Graphics 770",
    "ramTotalMb": 32768,
    "osName": "Windows 10 Pro",
    "osVersion": "24H2",
    "lastSeenAt": "2025-12-21T10:56:09"
  }
]
```

## Screenshots
### [Frontend]
1) **Home.vue**
<img width="1342" height="1038" alt="about" src="https://github.com/user-attachments/assets/e63e4173-9bcb-480c-a81a-c4295864d7b4" />

2) **About.vue**
<img width="1342" height="1038" alt="about" src="https://github.com/user-attachments/assets/4de65ec2-d894-4c4d-923b-99e1ffb580c4" />

3) **DashBoard.vue**
<img width="1131" height="366" alt="dashboard" src="https://github.com/user-attachments/assets/946871c3-c613-486d-92f9-dd3b3fb8f418" />

4) **Metrics.vue**
<img width="519" height="663" alt="metrics_view" src="https://github.com/user-attachments/assets/592209a5-1683-469e-a471-454027659647" />

### [Agent]
1) **정보 수집 경고 팝업창**
<img width="1117" height="629" alt="warning" src="https://github.com/user-attachments/assets/470b945e-e832-4899-8a8e-7ac80c7e550e" />

2) **동의 후 실행 화면**
<img width="1118" height="631" alt="running" src="https://github.com/user-attachments/assets/163e6580-aee9-4cf0-8b15-7fae481f2265" />


## Status
In progress / Experimental
