import { useEffect, useState } from 'react'

import { getCurrentProfile, type AuthProfile } from '../api/auth'

export type AuthStatus =
  | { kind: 'loading' }
  | { kind: 'anonymous' }
  | { kind: 'authenticated'; profile: AuthProfile }

export function useAuthStatus(): AuthStatus {
  const [status, setStatus] = useState<AuthStatus>({ kind: 'loading' })

  useEffect(() => {
    let active = true
    void getCurrentProfile().then((profile) => {
      if (!active) return
      setStatus(profile ? { kind: 'authenticated', profile } : { kind: 'anonymous' })
    }).catch(() => {
      if (active) setStatus({ kind: 'anonymous' })
    })
    return () => { active = false }
  }, [])

  return status
}
