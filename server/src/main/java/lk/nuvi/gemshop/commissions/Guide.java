package lk.nuvi.gemshop.commissions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lk.nuvi.gemshop.common.persistence.BaseEntity;

/** A guide or driver who brings customers (BRD F4, H1) and earns their own % of the sale. Screens come with P3-05. */
@Entity
@Table(name = "guide")
public class Guide extends BaseEntity {

    public record Details(String name, String vehicle, String vehicleNo, String phone, BigDecimal pct, LocalDate since) {}

    @Column(nullable = false, length = 12, updatable = false)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 40)
    private String vehicle;

    @Column(length = 20)
    private String vehicleNo;

    @Column(nullable = false, length = 40)
    private String phone;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal pct;

    private LocalDate since;

    @Column(nullable = false)
    private boolean active = true;

    protected Guide() {}

    public Guide(String code, Details d) {
        this.code = code;
        change(d);
    }

    public void change(Details d) {
        this.name = d.name();
        this.vehicle = d.vehicle();
        this.vehicleNo = d.vehicleNo();
        this.phone = d.phone();
        this.pct = d.pct();
        this.since = d.since();
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getVehicle() { return vehicle; }
    public String getVehicleNo() { return vehicleNo; }
    public String getPhone() { return phone; }
    public BigDecimal getPct() { return pct; }
    public LocalDate getSince() { return since; }
    public boolean isActive() { return active; }
}
