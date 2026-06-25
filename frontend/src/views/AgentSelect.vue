<template>
  <div class="agent-select">
    <header>
      <h1>Code Assistant</h1>
      <p class="subtitle">选择一个智能体开始工作</p>
    </header>
    <div class="agent-grid">
      <div
        v-for="agent in store.agents"
        :key="agent.id"
        class="agent-card"
        @click="selectAgent(agent)"
      >
        <div class="agent-icon">{{ agentIcon(agent.id) }}</div>
        <h3>{{ agent.name }}</h3>
        <p>{{ agent.description }}</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useChatStore } from '../stores/chat'
import { fetchAgents } from '../api/chat'
import type { Agent } from '../types'

const store = useChatStore()
const router = useRouter()

onMounted(async () => {
  if (store.agents.length === 0) {
    const agents = await fetchAgents()
    store.setAgents(agents)
  }
})

function agentIcon(id: string) {
  const icons: Record<string, string> = {
    'code-assistant': '{ }',
    'customer-service': '💬',
  }
  return icons[id] || '🤖'
}

function selectAgent(agent: Agent) {
  store.selectAgent(agent)
  router.push('/chat')
}
</script>

<style scoped>
.agent-select {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 40px;
}

header {
  text-align: center;
}

h1 {
  font-size: 28px;
  font-weight: 600;
}

.subtitle {
  color: var(--text-secondary);
  margin-top: 8px;
  font-size: 15px;
}

.agent-grid {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
  justify-content: center;
}

.agent-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 28px 24px;
  width: 260px;
  cursor: pointer;
  transition: all 0.2s;
}

.agent-card:hover {
  border-color: var(--accent);
  background: var(--bg-hover);
  transform: translateY(-2px);
  box-shadow: var(--shadow);
}

.agent-icon {
  font-size: 32px;
  font-weight: 700;
  color: var(--accent);
  margin-bottom: 12px;
  font-family: 'Fira Code', 'Cascadia Code', monospace;
}

.agent-card h3 {
  font-size: 16px;
  margin-bottom: 8px;
}

.agent-card p {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
}
</style>
