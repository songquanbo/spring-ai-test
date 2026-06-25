import { createRouter, createWebHistory } from 'vue-router'
import AgentSelect from '../views/AgentSelect.vue'
import ChatView from '../views/ChatView.vue'

const routes = [
  { path: '/', name: 'agents', component: AgentSelect },
  { path: '/chat', name: 'chat', component: ChatView },
]

export default createRouter({
  history: createWebHistory(),
  routes,
})
