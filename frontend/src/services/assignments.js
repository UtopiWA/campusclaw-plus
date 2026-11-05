import { apiRequest } from './api.js'

export const listAssignments = () => apiRequest('/api/assignments')
export const getAssignment = (id, options = {}) => apiRequest(`/api/assignments/${id}`, options)


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
export const reviewSubmission = (id, submissionId, payload) => apiRequest(
  `/api/assignments/${id}/submissions/${submissionId}/review`,
  { method: 'PUT', body: JSON.stringify(payload) },
)
