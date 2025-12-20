<template>
  <div>
    <h1>DashBoard</h1>
    <p>대시보드.</p>
  </div>

  <div v-if="recentAgents?.length">
    <ul>
      <li v-for="recentAgent in recentAgents" :key="recentAgent.id">
        <div> 최근 접속 {{recentAgent.lastSeenAt}}</div>
        <div>
          machineGuid: {{recentAgent.machineGuid}}
        </div>
        <div>
          ipAddress: {{recentAgent.ipAddress}}
        </div>
        <div>
          hostName: {{recentAgent.hostName}}
        </div>
      </li>
    </ul>
  </div>
</template>

<script>
import api from '@/api/index.js'

export default {
  data() {
    return {
      recentAgents: [], // null 말고 배열로
    }
  },
  mounted() {
    console.log("[DashBoard] mounted.")
    this.recent()
  },
  methods: {
    async recent() {
      try {
        const resp = await api.get('/api/backend/recent')
        this.recentAgents = resp.data
        console.log("[DashBoard] recent", this.recentAgents)
      } catch (e) {
        console.log("[DashBoard] recent error", e)
      }
    },
  },
}
</script>
