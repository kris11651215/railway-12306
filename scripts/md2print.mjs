import { readFileSync, writeFileSync } from "node:fs";

const args = process.argv.slice(2);
const input = args[0];
const output = args[1];
const chapterTitle = args[2] ?? "铁路智慧出行综合服务平台学习材料";
const mindmapIndex = args.indexOf("--mindmap");
const mindmapFile = mindmapIndex >= 0 ? args[mindmapIndex + 1] : null;

if (!input || !output) {
  console.error("用法: node scripts/md2print.mjs <input.md> <output.html> \"<章节标题>\" [--mindmap <file.mmd>]");
  process.exit(1);
}

const escapeHtml = (text) =>
  text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");

const inline = (text) => {
  const tokens = [];
  let html = escapeHtml(text).replace(/`([^`]+)`/g, (_, code) => {
    tokens.push(`<code>${code}</code>`);
    return `\u0000${tokens.length - 1}\u0000`;
  });
  html = html.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
  html = html.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2">$1</a>');
  html = html.replace(/\u0000(\d+)\u0000/g, (_, index) => tokens[Number(index)]);
  return html;
};

const isTableSeparator = (line) => /^\|?[\s:|-]+\|[\s:|-]*$/.test(line) && line.includes("-");

const splitRow = (line) =>
  line.replace(/^\|/, "").replace(/\|$/, "").split("|").map((cell) => cell.trim());

const lines = readFileSync(input, "utf8").replace(/\r\n/g, "\n").split("\n");
const out = [];
let i = 0;

while (i < lines.length) {
  const line = lines[i];

  if (line.trim() === "") {
    i += 1;
    continue;
  }

  const fence = line.match(/^```(\w*)\s*$/);
  if (fence) {
    const lang = fence[1];
    const code = [];
    i += 1;
    while (i < lines.length && !/^```\s*$/.test(lines[i])) {
      code.push(lines[i]);
      i += 1;
    }
    i += 1;
    const cls = lang ? ` class="language-${lang}"` : "";
    out.push(`<pre><code${cls}>${escapeHtml(code.join("\n"))}</code></pre>`);
    continue;
  }

  if (line.startsWith("|") && i + 1 < lines.length && isTableSeparator(lines[i + 1])) {
    const header = splitRow(line);
    i += 2;
    const rows = [];
    while (i < lines.length && lines[i].startsWith("|")) {
      rows.push(splitRow(lines[i]));
      i += 1;
    }
    out.push("<table>");
    out.push("<thead>");
    out.push("<tr>" + header.map((cell) => `<th>${inline(cell)}</th>`).join("") + "</tr>");
    out.push("</thead>");
    out.push("<tbody>");
    for (const row of rows) {
      out.push("<tr>" + row.map((cell) => `<td>${inline(cell)}</td>`).join("") + "</tr>");
    }
    out.push("</tbody>");
    out.push("</table>");
    continue;
  }

  if (line.startsWith(">")) {
    const quote = [];
    while (i < lines.length && lines[i].startsWith(">")) {
      quote.push(lines[i].replace(/^>\s?/, ""));
      i += 1;
    }
    out.push("<blockquote>");
    out.push("<p>" + quote.map((q) => inline(q)).join("<br />\n") + "</p>");
    out.push("</blockquote>");
    continue;
  }

  const heading = line.match(/^(#{1,4})\s+(.*)$/);
  if (heading) {
    const level = heading[1].length;
    out.push(`<h${level}>${inline(heading[2].trim())}</h${level}>`);
    i += 1;
    continue;
  }

  if (/^---+\s*$/.test(line)) {
    out.push("<hr />");
    i += 1;
    continue;
  }

  const listMatch = line.match(/^(\s*)([-*]|\d+\.)\s+(.*)$/);
  if (listMatch) {
    const ordered = /\d+\./.test(listMatch[2]);
    const baseIndent = listMatch[1].length;
    const items = [];
    while (i < lines.length) {
      const current = lines[i].match(/^(\s*)([-*]|\d+\.)\s+(.*)$/);
      if (!current || current[1].length !== baseIndent) {
        break;
      }
      items.push(current[3]);
      i += 1;
    }
    const tag = ordered ? "ol" : "ul";
    out.push(`<${tag}>`);
    for (const item of items) {
      out.push(`<li>${inline(item)}</li>`);
    }
    out.push(`</${tag}>`);
    continue;
  }

  const paragraph = [line.trim()];
  i += 1;
  while (i < lines.length && lines[i].trim() !== "" &&
         !/^(#{1,4})\s+/.test(lines[i]) && !lines[i].startsWith("|") &&
         !lines[i].startsWith(">") && !/^```/.test(lines[i]) &&
         !/^\s*([-*]|\d+\.)\s+/.test(lines[i]) && !/^---+\s*$/.test(lines[i])) {
    paragraph.push(lines[i].trim());
    i += 1;
  }
  out.push(`<p>${inline(paragraph.join(" "))}</p>`);
}

