package lk.nuvi.gemshop.settings.shop;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopSettingsRepository extends JpaRepository<ShopSettings, Long> {

    /** There is only one row. */
    default Optional<ShopSettings> current() {
        return findAll().stream().findFirst();
    }
}
