<template>
  <footer class="app-footer">
    <small class="copy">© {{ new Date().getFullYear() }} RedTeam · All rights reserved.</small>
  </footer>
</template>



<style scoped>
.app-footer {
  padding: 12px 16px;                 /* ✅ px 빠짐 수정 */
  border-top: 1px solid rgba(0,0,0,.12);
  opacity: 0.85;
  text-align: center;                 /* ✅ 깔끔하게 가운데 */
}

.copy{
  font-size: 16px;
  line-height: 1.4;
  letter-spacing: 0.6px;
  font-weight: 500;
}

</style>
<script setup lang="ts">
</script>