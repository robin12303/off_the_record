<template>
  <div class="wrap">
    <div class="card">
      <h1>Sign up</h1>

      <label>
        Name (optional)
        <input v-model.trim="name" type="text" placeholder="Your name" />
      </label>

      <label>
        Email
        <input v-model.trim="email" type="email" placeholder="you@example.com" />
      </label>

      <label>
        Password
        <input v-model="password" type="password" placeholder="••••••••" />
      </label>

      <div class="btn-row">
        <button :disabled="loading || !canSubmit" @click="signup">
          {{ loading ? "Creating..." : "Create account" }}
        </button>

        <button class="secondary" :disabled="loading" @click="goLogin">
          Back
        </button>
      </div>

      <p v-if="msg" class="msg">{{ msg }}</p>
    </div>
  </div>
</template>

<script>
import api from "@/api"; // 네 api.js 경로에 맞게

export default {
  data() {
    return {
      name: "",
      email: "",
      password: "",
      loading: false,
      msg: "",
    };
  },

  computed: {
    canSubmit() {
      return this.email.trim().length > 0 && this.password.length >= 4;
    },
  },

  methods: {
    async signup() {
      this.msg = "";
      if (!this.canSubmit) return;

      // 최소 검증(진짜 검증은 서버에서)
      const email = this.email.trim();
      const name = this.name.trim();
      const password = this.password;

      if (!email.includes("@")) {
        this.msg = "❌ Invalid email";
        return;
      }
      if (password.length < 4) {
        this.msg = "❌ Password too short";
        return;
      }

      this.loading = true;

      try {
        // 백엔드가 refresh token을 HttpOnly 쿠키로 내려주면
        // 브라우저가 쿠키를 저장하려면 withCredentials: true 필요
        const res = await api.post(
            // 프록시/베이스URL 설정되어 있으면 "/auth/signin"만 써도 됨
            "/auth/signin",
            { name, email, password }, // SigninRequest(String name, String email, String password)
            { withCredentials: true }
        );

        // 백엔드가 accessToken을 JSON body로 준다고 가정
        // 예: { accessToken: "...", tokenType: "Bearer" }
        const accessToken = res?.data?.accessToken;

        if (!accessToken) {
          this.msg = "❌ Signup succeeded but no access token returned";
          return;
        }

        // 간단 버전: localStorage 저장 (편하지만 XSS에 약함)
        localStorage.setItem("accessToken", accessToken);

        // 이후 요청들에 자동으로 Authorization 붙이기(선택)
        //axios.defaults.headers.common.Authorization = `Bearer ${accessToken}`;

        this.msg = "✅ Account created";
        this.$router.push("/home");
      } catch (err) {
        // 스프링에서 400/409/500 등으로 올 수 있음
        const status = err?.response?.status;
        const serverMsg =
            err?.response?.data?.message ||
            err?.response?.data?.error ||
            err?.response?.data ||
            err?.message;

        if (status === 409) this.msg = "❌ Email already exists";
        else if (status === 400) this.msg = `❌ Bad request: ${serverMsg}`;
        else this.msg = `❌ Signup failed: ${serverMsg}`;
      } finally {
        this.loading = false;
      }
    },

    goLogin() {
      this.$router.push("/");
    },
  },
};
</script>


<style scoped>
.wrap {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background: radial-gradient(1200px 600px at 20% 10%, #dbeafe 0%, transparent 60%),
  radial-gradient(900px 500px at 90% 20%, #e9d5ff 0%, transparent 55%),
  #f8fafc;
  color: #0f172a;
  font-family: system-ui, -apple-system, Segoe UI, Roboto, sans-serif;
}

.card {
  width: min(380px, 92vw);
  padding: 26px 24px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 18px 45px rgba(15, 23, 42, 0.12), 0 2px 10px rgba(15, 23, 42, 0.06);
  backdrop-filter: blur(10px);
}

h1 {
  margin: 0 0 18px;
  font-size: 22px;
  letter-spacing: -0.3px;
}

label {
  display: grid;
  gap: 8px;
  margin: 12px 0;
  font-size: 12px;
  color: rgba(15, 23, 42, 0.85);
}

input {
  padding: 11px 12px;
  border-radius: 12px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  background: white;
  color: inherit;
  outline: none;
  transition: box-shadow 0.15s ease, border-color 0.15s ease;
}

input::placeholder {
  color: rgba(15, 23, 42, 0.35);
}

input:focus {
  border-color: rgba(74, 125, 255, 0.55);
  box-shadow: 0 0 0 4px rgba(74, 125, 255, 0.18);
}

.btn-row {
  display: flex;
  gap: 10px;
  margin-top: 16px;
}

.btn-row button {
  flex: 1 1 0;
}

button {
  padding: 11px 12px;
  border: 0;
  border-radius: 12px;
  background: linear-gradient(180deg, #4a7dff 0%, #2f67ff 100%);
  color: white;
  font-weight: 800;
  cursor: pointer;
  transition: transform 0.06s ease, filter 0.15s ease, box-shadow 0.15s ease;
  box-shadow: 0 10px 18px rgba(47, 103, 255, 0.22);
}

button:hover {
  filter: brightness(0.98);
}

button:active {
  transform: translateY(1px);
}

button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  box-shadow: none;
}

.secondary {
  background: linear-gradient(180deg, #111827 0%, #0b1220 100%);
  box-shadow: 0 10px 18px rgba(15, 23, 42, 0.18);
}

.msg {
  margin-top: 14px;
  font-size: 13px;
  line-height: 1.35;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(15, 23, 42, 0.04);
  border: 1px solid rgba(15, 23, 42, 0.08);
  color: rgba(15, 23, 42, 0.85);
}
</style>
