package org.eventrails.demo.query.domain.mysql;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DemoMysqlRepository extends JpaRepository<DemoMysql, String> {
}
