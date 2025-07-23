package com.sankuai.xzf.dev.tech.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Response<T> implements Serializable {

    private String code;
    private String message;
    private T data;

    public static  Response success() {
        Response response = new Response();
        response.setCode("200");
        response.setMessage("成功");
        return response;
    }

    public static<T>  Response<T> success(T data) {
        Response<T> response = new Response<>();
        response.setCode("200");
        response.setMessage("成功");
        response.setData(data);
        return response;
    }
}
