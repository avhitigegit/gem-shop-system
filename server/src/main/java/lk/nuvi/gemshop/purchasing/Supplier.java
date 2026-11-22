package lk.nuvi.gemshop.purchasing;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lk.nuvi.gemshop.common.crypto.EncryptedString;
import lk.nuvi.gemshop.common.persistence.BaseEntity;

/** Who the shop buys gems from (BRD B1). The NIC / business registration number is stored encrypted. */
@Entity
@Table(name = "supplier")
public class Supplier extends BaseEntity {

    /** NIC = a person; BR = a business registration. */
    public enum IdType { NIC, BR }

    /** What the owner types on the form. */
    public record Details(String name, String contact, String phone, String address, IdType idType, String idNo,
            String notes) {}

    @Column(nullable = false, length = 12, updatable = false)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 120)
    private String contact;

    @Column(nullable = false, length = 40)
    private String phone;

    @Column(nullable = false, length = 300)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private IdType idType;

    @Convert(converter = EncryptedString.class)
    @Column(nullable = false, columnDefinition = "text")
    private String idNo;

    @Column(length = 500)
    private String notes;

    protected Supplier() {}

    public Supplier(String code, Details d) {
        this.code = code;
        change(d);
    }

    public void change(Details d) {
        this.name = d.name();
        this.contact = d.contact();
        this.phone = d.phone();
        this.address = d.address();
        this.idType = d.idType();
        this.idNo = d.idNo();
        this.notes = d.notes();
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getContact() { return contact; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public IdType getIdType() { return idType; }
    public String getIdNo() { return idNo; }
    public String getNotes() { return notes; }
}
