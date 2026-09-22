import { readFileSync, writeFileSync, existsSync } from "node:fs";

const CHAPTERS = [
  { id: "chapter-00", title: "第0章 序章与环境搭建", file: "docs/print/chapter-00.html", end: "docs/mindmaps/chapter-00-end.mmd" },
  { id: "chapter-01", title: "第1章 Java核心基础与实体建模", file: "docs/print/chapter-01.html", end: "docs/mindmaps/chapter-01-end.mmd" },
  { id: "chapter-02", title: "第2章 MySQL数据库设计与SQL实战", file: "docs/print/chapter-02.html", end: "docs/mindmaps/chapter-02-end.mmd" },
  { id: "chapter-03", title: "第3章 MyBatis与Spring Boot整合", file: "docs/print/chapter-03.html", end: "docs/mindmaps/chapter-03-end.mmd" },
  { id: "chapter-04", title: "第4章 高并发与Redis实战", file: "docs/print/chapter-04.html", end: "docs/mindmaps/chapter-04-end.mmd" },
  { id: "chapter-05", title: "第5章 路径规划与规则引擎", file: "docs/print/chapter-05.html", end: "docs/mindmaps/chapter-05-end.mmd" },
  { id: "chapter-06", title: "第6章 AI融合核心", file: "docs/print/chapter-06.html", end: "docs/mindmaps/chapter-06-end.mmd" },
  { id: "chapter-07", title: "第7章 Linux部署与运维", file: "docs/print/chapter-07.html", end: "docs/mindmaps/chapter-07-end.mmd" },
  { id: "chapter-08", title: "第8章 项目收尾与包装", file: "docs/print/chapter-08.html", end: "docs/mindmaps/chapter-08-end.mmd" }
];

const MINDMAPS = [
  { id: "overall", title: "总起导图", file: "docs/mindmaps/00-overall.md" },
  { id: "progress", title: "总进度导图", file: "docs/mindmaps/00-progress.md" },
  { id: "final", title: "项目结尾总复盘导图", file: "docs/mindmaps/99-final.md" }
];

const OUTPUT = "docs/print/full-book.html";

const escapeHtml = (text) =>
  String(text).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");

const inline = (text) =>
  escapeHtml(text).replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>").replace(/`([^`]+)`/g, "<code>$1</code>");

const readIfExists = (file) => (existsSync(file) ? readFileSync(file, "utf8") : "");

const extractStyle = (html) => {
  const match = html.match(/<style>([\s\S]*?)<\/style>/);
  return match ? match[1] : "";
};

const extractMain = (html) => {
  const match = html.match(/<main class="chapter">([\s\S]*?)<\/main>/);
  return match ? match[1].trim() : "";
};

