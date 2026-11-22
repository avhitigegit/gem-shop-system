package lk.nuvi.gemshop.settings.lists;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LookupValueRepository extends JpaRepository<LookupValue, Long> {

    List<LookupValue> findByListOrderBySortOrderAscIdAsc(ListName list);

    List<LookupValue> findAllByOrderByListAscSortOrderAscIdAsc();

    @Query("select v from LookupValue v where v.list = :list and lower(v.value) = lower(:value)")
    Optional<LookupValue> findSame(ListName list, String value);

    @Query("select coalesce(max(v.sortOrder), 0) from LookupValue v where v.list = :list")
    int lastOrder(ListName list);

    long countByList(ListName list);
}
