package lk.nuvi.gemshop.purchasing;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    Optional<Purchase> findByNumber(String number);

    /** Purchases by supplier (or all), branch (or all) and date, newest first. */
    @Query("""
            select p from Purchase p
            where (:supplier is null or p.supplier.id = :supplier)
              and (:branch is null or p.branch.id = :branch)
              and p.date between :from and :to
            order by p.date desc, p.id desc""")
    List<Purchase> search(Long supplier, Long branch, LocalDate from, LocalDate to);

    List<Purchase> findBySupplierOrderByDateDescIdDesc(Supplier supplier);

    long countByDateBetween(LocalDate from, LocalDate to);

    /** Per supplier: number of purchases and total bought (supplier id, count, sum). */
    @Query("select p.supplier.id, count(p), sum(p.total) from Purchase p group by p.supplier.id")
    List<Object[]> totalsBySupplier();
}
