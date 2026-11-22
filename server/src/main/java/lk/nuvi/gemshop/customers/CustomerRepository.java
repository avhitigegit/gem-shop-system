package lk.nuvi.gemshop.customers;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByCode(String code);

    List<Customer> findAllByOrderByNameAsc();

    @Query("select c.code from Customer c")
    List<String> allCodes();
}
