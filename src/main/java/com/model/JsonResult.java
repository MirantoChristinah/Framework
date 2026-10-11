
package com.model;

public class JsonResult {

    private final Object data;

    public JsonResult(Object data) {
        this.data = data;
    }

    public Object getData() {
        return data;
    }
}