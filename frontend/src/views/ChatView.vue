<template>
  <div class="chat-view">
    <aside class="sidebar">
      <div class="sidebar-header">
        <button class="back-btn" @click="goBack">← 返回</button>
        <h3>{{ store.currentAgent?.name }}</h3>
      </div>

      <div class="workspace-section">
        <label>工作空间</label>
        <div class="workspace-row">
          <input
            type="text"
            :value="store.workspace"
            placeholder="未设置"
            readonly
          />
          <button class="btn-secondary" @click="pickFolder">选择目录</button>
        </div>
      </div>

      <div class="session-info">
        <label>会话 ID</label>
        <code>{{ store.sessionId.slice(0, 16) }}...</code>
      </div>
    </aside>

    <main class="main">
      <div class="messages" ref="messagesRef">
        <div
          v-for="msg in store.messages"
          :key="msg.id"
          :class="['message', msg.role]"
        >
          <div class="avatar">{{ msg.role === 'user' ? 'U' : 'AI' }}</div>
          <div class="bubble">
            <div
              v-if="msg.content"
              class="markdown-body"
              v-html="renderMarkdown(msg.content)"
            ></div>
            <div v-else-if="store.loading && msg.role === 'assistant'" class="thinking">思考中...</div>
          </div>
        </div>
        <div v-if="store.messages.length === 0" class="empty-state">
          <p>输入消息开始与 {{ store.currentAgent?.name }} 对话</p>
        </div>
      </div>

      <div class="input-area">
        <textarea
          v-model="inputText"
          placeholder="输入你的问题..."
          @keydown.enter.exact="handleSend"
          :disabled="store.loading"
          rows="3"
        ></textarea>
        <button
          class="send-btn"
          @click="handleSend"
          :disabled="store.loading || !inputText.trim()"
        >
          {{ store.loading ? '发送中...' : '发送' }}
        </button>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useChatStore } from '../stores/chat'
import { getWorkspace, setWorkspace } from '../api/chat'
import { marked } from 'marked'
import { markedHighlight } from 'marked-highlight'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'

marked.use(markedHighlight({
  langPrefix: 'hljs language-',
  highlight(code: string, lang: string) {
    if (lang && hljs.getLanguage(lang)) {
      return hljs.highlight(code, { language: lang }).value
    }
    return hljs.highlightAuto(code).value
  }
}))
marked.setOptions({ breaks: true, async: false })

function renderMarkdown(content: string): string {
  return marked.parse(content) as string
}

const router = useRouter()
const store = useChatStore()
const inputText = ref('')
const messagesRef = ref<HTMLElement | null>(null)

onMounted(async () => {
  if (!store.currentAgent) {
    router.push('/')
    return
  }
  try {
    store.workspace = await getWorkspace()
  } catch {
    // ignore
  }
})

async function pickFolder() {
  try {
    const dir = await (window as any).showDirectoryPicker()
    const path = dir.name
    store.workspace = path
    await setWorkspace(path)
  } catch {
    // user cancelled or API not supported
    const input = document.createElement('input')
    input.type = 'file'
    input.webkitdirectory = true
    input.onchange = async () => {
      const path = (input.files?.[0] as any)?.webkitRelativePath?.split('/')[0]
      if (path) {
        store.workspace = path
        await setWorkspace(path)
      }
    }
    input.click()
  }
}

async function handleSend() {
  const text = inputText.value.trim()
  if (!text || store.loading) return
  inputText.value = ''
  await store.sendMessage(text)
  await nextTick()
  scrollToBottom()
}

