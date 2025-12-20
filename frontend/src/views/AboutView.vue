<template>
  <div>
    <h1>About</h1>
    <p>소개 페이지.</p>
  </div>
</template>

<script>
import api from '@/api/index.js'
export default {
  props: {

  },
  data(){
    return{

    };
  },
  mounted() {
    console.log("[AboutView] mounted.")
  },
}
</script>