<template>
  <div class="about">
    <h1>off_the_record</h1>
    <p class="subtitle">
      Windows C++ 에이전트가 OS 키 이벤트를 수집하고, Spring Boot 백엔드로 전송하여
      저장/스트리밍하는 실시간 이벤트 파이프라인 토이 프로젝트입니다.
    </p>

    <section>
      <h2>Architecture</h2>
      <ul>
        <li><b>Windows Agent (C++/WinAPI)</b>: 키 이벤트 수집 → JSON 직렬화</li>
        <li><b>WebSocket</b>: Agent → Backend 실시간 전송</li>
        <li><b>Spring Boot (Java 17)</b>: 세션 관리, 이벤트 처리/저장</li>
        <li><b>MySQL + Flyway</b>: 이벤트 영속화 및 스키마 마이그레이션</li>
        <li><b>SSE</b>: Backend → Frontend 실시간 스트리밍</li>
        <li><b>REST API</b>: Frontend → Backend 제어(Start/Stop 등)</li>
      </ul>

      <div class="flow">
        <code>
          Agent(C++) → WebSocket(JSON) → Spring Boot → MySQL(Flyway) → SSE → Vue UI
        </code>
      </div>
    </section>

    <section>
      <h2>Key Features</h2>
      <ul>
        <li>에이전트 연결/세션 관리 (machineGuid 기반)</li>
        <li>실시간 이벤트 스트리밍 (WebSocket + SSE)</li>
        <li>이벤트 저장 및 조회 (MySQL, 마이그레이션: Flyway)</li>
        <li>수집 제어 API (Start/Stop)</li>
      </ul>
    </section>

    <section>
      <h2>Why this project?</h2>
      <p>
        단순 CRUD를 넘어, OS 이벤트 처리부터 네트워크 전송, 서버 아키텍처, 데이터 저장까지
        end-to-end로 경험하고 동시성/실시간 통신 설계를 포트폴리오로 보여주기 위해 만들었습니다.
      </p>
    </section>

    <section class="notice">
      <h2>Ethics & Safety</h2>
      <ul>
        <li>본 프로젝트는 <b>학습/테스트 목적</b>이며, 사용자 <b>명시적 동의</b> 하에만 사용해야 합니다.</li>
        <li>민감정보(비밀번호/개인정보 등)를 저장하거나 전송하지 않도록 필터링/마스킹이 필요합니다.</li>
      </ul>
    </section>
  </div>
</template>

<script>
export default {
  mounted() {
    console.log("[AboutView] mounted.");
  },
};
</script>

<style scoped>
.about { max-width: 900px; margin: 0 auto; padding: 24px; }
.subtitle { margin-top: 8px; color: #666; }
section { margin-top: 24px; }
.flow { margin-top: 8px; padding: 12px; background: #f6f6f6; border-radius: 8px; }
.notice { border-left: 4px solid #ff6b6b; padding-left: 12px; }
</style>
