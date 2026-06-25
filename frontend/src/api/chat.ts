import type { Agent } from '../types'

export async function fetchAgents(): Promise<Agent[]> {
  const res = await fetch('/api/agents')
  return res.json()
}

export function streamChat(sessionId: string, userQuery: string): Promise<Response> {
  return fetch(`/api/code-assistant/chat?sessionId=${encodeURIComponent(sessionId)}&userQuery=${encodeURIComponent(userQuery)}`)
}

export async function getWorkspace(): Promise<string> {
  const res = await fetch('/api/workspace')
  const data = await res.json()
  return data.workspace
}

export async function setWorkspace(path: string): Promise<string> {
  const res = await fetch('/api/workspace', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ path }),
  })
  const data = await res.json()
  return data.workspace
}
