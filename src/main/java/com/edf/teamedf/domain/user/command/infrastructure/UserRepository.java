package com.edf.teamedf.domain.user.command.infrastructure;

import com.edf.teamedf.domain.user.command.domain.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUuid(String uuid);
    Optional<User> findByProviderAndProviderId(String provider, String providerId);
    List<User> findAllByEmailIgnoreCase(String email);
    Optional<User> findByNickname(String nickname);
    boolean existsByNickname(String nickname);

    /**
     * 사용자 행을 잠그고 조회한다. 같은 사용자의 활동 인증 요청이 동시에 들어와도
     * 하루 인증 횟수·이동 시간 겹침 검사를 한 번에 하나씩 하도록 직렬화하는 용도.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userId = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);
}
