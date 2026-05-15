package com.example.tracing.javaservice.trace;

import org.springframework.data.repository.CrudRepository;

public interface TraceRequestRepository extends CrudRepository<TraceRequestEntity, Long> {
}
