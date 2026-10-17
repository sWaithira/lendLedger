package com.lendLedger.backend.borrower;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Tenant isolation (ADR-05): this extends the bare Repository marker, NOT JpaRepository, so the
 * unscoped findById / findAll / deleteById methods do not exist here. Every query takes a tenantId.
 */
public interface BorrowerRepository extends Repository<Borrower, UUID> {

    Borrower save(Borrower borrower);

    Optional<Borrower> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Borrower> findByTenantIdAndPhoneNormalized(UUID tenantId, String phoneNormalized);

    Page<Borrower> findByTenantIdAndStatus(UUID tenantId, BorrowerStatus status, Pageable pageable);

    @Query("""
            select b from Borrower b
            where b.tenantId = :tenantId
              and b.status = :status
              and (lower(b.fullName) like :pattern or b.phoneNormalized like :phonePattern)
            """)
    Page<Borrower> search(@Param("tenantId") UUID tenantId,
                          @Param("status") BorrowerStatus status,
                          @Param("pattern") String pattern,
                          @Param("phonePattern") String phonePattern,
                          Pageable pageable);
}