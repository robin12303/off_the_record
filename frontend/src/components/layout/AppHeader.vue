<template>
  <header class="app-header">
    <nav class="nav">
      <router-link to="/home" class="link" exact-active-class="active">Home</router-link>
      <router-link to="/about" class="link" exact-active-class="active">About</router-link>
      <router-link to="/dashboard" class="link" exact-active-class="active">DashBoard</router-link>
      <router-link to="/log" class="link" exact-active-class="active">Log</router-link>
      <span class="spacer"></span>

      <button class="logout" @click="logout">Logout</button>
    </nav>
  </header>
</template>

<style scoped>
.app-header { padding: 12px 16px; border-bottom: 1px solid #ddd; }
.nav { display: flex; gap: 12px; }
.link { text-decoration: none; color: inherit; opacity: 0.8; }
.active { opacity: 1; font-weight: 700; }
</style>
<script setup lang="ts">
</script>

<script setup>
import {useRouter} from "vue-router";
const router = useRouter();
function logout(){

  router.push("/");
}

</script>