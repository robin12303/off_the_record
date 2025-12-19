<template>
  <div class="layout">
    <AppHeader v-if="showHeader" />

    <main class="content">
      <router-view />
    </main>

    <AppFooter v-if="showFooter" />
  </div>
</template>

<script setup>
import { computed } from "vue";
import { useRoute } from "vue-router";
import AppHeader from "@/components/layout/AppHeader.vue";
import AppFooter from "@/components/layout/AppFooter.vue";

const route = useRoute();

const showHeader = computed(() => !route.meta?.hideHeader);
const showFooter = computed(() => !route.meta?.hideFooter);
</script>

<style>
.layout { min-height: 100vh; display: flex; flex-direction: column; }
.content { flex: 1; padding: 16px; }
</style>
