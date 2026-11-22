package lk.nuvi.gemshop.common.numbers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lk.nuvi.gemshop.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/** P1-05: running numbers without gaps (BRD rule 4). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class NumbersTest {

    @Autowired Numbers numbers;
    @Autowired TransactionTemplate tx;

    @Test
    void formats() {
        assertThat(Numbers.format("GEM-", 5, 131)).isEqualTo("GEM-00131");
        assertThat(Numbers.format("INV-", 6, 141)).isEqualTo("INV-000141");
        assertThat(Numbers.format("CN-", 4, 12345)).isEqualTo("CN-12345"); // more than 9999 still works
    }

    @Test
    void continuesAfterThePrototype() {
        assertThat(numbers.lastUsed(Counter.GEM)).isGreaterThanOrEqualTo(130);
        assertThat(numbers.lastUsed(Counter.INVOICE)).isGreaterThanOrEqualTo(140);
        assertThat(numbers.lastUsed(Counter.EXPENSE)).isGreaterThanOrEqualTo(29);
        assertThat(numbers.lastUsed(Counter.QUOTE)).isGreaterThanOrEqualTo(2);
        assertThat(numbers.lastUsed(Counter.RESERVATION)).isGreaterThanOrEqualTo(2);
        assertThat(numbers.peek(Counter.TRANSFER)).matches("TR-\\d{4}");
    }

    @Test
    void nextNumberAndNoGapWhenTheDocumentFails() {
        String a = tx.execute(s -> numbers.next(Counter.CREDIT_NOTE));
        // a credit note that fails to save gives its number back
        tx.executeWithoutResult(s -> {
            numbers.next(Counter.CREDIT_NOTE);
            s.setRollbackOnly();
        });
        String b = tx.execute(s -> numbers.next(Counter.CREDIT_NOTE));
        assertThat(no(b)).isEqualTo(no(a) + 1);
        assertThat(a).startsWith("CN-");
    }

    @Test
    void onlyInsideTheTransactionThatSavesTheDocument() {
        assertThatThrownBy(() -> numbers.next(Counter.INVOICE)).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void twentySalesAtTheSameMomentGetTwentyDifferentNumbersInARow() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Callable<String>> jobs = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            jobs.add(() -> tx.execute(s -> numbers.next(Counter.HELD_SALE)));
        }
        List<Long> got = new ArrayList<>();
        for (Future<String> f : pool.invokeAll(jobs)) {
            got.add(no(f.get()));
        }
        pool.shutdown();
        Collections.sort(got);
        for (int i = 1; i < got.size(); i++) {
            assertThat(got.get(i)).isEqualTo(got.get(i - 1) + 1);
        }
    }

    private static long no(String n) {
        return Long.parseLong(n.substring(n.indexOf('-') + 1));
    }
}
