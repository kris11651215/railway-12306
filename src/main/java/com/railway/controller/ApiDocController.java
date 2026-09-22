package com.railway.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.railway.apidoc.ApiDocRegistry;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiDocController {

    private static final String SWAGGER_UI_HTML = """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>铁路智慧出行综合服务平台 API 文档</title>
            <style>
            body { margin: 0; background: #f6f8fa; color: #1f2328; font-family: "Microsoft YaHei", "PingFang SC", sans-serif; }
            main { max-width: 980px; margin: 0 auto; padding: 28px 24px 64px; background: #fff; min-height: 100vh; }
            h1 { border-bottom: 3px solid #0b5cad; padding-bottom: 10px; }
            h2 { color: #0b5cad; border-left: 4px solid #0b5cad; padding-left: 10px; margin-top: 32px; }
            .meta { color: #57606a; font-size: 14px; }
            .endpoint { border: 1px solid #d0d7de; border-radius: 8px; padding: 10px 14px; margin: 12px 0; }
            .method { display: inline-block; min-width: 52px; text-align: center; border-radius: 4px; color: #fff; font-weight: 700; font-size: 12px; padding: 2px 8px; margin-right: 8px; }
            .method.get { background: #1a7f37; }
            .method.post { background: #0b5cad; }
            .summary { color: #57606a; margin: 6px 0; }
            table { border-collapse: collapse; width: 100%; font-size: 13px; margin-top: 6px; }
            th, td { border: 1px solid #d0d7de; padding: 4px 8px; text-align: left; }
            th { background: #eef3f8; }
            code { background: #eef1f4; padding: 1px 5px; border-radius: 4px; }
            </style>
            </head>
            <body>
            <main>
            <h1>铁路智慧出行综合服务平台 API 文档</h1>
            <p class="meta">OpenAPI 3.0 描述文件：<a href="/v3/api-docs">/v3/api-docs</a>；字段细节见 docs/architecture/02-api-design.md。</p>
            <div id="app">加载中...</div>
            </main>
            <script>
            function esc(value) {
              return String(value).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
            }
            fetch('/v3/api-docs').then(function (response) { return response.json(); }).then(function (doc) {
              var groups = {};
              Object.keys(doc.paths || {}).forEach(function (path) {
                Object.keys(doc.paths[path]).forEach(function (method) {
                  var operation = doc.paths[path][method];
                  var tag = (operation.tags && operation.tags[0]) || '其他';
                  if (!groups[tag]) { groups[tag] = []; }
                  groups[tag].push({ path: path, method: method, operation: operation });
                });
              });
              var html = '';
              Object.keys(groups).forEach(function (tag) {
                html += '<h2>' + esc(tag) + '</h2>';
                groups[tag].forEach(function (item) {
                  html += '<div class="endpoint"><div><span class="method ' + esc(item.method) + '">'
                    + esc(item.method.toUpperCase()) + '</span><code>' + esc(item.path) + '</code></div>';
                  html += '<div class="summary">' + esc(item.operation.summary || '') + '</div>';
                  var params = item.operation.parameters || [];
                  if (params.length) {
                    html += '<table><tr><th>参数</th><th>位置</th><th>必填</th><th>说明</th></tr>';
                    params.forEach(function (parameter) {
                      html += '<tr><td>' + esc(parameter.name) + '</td><td>' + esc(parameter.in)
                        + '</td><td>' + (parameter.required ? '是' : '否') + '</td><td>'
                        + esc(parameter.description || '') + '</td></tr>';
                    });
                    html += '</table>';
                  }
                  html += '</div>';
                });
              });
              document.getElementById('app').innerHTML = html || '未获取到接口定义';
            }).catch(function (error) {
              document.getElementById('app').textContent = '加载 OpenAPI 失败：' + error;
            });
            </script>
            </body>
            </html>
            """;

    private final ApiDocRegistry apiDocRegistry;

    public ApiDocController(ApiDocRegistry apiDocRegistry) {
        this.apiDocRegistry = apiDocRegistry;
    }

    @GetMapping(value = "/v3/api-docs", produces = MediaType.APPLICATION_JSON_VALUE)
    public ObjectNode openApi() {
        return apiDocRegistry.toOpenApi();
    }

    @GetMapping(value = "/swagger-ui.html", produces = MediaType.TEXT_HTML_VALUE)
    public String swaggerUi() {
        return SWAGGER_UI_HTML;
    }
}
