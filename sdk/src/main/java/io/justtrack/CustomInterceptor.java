package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import io.justtrack.okhttp.Headers;
import io.justtrack.okhttp.Interceptor;
import io.justtrack.okhttp.MediaType;
import io.justtrack.okhttp.Request;
import io.justtrack.okhttp.RequestBody;
import io.justtrack.okhttp.Response;
import io.justtrack.okhttp.ResponseBody;
import io.justtrack.okio.Buffer;

class CustomInterceptor implements Interceptor {
    static String POST = "POST";
    private final HttpModifier modifier;

    CustomInterceptor(HttpModifier modifier) {
        this.modifier = modifier;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request originalRequest = chain.request();
        Request modifiedRequest = modifyRequest(originalRequest);

        Response originalResponse = chain.proceed(modifiedRequest);
        return modifyResponse(originalResponse);
    }

    private Request modifyRequest(Request request) {
        try {
            @Nullable String originalBody = null;

            if (request.body() != null) {
                originalBody = readRequestBody(request.body());
            }
            HttpModifier.ModifiedRequest modified = modifier.modifyRequest(
                    request.url().toString(),
                    headersToMap(request.headers()),
                    originalBody
            );

            Request.Builder builder = request.newBuilder();
            builder.url(modified.getUrl());
            builder.headers(Headers.of(modified.getHeaders()));

            if (POST.equals(request.method())) {
                builder.method(
                        request.method(),
                        createRequestBody(request, modified.getBody() != null ? modified.getBody() : "")
                );
            }

            return builder.build();

        } catch (Exception exception) {
            //nop
        }
        return request;
    }

    private Response modifyResponse(Response response) {
        try {
            @Nullable String originalBody = null;
            if (response.body() != null) {
                originalBody = response.body().string();
            }

            HttpModifier.ModifiedResponse modified = modifier.modifyResponse(
                    response.request().url().toString(),
                    originalBody,
                    response.code(),
                    response.message()
            );
            ResponseBody newBody = createResponseBody(
                    response,
                    modified.getBody() != null ? modified.getBody() : ""
            );

            return response.newBuilder()
                    .body(newBody)
                    .build();

        } catch (IOException exception) {
            //nop
        }
        return  response;
    }

    private String readRequestBody(RequestBody body) throws IOException {
        Buffer buffer = new Buffer();
        body.writeTo(buffer);
        return buffer.readString(StandardCharsets.UTF_8);
    }

    private RequestBody createRequestBody(Request request, String modifiedBody) {
        MediaType contentType;
        if (request.body() != null) {
            contentType = request.body().contentType();
        } else {
            contentType = MediaType.parse("text/plain");
        }

        return RequestBody.create(modifiedBody, contentType);
    }

    private ResponseBody createResponseBody(Response response, String modifiedBody) {
        MediaType contentType;
        if (response.body() != null) {
            contentType = response.body().contentType();
        } else {
            contentType = MediaType.parse("text/plain");
        }

        return ResponseBody.create(modifiedBody, contentType);
    }

    private Map<String, String> headersToMap(Headers headers) {
        Map<String, String> map = new HashMap<>();
        for (String name : headers.names()) {
            map.put(name, headers.get(name));
        }
        return map;
    }
}
