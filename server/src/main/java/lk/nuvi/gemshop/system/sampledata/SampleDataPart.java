package lk.nuvi.gemshop.system.sampledata;

/**
 * One part of the sample data (branches and users, shop details, gems, ...).
 * Each module adds its own part when its tables are built; parts run in {@link #order()}.
 */
public interface SampleDataPart {

    /** Lower runs first. 100 branches+users, 110 shop, 200 settings lists, 300 suppliers+gems, ... */
    int order();

    String name();

    /** Saves this part; returns how many records were saved. */
    int load(SampleData data);

    /**
     * Runs on a database that already has sample data from an earlier version of the app:
     * adds what this part brings when it is still missing (e.g. rates added in P1-02). Returns how many records were saved.
     */
    default int catchUp(SampleData data) {
        return 0;
    }
}
