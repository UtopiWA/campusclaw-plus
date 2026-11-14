import { beforeEach, describe, expect, it } from 'vitest'
import { authState } from '../services/auth.js'
import router, { authGuard } from './index.js'

describe('authGuard', () => {
  beforeEach(() => {
    authState.loaded = true
    authState.user = null
  })

  it('redirects an anonymous user away from a protected route', async () => {
    const result = await authGuard({ path: '/materials', fullPath: '/materials', meta: { requiresAuth: true } })
    expect(result).toEqual({ path: '/login', query: { redirect: '/materials' } })
  })

  it('redirects an authenticated user away from the login page', async () => {
    authState.user = { id: 1, role: 'teacher' }
    const result = await authGuard({ path: '/login', fullPath: '/login', meta: {} })
    expect(result).toBe('/home')
  })

  it('keeps the old search URL as a redirect to materials', () => {
    const route = router.getRoutes().find((item) => item.path === '/search')
    expect(route.redirect).toBe('/materials')
  })

  it('allows only teachers to enter assistant configuration', async () => {
    authState.user = { id: 1, role: 'student' }
    await expect(authGuard({ path: '/assistants', fullPath: '/assistants', meta: {
      requiresAuth: true, role: 'teacher',
    } })).resolves.toBe('/conversations')

    authState.user = { id: 2, role: 'teacher' }
    await expect(authGuard({ path: '/assistants', fullPath: '/assistants', meta: {
      requiresAuth: true, role: 'teacher',
    } })).resolves.toBe(true)
  })
})

it('registers protected mastery and plan subpages and keeps the old entry as a redirect', async () => {
  const routes = router.getRoutes()
  expect(routes.find((item) => item.path === '/learning').redirect).toBe('/learning/mastery')
  for (const path of ['/learning/mastery', '/learning/plan']) {
    const route = routes.find((item) => item.path === path)
    expect(route.meta).toMatchObject({ requiresAuth: true, role: 'student' })
    authState.user = { id: 2, role: 'teacher' }
    await expect(authGuard({ path, fullPath: path, meta: route.meta })).resolves.toBe('/conversations')
  }
})
