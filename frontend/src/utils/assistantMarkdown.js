import MarkdownIt from 'markdown-it'
import katex from 'katex'

const markdown = new MarkdownIt({
  html: false,
  breaks: true,
  linkify: true,
  typographer: false,
})

function renderMath(source, displayMode) {
  // 兼容旧JSON把\frac等命令的首字母解析为控制字符；只修复公式中的明确命令。
  /* eslint-disable no-control-regex -- 此处有意匹配JSON转义产生的控制字符，限定为已知公式命令。 */
  const normalized = source
    .replace(/\u000c(rac|box)(?=\s*\{)/g, '\\f$1')
    .replace(/\u0008(inom|ar|egin|oxed)(?=\s*\{)/g, '\\b$1')
    .replace(/\u0008eta\b/g, '\\beta')
  /* eslint-enable no-control-regex */
  return katex.renderToString(normalized, {
    displayMode,
    throwOnError: false,
    strict: 'warn',
    trust: false,
  })
}

markdown.inline.ruler.before('escape', 'math_display_inline', (state, silent) => {
  const delimiter = state.src.startsWith('$$', state.pos) ? '$$'
    : state.src.startsWith('\\[', state.pos) ? '\\]' : null
  if (!delimiter) return false
  const end = state.src.indexOf(delimiter, state.pos + 2)
  if (end <= state.pos + 2) return false
  if (!silent) {
    const token = state.push('math_display_inline', 'math', 0)
    token.content = state.src.slice(state.pos + 2, end)
  }
  state.pos = end + 2
  return true
})

markdown.inline.ruler.before('escape', 'math_inline', (state, silent) => {
  if (state.src[state.pos] !== '$' || state.src[state.pos + 1] === '$') return false
  let end = state.pos + 1
  while ((end = state.src.indexOf('$', end)) >= 0) {
    if (state.src[end - 1] !== '\\') break
    end += 1
  }
  if (end < 0 || end === state.pos + 1) return false
  if (!silent) {
    const token = state.push('math_inline', 'math', 0)
    token.content = state.src.slice(state.pos + 1, end)
  }
  state.pos = end + 1
  return true
})

markdown.inline.ruler.before('escape', 'math_parentheses', (state, silent) => {
  if (!state.src.startsWith('\\(', state.pos)) return false
  const end = state.src.indexOf('\\)', state.pos + 2)
  if (end < 0 || end === state.pos + 2) return false
  if (!silent) {
    const token = state.push('math_inline', 'math', 0)
    token.content = state.src.slice(state.pos + 2, end)
  }
  state.pos = end + 2
  return true
})

markdown.block.ruler.before('fence', 'math_block', (state, startLine, endLine, silent) => {
  const start = state.bMarks[startLine] + state.tShift[startLine]
  const firstLine = state.src.slice(start, state.eMarks[startLine]).trim()
  const closingDelimiter = firstLine.startsWith('$$') ? '$$'
    : firstLine.startsWith('\\[') ? '\\]' : null
  if (!closingDelimiter) return false
  if (silent) return true

  const lines = []
  const openingRemainder = firstLine.slice(2)
  if (openingRemainder.includes(closingDelimiter) && !openingRemainder.endsWith(closingDelimiter)) return false
  if (openingRemainder.endsWith(closingDelimiter)) {
    lines.push(openingRemainder.slice(0, -2))
    state.line = startLine + 1
  } else {
    if (openingRemainder) lines.push(openingRemainder)
    let nextLine = startLine + 1
    for (; nextLine < endLine; nextLine += 1) {
      const line = state.src.slice(state.bMarks[nextLine] + state.tShift[nextLine], state.eMarks[nextLine])
      if (line.trim().endsWith(closingDelimiter)) {
        lines.push(line.trim().slice(0, -2))
        break
      }
      if (line.includes(closingDelimiter)) return false
      lines.push(line)
    }
    if (nextLine === endLine) return false
    state.line = Math.min(nextLine + 1, endLine)
  }
  const token = state.push('math_block', 'math', 0)
  token.block = true
  token.content = lines.join('\n').trim()
  token.map = [startLine, state.line]
  return true
})

markdown.inline.ruler.before('link', 'conversation_citation', (state, silent) => {
  if (state.src[state.pos] !== '[') return false
  const match = /^\[(\d+)](?!\()/.exec(state.src.slice(state.pos))
  if (!match || !state.env?.citationNumbers?.has(Number(match[1]))) return false
  if (!silent) {
    const token = state.push('conversation_citation', '', 0)
    token.meta = { number: Number(match[1]) }
  }
  state.pos += match[0].length
  return true
})

markdown.renderer.rules.math_inline = (tokens, index) => renderMath(tokens[index].content, false)
markdown.renderer.rules.math_display_inline = (tokens, index) => renderMath(tokens[index].content, true)
markdown.renderer.rules.math_parentheses = markdown.renderer.rules.math_inline
markdown.renderer.rules.math_block = (tokens, index) => `${renderMath(tokens[index].content, true)}\n`
markdown.renderer.rules.conversation_citation = (tokens, index) => {
  const number = tokens[index].meta.number
  return `<button class="chat-inline-citation" type="button" data-citation-number="${number}" aria-label="查看引用 ${number}">[${number}]</button>`
}

export function renderAssistantMarkdown(content, citations = []) {
  const citationNumbers = new Set(citations.map((item) => Number(item.number)))
  return markdown.render(String(content || ''), { citationNumbers })
}
