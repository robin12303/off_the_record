프로젝트명 (off_the_record / redteam2)

한 줄 소개: C++ 에이전트가 이벤트를 수집하고, Java(Spring) 백엔드로 전송, Vue 대시보드에서 확인하는 토이 프로젝트.

개요

목적: (예: Windows에서 키/이벤트 수집과 전송 파이프라인을 직접 구현해보는 학습용 프로젝트)

구성: Agent(C++) + Backend(Java) + Dashboard(Vue)

상태: 

주요 기능

 C++ 에이전트


 Java 백엔드


 Vue 대시보드

기술 스택

Agent: C++ (예: WinAPI, std::jthread, semaphore 등)

Backend: Java 17, Spring Boot, WebSocket, JPA, Flyway, MySQL

Frontend: Vue

실행 방법
1) 요구사항

Windows (Agent)

JDK 17+

Node.js + npm(or pnpm)

MySQL (사용한다면)
