import { apiRequest } from './api.js'

const base = '/api/learning'
const send = (path, method, body, options = {}) => apiRequest(`${base}${path}`, { ...options, method, ...(body === undefined ? {} : { body: JSON.stringify(body) }) })
export const getLearningTags = (options) => apiRequest(`${base}/tags`, options)
export const replaceMaterialTags = (id, pointIds) => send(`/materials/${id}/tags`, 'PUT', { pointIds })
export const getLearningProfile = (options) => apiRequest(`${base}/profile`, options)
export const getLearningPlan = (options) => apiRequest(`${base}/plan`, options)
export const generateLearningPlan = (input, options) => send('/plan', 'POST', typeof input === 'string' ? { goal: input } : input, options)
export const completeLearningTask = (planId, taskId, options) => send(`/plans/${planId}/tasks/${taskId}/complete`, 'POST', undefined, options)
export const explainLearningPlan = (id, options) => send(`/plans/${id}/explanation`, 'POST', undefined, options)
export const saveLearningPoint = (id, body) => send(id ? `/tags/${id}` : '/tags', id ? 'PUT' : 'POST', body)
export const bindLearningMaterial = (pointId, materialId, remove = false) => send(`/points/${pointId}/materials${remove ? `/${materialId}` : ''}`, remove ? 'DELETE' : 'POST', remove ? undefined : { materialId })
export const getAssignmentPoints = (id) => apiRequest(`${base}/assignments/${id}/points`)
export const getClassLearning = (from, to, options) => apiRequest(`${base}/analytics?${new URLSearchParams({ ...(from ? { from } : {}), ...(to ? { to } : {}) })}`, options)
