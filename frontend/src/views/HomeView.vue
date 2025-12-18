<template>
  <div>
    <h1>Home</h1>
    <p>첫 페이지.</p>
  </div>
</template>

<script>
import { api } from '@/libs/api.js'
export default {
  props: {

  },
  data(){
    return{

    };
  },
  mounted() {
    console.log("[HomeView] mounted.")
  }
}
</script>