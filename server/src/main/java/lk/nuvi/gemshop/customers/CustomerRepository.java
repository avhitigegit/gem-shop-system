package lk.nuvi.gemshop.customers;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByCode(String code);

    Optional<Customer> findByUnsubToken(String unsubToken);

    /** Customers with a WhatsApp number (WhatsApp STOP: matched by the digits in Java – the shop has a few thousand at most). */
    List<Customer> findByWhatsappIsNotNull();

    List<Customer> findAllByOrderByNameAsc();

    @Query("select c.code from Customer c")
    List<String> allCodes();
}
