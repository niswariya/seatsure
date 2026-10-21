import { useEffect, useState } from 'react'
import { api } from './api'
import type { AppConfig } from './types'

let cached: Promise<AppConfig> | null = null

/** Feature flags from the backend, fetched once per page load. */
export function useAppConfig(): AppConfig | null {
  const [config, setConfig] = useState<AppConfig | null>(null)
  useEffect(() => {
    cached ??= api.config().catch(() => ({ aiEnabled: false, holdSeconds: 300 }))
    let alive = true
    cached.then(c => { if (alive) setConfig(c) })
    return () => { alive = false }
  }, [])
  return config
}
