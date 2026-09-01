import { useEffect, useState } from 'react'

import { getAuthStatus, type AuthProfile } from '../api/auth'

export type AuthStatus =
  | { kind: 'loading' }
  | { kind: 'anonymous' }
  | { kind: 'authenticated'; profile: AuthProfile }

export function useAuthStatus(): AuthStatus {
  const [status, setStatus] = useState<AuthStatus>({ kind: 'loading' })

  useEffect(() => {
    let active = true
    void getAuthStatus().then((result) => {
      if (!active) return
      setStatus(result.authenticated && result.profile
        ? { kind: 'authenticated', profile: result.profile }
        : { kind: 'anonymous' })
    }).catch(() => {
      if (active) setStatus({ kind: 'anonymous' })
    })
    return () => { active = false }
  }, [])

  return status
}