const mindmapLines = (file) => {
  const raw = readIfExists(file).replace(/\r\n/g, "\n");
  if (!raw) {
    return [];
  }
  const mermaid = raw.match(/```mermaid\n([\s\S]*?)```/);
  const source = mermaid ? mermaid[1] : raw;
  return source
    .split("\n")
    .filter((line) => line.trim() !== "" && line.trim() !== "mindmap")
    .filter((line) => !/^```/.test(line.trim()));
};

const buildOutline = (lines) => {
  const nodes = lines.map((line) => {
    const indent = line.match(/^\s*/)[0].length;
    let text = line.trim().replace(/^[-*]\s+/, "");
    text = text.replace(/^root\(\((.*)\)\)$/, "$1");
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
      const children = next > childStart ? build(childStart, nodes[childStart].indent) : "";
      html.push(`<li>${label}${children}</li>`);
      index = next;
    }
    return `<ul>${html.join("")}</ul>`;
  };
  return nodes.length ? build(0, nodes[0].indent) : "";
};

const missing = CHAPTERS.filter((chapter) => !existsSync(chapter.file)).map((chapter) => chapter.file);
if (missing.length) {
  console.error("缺少章节打印版，请先运行 md2print.mjs：\n" + missing.join("\n"));
  process.exit(1);
}

const baseStyle = extractStyle(readIfExists(CHAPTERS[0].file));
const extraStyle = `
.cover { text-align: center; padding-top: 140px; page-break-after: always; break-after: page; }
.cover h1 { border: none; font-size: 27pt; page-break-before: avoid; break-before: avoid; }
.cover .subtitle { color: #57606a; font-size: 13pt; margin-top: 16px; }
.cover .meta { color: #57606a; font-size: 10.5pt; margin-top: 48px; }
.toc-page { page-break-after: always; break-after: page; }
.toc-page ol { line-height: 2.1; font-size: 12pt; }
.toc-page a { text-decoration: none; }
.mindmap-page { page-break-after: always; break-after: page; }
.mindmap-page .mindmap-outline { border: 1px solid var(--line); border-radius: 8px; background: #fbfdff; padding: 10px 18px; }
.mindmap-page ul { list-style: none; padding-left: 18px; border-left: 1px dashed #b9c6d4; margin: 4px 0; }
.mindmap-page > .mindmap-outline > ul { border-left: none; padding-left: 4px; }
.mindmap-page li { margin: 3px 0; }
.mindmap-page .mm-root-node { color: var(--accent); font-size: 12.5pt; }
.appendix table { font-size: 10pt; }
`;

const chapterSections = CHAPTERS.map((chapter) => {
  const content = extractMain(readIfExists(chapter.file));
  const withId = content.includes("<h1>")
    ? content.replace("<h1>", `<h1 id="${chapter.id}">`)
    : `<h1 id="${chapter.id}">${chapter.title}</h1>${content}`;
  const endLines = chapter.end && existsSync(chapter.end) ? mindmapLines(chapter.end) : [];
  const endOutline = endLines.length ? buildOutline(endLines) : "";
  const endBlock = endOutline
    ? `<h2>${chapter.title} · 本章复盘导图</h2>\n<div class="mindmap-outline">${endOutline}</div>`
    : "";
  return `<section class="chapter-section">\n${withId}\n${endBlock}\n</section>`;
}).join("\n");

const mindmapSections = MINDMAPS.map((mindmap) => {
  const lines = mindmapLines(mindmap.file);
  const outline = buildOutline(lines);
  return `<section class="mindmap-page">
<h1 id="${mindmap.id}">${mindmap.title}</h1>
<p>导图源码：<code>${mindmap.file}</code>（打印版以 Markdown 缩进大纲内嵌，离线可读；如已渲染 SVG 可替换本节）。</p>
<div class="mindmap-outline">${outline}</div>
</section>`;
}).join("\n");

const tocEntries = [
  ...MINDMAPS.map((mindmap) => `<li><a href="#${mindmap.id}">${mindmap.title}（大纲）</a></li>`),
  ...CHAPTERS.map((chapter) => `<li><a href="#${chapter.id}">${chapter.title}</a>（含本章开头与复盘导图大纲）</li>`),
  `<li><a href="#appendix-a">附录A：文档与仓库文件清单</a></li>`,
  `<li><a href="#appendix-b">附录B：验证边界说明</a></li>`
].join("\n");

const appendix = `<section class="appendix">
<h1 id="appendix-a">附录A：文档与仓库文件清单</h1>
<table>
<thead><tr><th>类别</th><th>文件</th><th>说明</th></tr></thead>
<tbody>
<tr><td>数据库</td><td>docs/architecture/01-database-design.md</td><td>七张核心表、席位复用、索引与事务</td></tr>
<tr><td>接口</td><td>docs/architecture/02-api-design.md</td><td>全部接口字段与错误码</td></tr>
<tr><td>算法</td><td>docs/architecture/03-algorithm-design.md</td><td>时间扩展图、Dijkstra/A*、规则引擎</td></tr>
<tr><td>业务</td><td>docs/domain/railway-domain-knowledge.md</td><td>铁路客运常识底座</td></tr>
<tr><td>缘起</td><td>docs/project-origin.md</td><td>华交与单杏花叙事</td></tr>
<tr><td>导图</td><td>docs/mindmaps/</td><td>总起、总进度、各章开头/结尾、总复盘</td></tr>
<tr><td>章节</td><td>docs/chapters/chapter-00.md ~ chapter-08.md</td><td>九章学习材料</td></tr>
<tr><td>打印</td><td>docs/print/chapter-00.html ~ chapter-08.html</td><td>每章 A4 打印版</td></tr>
<tr><td>部署</td><td>Dockerfile、docker-compose.yml、deploy/</td><td>镜像、编排、Nginx、监控配置</td></tr>
<tr><td>脚本</td><td>scripts/md2print.mjs、merge-print.mjs、grab-stress.ps1</td><td>离线打印与压测脚本</td></tr>
<tr><td>接口文档</td><td>/swagger-ui.html、/v3/api-docs</td><td>自实现 OpenAPI 3.0 文档</td></tr>
</tbody>
</table>
<h1 id="appendix-b">附录B：验证边界说明</h1>
<ul>
<li>打印总册由离线脚本合并，导图为缩进大纲，不依赖网络与浏览器 JS。</li>
<li>Docker/Nginx/Prometheus/Grafana/SkyWalking 未在本机实机启动，配置通过静态守护测试与复现手册验证。</li>
<li>MySQL/Redis/真实 LLM 未联机，默认使用内存与 Mock 双模式完成离线测试。</li>
<li>量化指标以 JUnit 单测与各章实测记录为准，模拟验证项均已标注。</li>
</ul>
</section>`;

const html = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>铁路智慧出行综合服务平台 — 打印总册</title>
<style>
${baseStyle}
${extraStyle}
</style>
</head>
<body>
<main class="chapter">
<section class="cover">
<h1>铁路智慧出行综合服务平台</h1>
<p class="subtitle">AI 主线 · 华交底色 · 广铁特色 · 通用基本盘</p>
<p class="subtitle">余票查询 · 高并发防超卖 · 中转换乘 · 智能购票助手 · 部署运维</p>
<p class="meta">学习材料打印总册 · 第0-8章 · 90 个自动化测试<br />生成时间：2026-09-15</p>
</section>

<section class="toc-page">
<h1>目录</h1>
<ol>
${tocEntries}
</ol>
</section>

${mindmapSections}

${chapterSections}

${appendix}
</main>
</body>
</html>
`;

writeFileSync(OUTPUT, html, "utf8");
console.log(`生成成功: ${OUTPUT} (${html.length} chars)`);
