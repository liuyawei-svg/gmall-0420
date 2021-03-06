package com.atguigu.gmall.cart.config;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Autowired
    private UncaughtExceptionHandler exceptionHandler;
    /**
     * 自定义线程池
     * */

    @Override
    public Executor getAsyncExecutor() {
        return null;
    }
    /**
    *配制异步异常处理器
    * */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return exceptionHandler;
    }

}
