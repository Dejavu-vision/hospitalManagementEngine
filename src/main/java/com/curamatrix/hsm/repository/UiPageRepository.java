package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.UiPage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UiPageRepository extends JpaRepository<UiPage, Long> {
    Optional<UiPage> findByPageKey(String pageKey);
    List<UiPage> findByPageKeyIn(Collection<String> pageKeys);
    List<UiPage> findByIsActiveTrue();
}
