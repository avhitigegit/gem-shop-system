package lk.nuvi.gemshop.gems;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface GemRepository extends JpaRepository<Gem, Long>, JpaSpecificationExecutor<Gem> {

    Optional<Gem> findByCode(String code);

    /** Gems bought from a supplier that are in one of these statuses (returns to supplier). */
    java.util.List<Gem> findBySupplierCodeIsNotNullAndStatusInOrderByCodeDesc(java.util.Collection<GemStatus> statuses);

    @Query("select count(g) from Gem g where g.gemType = :v")
    long countGemType(String v);

    @Query("select count(g) from Gem g where g.productType = :v")
    long countProductType(String v);

    @Query("select count(g) from Gem g where g.colour = :v")
    long countColour(String v);

    @Query("select count(g) from Gem g where g.shape = :v")
    long countShape(String v);

    @Query("select count(g) from Gem g where g.treatment = :v")
    long countTreatment(String v);

    @Query("select count(g) from Gem g where g.certLab = :v")
    long countLab(String v);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Gem g set g.gemType = :to where g.gemType = :from")
    int renameGemType(String from, String to);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Gem g set g.productType = :to where g.productType = :from")
    int renameProductType(String from, String to);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Gem g set g.colour = :to where g.colour = :from")
    int renameColour(String from, String to);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Gem g set g.shape = :to where g.shape = :from")
    int renameShape(String from, String to);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Gem g set g.treatment = :to where g.treatment = :from")
    int renameTreatment(String from, String to);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Gem g set g.certLab = :to where g.certLab = :from")
    int renameLab(String from, String to);
}
