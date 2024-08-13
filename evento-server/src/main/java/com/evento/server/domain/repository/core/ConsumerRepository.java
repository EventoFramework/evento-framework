package com.evento.server.domain.repository.core;

import com.evento.server.domain.model.core.Component;
import com.evento.server.domain.model.core.Consumer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsumerRepository  extends JpaRepository<Consumer, String> {
    void deleteAllByInstanceId(String s);
}
