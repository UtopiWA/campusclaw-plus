import { expect, it } from 'vitest'
import { renderAssistantMarkdown } from './assistantMarkdown.js'

const render = (source) => {
  const element = document.createElement('div')
  element.innerHTML = renderAssistantMarkdown(source)
  return element
}

it.each([
  String.raw`结果是 $\frac{2}{3}+\frac{1}{6}=\frac{5}{6}$。`,
  String.raw`结果是 \(\frac{5}{6}\)。`,
  String.raw`计算如下：$$\frac{2}{3}+\frac{1}{6}=\frac{5}{6}$$，继续检查。`,
  String.raw`计算如下：\[\frac{5}{6}\]，继续检查。`,
  '$$\n\\frac{5}{6}\n$$',
  '\\[\n\\frac{5}{6}\n\\]',
])('renders supported formula delimiters in learning content: %s', (source) => {
  const element = render(source)
  expect(element.querySelector('.katex')).not.toBeNull()
  expect(element.querySelector('.katex-error')).toBeNull()
})

it('renders headings/lists but preserves code and unmatched formulas', () => {
  const element = render('# 评语\n\n- **通分正确**\n\n`$x$`\n\n```text\n\\[x\\]\n```\n\n$$未闭合\n\n## 下一段')
  expect(element.querySelector('h1').textContent).toBe('评语')
  expect(element.querySelector('li strong').textContent).toBe('通分正确')
  expect(element.querySelector('code').textContent).toBe('$x$')
  expect(element.querySelector('pre').textContent).toContain('\\[x\\]')
  expect(element.querySelector('h2').textContent).toBe('下一段')
  expect(element.querySelectorAll('.katex')).toHaveLength(0)
})

it('does not execute HTML, dangerous links or trusted KaTeX commands', () => {
  const element = render('<script>alert(1)</script>\n\n[链接](javascript:alert(1))\n\n$\\href{javascript:alert(1)}{危险}$')
  expect(element.querySelector('script')).toBeNull()
  expect(element.querySelector('[href^="javascript:"]')).toBeNull()
})

it.each(['$\frac{1}{2}+\frac{1}{4}$', '$\binom{3}{2}+\beta$', '$\boxed{1}$'])('repairs only known JSON control-character damage inside math', (source) => {
  const element = render(source)
  expect(element.querySelector('.katex')).not.toBeNull()
  expect(element.querySelector('.katex-error')).toBeNull()
})

it('preserves control characters outside math and does not infer commands from ordinary whitespace', () => {
  const element = render('原文\frac{1}{2}\n\n`$\frac{1}{2}$`\n\n$x\nright+y$')
  expect(element.querySelector('p').textContent).toContain('\frac{1}{2}')
  expect(element.querySelector('code').textContent).toBe('$\frac{1}{2}$')
  expect(element.querySelector('.katex annotation').textContent).toBe('x\nright+y')
})
