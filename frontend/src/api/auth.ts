export interface AuthProfile {
  id: number
  username: string
}

export async function getCurrentProfile(): Promise<AuthProfile | null> {
  const response = await fetch('/api/auth/me', { credentials: 'include' })
  if (response.status === 401) return null
  if (!response.ok) throw new Error('Unable to load authentication state')
  return response.json() as Promise<AuthProfile>
}
