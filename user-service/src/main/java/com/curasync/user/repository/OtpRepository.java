package com.curasync.user.repository;

import com.curasync.user.model.OtpRecord;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface OtpRepository extends MongoRepository<OtpRecord, String> {

    Optional<OtpRecord> findByPhone(String phone);

    void deleteByPhone(String phone);

}
