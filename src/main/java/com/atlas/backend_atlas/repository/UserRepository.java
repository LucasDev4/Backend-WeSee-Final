package com.atlas.backend_atlas.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.atlas.backend_atlas.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

}