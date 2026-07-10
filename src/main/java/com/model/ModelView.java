package com.model;

import java.util.Map;
import java.util.HashMap;

public class ModelView {

    private String url;
    private Map<String, Object> data = new HashMap<>();

    public ModelView() {}

    public ModelView(String url) {
        this.url = url;
    }

    public void addAttribute(String key, Object value) {
        data.put(key, value);
    }

    public void setUrl(String url){
        this.url = url;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public String getUrl() {
        return url;
    }

}
