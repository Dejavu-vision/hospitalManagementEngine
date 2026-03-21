package com.curamatrix.hsm.repository;

import com.curamatrix.hsm.entity.UserPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserPageRepository extends JpaRepository<UserPage, Long> {

    List<UserPage> findByUserIdAndTenantId(Long userId, Long tenantId);

    /** All GRANT page keys (extra pages added beyond role defaults). */
    @Query("SELECT up.page.pageKey FROM UserPage up WHERE up.user.id = :userId AND up.tenantId = :tenantId AND up.page.isActive = true AND up.effect = 'GRANT'")
    List<String> findGrantedPageKeysByUserIdAndTenantId(Long userId, Long tenantId);

    /** All DENY page keys (role-default pages explicitly blocked for this user). */
    @Query("SELECT up.page.pageKey FROM UserPage up WHERE up.user.id = :userId AND up.tenantId = :tenantId AND up.page.isActive = true AND up.effect = 'DENY'")
    List<String> findDeniedPageKeysByUserIdAndTenantId(Long userId, Long tenantId);

    /** Legacy — returns all (GRANT + DENY) active page keys. */
    @Query("SELECT up.page.pageKey FROM UserPage up WHERE up.user.id = :userId AND up.tenantId = :tenantId AND up.page.isActive = true")
    List<String> findActivePageKeysByUserIdAndTenantId(Long userId, Long tenantId);

    void deleteByUserIdAndTenantId(Long userId, Long tenantId);

    @Query("SELECT up FROM UserPage up WHERE up.user.id = :userId AND up.page.pageKey = :pageKey AND up.tenantId = :tenantId")
    Optional<UserPage> findByUserIdAndPageKeyAndTenantId(Long userId, String pageKey, Long tenantId);

    boolean existsByUserIdAndPagePageKeyAndTenantId(Long userId, String pageKey, Long tenantId);
}
