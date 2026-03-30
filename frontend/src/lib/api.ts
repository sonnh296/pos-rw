export type ApiError = {
  status: number
  message: string
  body?: unknown
}

function baseUrl() {
  const raw = import.meta.env.VITE_API_BASE_URL as string | undefined
  if (!raw) return ''
  return raw.replace(/\/+$/, '')
}

export async function apiFetch<T>(
  path: string,
  init?: RequestInit & { timeoutMs?: number }
): Promise<{ ok: true; data: T } | { ok: false; error: ApiError }> {
  const url = `${baseUrl()}${path.startsWith('/') ? path : `/${path}`}`

  const controller = new AbortController()
  const timeoutMs = init?.timeoutMs
  const timeout =
    typeof timeoutMs === 'number' && timeoutMs > 0
      ? window.setTimeout(() => controller.abort(), timeoutMs)
      : null

  try {
    const res = await fetch(url, {
      ...init,
      headers: {
        Accept: 'application/json',
        ...(init?.headers ?? {}),
      },
      signal: controller.signal,
    })

    const contentType = res.headers.get('content-type') ?? ''
    const isJson = contentType.includes('application/json')
    const body = isJson ? await res.json().catch(() => null) : await res.text().catch(() => '')

    if (!res.ok) {
      return {
        ok: false,
        error: {
          status: res.status,
          message: `HTTP ${res.status}`,
          body,
        },
      }
    }

    return { ok: true, data: body as T }
  } catch (e: any) {
    return {
      ok: false,
      error: {
        status: 0,
        message: e?.name === 'AbortError' ? 'TIMEOUT' : (e?.message ?? 'NETWORK_ERROR'),
      },
    }
  } finally {
    if (timeout != null) window.clearTimeout(timeout)
  }
}

export function jsonBody(body: unknown) {
  return {
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  } as const
}

