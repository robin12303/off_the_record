<template>
  <div>
    <h1>Log</h1>
    <p>첫 페이지.</p>

    <div>
      <input
          v-model="input"
          placeholder="input machine_guid"
          @keydown.enter.prevent="submit"
      />
    </div>

    <!-- pre 추천: \n, \t 그대로 보임 -->
    <pre class="log">text_log:
{{ text_log }}</pre>
  </div>
</template>

<script>
import api from '@/api/index.js'
export default {
  data() {
    return {
      API_BASE: import.meta.env.VITE_API_URL,
      input: "",
      text_log: "",
      clientId: null,
      es: null,
      recentAgents: null, // 이거도 선언해두는 게 좋음
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
    async submit() {
      console.log("[LogView] submit:", this.input);

      try {
        const resp = await api.post(
            `/api/backend/readStart/${encodeURIComponent(this.input)}/${encodeURIComponent(this.clientId)}`
        );
        this.recentAgents = resp.data;

        const sse_url = `${this.API_BASE}/api/sse/stream/${encodeURIComponent(this.input)}`;
        if (this.es) this.es.close(); // 중복 연결 방지
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
      }
    },
  },
}
</script>

<style scoped>
.log {
  white-space: pre-wrap; /* \n 줄바꿈 + 긴 줄도 wrap */
}
</style>
