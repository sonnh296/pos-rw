export type ApiError = {
  status: number
  message: string
  body?: any
}

export type ApiResponse<T> = 
  | { ok: true; data: T } 
  | { ok: false; error: ApiError }

function getBaseUrl() {
  const raw = import.meta.env.VITE_API_BASE_URL as string | undefined
  if (!raw) return ''
  return raw.replace(/\/+$/, '')
}

/**
 * Enhanced fetch wrapper with timeout and error handling
 */
export async function apiFetch<T>(
  path: string,
  init?: RequestInit & { timeoutMs?: number }
): Promise<ApiResponse<T>> {
  const url = `${getBaseUrl()}${path.startsWith('/') ? path : `/${path}`}`

  const controller = new AbortController()
  const timeoutMs = init?.timeoutMs
  const timeoutId =
    typeof timeoutMs === 'number' && timeoutMs > 0
      ? window.setTimeout(() => controller.abort(), timeoutMs)
      : null

  try {
    const res = await fetch(url, {
      ...init,
      headers: {
        'Accept': 'application/json',
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
          message: body?.message || `HTTP ${res.status}`,
          body,
        },
      }
    }

    return { ok: true, data: body as T }
  } catch (e: any) {
    const isAbort = e?.name === 'AbortError'
    return {
      ok: false,
      error: {
        status: 0,
        message: isAbort ? 'Yêu cầu quá hạn (Timeout)' : (e?.message ?? 'Lỗi kết nối mạng'),
      },
    }
  } finally {
    if (timeoutId != null) window.clearTimeout(timeoutId)
  }
}

export function jsonBody(body: unknown) {
  return {
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  } as const
}
