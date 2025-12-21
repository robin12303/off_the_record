<img width="965" height="744" alt="아키텍처6" src="https://github.com/user-attachments/assets/3a4eebf9-1de0-4d44-943a-759fec91d805" />
---

# Project Name

**off_the_record**
<img width="965" height="744" alt="아키텍처6" src="https://github.com/user-attachments/assets/af21582c-87d4-4c4a-b037-685af1c738b1" />

## Description

A toy project where a **C++ agent collects system events**, sends them to a **Java (Spring Boot) backend**, and visualizes them in a **Vue-based dashboard**.

## Overview

**Purpose**
A learning-oriented project focused on implementing an end-to-end event collection and transmission pipeline on Windows.

**Architecture**

* Agent (C++)
* Backend (Java)
* Dashboard (Vue)

**Status**
In progress / Experimental

## Features

### C++ Agent

* Collects system-level events on Windows
* Serializes events into JSON
* Sends data to the backend over the network

### Java Backend

* Receives events via WebSocket
* Manages agent connections and health checks
* Persists data using a relational database

### Vue Dashboard

* Displays incoming events in real time
* Shows agent connection and status information

## Tech Stack

**Agent**

* C++ (WinAPI, `std::jthread`, semaphores)

**Backend**

* Java 17
* Spring Boot
* WebSocket
* JPA / Flyway
* MySQL

**Frontend**

* Vue

## Getting Started

### Requirements

* Windows (for the agent)
* JDK 17 or higher
* Node.js with npm or pnpm
* MySQL (optional, if database persistence is enabled)

--- 






# API 명세서

# REST API

| **API** | **Method** | **Endpoint** | **Request** | **Reponse** |
| --- | --- | --- | --- | --- |
| agent 읽기 시작 | POST | `/readStart/{machineGuid}/{commandId}` | PathVariable |  |
| agent 읽기 중단 | POST | `/readStop/{machineGuid}/{commandId}` | PathVariable |  |

---

# SSE

| **API** | **Method** | **Endpoint** | **Request** | **Reponse** |
| --- | --- | --- | --- | --- |
| SSE 연결 생성 | GET | `/stream/{machineGuid}` |  |  |

---

# WebSocket

## Backend

| **API** | **Prefix** | **Task Type** |
| --- | --- | --- |
| KeyHookEvent 송신 진행 | READ | START |
| KeyHookEvent 송신 중단 | READ | STOP |
|  |  |  |

## Agent

| **API** | **Prefix** | **Task Type** | **Payload** |
| --- | --- | --- | --- |
| KeyHookEvent 전송 | READ | START | string(json) |
| Health check | HEARTBEAT | HEARTBEAT | string(json) |

---
flowchart LR
  A[Source: OS/Event] -->|Event(JSON)| B[Agent/Collector]
  B -->|WebSocket| C[Backend WS Handler]
  C --> D[Service/Processor]
  D -->|INSERT/UPSERT| E[(DB)]
  D -->|SSE/WS| F[Frontend UI]


