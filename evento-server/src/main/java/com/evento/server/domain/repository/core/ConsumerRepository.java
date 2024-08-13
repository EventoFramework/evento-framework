package com.evento.server.domain.repository.core;

import com.evento.server.domain.model.core.Component;
import com.evento.server.domain.model.core.Consumer;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsumerRepository  extends JpaRepository<Consumer, String> {

    @Transactional
    void deleteAllByInstanceId(String s);

    List<Consumer> findAllByConsumerId(String consumerId);
}
