<template>
  <header class="app-header">
    <nav class="nav">
      <div class="nav-left">
        <router-link to="/home" class="link" exact-active-class="active">Home</router-link>
        <router-link to="/about" class="link" exact-active-class="active">About</router-link>
        <router-link to="/dashboard" class="link" exact-active-class="active">DashBoard</router-link>
        <router-link to="/log" class="link" exact-active-class="active">Log</router-link>
      </div>

      <div class="nav-right">
        <button class="logout" type="button" @click="logout" aria-label="Logout">
          <span class="logout-icon" aria-hidden="true">⎋</span>
          <span>Logout</span>
        </button>
      </div>
    </nav>
  </header>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";
// import { tokenStore } from "@/tokenStore"; // 쓰면 주석 해제

const router = useRouter();

function logout() {
  // tokenStore.clear(); // access token 메모리 저장이면 이거 같이 해주는 게 깔끔
  router.push("/");
}
</script>

<style scoped>
/* 색은 변수로 빼서 다크모드도 대충 사람처럼 보이게 */
.app-header {
  --bg: rgba(255, 255, 255, 0.86);
  --border: rgba(15, 23, 42, 0.12);
  --text: #0f172a;
  --muted: rgba(15, 23, 42, 0.78);
  --hover: rgba(15, 23, 42, 0.06);

  --danger: #b42318;
  --danger-bg: rgba(180, 35, 24, 0.10);
  --danger-border: rgba(180, 35, 24, 0.28);

  position: sticky;
  top: 0;
  z-index: 10;

  background: var(--bg);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--border);
}

@media (prefers-color-scheme: dark) {
  .app-header {
    --bg: rgba(15, 23, 42, 0.72);
    --border: rgba(148, 163, 184, 0.18);
    --text: #e2e8f0;
    --muted: rgba(226, 232, 240, 0.82);
    --hover: rgba(226, 232, 240, 0.08);

    --danger: #ff6b6b;
    --danger-bg: rgba(255, 107, 107, 0.10);
    --danger-border: rgba(255, 107, 107, 0.26);
  }
}

.nav {
  display: flex;
  align-items: center;
  justify-content: space-between;

  max-width: 1100px;
  margin: 0 auto;
  padding: 12px 16px;
  gap: 12px;
}

.nav-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.link {
  color: var(--text);
  text-decoration: none;
  opacity: 0.85;

  padding: 8px 10px;
  border-radius: 10px;

  transition: opacity 140ms ease, background-color 140ms ease;
}

.link:hover {
  opacity: 1;
  background: var(--hover);
}

.active {
  opacity: 1;
  font-weight: 700;
  background: var(--hover);
}

/* ✅ 개선된 로그아웃 버튼 */
.logout {
  display: inline-flex;
  align-items: center;
  gap: 8px;

  padding: 8px 12px;
  border-radius: 999px;

  border: 1px solid var(--danger-border);
  background: var(--danger-bg);
  color: var(--danger);

  font-weight: 700;
  letter-spacing: 0.2px;

  cursor: pointer;
  user-select: none;

  transition: transform 120ms ease, background-color 160ms ease, border-color 160ms ease, box-shadow 160ms ease;
}

.logout:hover {
  border-color: var(--danger);
}

.logout:active {
  transform: translateY(1px);
}

.logout:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.35);
}

.logout-icon {
  font-size: 14px;
  line-height: 1;
  opacity: 0.9;
}

@media (max-width: 640px) {
  .nav {
    padding: 10px 12px;
  }
  .link {
    padding: 7px 9px;
  }
  .logout {
    padding: 7px 11px;
  }
}
</style>
