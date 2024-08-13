package com.evento.demo.memory.query.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DemoRepository extends JpaRepository<Demo, String> {
}