const renderMindmap = (file) => {
  if (!file) {
    return "";
  }
  const raw = readFileSync(file, "utf8").replace(/\r\n/g, "\n").split("\n")
    .filter((line) => line.trim() !== "" && line.trim() !== "mindmap");
  const nodes = raw.map((line) => {
    const indent = line.match(/^\s*/)[0].length;
    let text = line.trim().replace(/^root\(\((.*)\)\)$/, "$1");
    return { indent, text };
  });
  const build = (start, depth) => {
    const html = [];
    let index = start;
    while (index < nodes.length && nodes[index].indent >= depth) {
      if (nodes[index].indent > depth) {
        index += 1;
        continue;
      }
      const node = nodes[index];
      const childStart = index + 1;
      let next = childStart;
      while (next < nodes.length && nodes[next].indent > depth) {
        next += 1;
      }
      const label = index === 0 && depth === nodes[0].indent
        ? `<strong class="mm-root-node">${inline(node.text)}</strong>`
        : inline(node.text);
      const children = next > childStart
        ? build(childStart, nodes[childStart].indent)
        : "";
      html.push(`<li>${label}${children}</li>`);
      index = next;
    }
    return `<ul>${html.join("")}</ul>`;
  };
  const body = nodes.length ? build(0, nodes[0].indent) : "";
  return `<h2>本章开头导图 · 打印备份</h2>
<p>Mermaid 源码：<code>${file.replace(/\\/g, "/")}</code>（可用 <code>mmdc -i ${file.replace(/\\/g, "/")} -o docs/print/assets/${file.split(/[\\/]/).pop().replace(/\.mmd$/, "")}.svg</code> 渲染为 SVG 后替换本节）。</p>
<div class="mindmap-outline">${body}</div>`;
};

let body = out.join("\n");
if (mindmapFile) {
  body = body.replace("</blockquote>", "</blockquote>\n" + renderMindmap(mindmapFile));
}

