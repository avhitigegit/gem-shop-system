package lk.nuvi.gemshop.files;

import java.util.Set;

/**
 * What an upload is for. Each kind says which file types it accepts.
 * Photos are made smaller (about 300 KB) and get a thumbnail; PDFs are kept as they are.
 */
public enum FileKind {
    /** Gem / jewellery photos (C1, purchases). Pictures only. */
    PRODUCT_PHOTO(Set.of(FileType.JPEG, FileType.PNG, FileType.WEBP), 1600, false),
    /** Bill / receipt photos for expenses and purchases (M1, B1). Picture or PDF. */
    BILL_PHOTO(Set.of(FileType.JPEG, FileType.PNG, FileType.WEBP, FileType.PDF), 1800, false),
    /** Lab or shop certificate scans (C3). Picture or PDF. */
    CERTIFICATE(Set.of(FileType.JPEG, FileType.PNG, FileType.WEBP, FileType.PDF), 2000, false),
    /** Shop logo for invoices and PDFs (Settings → Shop details). PNG keeps its see-through background. */
    LOGO(Set.of(FileType.JPEG, FileType.PNG, FileType.WEBP), 600, true);

    private final Set<FileType> accepts;
    private final int maxSide;
    private final boolean keepPng;

    FileKind(Set<FileType> accepts, int maxSide, boolean keepPng) {
        this.accepts = accepts;
        this.maxSide = maxSide;
        this.keepPng = keepPng;
    }

    public Set<FileType> accepts() {
        return accepts;
    }

    /** Longest side in pixels after resizing. */
    public int maxSide() {
        return maxSide;
    }

    /** Keep PNG pictures as PNG (logo); everything else becomes JPEG. */
    public boolean keepPng() {
        return keepPng;
    }
}
