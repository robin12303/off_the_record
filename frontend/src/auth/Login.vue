<template>
  <div class="wrap">
    <div class="card">
      <h1>Login</h1>

      <label>
        Email
        <input v-model.trim="email" type="email" placeholder="you@example.com" />
      </label>

      <label>
        Password
        <input v-model="password" type="password" placeholder="••••••••" />
      </label>

      <button :disabled="loading || !canSubmit" @click="login">
        {{ loading ? "Logging in..." : "Login" }}
      </button>

      <p v-if="msg" class="msg">{{ msg }}</p>
    </div>
  </div>
</template>

<script>
export default {
  data() {
    return {
      email: "",
      password: "",
      loading: false,
      msg: "", // 메시지는 문자열이 편함
    };
  },
  mounted() {
    console.log("[Login.vue] mounted");
  },
  computed: {
    canSubmit() {
      return this.email.length > 0 && this.password.length > 0;
    },
  },
  methods: {
    async login() {
      this.msg = "";
      this.loading = true;

      await new Promise((r) => setTimeout(r, 600));

      if (this.password.length >= 4) {
        this.msg = `✅ Logged in as ${this.email}`;

        this.$router.push("/home");
      } else {
        this.msg = "❌ Login failed (password too short)";
      }

      this.loading = false;
    },
  },
};
</script>

<style scoped>
.wrap {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background: white;
  color: #111827;        /* 글씨 진하게 */
  font-family: system-ui, -apple-system, Segoe UI, Roboto, sans-serif;
}

.card {
  width: 360px;
  padding: 24px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.06);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(8px);
}

h1 {
  margin: 0 0 16px;
  font-size: 22px;
}

label {
  display: grid;
  gap: 8px;
  margin: 12px 0;
  font-size: 13px;
  opacity: 0.95;
}

input {
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.15);
  background: rgba(0, 0, 0, 0.25);
  color: inherit;
  outline: none;
}

input:focus {
  border-color: rgba(140, 180, 255, 0.75);
}

button {
  width: 100%;
  margin-top: 14px;
  padding: 10px 12px;
  border: 0;
  border-radius: 10px;
  background: #4a7dff;
  color: white;
  font-weight: 700;
  cursor: pointer;
}

button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.msg {
  margin-top: 12px;
  font-size: 13px;
  opacity: 0.95;
  color: #111827;        /* 메시지도 잘 보이게 */
}
</style>
