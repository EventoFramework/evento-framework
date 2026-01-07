package com.evento.demo.command.invoker;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

@Component
public class Deref implements ApplicationContextAware {
    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public void send(String body) throws InterruptedException, ExecutionException {
        applicationContext.getBean(NotService.class).send(body);
    }
}
