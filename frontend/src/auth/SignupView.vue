<template>
  <div style="border: 1px solid #ddd; padding: 20px; border-radius: 12px;">
    <h1 style="margin: 0 0 12px;">Sign up</h1>

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
            autocomplete="new-password"
            style="padding:10px; border:1px solid #ccc; border-radius:10px;"
        />
      </label>

      <label style="display:grid; gap:6px;">
        <span>Confirm Password</span>
        <input
            v-model="form.password2"
            type="password"
            placeholder="••••••••"
            autocomplete="new-password"
            style="padding:10px; border:1px solid #ccc; border-radius:10px;"
        />
      </label>

      <p v-if="error" style="color: #c00; margin: 0;">{{ error }}</p>
      <p v-if="ok" style="color: #0a7a2f; margin: 0;">회원가입 성공</p>

      <div style="display:flex; gap:12px;">
        <button
            type="submit"
            :disabled="loading"
            style="flex:1; padding:10px; border-radius:10px; border:1px solid #333; background:#111; color:#fff; cursor:pointer;"
        >
          {{ loading ? "Signing up..." : "Create account" }}
        </button>

        <button
            type="button"
            @click="goLogin"
            style="flex:1; padding:10px; border-radius:10px; border:1px solid #333; background:#fff; color:#111; cursor:pointer;"
        >
          Back to login
        </button>
      </div>

      <small style="color:#666;">
        테스트용: 이메일은 @ 포함, 비번은 6자 이상 + 확인 일치면 통과.
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
        password2: "",
      },
      loading: false,
      error: "",
      ok: false,
    };
  },
  mounted() {
    console.log("[SignupView] mounted.");
  },
  methods: {
    goLogin() {
      this.$router.push("/");
    },
    validate() {
      if (!this.form.email.includes("@")) return "이메일 형식이 이상함";
      if (this.form.password.length < 6) return "비밀번호는 6자 이상";
      if (this.form.password !== this.form.password2) return "비밀번호 확인이 다름";
      return "";
    },

    async onSubmit() {
      this.ok = false;
      this.error = this.validate();
      if (this.error) return;

      this.loading = true;
      try {
        const payload = {
          email: this.form.email.trim(),
          password: this.form.password,
        };

        const { data } = await api.post("/api/auth/signup", payload);
        console.log("signup ok:", data);

        this.ok = true;

        // ✅ 1) signup 응답이 토큰을 주는 구조면: 바로 로그인 처리
        if (data?.accessToken) {
          localStorage.setItem("access_token", data.accessToken);
          await this.$router.push("/home");
          return;
        }

        // ✅ 2) 토큰 안 주면: 로그인 페이지로
        await this.$router.push("/login");
      } catch (e) {
        const status = e.response?.status;
        const apiErr = e.response?.data;

        console.log("signup status:", status);
        console.log("signup err:", apiErr);

        this.error =
            apiErr?.message ||
            apiErr?.code ||
            (status ? `회원가입 실패 (${status})` : "네트워크 오류");
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>
