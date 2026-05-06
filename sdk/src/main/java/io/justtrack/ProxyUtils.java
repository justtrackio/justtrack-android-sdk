package io.justtrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.justtrack.integrations.ProxyHandler;
import io.justtrack.log.Logger;
import io.justtrack.log.LoggerFieldsBuilder;

/**
 * This proxy solution will be replaced with adapter sdk in the future.
 * There is no point in converting this confusing logic to kotlin.
 */
public class ProxyUtils {
    @NonNull
    static Object createProxy(
            @NonNull Class<?> implementsInterface,
            @NonNull Method method,
            @NonNull ProxyHandler handler,
            @NonNull Logger logger
    ) {
        return createProxy(implementsInterface, null, Collections.singletonMap(method, handler), logger);
    }

    @NonNull
    public static Object createProxy(
            @NonNull Class<?> implementsInterface,
            @Nullable Object proxiedObject,
            @NonNull Method method,
            @NonNull ProxyHandler handler,
            @NonNull Logger logger
    ) {
        return createProxy(implementsInterface, proxiedObject, Collections.singletonMap(method, handler), logger);
    }

    @NonNull
    static Object createProxy(
            @NonNull Class<?> implementsInterface,
            @Nullable Object proxiedObject,
            @NonNull Map<Method, ProxyHandler> handlers,
            @NonNull Logger logger
    ) {
        Map<Method, ProxyHandler> mergedHandlers = proxiedObject == null ? proxyBaseObject(new Object(), handlers, logger) : handlers;

        return Proxy.newProxyInstance(implementsInterface.getClassLoader(), new Class[]{implementsInterface}, (proxy, method, args) -> {
            ProxyHandler handler = mergedHandlers.get(method);
            if (handler != null) {
                return handler.handle(args == null ? new Object[0] : args);
            }

            if (proxiedObject != null) {
                if (args != null) {
                    return method.invoke(proxiedObject, args);
                }

                return method.invoke(proxiedObject);
            }

            logger.error("Failed to handle call to proxied method", new LoggerFieldsBuilder()
                    .with("declaringClass", method.getDeclaringClass().getName())
                    .with("methodName", method.getName())
                    .with("returnType", method.getReturnType().getName()));

            return null;
        });
    }

    static Map<Method, ProxyHandler> proxyBaseObject(
            @NonNull Object baseObject,
            Map<Method, ProxyHandler> handlers,
            Logger logger
    ) {
        try {
            Map<Method, ProxyHandler> mergedHandlers = new HashMap<>(handlers);
            Method hashCodeMethod = Object.class.getMethod("hashCode");
            Method equalsMethod = Object.class.getMethod("equals", Object.class);
            Method toStringMethod = Object.class.getMethod("toString");
            Method notifyMethod = Object.class.getMethod("notify");
            Method notifyAllMethod = Object.class.getMethod("notifyAll");
            Method waitMethod1 = Object.class.getMethod("wait");
            Method waitMethod2 = Object.class.getMethod("wait", long.class);
            Method waitMethod3 = Object.class.getMethod("wait", long.class, int.class);
            // no need to override getClass, that is provided by the proxy

            mergedHandlers.put(hashCodeMethod, args -> baseObject.hashCode());
            mergedHandlers.put(equalsMethod, args -> baseObject.equals(args[0]));
            mergedHandlers.put(toStringMethod, args -> "proxied " + baseObject);
            mergedHandlers.put(notifyMethod, args -> {
                synchronized (baseObject) {
                    baseObject.notify();
                }

                return null;
            });
            mergedHandlers.put(notifyAllMethod, args -> {
                synchronized (baseObject) {
                    baseObject.notifyAll();
                }

                return null;
            });
            mergedHandlers.put(waitMethod1, args -> {
                synchronized (baseObject) {
                    baseObject.wait();
                }

                return null;
            });
            mergedHandlers.put(waitMethod2, args -> {
                synchronized (baseObject) {
                    baseObject.wait((Long) args[0]);
                }

                return null;
            });
            mergedHandlers.put(waitMethod3, args -> {
                synchronized (baseObject) {
                    baseObject.wait((Long) args[0], (Integer) args[1]);
                }

                return null;
            });

            return mergedHandlers;
        } catch (Throwable exception) {
            logger.error("Failed to setup base handlers", exception);

            return handlers;
        }
    }


}
