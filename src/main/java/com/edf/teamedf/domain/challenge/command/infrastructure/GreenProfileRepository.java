package com.edf.teamedf.domain.challenge.command.infrastructure;

import com.edf.teamedf.domain.challenge.command.domain.GreenProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GreenProfileRepository extends JpaRepository<GreenProfile, Long> {

    Optional<GreenProfile> findByUser_UserId(Long userId);
}