const css = `:root { --ink:#1f2328; --muted:#57606a; --line:#d0d7de; --accent:#0b5cad; }
* { box-sizing: border-box; }
html { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
body {
  margin: 0; color: var(--ink); background: #f6f8fa;
  font-family: "Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC", "Source Han Sans SC", sans-serif;
  font-size: 11pt; line-height: 1.75;
}
.chapter { max-width: 860px; margin: 0 auto; padding: 36px 44px 72px; background: #fff; }
@media screen { .chapter { box-shadow: 0 1px 4px rgba(0,0,0,.08); margin-top: 24px; margin-bottom: 24px; } }
h1 { font-size: 22pt; border-bottom: 3px solid var(--accent); padding-bottom: 10px; margin: 0 0 22px; }
h2 { font-size: 15pt; color: var(--accent); border-left: 4px solid var(--accent); padding-left: 10px; margin: 28px 0 12px; }
h3 { font-size: 12.5pt; margin: 20px 0 8px; }
h4 { font-size: 11.5pt; margin: 16px 0 6px; }
p { margin: 8px 0; }
blockquote { margin: 12px 0; padding: 8px 14px; border-left: 3px solid var(--line); background: #f6f8fa; color: var(--muted); border-radius: 0 6px 6px 0; }
blockquote p { margin: 2px 0; }
table { border-collapse: collapse; width: 100%; margin: 12px 0; font-size: 10pt; }
th, td { border: 1px solid var(--line); padding: 6px 8px; text-align: left; vertical-align: top; }
th { background: #eef3f8; }
tr:nth-child(even) td { background: #fafcfe; }
pre { background: #0f172a; color: #e6edf3; padding: 12px 14px; border-radius: 6px; overflow-x: auto; font-size: 9.5pt; line-height: 1.6; }
pre code { font-family: "Cascadia Mono", Consolas, "Courier New", monospace; background: transparent; padding: 0; }
code { font-family: "Cascadia Mono", Consolas, "Courier New", monospace; background: #eef1f4; padding: 1px 4px; border-radius: 4px; font-size: 95%; }
ul, ol { padding-left: 24px; }
li { margin: 4px 0; }
hr { border: none; border-top: 1px dashed var(--line); margin: 24px 0; }
a { color: var(--accent); }
.mindmap-outline { border: 1px solid var(--line); border-radius: 8px; background: #fbfdff; padding: 10px 18px; margin: 12px 0 20px; }
.mindmap-outline ul { list-style: none; padding-left: 18px; border-left: 1px dashed #b9c6d4; margin: 4px 0; }
.mindmap-outline > ul { border-left: none; padding-left: 4px; }
.mindmap-outline li { margin: 3px 0; }
.mindmap-outline .mm-root-node { color: var(--accent); font-size: 12pt; }
.print-header, .print-footer { display: none; }

@page {
  size: A4;
  margin: 18mm 14mm 20mm 14mm;
  @top-left { content: "铁路智慧出行综合服务平台"; font-size: 8.5pt; color: #666; }
  @top-right { content: "${chapterTitle}"; font-size: 8.5pt; color: #666; }
  @bottom-center { content: "第 " counter(page) " 页 / 共 " counter(pages) " 页"; font-size: 8.5pt; color: #666; }
}

@media print {
  body { background: #fff; }
  .chapter { max-width: none; margin: 0; padding: 0; box-shadow: none; }
  h1 { page-break-before: always; break-before: page; }
  h1:first-of-type { page-break-before: avoid; break-before: auto; }
  h2, h3 { page-break-after: avoid; break-after: avoid; }
  pre, table, blockquote, tr, img { page-break-inside: avoid; break-inside: avoid; }
  thead { display: table-header-group; }
  .print-header {
    display: flex; justify-content: space-between; position: fixed;
    top: 0; left: 0; right: 0; padding: 2mm 0 1.5mm;
    border-bottom: .5pt solid #ccc; font-size: 8.5pt; color: #666;
  }
  .print-footer {
    display: flex; justify-content: space-between; position: fixed;
    bottom: 0; left: 0; right: 0; padding: 1.5mm 0 2mm;
    border-top: .5pt solid #ccc; font-size: 8.5pt; color: #666;
  }
}`;

const html = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>${chapterTitle} — 铁路智慧出行综合服务平台</title>
<style>
${css}
</style>
</head>
<body>
<header class="print-header">
  <span>铁路智慧出行综合服务平台</span>
  <span>${chapterTitle}</span>
</header>
<main class="chapter">
${body}
</main>
<footer class="print-footer">
  <span>docs/print/${output.split(/[\\/]/).pop()}</span>
  <span>${chapterTitle}</span>
</footer>
</body>
</html>
`;

writeFileSync(output, html, "utf8");
console.log(`生成成功: ${output} (${html.length} chars)`);
