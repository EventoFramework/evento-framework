package org.evento.demo.query.domain.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface DemoMongoRepository extends MongoRepository<DemoMongo, String> {
}
