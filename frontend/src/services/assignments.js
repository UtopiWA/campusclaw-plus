import { apiRequest } from './api.js'

export const listAssignments = () => apiRequest('/api/assignments')

export const getAssignment = (id) => apiRequest(`/api/assignments/${id}`)

export const createAssignment = (payload) => apiRequest('/api/assignments', {
  method: 'POST',
  body: JSON.stringify(payload),
})

export const submitAssignment = (id, content, idempotencyKey) => apiRequest(
  `/api/assignments/${id}/submissions`,
  {
    method: 'POST',
    headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {},
    body: JSON.stringify({ content }),
  },
)

export const getGradingTask = (taskId, options = {}) => apiRequest(`/api/grading/tasks/${taskId}`, options)

export const listTeacherSubmissions = (id, options = {}) => apiRequest(`/api/assignments/${id}/submissions`, options)
