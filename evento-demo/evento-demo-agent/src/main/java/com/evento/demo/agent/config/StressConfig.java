package com.evento.demo.agent.config;

import com.evento.demo.api.utils.StressDB;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StressConfig {

    @Bean
    public StressDB stressDB(){
        var db =  new StressDB();
        db.init();
        return db;
    }
}
