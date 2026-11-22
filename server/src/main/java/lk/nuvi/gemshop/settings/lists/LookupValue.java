package lk.nuvi.gemshop.settings.lists;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lk.nuvi.gemshop.common.persistence.BaseEntity;

/** One value in a drop-down list, e.g. GEM_TYPE "Blue Sapphire". */
@Entity
@Table(name = "lookup_value")
public class LookupValue extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "list_name", nullable = false, length = 30)
    private ListName list;

    @Column(nullable = false, length = 80)
    private String value;

    @Column(nullable = false)
    private int sortOrder;

    protected LookupValue() {}

    public LookupValue(ListName list, String value, int sortOrder) {
        this.list = list;
        this.value = value;
        this.sortOrder = sortOrder;
    }

    public ListName getList() { return list; }
    public String getValue() { return value; }
    public int getSortOrder() { return sortOrder; }

    void rename(String value) {
        this.value = value;
    }

    void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
