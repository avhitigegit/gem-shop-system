package lk.nuvi.gemshop.settings.branch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lk.nuvi.gemshop.common.persistence.BaseEntity;

/** A shop branch (Settings → Branches). Each branch has its own stock and cash. */
@Entity
@Table(name = "branch")
public class Branch extends BaseEntity {

    /** Short fixed code used in links and numbers, e.g. {@code ella}, {@code b2}. */
    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    /** Name in tight places (top bar, tables), e.g. "Branch 2". */
    @Column(length = 60)
    private String shortName;

    @Column(length = 255)
    private String address;

    @Column(length = 40)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BranchStatus status;

    private LocalDate openedOn;

    protected Branch() {}

    public Branch(String code, String name, BranchStatus status) {
        this.code = code;
        this.name = name;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getShortName() {
        return shortName;
    }

    public String getDisplayName() {
        return shortName != null && !shortName.isBlank() ? shortName : name;
    }

    public String getAddress() {
        return address;
    }

    public String getPhone() {
        return phone;
    }

    public BranchStatus getStatus() {
        return status;
    }

    public LocalDate getOpenedOn() {
        return openedOn;
    }

    public Branch details(String shortName, String address, String phone, LocalDate openedOn) {
        this.shortName = shortName;
        this.address = address;
        this.phone = phone;
        this.openedOn = openedOn;
        return this;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void setStatus(BranchStatus status) {
        this.status = status;
    }
}
