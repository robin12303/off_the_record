<template>
  <div style="border: 1px solid #ddd; padding: 20px; border-radius: 12px;">
    <h1 style="margin: 0 0 12px;">Login</h1>

    <form @submit.prevent="onSubmit" style="display: grid; gap: 12px;">
      <label style="display:grid; gap:6px;">
        <span>Email</span>
        <input
            v-model.trim="form.email"
            type="email"
            placeholder="you@example.com"
            autocomplete="username"
            style="padding:10px; border:1px solid #ccc; border-radius:10px;"
        />
      </label>

      <label style="display:grid; gap:6px;">
        <span>Password</span>
        <input
            v-model="form.password"
            type="password"
            placeholder="••••••••"
            autocomplete="current-password"
            style="padding:10px; border:1px solid #ccc; border-radius:10px;"
        />
      </label>

      <p v-if="error" style="color: #c00; margin: 0;">{{ error }}</p>

      <div style="display:flex; gap:12px;">
        <button
            type="submit"
            :disabled="loading"
            style="flex:1; padding:10px; border-radius:10px; border:1px solid #333; background:#111; color:#fff; cursor:pointer;"
        >
          {{ loading ? "Logging in..." : "Login" }}
        </button>

        <button
            type="button"
            @click="goSignup"
            style="flex:1; padding:10px; border-radius:10px; border:1px solid #333; background:#fff; color:#111; cursor:pointer;"
        >
          Sign up
        </button>
      </div>


      <small style="color:#666;">
        테스트용: 이메일은 @ 포함, 비번은 6자 이상이면 통과.
      </small>
    </form>
  </div>
</template>

<script>
import api from "@/api/index.js";
export default {
  data() {
    return {
      form: {
        email: "",
        password: "",
      },
      loading: false,
      error: "",
    };
  },
  mounted() {
    console.log("[LoginView] mounted.");
    // this.recent(); // ❌ 없으면 지워
  },
  methods: {
    goSignup() {
      this.$router.push("/signup");
    },
    validate() {
      if (!this.form.email.includes("@")) return "이메일 형식이 이상함";
      if (this.form.password.length < 6) return "비밀번호는 6자 이상";
      return "";
    },
    async onSubmit() {
      this.error = this.validate();
      if (this.error) return;

      this.loading = true;
      try {
        const payload = {
          email: this.form.email.trim(),
          password: this.form.password,
        };

        const { data } = await api.post("/api/auth/login", payload);

        // ✅ access 토큰 저장 (로그인 상태/Authorization 헤더에 필요)
        if (data?.accessToken) {
          localStorage.setItem("access_token", data.accessToken);
        } else {
          // 서버가 토큰을 안 주는 구조면 여기서 바로 잡아야 함
          throw new Error("No accessToken in response");
        }

        // (선택) 네가 id/email을 응답에 넣었다면 저장 가능
        // localStorage.setItem("user_email", data.email);

        this.$router.push("/home");
      } catch (e) {
        const status = e.response?.status;
        const apiErr = e.response?.data;

        console.log("status:", status);
        console.log("err:", apiErr);

        // ✅ 화면에 보여줄 메시지 구성
        this.error =
            apiErr?.message ||
            apiErr?.code ||
            (status ? `로그인 실패 (${status})` : "네트워크 오류");
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>