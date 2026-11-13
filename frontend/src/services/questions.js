import { apiRequest } from './api.js'
const base = '/api/questions'
export const listQuestions = (options) => apiRequest(base, options)
export const getQuestion = (id, options) => apiRequest(`${base}/${id}`, options)
export const saveQuestion = (id, body) => apiRequest(id ? `${base}/${id}` : base, { method: id ? 'PUT' : 'POST', body: JSON.stringify(body) })
export const setQuestionAvailability = (id, enabled) => apiRequest(`${base}/${id}/availability`, { method: 'PUT', body: JSON.stringify({ enabled }) })
export const deleteQuestion = (id) => apiRequest(`${base}/${id}`, { method: 'DELETE' })
export const submitQuestion = (id, content, key, contentVersion) => apiRequest(`${base}/${id}/responses`, { method: 'POST', headers: { 'Idempotency-Key': key, ...(contentVersion ? { 'If-Match': String(contentVersion) } : {}) }, body: JSON.stringify({ content }) })
export const getQuestionResponse = (id, options) => apiRequest(`${base}/${id}/response`, options)
