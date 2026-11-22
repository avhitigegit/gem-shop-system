package lk.nuvi.gemshop.files;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    Optional<StoredFile> findByPublicId(UUID publicId);

    List<StoredFile> findByOwnerTypeAndOwnerRefOrderByIdAsc(String ownerType, String ownerRef);

    @Query("select f from StoredFile f where f.ownerType is null and f.createdAt < :before")
    List<StoredFile> findUnattachedBefore(@Param("before") Instant before);
}
