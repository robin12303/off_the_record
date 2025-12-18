<template>
  <div class="layout">
    <AppHeader />

    <main class="content">
      <router-view />
    </main>

    <AppFooter />
  </div>
</template>

<script setup>
import AppHeader from "@/components/layout/AppHeader.vue";
import AppFooter from "@/components/layout/AppFooter.vue";
</script>

<style>
.layout { min-height: 100vh; display: flex; flex-direction: column; }
.content { flex: 1; padding: 16px; }
</style>