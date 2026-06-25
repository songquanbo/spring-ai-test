export interface Agent {
  id: string
  name: string
  description: string
}

export interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
  timestamp: number
}
