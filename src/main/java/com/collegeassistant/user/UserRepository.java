package com.collegeassistant.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRole(Role role);

    long countByRole(Role role);

    @Query("select u from User u where lower(u.name) like :p or lower(u.email) like :p order by u.createdAt desc")
    List<User> search(@Param("p") String pattern);
}
