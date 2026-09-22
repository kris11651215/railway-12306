package com.railway.apidoc;

import java.util.List;
import java.util.Map;

public class ApiEndpointDoc {

    private final String method;
    private final String path;
    private final String tag;
    private final String summary;
    private final List<Map<String, Object>> parameters;
    private final Map<String, Object> requestBody;
    private final String responseDescription;
    private final String responseContentType;

    public ApiEndpointDoc(String method, String path, String tag, String summary,
                          List<Map<String, Object>> parameters, Map<String, Object> requestBody,
                          String responseDescription, String responseContentType) {
        this.method = method;
        this.path = path;
        this.tag = tag;
        this.summary = summary;
        this.parameters = List.copyOf(parameters);
        this.requestBody = requestBody;
        this.responseDescription = responseDescription;
        this.responseContentType = responseContentType;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getTag() {
        return tag;
    }

    public String getSummary() {
        return summary;
    }

    public List<Map<String, Object>> getParameters() {
        return parameters;
    }

    public Map<String, Object> getRequestBody() {
        return requestBody;
    }

    public String getResponseDescription() {
        return responseDescription;
    }

    public String getResponseContentType() {
        return responseContentType;
    }

    public String key() {
        return method.toUpperCase() + " " + path;
    }
}
