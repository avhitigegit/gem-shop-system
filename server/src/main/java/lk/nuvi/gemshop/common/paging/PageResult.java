package lk.nuvi.gemshop.common.paging;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** One page of a list: the rows, how many there are in total, and which page this is (0 = first). */
public record PageResult<T>(@Schema(requiredMode = REQUIRED) List<T> items, @Schema(requiredMode = REQUIRED) long total,
        @Schema(requiredMode = REQUIRED) int page, @Schema(requiredMode = REQUIRED) int size) {

    public static <E, T> PageResult<T> of(Page<E> page, Function<E, T> map) {
        return new PageResult<>(page.getContent().stream().map(map).toList(), page.getTotalElements(),
                page.getNumber(), page.getSize());
    }

    /** Page size from the request, kept between 1 and {@code max}. */
    public static int size(Integer asked, int fallback, int max) {
        return asked == null ? fallback : Math.max(1, Math.min(asked, max));
    }
}
