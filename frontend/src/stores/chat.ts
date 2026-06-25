import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Agent, Message } from '../types'
import { streamChat } from '../api/chat'

export const useChatStore = defineStore('chat', () => {
  const agents = ref<Agent[]>([])
  const currentAgent = ref<Agent | null>(null)
  const messages = ref<Message[]>([])
  const sessionId = ref(generateSessionId())
  const loading = ref(false)
  const workspace = ref('')

  function generateSessionId(): string {
    return 'session_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8)
  }

  function setAgents(list: Agent[]) {
    agents.value = list
  }

  function selectAgent(agent: Agent) {
    currentAgent.value = agent
    messages.value = []
    sessionId.value = generateSessionId()
  }

  function addMessage(msg: Message) {
    messages.value.push(msg)
  }

  function updateLastMessage(chunk: string) {
    const last = messages.value[messages.value.length - 1]
    if (last && last.role === 'assistant') {
      last.content += chunk
    }
  }

  async function sendMessage(userQuery: string) {
    if (!userQuery.trim() || loading.value) return

    addMessage({
      id: Date.now().toString(),
      role: 'user',
      content: userQuery,
      timestamp: Date.now(),
    })

    addMessage({
      id: 'assistant-' + Date.now(),
      role: 'assistant',
      content: '',
      timestamp: Date.now(),
    })

    loading.value = true
    try {
      const response = await streamChat(sessionId.value, userQuery)
      const reader = response.body!.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''
        for (const line of lines) {
          if (!line.startsWith('data:')) continue
          const text = line.slice(5)
          if (text) {
            updateLastMessage(text)
          } else {
            updateLastMessage('\n')
          }
        }
      }
    } catch (err) {
      updateLastMessage('请求失败：' + (err as Error).message)
    } finally {
      loading.value = false
    }
  }

  return {
    agents, currentAgent, messages, sessionId, loading, workspace,
    setAgents, selectAgent, addMessage, sendMessage, setWorkspace: (p: string) => { workspace.value = p },
  }
})