function scrollToBottom() {
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

function goBack() {
  router.push('/')
}
</script>

<style scoped>
.chat-view {
  display: flex;
  height: 100%;
}

.sidebar {
  width: 280px;
  background: var(--bg-secondary);
  border-right: 1px solid var(--border);
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  flex-shrink: 0;
}

.sidebar-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.sidebar-header h3 {
  font-size: 15px;
}

.back-btn {
  background: transparent;
  color: var(--text-secondary);
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 13px;
}

.back-btn:hover {
  color: var(--text-primary);
  background: var(--bg-hover);
}

.workspace-section label,
.session-info label {
  display: block;
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 6px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.workspace-row {
  display: flex;
  gap: 6px;
}

.workspace-row input {
  flex: 1;
  background: var(--bg-primary);
  border: 1px solid var(--border);
  border-radius: 4px;
  padding: 6px 8px;
  color: var(--text-primary);
  font-size: 12px;
}

.btn-secondary {
  background: var(--bg-hover);
  color: var(--text-primary);
  padding: 6px 10px;
  border-radius: 4px;
  font-size: 12px;
  white-space: nowrap;
}

.btn-secondary:hover {
  background: var(--border);
}

.session-info code {
  display: block;
  font-size: 12px;
  color: var(--text-secondary);
  background: var(--bg-primary);
  padding: 6px 8px;
  border-radius: 4px;
}

.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.message {
  display: flex;
  gap: 12px;
  max-width: 80%;
}

.message.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.message.user .avatar {
  background: var(--user-msg);
  color: white;
}

.message.assistant .avatar {
  background: var(--accent);
  color: white;
}

.bubble {
  background: var(--assistant-msg);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 12px 16px;
  line-height: 1.6;
}

.message.user .bubble {
  background: var(--user-msg);
  border-color: var(--user-msg);
}

.thinking {
  color: var(--text-secondary);
  font-style: italic;
}

.markdown-body {
  font-size: 14px;
  line-height: 1.6;
}

.markdown-body :deep(p) {
  margin: 0 0 8px;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(pre) {
  background: #0d1117;
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 12px;
  overflow-x: auto;
  margin: 8px 0;
}

.markdown-body :deep(code) {
  font-family: 'JetBrains Mono', 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
}

.markdown-body :deep(p code) {
  background: var(--bg-hover);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
}

.markdown-body :deep(pre code) {
  background: none;
  padding: 0;
  border-radius: 0;
}

.markdown-body :deep(ul), .markdown-body :deep(ol) {
  margin: 8px 0;
  padding-left: 20px;
}

.markdown-body :deep(li) {
  margin: 4px 0;
}

.markdown-body :deep(h1), .markdown-body :deep(h2), .markdown-body :deep(h3),
.markdown-body :deep(h4), .markdown-body :deep(h5), .markdown-body :deep(h6) {
  margin: 16px 0 8px;
  color: var(--text-primary);
}

.markdown-body :deep(h1) { font-size: 20px; }
.markdown-body :deep(h2) { font-size: 18px; }
.markdown-body :deep(h3) { font-size: 16px; }

.markdown-body :deep(blockquote) {
  border-left: 3px solid var(--accent);
  margin: 8px 0;
  padding: 4px 12px;
  color: var(--text-secondary);
  background: var(--bg-hover);
  border-radius: 0 4px 4px 0;
}

.markdown-body :deep(table) {
  border-collapse: collapse;
  margin: 8px 0;
  width: 100%;
}

.markdown-body :deep(th), .markdown-body :deep(td) {
  border: 1px solid var(--border);
  padding: 6px 12px;
  text-align: left;
}

.markdown-body :deep(th) {
  background: var(--bg-hover);
}

.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid var(--border);
  margin: 16px 0;
}

.markdown-body :deep(a) {
  color: var(--accent);
  text-decoration: none;
}

.markdown-body :deep(a:hover) {
  text-decoration: underline;
}

.empty-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  font-size: 15px;
}

.input-area {
  display: flex;
  gap: 10px;
  padding: 16px 20px;
  border-top: 1px solid var(--border);
  background: var(--bg-secondary);
}

.input-area textarea {
  flex: 1;
  background: var(--bg-primary);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 10px 12px;
  color: var(--text-primary);
  resize: none;
  outline: none;
}

.input-area textarea:focus {
  border-color: var(--accent);
}

.send-btn {
  align-self: flex-end;
  background: var(--accent);
  color: white;
  padding: 10px 20px;
  border-radius: var(--radius);
  font-weight: 500;
}

.send-btn:hover:not(:disabled) {
  background: var(--accent-hover);
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
