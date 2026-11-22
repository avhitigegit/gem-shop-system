package lk.nuvi.gemshop.purchasing;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierReturnRepository extends JpaRepository<SupplierReturn, Long> {

    List<SupplierReturn> findAllByOrderByReturnedAtDescIdDesc();

    List<SupplierReturn> findBySupplierOrderByReturnedAtDescIdDesc(Supplier supplier);
}
