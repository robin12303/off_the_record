<template>
  <div class="page">
    <header class="header">
      <div>
        <h1 class="title">DashBoard</h1>
        <p class="subtitle">최근 접속 에이전트 목록</p>
      </div>

      <button class="refresh-btn" type="button" @click="recent" :disabled="isLoading">
        {{ isLoading ? "Loading..." : "Refresh" }}
      </button>
    </header>

    <section v-if="recentAgents?.length" class="grid">
      <article v-for="a in recentAgents" :key="a.id" class="card">
        <div class="card-top">
          <div class="pill">최근 접속</div>
          <div class="time">{{ a.lastSeenAt }}</div>
        </div>

        <div class="rows">
          <div class="row">
            <div class="label">machineUuid</div>
            <div class="value mono wrap" :title="a.machineUuid">{{ a.machineUuid }}</div>
          </div>
          <div class="row">
            <div class="label">ipAddress</div>
            <div class="value mono wrap" :title="a.ipAddress">{{ a.ipAddress }}</div>
          </div>

          <div class="row">
            <div class="label">hostName</div>
            <div class="value">{{ a.hostName }}</div>
          </div>
        </div>
      </article>
    </section>

    <section v-else class="empty">
      <div class="empty-card">
        <div class="empty-title">표시할 에이전트가 없습니다</div>
        <div class="empty-desc">서버에 최근 접속 기록이 없거나, 아직 수집이 안 됐습니다.</div>
      </div>
    </section>
  </div>
</template>

<script>
import api from "@/api/index.js";

export default {
  data() {
    return {
      recentAgents: [],
      isLoading: false,
    };
  },
  mounted() {
    console.log("[DashBoard] mounted.");
    this.recent();
  },
  methods: {
    async recent() {
      if (this.isLoading) return;
      this.isLoading = true;

      try {
        const resp = await api.get("/api/backend/recent");
        this.recentAgents = resp.data ?? [];
        console.log("[DashBoard] recent", this.recentAgents);
      } catch (e) {
        console.log("[DashBoard] recent error", e);
      } finally {
        this.isLoading = false;
      }
    },
  },
};
</script>

<style scoped>
.page {
  max-width: 1100px;
  margin: 0 auto;
  padding: 18px 16px 28px;
}

.header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.title {
  margin: 0;
  font-size: 24px;
  letter-spacing: 0.2px;
}

.subtitle {
  margin: 6px 0 0;
  opacity: 0.75;
}

.refresh-btn {
  height: 42px;
  padding: 0 14px;
  border-radius: 12px;

  border: 1px solid rgba(15, 23, 42, 0.14);
  background: rgba(15, 23, 42, 0.06);

  cursor: pointer;
  font-weight: 800;

  transition: background-color 140ms ease, transform 120ms ease, box-shadow 160ms ease;
}

.refresh-btn:hover:not(:disabled) {
  background: rgba(15, 23, 42, 0.10);
}

.refresh-btn:active:not(:disabled) {
  transform: translateY(1px);
}

.refresh-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

@media (max-width: 980px) {
  .grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 640px) {
  .grid {
    grid-template-columns: 1fr;
  }
}

.card {
  padding: 14px;
  border-radius: 16px;

  background: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(15, 23, 42, 0.12);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.06);

  backdrop-filter: blur(10px);

  transition: transform 140ms ease, box-shadow 160ms ease;
}

.card:hover {
  transform: translateY(-1px);
  box-shadow: 0 14px 28px rgba(15, 23, 42, 0.10);
}

@media (prefers-color-scheme: dark) {
  .card {
    background: rgba(15, 23, 42, 0.55);
    border-color: rgba(148, 163, 184, 0.18);
    box-shadow: 0 10px 24px rgba(0, 0, 0, 0.35);
  }
  .card:hover {
    box-shadow: 0 14px 28px rgba(0, 0, 0, 0.42);
  }
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}

.pill {
  font-size: 12px;
  font-weight: 800;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(59, 130, 246, 0.12);
  border: 1px solid rgba(59, 130, 246, 0.22);
}

.time {
  font-size: 12px;
  opacity: 0.75;
  white-space: nowrap;
}

.rows {
  display: grid;
  gap: 10px;
}

.row {
  display: grid;
  grid-template-columns: 110px 1fr;
  gap: 10px;
  align-items: baseline;
}

.label {
  opacity: 0.65;
  font-weight: 700;
  font-size: 12px;
}

.value {
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-weight: 650;
}

.empty {
  margin-top: 14px;
}

.empty-card {
  padding: 18px;
  border-radius: 16px;
  border: 1px dashed rgba(15, 23, 42, 0.22);
  background: rgba(15, 23, 42, 0.03);
}

.empty-title {
  font-weight: 900;
}

.empty-desc {
  margin-top: 6px;
  opacity: 0.75;
}

/* 긴 문자열(IPv6, GUID 등)만 줄바꿈 허용 */
.wrap {
  white-space: normal;          /* ✅ 줄바꿈 허용 */
  overflow: visible;            /* ✅ 잘리지 않게 */
  text-overflow: clip;          /* ✅ ... 제거 */
  overflow-wrap: anywhere;      /* ✅ 어디서든 끊기 (IPv6에도 좋음) */
  word-break: break-word;       /* ✅ 안전장치 */
}
</style>
