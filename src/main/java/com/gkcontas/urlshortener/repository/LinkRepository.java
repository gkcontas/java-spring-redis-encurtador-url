package com.gkcontas.urlshortener.repository;

import com.gkcontas.urlshortener.model.Link;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LinkRepository extends JpaRepository<Link, Long> {

    Optional<Link> findByCode(String code);

    @Modifying
    @Query("UPDATE Link l SET l.totalAccesses = l.totalAccesses + 1 WHERE l.code = :code")
    void incrementAccesses(@Param("code") String code);
}
