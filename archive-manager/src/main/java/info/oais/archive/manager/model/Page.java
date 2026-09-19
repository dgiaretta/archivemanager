package info.oais.archive.manager.model;

import java.util.List;

/** A single page of results, plus enough to render Previous/Next controls and a total count. */
public record Page<T>(List<T> items, int page, int pageSize, long totalCount) {

    public int totalPages() {
        return pageSize <= 0 ? 1 : (int) Math.max(1, Math.ceil((double) totalCount / pageSize));
    }

    public boolean hasPrevious() {
        return page > 1;
    }

    public boolean hasNext() {
        return page < totalPages();
    }

    public int previousPage() {
        return Math.max(1, page - 1);
    }

    public int nextPage() {
        return Math.min(totalPages(), page + 1);
    }

    /** 1-based index of the first item on this page, for a "showing X-Y of Z" line. Zero if the page is empty. */
    public long firstItemNumber() {
        return items.isEmpty() ? 0 : (long) (page - 1) * pageSize + 1;
    }

    /** 1-based index of the last item on this page. */
    public long lastItemNumber() {
        return (long) (page - 1) * pageSize + items.size();
    }
}
