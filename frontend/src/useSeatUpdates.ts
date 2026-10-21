import { Client } from '@stomp/stompjs'
import { useEffect, useRef, useState } from 'react'
import type { SeatsChangedMessage } from './types'

export type LiveState = 'connecting' | 'live' | 'offline'

/**
 * Subscribes to live seat changes for one event. Reconnects automatically.
 * `onChange` is kept in a ref so re-renders don't tear down the socket.
 */
export function useSeatUpdates(eventId: number, onChange: (msg: SeatsChangedMessage) => void): LiveState {
  const [state, setState] = useState<LiveState>('connecting')
  const handler = useRef(onChange)
  handler.current = onChange

  useEffect(() => {
    const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const client = new Client({
      brokerURL: `${scheme}://${window.location.host}/ws`,
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        setState('live')
        client.subscribe(`/topic/events/${eventId}/seats`, frame => {
          try {
            handler.current(JSON.parse(frame.body) as SeatsChangedMessage)
          } catch {
            /* ignore malformed frame */
          }
        })
      },
      onWebSocketClose: () => setState('offline'),
      onStompError: () => setState('offline'),
    })
    client.activate()
    return () => {
      void client.deactivate()
    }
  }, [eventId])

  return state
}
