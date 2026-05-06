package io.justtrack;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.zip.GZIPOutputStream;

import io.justtrack.okhttp.Interceptor;
import io.justtrack.okhttp.MediaType;
import io.justtrack.okhttp.Request;
import io.justtrack.okhttp.RequestBody;
import io.justtrack.okhttp.Response;
import io.justtrack.okio.BufferedSink;
import io.justtrack.okio.Okio;

class GzipRequestInterceptor implements Interceptor {

    @NonNull
    @Override
    public Response intercept(Interceptor.Chain chain) throws IOException {
        Request originalRequest = chain.request();

        // If the request body is null or already compressed, return as is
        if (originalRequest.body() == null || originalRequest.header("Content-Encoding") != null) {
            return chain.proceed(originalRequest);
        }

        // Wrap the original body in a GZIP-compressed body
        Request compressedRequest = originalRequest.newBuilder()
                .header("Content-Encoding", "gzip")
                .method(originalRequest.method(), gzip(originalRequest.body()))
                .build();

        return chain.proceed(compressedRequest);
    }

    private RequestBody gzip(final RequestBody body) {
        return new RequestBody() {
            @Override
            public MediaType contentType() {
                return body.contentType();
            }

            @Override
            public long contentLength() {
                return -1;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                BufferedSink gzipSink = Okio.buffer(Okio.sink(new GZIPOutputStream(sink.outputStream())));
                body.writeTo(gzipSink);
                gzipSink.close();
            }
        };
    }
}