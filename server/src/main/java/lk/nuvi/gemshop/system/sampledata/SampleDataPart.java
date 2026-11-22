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
}
