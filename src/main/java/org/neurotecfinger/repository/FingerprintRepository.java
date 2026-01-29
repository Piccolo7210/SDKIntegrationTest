package org.neurotecfinger.repository;

import org.neurotecfinger.model.FingerprintEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FingerprintRepository extends JpaRepository<FingerprintEntity, Long> {

    // Find all fingers of a specific type (e.g., all "r_thumb"s)
    List<FingerprintEntity> findByFingerType(String fingerType);

    // You can add more custom queries here later if needed
}