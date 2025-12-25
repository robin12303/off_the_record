<template>
  <header class="app-header">
    <nav class="nav">
      <div class="nav-left">
        <router-link to="/home" class="link" exact-active-class="active">Home</router-link>
        <router-link to="/about" class="link" exact-active-class="active">About</router-link>
        <router-link to="/dashboard" class="link" exact-active-class="active">DashBoard</router-link>
        <router-link to="/metrics" class="link" exact-active-class="active">Metrics</router-link>
      </div>

      <div class="nav-right">
        <button class="btn" @click="onLogout" :disabled="loggingOut">
          {{ loggingOut ? "Logging out..." : "Logout" }}
        </button>
      </div>
    </nav>
  </header>
</template>


<script>
import api from "@/api"; // 너가 만든 axios 인스턴스
export default {
  name: "AppHeader",
  data() {
    return {
      loggingOut: false,
      // localStorage 변경 감지용(같은 탭에서 logout하면 즉시 반영)
      authTick: 0,
    };
  },
  mounted() {
    // 다른 탭에서 로그인/로그아웃하면 상태 갱신
    window.addEventListener("storage", this.onStorage);
  },
  beforeUnmount() {
    window.removeEventListener("storage", this.onStorage);
  },
  methods: {
    onStorage() {
      this.authTick++; // computed 재평가 트리거
    },
    async onLogout() {
      this.loggingOut = true;
      try {
        // 서버에 refresh 쿠키 폐기 요청 (withCredentials 켜져있어야 쿠키 포함됨)
        await api.post("/api/auth/logout");
      } catch (e) {
        // 서버가 죽었든 뭐든, 클라 토큰은 지워야 로그아웃처럼 보임
        // (인간 UX를 위해서)
      } finally {
        localStorage.removeItem("access_token");
        this.authTick++;
        this.loggingOut = false;
        this.$router.push("/");
      }
    },
  },
};
</script>

<style scoped>
.app-header {
  border-bottom: 1px solid #eee;
  background: #fff;
}

.nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
}

.nav-left,
.nav-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* ✅ 왼쪽 router-link용: a태그 기본 링크룩 제거 + 버튼처럼 */
.link {
  text-decoration: none;
  color: inherit;
  padding: 8px 12px;
  border-radius: 10px;
  border: 1px solid transparent;
  display: inline-flex;
  align-items: center;
  line-height: 1;
}

.link:hover {
  background: #f5f5f5;
  border-color: #eee;
}

/* exact-active-class="active"가 붙는 클래스 */
.active {
  background: #111;
  color: #fff;
  border-color: #111;
}

/* ✅ 오른쪽 버튼 (button + router-link 둘 다 동일 룩) */
.btn {
  padding: 8px 12px;
  border: 1px solid #ddd;
  border-radius: 10px;
  background: white;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  line-height: 1;
  text-decoration: none; /* router-link가 btn일 때 밑줄 제거 */
  color: inherit;
}

.btn:hover {
  background: #f5f5f5;
}

.btn:disabled {
  opacity: .6;
  cursor: not-allowed;
}

.link-btn {
  text-decoration: none;
}
</style>
