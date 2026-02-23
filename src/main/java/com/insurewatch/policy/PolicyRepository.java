package com.insurewatch.policy;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface PolicyRepository extends MongoRepository<Policy, String> {
    List<Policy> findByCustomerId(String customerId);
    Optional<Policy> findByCustomerIdAndStatus(String customerId, String status);
    Optional<Policy> findByPolicyNumber(String policyNumber);
}
