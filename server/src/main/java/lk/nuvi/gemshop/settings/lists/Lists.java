package lk.nuvi.gemshop.settings.lists;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lk.nuvi.gemshop.audit.log.ActivityLog;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.common.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drop-down lists (BRD A5, NFR-8): gem types, product types, colours, shapes, labs, treatments, expense types.
 * Changes show in every form straight away. Forms read {@link #values}; modules check a value with {@link #check}.
 */
@Service
public class Lists {

    public static final int MAX_LENGTH = 80;

    public static final String LIST_DUPLICATE = "LIST_DUPLICATE";
    public static final String LIST_IN_USE = "LIST_IN_USE";
    public static final String LIST_LAST_VALUE = "LIST_LAST_VALUE";
    public static final String LIST_VALUE = "LIST_VALUE";

    private final LookupValueRepository repo;
    private final List<ListUsage> usages;
    private final ActivityLog log;

    public Lists(LookupValueRepository repo, List<ListUsage> usages, ActivityLog log) {
        this.repo = repo;
        this.usages = usages;
        this.log = log;
    }

    /** One value with how many records use it ("Used by"). */
    public record Item(long id, String value, long usedBy, String unit) {}

    /** The values of one list, in order (for drop-downs). */
    @Transactional(readOnly = true)
    public List<String> values(ListName list) {
        return repo.findByListOrderBySortOrderAscIdAsc(list).stream().map(LookupValue::getValue).toList();
    }

    /** All lists (for drop-downs): list → values in order. */
    @Transactional(readOnly = true)
    public Map<ListName, List<String>> all() {
        Map<ListName, List<String>> out = new EnumMap<>(ListName.class);
        for (ListName l : ListName.values()) {
            out.put(l, new ArrayList<>());
        }
        repo.findAllByOrderByListAscSortOrderAscIdAsc().forEach(v -> out.get(v.getList()).add(v.getValue()));
        return out;
    }

    /** The Drop-down lists screen: each list with "Used by". */
    @Transactional(readOnly = true)
    public Map<ListName, List<Item>> withUsage() {
        Map<ListName, List<Item>> out = new EnumMap<>(ListName.class);
        for (ListName l : ListName.values()) {
            out.put(l, new ArrayList<>());
        }
        repo.findAllByOrderByListAscSortOrderAscIdAsc()
                .forEach(v -> out.get(v.getList()).add(new Item(v.getId(), v.getValue(), used(v.getList(), v.getValue()),
                        unit(v.getList()))));
        return out;
    }

    /**
     * For modules saving a record: the value must be in the list. Returns the value as written in the list
     * ("blue sapphire" → "Blue Sapphire"). {@code field} = the form field to mark red.
     */
    @Transactional(readOnly = true)
    public String check(ListName list, String value, String field) {
        String v = value == null ? "" : value.trim();
        return repo.findSame(list, v).map(LookupValue::getValue).orElseThrow(() -> ApiException.field(HttpStatus.BAD_REQUEST,
                LIST_VALUE, "Choose a " + list.one() + " from the list.", field, "Choose from the list."));
    }

    @Transactional
    public LookupValue add(ListName list, String value) {
        String v = clean(value);
        duplicate(list, v, null);
        LookupValue saved = repo.save(new LookupValue(list, v, repo.lastOrder(list) + 1));
        log.add("List value added", list.label() + ": " + v, null);
        return saved;
    }

    /** Rename; records using the old value get the new one too. */
    @Transactional
    public LookupValue rename(long id, String value) {
        LookupValue item = get(id);
        String v = clean(value);
        String old = item.getValue();
        if (old.equals(v)) {
            return item;
        }
        duplicate(item.getList(), v, item.getId());
        item.rename(v);
        repo.flush();
        usages.forEach(u -> u.rename(item.getList(), old, v));
        log.add("List value renamed", item.getList().label() + ": " + old + " → " + v, null);
        return item;
    }

    /** "↑": swap with the value above. */
    @Transactional
    public void moveUp(long id) {
        LookupValue item = get(id);
        List<LookupValue> list = repo.findByListOrderBySortOrderAscIdAsc(item.getList());
        int i = list.indexOf(item);
        if (i <= 0) {
            return;
        }
        // renumber the whole list, so equal or missing numbers never confuse the order
        LookupValue above = list.get(i - 1);
        list.set(i - 1, item);
        list.set(i, above);
        for (int n = 0; n < list.size(); n++) {
            list.get(n).setSortOrder(n + 1);
        }
    }

    /** Remove a value nobody uses ("Rename it instead" when it is used). */
    @Transactional
    public void remove(long id) {
        LookupValue item = get(id);
        long n = used(item.getList(), item.getValue());
        if (n > 0) {
            throw ApiException.conflict(LIST_IN_USE, item.getValue() + " is used by " + n + " " + unit(item.getList())
                    + ". Rename it instead.");
        }
        if (repo.countByList(item.getList()) <= 1) {
            throw ApiException.conflict(LIST_LAST_VALUE, "A list needs at least one value.");
        }
        repo.delete(item);
        log.add("List value removed", item.getList().label() + ": " + item.getValue(), null);
    }

    private LookupValue get(long id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("This list value no longer exists."));
    }

    private long used(ListName list, String value) {
        return usages.stream().mapToLong(u -> u.count(list, value)).sum();
    }

    private String unit(ListName list) {
        return usages.stream().map(u -> u.unit(list)).filter(s -> s != null && !s.isBlank()).findFirst()
                .orElse(list == ListName.EXPENSE_TYPE ? "expense(s)" : "gem(s)");
    }

    private void duplicate(ListName list, String value, Long self) {
        repo.findSame(list, value).filter(v -> !v.getId().equals(self)).ifPresent(v -> {
            throw ApiException.field(HttpStatus.CONFLICT, LIST_DUPLICATE, "Already in the list: " + v.getValue(), "value",
                    "Already in the list.");
        });
    }

    private static String clean(String value) {
        String v = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (v.isEmpty()) {
            throw ApiException.field(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Please enter a value.", "value", "Required.");
        }
        if (v.length() > MAX_LENGTH) {
            throw ApiException.field(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "At most " + MAX_LENGTH + " characters.", "value",
                    "At most " + MAX_LENGTH + " characters.");
        }
        return v;
    }
}
