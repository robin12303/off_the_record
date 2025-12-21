<template>
  <div class="home">
    <h1>Home</h1>

    <div class="card">
      <h2>Backend Status</h2>

      <p>
        <span class="dot" :class="statusClass"></span>
        <b>{{ statusText }}</b>
        <span v-if="httpStatus !== null" class="meta">
          (HTTP {{ httpStatus }} / {{ latencyMs }}ms)
        </span>
      </p>

      <p class="sub" v-if="lastCheckedAt">
        Last checked: {{ lastCheckedAt }}
      </p>

      <p class="err" v-if="errorMessage">
        {{ errorMessage }}
      </p>

      <!-- /actuator/health 응답 JSON 미리보기 -->
      <pre class="json" v-if="healthJson">{{ healthJson }}</pre>

      <button @click="checkBackend" :disabled="checking">
        {{ checking ? "Checking..." : "Refresh" }}
      </button>
    </div>
  </div>
</template>

<script>
import api from "@/api/index.js";

export default {
  data() {
    return {
      backendOnline: null,
      checking: false,
      lastCheckedAt: "",

      // ✅ curl -i 느낌(상태코드/응답시간/바디)
      httpStatus: null,
      latencyMs: 0,
      healthJson: "",
      errorMessage: "",
    };
  },
  computed: {
    statusText() {
      if (this.backendOnline === null) return "Unknown";
      return this.backendOnline ? "Online" : "Offline";
    },
    statusClass() {
      if (this.backendOnline === null) return "dot-unknown";
      return this.backendOnline ? "dot-ok" : "dot-bad";
    },
  },
  mounted() {
    console.log("[HomeView] mounted.");
    this.checkBackend();
  },
  methods: {
    async checkBackend() {
      this.checking = true;
      this.errorMessage = "";
      this.healthJson = "";
      this.httpStatus = null;

      const t0 = performance.now();

      try {
        // ✅ validateStatus를 열어두면(항상 true) 503 같은 응답도 catch로 안 가고 response로 받아짐
        const res = await api.get("/actuator/health", {
          timeout: 2000,
          validateStatus: () => true,
        });

        this.latencyMs = Math.round(performance.now() - t0);
        this.httpStatus = res.status;

        // /actuator/health는 보통 { status: "UP", ... } 이런 JSON
        this.healthJson = JSON.stringify(res.data, null, 2);

        // “살아있음” 판정: HTTP 200 + status=UP(가능하면)
        const status = res?.data?.status;
        this.backendOnline = res.status === 200 && (status ? status === "UP" : true);

        if (!this.backendOnline) {
          this.errorMessage = `Backend responded but not healthy (status=${status || "unknown"})`;
        }
      } catch (e) {
        this.latencyMs = Math.round(performance.now() - t0);
        this.backendOnline = false;
        this.errorMessage = `Request failed: ${e?.message || "unknown error"}`;
      } finally {
        this.lastCheckedAt = new Date().toLocaleString();
        this.checking = false;
      }
    },
  },
};
</script>

<style scoped>
.card { padding: 16px; border: 1px solid #ddd; border-radius: 12px; max-width: 520px; }
.dot { display: inline-block; width: 10px; height: 10px; border-radius: 50%; margin-right: 8px; }
.dot-ok { background: #1a7f37; }
.dot-bad { background: #c62828; }
.dot-unknown { background: #999; }
.sub { color: #777; font-size: 0.9rem; margin-top: 6px; }
.meta { margin-left: 8px; color: #666; font-size: 0.9rem; }
.err { color: #c62828; margin-top: 6px; }
.json { margin-top: 10px; padding: 10px; background: #f6f6f6; border-radius: 8px; max-height: 240px; overflow: auto; }
button { margin-top: 10px; padding: 8px 12px; border-radius: 10px; border: 1px solid #ddd; background: #fff; }
</style>
